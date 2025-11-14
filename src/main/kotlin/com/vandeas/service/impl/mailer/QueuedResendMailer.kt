package com.vandeas.service.impl.mailer

/**
 * Queued Resend mailer. sendEmail is suspend and enqueues instantly.
 */
class QueuedResendMailer(
    apiKey: String,
    rateLimit: Int = System.getenv("MAIL_RATE_LIMIT")?.toIntOrNull() ?: 10
) : AbstractQueuedMailer(
    internalMailer = ResendMailer(apiKey),
    rateLimit = rateLimit
) {
    override val loggerName = "com.vandeas.service.impl.mailer.QueuedResendMailer"
}
