package com.vandeas.service.impl.mailer

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.Mailer
import io.ktor.util.logging.*

class ResendMailer(
    apiKey: String
): Mailer {
    private val resend = Resend(apiKey)

    private val logger = KtorSimpleLogger("com.vandeas.service.impl.mailer.ResendMailer")

    override suspend fun sendEmail(to: String, from: String, subject: String, content: String): SendOperationResult {
        val sendMailRequest = CreateEmailOptions.builder()
            .from(from)
            .to(to)
            .subject(subject)
            .html(content)
            .build()

        return try {
            val response = resend.emails().send(sendMailRequest)

            logger.info("Email sent to $to")
            logger.info("Email id: ${response.id}")

            SendOperationResult(
                sent = listOf(to),
            )
        } catch (e: ResendException) {
            logger.error("Failed to send email to $to")
            logger.error("Error: ${e.message}")

            val statusCode = e.statusCode
            logger.debug("HTTP Status Code: $statusCode")

            return when (statusCode) {
                // 4xx errors (except rate limit) are typically permanent failures
                400, 404 -> {
                    // Bad request or email not found - permanent failure
                    logger.warn("Email bounced (permanent failure): $to - Status: $statusCode")
                    SendOperationResult(
                        bounced = listOf(to),
                        failed = listOf(to)
                    )
                }
                422 -> {
                    // Unprocessable entity - usually validation errors (permanent)
                    logger.warn("Email bounced (validation error): $to - Status: $statusCode")
                    SendOperationResult(
                        bounced = listOf(to),
                        failed = listOf(to)
                    )
                }
                429 -> {
                    // Rate limit - temporary failure, should retry later
                    logger.warn("Rate limit hit, temporary failure: $to - Status: $statusCode")
                    SendOperationResult(
                        temporary = listOf(to),
                        failed = listOf(to)
                    )
                }
                in 500..599 -> {
                    // Server errors - temporary failures, should retry
                    logger.warn("Server error, temporary failure: $to - Status: $statusCode")
                    SendOperationResult(
                        temporary = listOf(to),
                        failed = listOf(to)
                    )
                }
                else -> {
                    // Unknown status codes - treat as temporary to allow retry
                    logger.warn("Unknown failure type (status: $statusCode), treating as temporary: $to")
                    SendOperationResult(
                        temporary = listOf(to),
                        failed = listOf(to)
                    )
                }
            }
        }
    }

    override suspend fun sendEmails(mails: List<Mail>): SendOperationResult {
        val requests = mails.map {
            CreateEmailOptions.builder()
                .from(it.from)
                .to(it.to)
                .subject(it.subject)
                .html(it.content)
                .build()
        }

        return try {
            val response = resend.batch().send(requests)

            logger.info("Emails sent: [${mails.joinToString { it.to }}]")
            logger.info("Email ids: [${response.data.joinToString { it.id }}]")

            SendOperationResult(
                sent = mails.map { it.to },
            )
        } catch (e: Exception) {
            logger.error("Failed to send batch emails: [${mails.joinToString { it.to }}]")
            logger.error("Error: ${e.message}")

            // When batch fails, we need to send individually to categorize failures
            logger.info("Falling back to individual sends to categorize failures")
            val results = mails.map { mail ->
                sendEmail(mail.to, mail.from, mail.subject, mail.content)
            }

            // Aggregate all results
            SendOperationResult(
                sent = results.flatMap { it.sent },
                failed = results.flatMap { it.failed },
                bounced = results.flatMap { it.bounced },
                temporary = results.flatMap { it.temporary }
            )
        }
    }
}
