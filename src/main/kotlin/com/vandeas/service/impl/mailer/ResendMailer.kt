package com.vandeas.service.impl.mailer

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.vandeas.entities.Attachment
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.Mailer
import io.ktor.util.logging.*
import java.util.Base64
import com.resend.services.emails.model.Attachment as ResendAttachment

class ResendMailer(
    apiKey: String
): Mailer {
    private val resend = Resend(apiKey)

    private val logger = KtorSimpleLogger("com.vandeas.service.impl.mailer.ResendMailer")

    override suspend fun sendEmail(to: String, from: String, subject: String, content: String, attachments: List<Attachment>): SendOperationResult {
        val builder = CreateEmailOptions.builder()
            .from(from)
            .to(to)
            .subject(subject)
            .html(content)

        if (attachments.isNotEmpty()) {
            builder.attachments(attachments.map { attachment ->
                ResendAttachment.builder()
                    .fileName(attachment.filename)
                    .content(Base64.getEncoder().encodeToString(attachment.content))
                    .contentType(attachment.contentType)
                    .build()
            })
        }

        val sendMailRequest = builder.build()

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
            logger.debug("HTTP Status Code: ${e.statusCode}")
            classifyResendStatus(to, e.statusCode)
        } catch (e: Exception) {
            logger.error("Unexpected error while sending email to $to: ${e.message}")
            classifyUnexpectedException(to, e)
        }
    }

    override suspend fun sendEmails(mails: List<Mail>): SendOperationResult {
        val requests = mails.map {
            val builder = CreateEmailOptions.builder()
                .from(it.from)
                .to(it.to)
                .subject(it.subject)
                .html(it.content)

            if (it.attachments.isNotEmpty()) {
                builder.attachments(it.attachments.map { attachment ->
                    ResendAttachment.builder()
                        .fileName(attachment.filename)
                        .content(Base64.getEncoder().encodeToString(attachment.content))
                        .contentType(attachment.contentType)
                        .build()
                })
            }

            builder.build()
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
                sendEmail(mail.to, mail.from, mail.subject, mail.content, mail.attachments)
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

    companion object {
        /**
         * Classify a Resend HTTP status code into a result for [to].
         *
         * The address is placed into exactly one of `bounced` or `temporary` — never also
         * into `failed`, which is reserved for the queue's terminal failure decision.
         */
        fun classifyResendStatus(to: String, statusCode: Int): SendOperationResult = when (statusCode) {
            400, 404, 422 -> SendOperationResult(bounced = listOf(to))
            429 -> SendOperationResult(temporary = listOf(to))
            in 500..599 -> SendOperationResult(temporary = listOf(to))
            else -> SendOperationResult(temporary = listOf(to))
        }

        /** Network/timeout/etc.: retry. */
        fun classifyUnexpectedException(to: String, @Suppress("UNUSED_PARAMETER") e: Exception): SendOperationResult =
            SendOperationResult(temporary = listOf(to))
    }
}
