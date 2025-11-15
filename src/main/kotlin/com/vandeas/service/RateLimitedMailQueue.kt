package com.vandeas.service

import com.vandeas.entities.MailQueueItem
import com.vandeas.entities.QueuedMailResult
import com.vandeas.entities.SendOperationResult
import com.vandeas.utils.Constants
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

/**
 * A rate-limited mail queue that processes emails with configurable throughput.
 *
 * @property mailer The underlying mailer implementation to use for sending
 * @property rateLimit Maximum number of emails to send per second (default: 10)
 * @property workerCount Number of concurrent workers to process emails (default: 5)
 * @property scope Coroutine scope for queue processing
 */
class RateLimitedMailQueue(
    private val mailer: Mailer,
    private val rateLimit: Int = Constants.mailRateLimit,
    private val workerCount: Int = 5,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val logger = KtorSimpleLogger("com.vandeas.service.RateLimitedMailQueue")

    // FIFO queue - items are processed in the order they are enqueued
    // Note: MailQueueItem has a priority field for future enhancement, but it's currently unused
    private val queue = Channel<MailQueueItem>(Channel.UNLIMITED)

    // Results flow for external consumers
    private val _results = MutableSharedFlow<QueuedMailResult>(replay = 0)
    val results = _results.asSharedFlow()

    // Tracking
    private val sentCount = AtomicLong(0)
    private val queuedCount = AtomicLong(0)
    private val activeWorkers = AtomicInteger(0)

    // Track retry jobs and worker jobs for proper shutdown and error handling
    private val retryJobs = mutableListOf<Job>()
    private val workerJobs = mutableListOf<Job>()

    private val interval = 1.seconds
    private val tokensAvailable = AtomicInteger(rateLimit)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    init {
        logger.info("Initializing RateLimitedMailQueue with rate limit: $rateLimit emails/second, workers: $workerCount")
        startWorkerPool()
    }

    /**
     * Add a mail item to the queue.
     *
     * @param item The mail queue item to process
     * @return The reference ID for tracking this operation
     */
    suspend fun enqueue(item: MailQueueItem): String {
        queuedCount.incrementAndGet()
        queue.send(item)
        logger.debug("Enqueued mail with reference: ${item.reference} (priority: ${item.priority})")
        return item.reference
    }

    /**
     * Add multiple mail items to the queue.
     *
     * @param items List of mail queue items to process
     * @return List of reference IDs for tracking these operations
     */
    suspend fun enqueueAll(items: List<MailQueueItem>): List<String> {
        return items.map { enqueue(it) }
    }

    /**
     * Get current queue statistics.
     */
    fun getStats(): QueueStats {
        return QueueStats(
            queued = queuedCount.get(),
            sent = sentCount.get(),
            activeWorkers = activeWorkers.get(),
            rateLimit = rateLimit
        )
    }

    /**
     * Get the number of currently active workers.
     */
    fun getActiveWorkers(): Int = activeWorkers.get()

    /**
     * Refill rate limit tokens based on time elapsed.
     * Thread-safe implementation using atomic operations.
     */
    @OptIn(ExperimentalTime::class)
    private fun refillTokens() {
        val now = Clock.System.now().toEpochMilliseconds()
        val lastRefill = lastRefillTime.get()
        val timeElapsed = now - lastRefill

        if (timeElapsed >= interval.inWholeMilliseconds) {
            // Atomic compare-and-set to avoid race conditions
            if (lastRefillTime.compareAndSet(lastRefill, now)) {
                tokensAvailable.set(rateLimit)
                logger.debug("Refilled tokens: $rateLimit")
            }
        }
    }

    /**
     * Check if we can send an email based on rate limit.
     * Thread-safe implementation using atomic operations.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun acquireToken() {
        while (true) {
            refillTokens()

            // Atomically decrement if a token is available
            val previous = tokensAvailable.getAndUpdate { cur -> if (cur > 0) cur - 1 else cur }
            if (previous > 0) {
                return
            }

            // Calculate how long to wait until next refill
            val now = Clock.System.now().toEpochMilliseconds()
            val lastRefill = lastRefillTime.get()
            val timeUntilRefill = interval.inWholeMilliseconds - (now - lastRefill)

            if (timeUntilRefill > 0) {
                logger.debug("Rate limit reached, waiting ${timeUntilRefill}ms")
                delay(timeUntilRefill)
                
                // After delay, refill tokens and try immediately to avoid extra loop iteration
                refillTokens()
                val afterDelay = tokensAvailable.getAndUpdate { cur -> if (cur > 0) cur - 1 else cur }
                if (afterDelay > 0) {
                    return
                }
            }
        }
    }

    /**
     * Start a pool of worker coroutines to process the queue concurrently.
     * Each worker runs independently and processes items from the shared queue.
     */
    private fun startWorkerPool() {
        logger.info("Starting worker pool with $workerCount workers")

        repeat(workerCount) { workerId ->
            val job = scope.launch {
                logger.info("Worker #$workerId started")
                activeWorkers.incrementAndGet()

                try {
                    for (item in queue) {
                        try {
                            processMailItem(item, workerId)
                        } catch (e: CancellationException) {
                            logger.info("Worker #$workerId cancelled while processing")
                            throw e
                        } catch (e: Exception) {
                            logger.error("Worker #$workerId error processing item: ${e.message}", e)
                            // Continue processing next items even if one fails
                        }
                    }
                } catch (e: CancellationException) {
                    logger.info("Worker #$workerId cancelled")
                    throw e
                } finally {
                    activeWorkers.decrementAndGet()
                    logger.info("Worker #$workerId stopped")
                }
            }

            synchronized(workerJobs) {
                workerJobs.add(job)
            }
        }

        logger.info("Worker pool started with $workerCount workers")
    }

    /**
     * Process a single mail item from the queue.
     * @param workerId The ID of the worker processing this item (for logging)
     */
    private suspend fun processMailItem(item: MailQueueItem, workerId: Int) {
        try {
            // Acquire rate limit token before sending
            acquireToken()

            logger.debug("Worker #$workerId processing mail with reference: ${item.reference}")

            val result = mailer.sendEmail(
                to = item.mail.to,
                from = item.mail.from,
                subject = item.mail.subject,
                content = item.mail.content
            )

            // Handle the result
            when {
                result.sent.isNotEmpty() -> {
                    sentCount.incrementAndGet()
                    logger.info("Worker #$workerId successfully sent mail to ${item.mail.to} (ref: ${item.reference})")
                    _results.emit(QueuedMailResult(item.reference, result))
                }

                result.temporary.isNotEmpty() && item.retryCount < item.maxRetries -> {
                    // Re-queue for retry with exponential backoff
                    val retryDelay = (1000L * (item.retryCount + 1)).milliseconds
                    logger.warn("Temporary failure for ${item.mail.to}, retrying in $retryDelay (attempt ${item.retryCount + 1}/${item.maxRetries})")

                    // Emit intermediate result to notify consumers about the retry attempt
                    _results.emit(QueuedMailResult(item.reference, result))

                    // Track retry job for proper error handling and shutdown
                    val retryJob = scope.launch {
                        try {
                            delay(retryDelay)
                            val retryItem = item.copy(retryCount = item.retryCount + 1)
                            enqueue(retryItem)
                        } catch (e: CancellationException) {
                            logger.info("Retry cancelled for ${item.mail.to} (ref: ${item.reference})")
                            throw e
                        } catch (e: Exception) {
                            logger.error("Error re-enqueueing mail ${item.reference}: ${e.message}", e)
                            // Emit final failure result if re-enqueue fails
                            _results.emit(
                                QueuedMailResult(
                                    item.reference,
                                    SendOperationResult(failed = listOf(item.mail.to))
                                )
                            )
                        }
                    }
                    synchronized(retryJobs) {
                        retryJobs.add(retryJob)
                        // Clean up completed jobs to prevent memory leak
                        retryJobs.removeAll { it.isCompleted }
                    }
                }

                result.bounced.isNotEmpty() || result.failed.isNotEmpty() -> {
                    logger.error("Failed to send mail to ${item.mail.to} (ref: ${item.reference})")
                    _results.emit(QueuedMailResult(item.reference, result))
                }
            }
        } catch (e: Exception) {
            logger.error("Error processing mail item ${item.reference}: ${e.message}", e)
            _results.emit(
                QueuedMailResult(
                    item.reference,
                    SendOperationResult(failed = listOf(item.mail.to))
                )
            )
        }
    }

    /**
     * Shutdown the queue processor gracefully.
     */
    fun shutdown() {
        logger.info("Shutting down queue processor")
        queue.close()
        scope.cancel()
    }

    data class QueueStats(
        val queued: Long,
        val sent: Long,
        val activeWorkers: Int,
        val rateLimit: Int
    )
}
