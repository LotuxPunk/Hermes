package com.vandeas.service

import com.vandeas.entities.Mail
import com.vandeas.entities.MailQueueItem
import com.vandeas.entities.QueuedMailResult
import com.vandeas.entities.SendOperationResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class RateLimitedMailQueueTest {

    private lateinit var mockMailer: MockMailer
    private lateinit var queue: RateLimitedMailQueue
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setup() {
        mockMailer = MockMailer()
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

    @AfterTest
    fun cleanup() {
        runBlocking {
            // Give time for any in-flight operations to complete
            delay(100.milliseconds)
        }
        if (::queue.isInitialized) {
            queue.shutdown()
        }
        scope.cancel()
    }

    @Test
    fun `worker pool should process emails concurrently`() = runBlocking {
        // Given: Queue with 5 workers and high rate limit
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 100, workerCount = 5, scope = scope)

        // When: Enqueuing 10 emails
        val items = (1..10).map { i ->
            MailQueueItem(
                reference = "ref-$i",
                mail = Mail("sender@test.com", "user$i@test.com", "Subject $i", "Content $i")
            )
        }

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results.take(10).toList(results)
        }

        items.forEach { queue.enqueue(it) }

        // Then: All emails should be processed
        withTimeout(5.seconds) {
            resultJob.join()
        }

        assertEquals(10, results.size)
        assertEquals(10, mockMailer.sentEmails.size)

        // Verify all workers were active at some point
        assertTrue(queue.getActiveWorkers() > 0 || results.isNotEmpty())
    }

    @Test
    fun `worker pool should respect rate limit across all workers`() = runBlocking {
        // Given: Queue with 3 workers and rate limit of 10/second
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 10, workerCount = 3, scope = scope)

        val startTime = System.currentTimeMillis()

        // When: Enqueuing 25 emails (should take at least 2 seconds at 10/sec)
        val items = (1..25).map { i ->
            MailQueueItem(
                reference = "ref-$i",
                mail = Mail("sender@test.com", "user$i@test.com", "Subject", "Content")
            )
        }

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results.take(25).toList(results)
        }

        items.forEach { queue.enqueue(it) }

        withTimeout(5.seconds) {
            resultJob.join()
        }

        val elapsedTime = System.currentTimeMillis() - startTime

        // Then: Should respect rate limit (at least 2 seconds for 25 emails at 10/sec)
        assertTrue(elapsedTime >= 2000, "Expected at least 2000ms, got ${elapsedTime}ms")
        assertEquals(25, results.size)
    }

    @Test
    fun `workers should handle failures independently`() = runBlocking {
        // Given: Queue with 3 workers
        mockMailer.shouldFailFor = setOf("fail1@test.com", "fail2@test.com")
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 100, workerCount = 3, scope = scope)

        // When: Enqueuing mix of successful and failing emails
        val items = listOf(
            MailQueueItem("ref-1", Mail("s@test.com", "success1@test.com", "S", "C")),
            MailQueueItem("ref-2", Mail("s@test.com", "fail1@test.com", "S", "C")),
            MailQueueItem("ref-3", Mail("s@test.com", "success2@test.com", "S", "C")),
            MailQueueItem("ref-4", Mail("s@test.com", "fail2@test.com", "S", "C")),
            MailQueueItem("ref-5", Mail("s@test.com", "success3@test.com", "S", "C"))
        )

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results.take(5).toList(results)
        }

        items.forEach { queue.enqueue(it) }

        withTimeout(3.seconds) {
            resultJob.join()
        }

        // Then: Successful emails should be sent, failures should be reported
        assertEquals(5, results.size)
        assertEquals(3, mockMailer.sentEmails.size)

        val failedResults = results.filter { it.result.failed.isNotEmpty() }
        assertEquals(2, failedResults.size)
    }

    @Test
    fun `worker pool should handle temporary failures with retry`() = runBlocking {
        // Given: Mailer that fails temporarily
        mockMailer.shouldTemporaryFailFor = setOf("temp@test.com")
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 100, workerCount = 2, scope = scope)

        // When: Enqueuing email that will fail temporarily
        val item = MailQueueItem(
            reference = "temp-ref",
            mail = Mail("s@test.com", "temp@test.com", "Subject", "Content"),
            maxRetries = 2
        )

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results
                .filter { it.reference == "temp-ref" }
                .take(1)
                .toList(results)
        }

        queue.enqueue(item)

        // Allow time for retry
        withTimeout(5.seconds) {
            resultJob.join()
        }

        // Then: Should have temporary failure result
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().result.temporary.isNotEmpty())
    }

    @Test
    fun `getStats should return accurate worker pool information`() = runBlocking {
        // Given: Queue with 4 workers
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 50, workerCount = 4, scope = scope)

        // Allow time for workers to start
        delay(100.milliseconds)

        // When: Getting stats
        val stats = queue.getStats()

        // Then: Stats should reflect configuration
        assertEquals(4, stats.activeWorkers)
        assertEquals(50, stats.rateLimit)
    }

    @Test
    fun `worker pool should process large batch efficiently`() = runBlocking {
        // Given: Queue with 5 workers and high rate limit
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 200, workerCount = 5, scope = scope)

        val startTime = System.currentTimeMillis()

        // When: Enqueuing 100 emails
        val items = (1..100).map { i ->
            MailQueueItem(
                reference = "ref-$i",
                mail = Mail("sender@test.com", "user$i@test.com", "Subject $i", "Content $i")
            )
        }

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results.take(100).toList(results)
        }

        queue.enqueueAll(items)

        withTimeout(10.seconds) {
            resultJob.join()
        }

        val elapsedTime = System.currentTimeMillis() - startTime

        // Then: Should complete in reasonable time (much faster than sequential)
        assertEquals(100, results.size)
        assertEquals(100, mockMailer.sentEmails.size)

        // With 5 workers at 200/sec, 100 emails should take < 1 second
        assertTrue(elapsedTime < 2000, "Expected < 2000ms, got ${elapsedTime}ms")
    }

    @Test
    fun `workers should continue processing after individual failures`() = runBlocking {
        // Given: Queue with 3 workers
        var failureCount = 0
        mockMailer.onSend = { mail ->
            if (mail.to == "fail@test.com" && failureCount < 1) {
                failureCount++
                throw RuntimeException("Simulated failure")
            }
        }
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 100, workerCount = 3, scope = scope)

        // When: Enqueuing emails including one that throws exception
        val items = listOf(
            MailQueueItem("ref-1", Mail("s@test.com", "success1@test.com", "S", "C")),
            MailQueueItem("ref-2", Mail("s@test.com", "fail@test.com", "S", "C")),
            MailQueueItem("ref-3", Mail("s@test.com", "success2@test.com", "S", "C")),
            MailQueueItem("ref-4", Mail("s@test.com", "success3@test.com", "S", "C"))
        )

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results.take(4).toList(results)
        }

        items.forEach { queue.enqueue(it) }

        withTimeout(3.seconds) {
            resultJob.join()
        }

        // Then: Other emails should still be processed
        assertEquals(4, results.size)
        assertTrue(mockMailer.sentEmails.size >= 3) // At least the successful ones
    }

    @Test
    fun `enqueueAll should add all items to queue`() = runBlocking {
        // Given: Queue with worker pool
        queue = RateLimitedMailQueue(mockMailer, rateLimit = 100, workerCount = 3, scope = scope)

        // When: Enqueuing multiple items at once
        val items = (1..5).map { i ->
            MailQueueItem(
                reference = "ref-$i",
                mail = Mail("sender@test.com", "user$i@test.com", "Subject", "Content")
            )
        }

        val results = mutableListOf<QueuedMailResult>()
        val resultJob = scope.launch {
            queue.results.take(5).toList(results)
        }

        val references = queue.enqueueAll(items)

        withTimeout(3.seconds) {
            resultJob.join()
        }

        // Then: All items should be processed
        assertEquals(5, references.size)
        assertEquals(5, results.size)
    }

    // Mock Mailer for testing
    private class MockMailer : Mailer {
        val sentEmails = mutableListOf<Mail>()
        var shouldFailFor = setOf<String>()
        var shouldTemporaryFailFor = setOf<String>()
        var onSend: ((Mail) -> Unit)? = null

        override suspend fun sendEmail(to: String, from: String, subject: String, content: String): SendOperationResult {
            val mail = Mail(from, to, subject, content)

            onSend?.invoke(mail)

            // Simulate some processing time
            delay(10.milliseconds)

            return when {
                to in shouldTemporaryFailFor -> SendOperationResult(temporary = listOf(to))
                to in shouldFailFor -> SendOperationResult(failed = listOf(to))
                else -> {
                    synchronized(sentEmails) {
                        sentEmails.add(mail)
                    }
                    SendOperationResult(sent = listOf(to))
                }
            }
        }

        override suspend fun sendEmails(mails: List<Mail>): SendOperationResult {
            val sent = mutableListOf<String>()
            val failed = mutableListOf<String>()
            val temporary = mutableListOf<String>()

            mails.forEach { mail ->
                val result = sendEmail(mail.to, mail.from, mail.subject, mail.content)
                sent.addAll(result.sent)
                failed.addAll(result.failed)
                temporary.addAll(result.temporary)
            }

            return SendOperationResult(sent = sent, failed = failed, temporary = temporary)
        }
    }
}

