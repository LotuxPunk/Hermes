package com.vandeas.service.impl.mailer

import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.Mailer
import io.ktor.util.logging.*
import java.util.*
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.SendFailedException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class SMTPMailer(
	username: String,
	password: String,
	host: String,
	port: Int = 587
): Mailer {

    private val LOGGER = KtorSimpleLogger("com.vandeas.service.impl.mailer.SMTPMailer")

    private val session: Session = Session.getInstance(
			Properties().apply {
				put("mail.smtp.host", host)
				put("mail.smtp.port", "$port")
				put("mail.smtp.auth", "true")
				put("mail.smtp.starttls.enable", "true")
			},
			object : Authenticator() {
				override fun getPasswordAuthentication(): PasswordAuthentication {
					return PasswordAuthentication(username, password)
				}
			},
		)

	override suspend fun sendEmail(to: String, from: String, subject: String, content: String): SendOperationResult {
		val message = MimeMessage(session).apply {
			setFrom(InternetAddress(from))
			addRecipient(Message.RecipientType.TO, InternetAddress(to))
			this.subject = subject
			setText(content, "utf-8", "html")
		}
		return try {
			Transport.send(message)

            LOGGER.info("Email sent to $to")

            SendOperationResult(
                sent = listOf(to)
            )
		} catch (e: SendFailedException) {
            LOGGER.error("Failed to send email to $to")
            LOGGER.error("Error: ${e.message}")

            // Check if it's a permanent failure (bounce)
            val isPermanentFailure = e.validUnsentAddresses?.isNotEmpty() == true ||
                    e.message?.contains("550", ignoreCase = true) == true || // Mailbox not found
                    e.message?.contains("551", ignoreCase = true) == true || // User not local
                    e.message?.contains("553", ignoreCase = true) == true    // Invalid address

            if (isPermanentFailure) {
                LOGGER.warn("Email bounced (permanent failure): $to")
                SendOperationResult(
                    bounced = listOf(to),
                    failed = listOf(to)
                )
            } else {
                LOGGER.warn("Temporary email failure: $to")
                SendOperationResult(
                    temporary = listOf(to),
                    failed = listOf(to)
                )
            }
		} catch (e: MessagingException) {
            LOGGER.error("Messaging exception while sending email to $to: ${e.message}")

            // Most messaging exceptions are temporary (network issues, etc.)
            SendOperationResult(
                temporary = listOf(to),
                failed = listOf(to)
            )
		} catch (e: Exception) {
            LOGGER.error("Unexpected error while sending email to $to: ${e.message}")

            // Unknown errors treated as temporary to allow retry
            SendOperationResult(
                temporary = listOf(to),
                failed = listOf(to)
            )
		}
	}

	override suspend fun sendEmails(mails: List<Mail>) = mails.map {
		sendEmail(it.to, it.from, it.subject, it.content)
	}.let { responses ->
        SendOperationResult(
            sent = responses.flatMap { it.sent },
            failed = responses.flatMap { it.failed },
            bounced = responses.flatMap { it.bounced },
            temporary = responses.flatMap { it.temporary }
        )
	}
}
