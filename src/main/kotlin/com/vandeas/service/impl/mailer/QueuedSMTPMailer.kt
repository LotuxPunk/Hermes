package com.vandeas.service.impl.mailer

/**
 * Queued SMTP mailer. suspend sendEmail enqueues mail immediately.
 */
class QueuedSMTPMailer(
    username: String,
    password: String,
    host: String,
    port: Int = 587,
    rateLimit: Int = System.getenv("MAIL_RATE_LIMIT")?.toIntOrNull() ?: 10
) : AbstractQueuedMailer(
    internalMailer = SMTPMailer(username, password, host, port),
    rateLimit = rateLimit
) {
    override val loggerName = "com.vandeas.service.impl.mailer.QueuedSMTPMailer"
}
