package com.vandeas.service

import com.vandeas.entities.Mail
import com.vandeas.entities.MailQueueItem
import com.vandeas.service.impl.mailer.AbstractQueuedMailer
import com.vandeas.support.RecordingMailer
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
 * With `USE_MAIL_QUEUE=true` a mail goes through [AbstractQueuedMailer] and
 * [RateLimitedMailQueue], which rebuild and unwrap [Mail] field by field. A field dropped on
 * that path would silently send the config's name instead of the request's.
 */
class QueueSenderNameTest {

    private suspend fun RecordingMailer.awaitMails(count: Int): List<Mail> {
        withTimeout(5.seconds) {
            while (mails.size < count) delay(20.milliseconds)
        }
        return mails.toList()
    }

    private fun mail(to: String, senderName: String?) = Mail(
        from = "Courtly <ne-pas-repondre@courtly.be>",
        to = to,
        subject = "Subject",
        content = "Content",
        senderName = senderName,
    )

    @Test
    fun `the queue hands the sender name to the mailer`() = runBlocking {
        val mailer = RecordingMailer()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = RateLimitedMailQueue(mailer, rateLimit = 1000, workerCount = 1, scope = scope)

        try {
            queue.enqueue(MailQueueItem(mail = mail("a@example.com", "Spy Club via Courtly")))
            queue.enqueue(MailQueueItem(mail = mail("b@example.com", null)))

            val byRecipient = mailer.awaitMails(2).associate { it.to to it.senderName }
            assertEquals(mapOf("a@example.com" to "Spy Club via Courtly", "b@example.com" to null), byRecipient)
        } finally {
            queue.shutdown()
            scope.cancel()
        }
    }

    @Test
    fun `a queued mailer carries the sender name from sendEmail and sendEmails`() = runBlocking {
        val internal = RecordingMailer()
        val queued = object : AbstractQueuedMailer(internal, rateLimit = 1000) {
            override val loggerName = "com.vandeas.service.QueueSenderNameTest"
        }

        try {
            queued.sendEmail(
                to = "single@example.com",
                from = "Courtly <ne-pas-repondre@courtly.be>",
                subject = "Subject",
                content = "Content",
                senderName = "TC Uccle via Courtly",
            )
            queued.sendEmails(listOf(mail("batch@example.com", "Spy Club via Courtly")))

            val byRecipient = internal.awaitMails(2).associate { it.to to it.senderName }
            assertEquals(
                mapOf("single@example.com" to "TC Uccle via Courtly", "batch@example.com" to "Spy Club via Courtly"),
                byRecipient,
            )
        } finally {
            queued.shutdown()
        }
    }
}
