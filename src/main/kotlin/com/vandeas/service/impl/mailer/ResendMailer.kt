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

    private val LOGGER = KtorSimpleLogger("com.vandeas.service.impl.mailer.ResendMailer")

    override fun sendEmail(to: String, from: String, subject: String, content: String): SendOperationResult {
        val sendMailRequest = CreateEmailOptions.builder()
            .from(from)
            .to(to)
            .subject(subject)
            .html(content)
            .build()

        return try {
            val response = resend.emails().send(sendMailRequest)

            LOGGER.info("Email sent to $to")
            LOGGER.info("Email id: ${response.id}")

            SendOperationResult(
                sent = listOf(to),
            )
        } catch (e: Exception) {

            LOGGER.error("Failed to send email to $to")
            LOGGER.error("Error: ${e.message}")

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

            LOGGER.info("Emails sent: [${mails.joinToString { it.to }}]")
            LOGGER.info("Email ids: [${response.data.joinToString { it.id }}]")

            SendOperationResult(
                sent = mails.map { it.to },
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to send emails: [${mails.joinToString { it.to }}]")
            LOGGER.error("Error: ${e.message}")

            return SendOperationResult(
                failed = mails.map { it.to },
            )
        }
    }
}
