package com.vandeas.service.impl.mailer

import com.vandeas.entities.Mail
import com.vandeas.service.BatchResponse
import com.vandeas.service.Mailer
import com.vandeas.service.Response
import java.util.*
import javax.mail.Authenticator
import javax.mail.Message
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

	override fun sendEmail(to: String, from: String, subject: String, content: String): Response {
		val message = MimeMessage(session).apply {
			setFrom(InternetAddress(from))
			addRecipient(Message.RecipientType.TO, InternetAddress(to))
			this.subject = subject
			setText(content)
		}
		return try {
			Transport.send(message)
			Response(200, "[$to] ok")
		} catch (e: SendFailedException) {
			Response(500, "[$to] ${e.message ?: "Unknown error"}")
		}
	}

	override suspend fun sendEmails(mails: List<Mail>) = mails.map {
		sendEmail(it.to, it.from, it.subject, it.content)
	}.let { responses ->
		BatchResponse(
			200.takeIf { responses.all { it.isSuccessful } } ?: 500,
			responses.map { it.body ?: "Unknown error"}
		)
	}
}