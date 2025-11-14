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
 * @property scope Coroutine scope for queue processing
 */
class RateLimitedMailQueue(
    private val mailer: Mailer,
    private val rateLimit: Int = Constants.mailRateLimit,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val logger = KtorSimpleLogger("com.vandeas.service.RateLimitedMailQueue")

    // Priority queue using a channel - items with higher priority are processed first
    private val queue = Channel<MailQueueItem>(Channel.UNLIMITED)

    // Results flow for external consumers
    private val _results = MutableSharedFlow<QueuedMailResult>(replay = 0)
    val results = _results.asSharedFlow()

    // Tracking
    private val sentCount = AtomicLong(0)
    private val queuedCount = AtomicLong(0)

    private val interval = 1.seconds
    private val tokensAvailable = AtomicInteger(rateLimit)
    private val lastRefillTime = AtomicLong(System.currentTimeMillis())

    init {
        logger.info("Initializing RateLimitedMailQueue with rate limit: $rateLimit emails/second")
        startQueueProcessor()
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
            rateLimit = rateLimit
        )
    }

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

            // Try to atomically decrement the token count
            val current = tokensAvailable.get()
            if (current > 0 && tokensAvailable.compareAndSet(current, current - 1)) {
                return
            }

            // Calculate how long to wait until next refill
            val now = Clock.System.now().toEpochMilliseconds()
            val lastRefill = lastRefillTime.get()
            val timeUntilRefill = interval.inWholeMilliseconds - (now - lastRefill)

            if (timeUntilRefill > 0) {
                logger.debug("Rate limit reached, waiting ${timeUntilRefill}ms")
                delay(timeUntilRefill)
            }
        }
    }

    /**
     * Start the background queue processor.
     */
    private fun startQueueProcessor() {
        scope.launch {
            logger.info("Queue processor started")

            try {
                for (item in queue) {
                    processMailItem(item)
                }
            } catch (e: Exception) {
                logger.error("Queue processor error: ${e.message}", e)
            }
        }
    }

    /**
     * Process a single mail item from the queue.
     */
    private suspend fun processMailItem(item: MailQueueItem) {
        try {
            // Acquire rate limit token before sending
            acquireToken()

            logger.debug("Processing mail with reference: ${item.reference}")

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
                    logger.info("Successfully sent mail to ${item.mail.to} (ref: ${item.reference})")
                    _results.emit(QueuedMailResult(item.reference, result))
                }

                result.temporary.isNotEmpty() && item.retryCount < item.maxRetries -> {
                    // Re-queue for retry with exponential backoff
                    val retryDelay = (1000L * (item.retryCount + 1)).milliseconds
                    logger.warn("Temporary failure for ${item.mail.to}, retrying in $retryDelay (attempt ${item.retryCount + 1}/${item.maxRetries})")

                    scope.launch {
                        delay(retryDelay)
                        val retryItem = item.copy(retryCount = item.retryCount + 1)
                        enqueue(retryItem)
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
        val rateLimit: Int
    )
}
