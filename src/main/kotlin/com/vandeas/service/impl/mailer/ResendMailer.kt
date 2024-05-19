package com.vandeas.service.impl.mailer

import com.resend.Resend
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

    override fun sendEmail(to: String, from: String, subject: String, content: String): SendOperationResult {
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
        } catch (e: Exception) {

            logger.error("Failed to send email to $to")
            logger.error("Error: ${e.message}")

            return SendOperationResult(
                failed = listOf(to),
            )
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
            logger.error("Failed to send emails: [${mails.joinToString { it.to }}]")
            logger.error("Error: ${e.message}")

            return SendOperationResult(
                failed = mails.map { it.to },
            )
        }
    }
}
