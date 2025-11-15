package com.vandeas.service

import com.vandeas.entities.Mail
import com.vandeas.entities.MailQueueItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class SimpleQueueTest {

    @Test
    fun `simple enqueue and process test`() = runBlocking {
        val mailer = SimpleMailer()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = RateLimitedMailQueue(mailer, rateLimit = 100, workerCount = 2, scope = scope)

        try {
            // Enqueue a single email
            val item = MailQueueItem(
                reference = "test-ref",
                mail = Mail("from@test.com", "to@test.com", "Subject", "Content")
            )

            queue.enqueue(item)

            // Wait for result
            val result = withTimeoutOrNull(5.seconds) {
                queue.results.first()
            }

            println("Result: $result")
            println("Sent emails: ${mailer.sentCount}")

            assertEquals("test-ref", result?.reference)
            assertEquals(1, mailer.sentCount)
        } finally {
            queue.shutdown()
            scope.cancel()
        }
    }

    private class SimpleMailer : Mailer {
        var sentCount = 0

        override suspend fun sendEmail(to: String, from: String, subject: String, content: String): com.vandeas.entities.SendOperationResult {
            delay(10)
            sentCount++
            return com.vandeas.entities.SendOperationResult(sent = listOf(to))
        }

        override suspend fun sendEmails(mails: List<Mail>): com.vandeas.entities.SendOperationResult {
            val sent = mutableListOf<String>()
            mails.forEach {
                sendEmail(it.to, it.from, it.subject, it.content)
                sent.add(it.to)
            }
            return com.vandeas.entities.SendOperationResult(sent = sent)
        }
    }
}

