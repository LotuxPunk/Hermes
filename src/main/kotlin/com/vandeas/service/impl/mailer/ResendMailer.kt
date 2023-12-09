package com.vandeas.service.impl.mailer

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.SendEmailRequest
import com.vandeas.service.Mailer
import com.vandeas.service.Response
import io.ktor.http.*

class ResendMailer(
    override val apiKey: String
): Mailer {
    private val resend = Resend(apiKey)
    override fun sendEmail(to: String, from: String, subject: String, content: String): Response {
        val sendMailRequest = SendEmailRequest.builder()
            .from(from)
            .to(to)
            .subject(subject)
            .html(content)
            .build()

        try {
            val response = resend.emails().send(sendMailRequest)

            return Response(
                HttpStatusCode.OK.value,
                response.id,
            )
        } catch (e: Exception) {
            if (e is ResendException) {
                return Response(
                    HttpStatusCode.InternalServerError.value,
                    e.message,
                )
            }
            return Response(
                HttpStatusCode.InternalServerError.value,
                e.message,
            )
        }
    }
}
