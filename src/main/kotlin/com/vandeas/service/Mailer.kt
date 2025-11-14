package com.vandeas.service

import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult

interface Mailer {
    suspend fun sendEmail(
        to: String,
        from: String,
        subject: String,
        content: String,
    ) : SendOperationResult

    suspend fun sendEmails(
        mails: List<Mail>,
    ): SendOperationResult

    /**
     * Send emails with retry logic for temporary failures.
     * Bounced emails will not be retried.
     *
     * @param mails List of emails to send
     * @param maxRetries Maximum number of retry attempts for temporary failures
     * @param retryDelayMs Delay in milliseconds between retry attempts
     * @return SendOperationResult with detailed failure information
     */
    suspend fun sendEmailsWithRetry(
        mails: List<Mail>,
        maxRetries: Int = 3,
        retryDelayMs: Long = 1000L
    ): SendOperationResult {
        var result = sendEmails(mails)
        var retryCount = 0

        // Retry only temporary failures
        while (result.temporary.isNotEmpty() && retryCount < maxRetries) {
            retryCount++
            kotlinx.coroutines.delay(retryDelayMs * retryCount) // Exponential backoff

            val retryMails = mails.filter { it.to in result.temporary }
            val retryResult = sendEmails(retryMails)

            // Combine results
            result = SendOperationResult(
                sent = result.sent + retryResult.sent,
                failed = retryResult.failed,
                bounced = result.bounced + retryResult.bounced,
                temporary = retryResult.temporary
            )
        }

        // After all retries, move remaining temporary failures to failed
        if (result.temporary.isNotEmpty()) {
            result = SendOperationResult(
                sent = result.sent,
                failed = result.failed + result.temporary,
                bounced = result.bounced,
                temporary = emptyList()
            )
        }

        return result
    }
}
