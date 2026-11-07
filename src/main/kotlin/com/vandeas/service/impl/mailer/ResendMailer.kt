package com.vandeas.service.impl.mailer

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.vandeas.entities.Attachment
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.Mailer
import com.vandeas.utils.senderAddress
import io.ktor.util.logging.*
import java.util.Base64
import com.resend.services.emails.model.Attachment as ResendAttachment

class ResendMailer(
    apiKey: String
): Mailer {
    private val resend = Resend(apiKey)

    private val logger = KtorSimpleLogger("com.vandeas.service.impl.mailer.ResendMailer")

    override suspend fun sendEmail(to: String, from: String, subject: String, content: String, attachments: List<Attachment>, replyTo: String?, senderName: String?): SendOperationResult {
        val sendMailRequest = createEmailOptions(
            Mail(from = from, to = to, subject = subject, content = content, attachments = attachments, replyTo = replyTo, senderName = senderName)
        )

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
        val requests = mails.map(::createEmailOptions)

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
                sendEmail(mail.to, mail.from, mail.subject, mail.content, mail.attachments, mail.replyTo, mail.senderName)
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
        /** Builds the Resend request of [mail] without sending it. */
        fun createEmailOptions(mail: Mail): CreateEmailOptions {
            val builder = CreateEmailOptions.builder()
                .from(fromField(mail.from, mail.senderName))
                .to(mail.to)
                .subject(mail.subject)
                .html(mail.content)

            mail.replyTo?.let { replyTo ->
                builder.replyTo(replyTo)
            }

            if (mail.attachments.isNotEmpty()) {
                builder.attachments(mail.attachments.map { attachment ->
                    ResendAttachment.builder()
                        .fileName(attachment.filename)
                        .content(Base64.getEncoder().encodeToString(attachment.content))
                        .contentType(attachment.contentType)
                        .build()
                })
            }

            return builder.build()
        }

        /**
         * The `from` field Resend receives for config sender [from].
         *
         * Without a [senderName], it is [from] exactly as configured. With one, it is
         * `Name <address>`, the name quoted and escaped when it holds specials. It is not an
         * RFC 2047 encoded word: Resend takes the name as UTF-8 JSON and encodes the header
         * itself.
         */
        fun fromField(from: String, senderName: String?): String =
            if (senderName == null) from else senderAddress(from, senderName).toUnicodeString()

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
