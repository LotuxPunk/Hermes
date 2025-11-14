package com.vandeas.service.impl.mailer

/**
 * Queued SMTP mailer with rate limiting.
 */
class QueuedSMTPMailer(
    username: String,
    password: String,
    host: String,
    port: Int = 587
) : AbstractQueuedMailer(
    internalMailer = SMTPMailer(username, password, host, port)
) {
    override val loggerName = "com.vandeas.service.impl.mailer.QueuedSMTPMailer"
}
