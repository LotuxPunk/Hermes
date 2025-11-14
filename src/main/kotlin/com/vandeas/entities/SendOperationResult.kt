package com.vandeas.entities

import kotlinx.serialization.Serializable

/**
 * Enum class representing the status of a send operation.
 * @property SENT The operation was successful.
 * @property PARTIAL The operation was partially successful.
 * @property FAILED The operation failed.
 */
enum class MailSendStatus {
    SENT,
    PARTIAL,
    FAILED,
}

/**
 * Result of a send operation.
 * @property sent List of email addresses that were successfully sent.
 * @property failed List of email addresses that failed to send.
 * @property bounced List of email addresses that permanently bounced (should not retry).
 * @property temporary List of email addresses that had temporary failures (can retry).
 *
 * @property status Status of the send operation.
 */
@Serializable
data class SendOperationResult(
    val sent: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val bounced: List<String> = emptyList(),
    val temporary: List<String> = emptyList(),
) {
    val status: MailSendStatus
        get() = when {
            failed.isNotEmpty() && sent.isNotEmpty() -> MailSendStatus.PARTIAL
            failed.isNotEmpty() -> MailSendStatus.FAILED
            else -> MailSendStatus.SENT
        }

    /**
     * Get list of emails that can be retried (temporary failures only)
     */
    fun getRetryableEmails(): List<String> = temporary

    /**
     * Combine multiple results into one
     */
    operator fun plus(other: SendOperationResult): SendOperationResult {
        return SendOperationResult(
            sent = this.sent + other.sent,
            failed = this.failed + other.failed,
            bounced = this.bounced + other.bounced,
            temporary = this.temporary + other.temporary
        )
    }
}
