package com.vandeas.entities

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
 *
 * @property status Status of the send operation.
 */
data class SendOperationResult(
    val sent: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
) {
    val status: MailSendStatus
        get() = when {
            failed.isNotEmpty() && sent.isNotEmpty() -> MailSendStatus.PARTIAL
            failed.isNotEmpty() -> MailSendStatus.FAILED
            else -> MailSendStatus.SENT
        }
}
