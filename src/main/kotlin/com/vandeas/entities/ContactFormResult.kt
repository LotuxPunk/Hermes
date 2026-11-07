package com.vandeas.entities

/**
 * Outcome of a Visitor confirmation, as reported to the frontend.
 *
 * The reason for [NOT_SENT] is logged, never returned, so the contact endpoint does not
 * reveal whether an address has hit the Confirmation recipient limit.
 */
enum class VisitorConfirmationStatus {
    /** Sent, or queued for sending. */
    SENT,

    /** Skipped or failed: limit reached, template missing, render or send error, or failed Org notification. */
    NOT_SENT,
}

/**
 * Result of a contact form Submission.
 *
 * @property orgNotification Result of sending the Org notification. The HTTP status of
 * the Submission derives from it alone.
 * @property confirmation Outcome of the Visitor confirmation, or null when the contact form
 * config has none.
 */
data class ContactFormResult(
    val orgNotification: SendOperationResult,
    val confirmation: VisitorConfirmationStatus? = null,
)
