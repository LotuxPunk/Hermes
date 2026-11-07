package com.vandeas.service.impl.mailer

import com.vandeas.entities.Attachment
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.Mailer
import com.vandeas.utils.senderAddress
import io.ktor.util.logging.*
import java.util.*
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.SendFailedException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

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

	override suspend fun sendEmail(to: String, from: String, subject: String, content: String, attachments: List<Attachment>, replyTo: String?, senderName: String?): SendOperationResult =
		send(Mail(from = from, to = to, subject = subject, content = content, attachments = attachments, replyTo = replyTo, senderName = senderName))

	override suspend fun sendEmails(mails: List<Mail>) = mails.map { send(it) }.let { responses ->
        SendOperationResult(
            sent = responses.flatMap { it.sent },
            failed = responses.flatMap { it.failed },
            bounced = responses.flatMap { it.bounced },
            temporary = responses.flatMap { it.temporary }
        )
	}

	/**
	 * Builds the message of [mail] without sending it.
	 *
	 * `From` comes from [senderAddress], so a [Mail.senderName] only changes the display name.
	 */
	internal fun createMessage(mail: Mail): MimeMessage {
		// Resolved outside the apply block: MimeMessage has its own `replyTo` member, which
		// would shadow the mail's inside the block.
		val replyToAddress = mail.replyTo?.let { InternetAddress(it) }
		return MimeMessage(session).apply {
			setFrom(senderAddress(mail.from, mail.senderName))
			addRecipient(Message.RecipientType.TO, InternetAddress(mail.to))
			replyToAddress?.let { setReplyTo(arrayOf(it)) }
			this.subject = mail.subject
			if (mail.attachments.isEmpty()) {
				setText(mail.content, "utf-8", "html")
			} else {
				val multipart = MimeMultipart().apply {
					addBodyPart(MimeBodyPart().apply {
						setText(mail.content, "utf-8", "html")
					})
					mail.attachments.forEach { attachment ->
						addBodyPart(MimeBodyPart().apply {
							dataHandler = DataHandler(ByteArrayDataSource(attachment.content, attachment.contentType))
							fileName = attachment.filename
						})
					}
				}
				setContent(multipart)
			}
		}
	}

	private fun send(mail: Mail): SendOperationResult {
		val to = mail.to
		val message = createMessage(mail)
		return try {
			Transport.send(message)

            LOGGER.info("Email sent to $to")

            SendOperationResult(
                sent = listOf(to)
            )
		} catch (e: SendFailedException) {
            LOGGER.error("Failed to send email to $to")
            LOGGER.error("Error: ${e.message}")
            classifySendFailedException(to, e)
		} catch (e: MessagingException) {
            LOGGER.error("Messaging exception while sending email to $to: ${e.message}")
            classifyMessagingException(to, e)
		} catch (e: Exception) {
            LOGGER.error("Unexpected error while sending email to $to: ${e.message}")
            classifyUnexpectedException(to, e)
		}
	}

    companion object {
        /**
         * Classify a [SendFailedException] into a result. SMTP 5xx (except 421) and
         * invalid-address rejections are permanent (bounced); everything else is temporary.
         *
         * The address is placed into exactly one of `bounced` or `temporary` — never also
         * into `failed`, which is reserved for the queue's terminal failure decision.
         */
        fun classifySendFailedException(to: String, e: SendFailedException): SendOperationResult {
            val hasInvalidAddresses = e.invalidAddresses?.isNotEmpty() == true
            val isPermanentSMTPError = e.message?.let { msg ->
                msg.contains("550", ignoreCase = true) ||  // Mailbox not found
                msg.contains("551", ignoreCase = true) ||  // User not local
                msg.contains("552", ignoreCase = true) ||  // Mailbox full
                msg.contains("553", ignoreCase = true) ||  // Invalid address
                msg.contains("554", ignoreCase = true)     // Transaction failed
            } ?: false

            return if (hasInvalidAddresses || isPermanentSMTPError) {
                SendOperationResult(bounced = listOf(to))
            } else {
                SendOperationResult(temporary = listOf(to))
            }
        }

        /** Generic [MessagingException] (network glitch, etc.) is treated as temporary. */
        fun classifyMessagingException(to: String, @Suppress("UNUSED_PARAMETER") e: MessagingException): SendOperationResult =
            SendOperationResult(temporary = listOf(to))

        /** Unknown error: treat as temporary so the queue can retry. */
        fun classifyUnexpectedException(to: String, @Suppress("UNUSED_PARAMETER") e: Exception): SendOperationResult =
            SendOperationResult(temporary = listOf(to))
    }
}
