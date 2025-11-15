package com.vandeas.service.impl.mailer

/**
 * Queued Resend mailer with rate limiting.
 */
class QueuedResendMailer(
    apiKey: String
) : AbstractQueuedMailer(
    internalMailer = ResendMailer(apiKey)
) {
    override val loggerName = "com.vandeas.service.impl.mailer.QueuedResendMailer"
}
