package com.vandeas.service.impl.mailer

import com.vandeas.entities.Mail
import com.vandeas.entities.MailQueueItem
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.Mailer
import com.vandeas.service.RateLimitedMailQueue
import io.ktor.util.logging.*

/**
 * Abstract base class for queued mailers with rate limiting.
 * Handles common queue management logic that is shared between all queued mailer implementations.
 *
 * @property internalMailer The direct mailer implementation to use for actual sending
 * @property rateLimit Maximum number of emails to send per second
 */
abstract class AbstractQueuedMailer(
    protected val internalMailer: Mailer,
    rateLimit: Int = com.vandeas.utils.Constants.mailRateLimit
) : Mailer {

    protected abstract val loggerName: String

    protected val logger by lazy { KtorSimpleLogger(loggerName) }

    // Queue for rate-limited processing
    private val queue = RateLimitedMailQueue(internalMailer, rateLimit)

    /**
     * Send email via queue with reference tracking.
     *
     * @return SendOperationResult with reference for tracking
     */
    override suspend fun sendEmail(to: String, from: String, subject: String, content: String): SendOperationResult {
        val mail = Mail(from, to, subject, content)
        val queueItem = MailQueueItem(mail = mail)
        val reference = queue.enqueue(queueItem)

        logger.info("Queued email to $to with reference: $reference")

        // Return immediately with reference info
        return SendOperationResult(
            sent = listOf(to)  // Marked as "sent" meaning queued for sending
        )
    }

    override suspend fun sendEmails(mails: List<Mail>): SendOperationResult {
        val queueItems = mails.map { mail ->
            MailQueueItem(mail = mail)
        }

        val references = queue.enqueueAll(queueItems)

        logger.info("Queued ${mails.size} emails with references: ${references.joinToString()}")

        // Return immediately with all emails marked as queued
        return SendOperationResult(
            sent = mails.map { it.to }
        )
    }

    /**
     * Queued mailers handle retries internally via the queue processor.
     * To avoid double retries, this override performs a single enqueue pass.
     */
    override suspend fun sendEmailsWithRetry(
        mails: List<Mail>,
        maxRetries: Int,
        retryDelayMs: Long
    ): SendOperationResult {
        logger.debug("sendEmailsWithRetry called on queued mailer; delegating to single-pass enqueue to avoid double retries")
        return sendEmails(mails)
    }

    /**
     * Get the queue for accessing results and stats.
     */
    fun getQueue(): RateLimitedMailQueue = queue

    /**
     * Shutdown the mailer and its queue.
     */
    fun shutdown() {
        queue.shutdown()
    }
}
