package com.vandeas.service

import com.vandeas.entities.Attachment
import com.vandeas.entities.Mail
import com.vandeas.entities.MailQueueItem
import com.vandeas.entities.SendOperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [RateLimitedMailQueue] unwraps [Mail] field by field before calling the mailer, so a
 * new field is dropped unless that call site is updated. The queue is on by default
 * (`USE_MAIL_QUEUE`), which makes this the path most single sends take.
 */
class QueueReplyToTest {

    @Test
    fun `reply-to survives the queue`() = runBlocking {
        val mailer = RecordingMailer()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = RateLimitedMailQueue(mailer, rateLimit = 1000, workerCount = 1, scope = scope)

        try {
            queue.enqueue(
                MailQueueItem(
                    reference = "reply-to-ref",
                    mail = Mail(
                        from = "no-reply@acme.com",
                        to = "john@example.com",
                        subject = "Subject",
                        content = "Content",
                        replyTo = "support@acme.com"
                    )
                )
            )

            withTimeout(5.seconds) {
                while (mailer.replyTos.isEmpty()) delay(20.milliseconds)
            }

            assertEquals(listOf("support@acme.com"), mailer.replyTos.toList())
        } finally {
            queue.shutdown()
            scope.cancel()
        }
    }

    @Test
    fun `a mail with no reply-to reaches the mailer as null`() = runBlocking {
        val mailer = RecordingMailer()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = RateLimitedMailQueue(mailer, rateLimit = 1000, workerCount = 1, scope = scope)

        try {
            queue.enqueue(
                MailQueueItem(
                    reference = "no-reply-to-ref",
                    mail = Mail(
                        from = "no-reply@acme.com",
                        to = "john@example.com",
                        subject = "Subject",
                        content = "Content"
                    )
                )
            )

            withTimeout(5.seconds) {
                while (mailer.replyTos.isEmpty()) delay(20.milliseconds)
            }

            assertEquals(listOf<String?>(null), mailer.replyTos.toList())
        } finally {
            queue.shutdown()
            scope.cancel()
        }
    }

    private class RecordingMailer : Mailer {
        val replyTos: MutableList<String?> = java.util.Collections.synchronizedList(mutableListOf())

        override suspend fun sendEmail(
            to: String,
            from: String,
            subject: String,
            content: String,
            attachments: List<Attachment>,
            replyTo: String?
        ): SendOperationResult {
            replyTos.add(replyTo)
            return SendOperationResult(sent = listOf(to))
        }

        override suspend fun sendEmails(mails: List<Mail>): SendOperationResult =
            SendOperationResult(sent = mails.map { it.to })
    }
}
