package com.vandeas.service.impl.mailer

import com.resend.Resend
import com.resend.core.exception.ResendException
import com.resend.services.emails.model.CreateEmailOptions
import com.vandeas.entities.Mail
import com.vandeas.service.BatchResponse
import com.vandeas.service.Mailer
import com.vandeas.service.Response
import io.ktor.http.*

class ResendMailer(
    override val apiKey: String
): Mailer {
    private val resend = Resend(apiKey)
    override fun sendEmail(to: String, from: String, subject: String, content: String): Response {
        val sendMailRequest = CreateEmailOptions.builder()
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

    override suspend fun sendEmails(mails: List<Mail>): BatchResponse {
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

            BatchResponse(
                HttpStatusCode.OK.value,
                response.data.map { it.id },
            )
        } catch (e: Exception) {
            if (e is ResendException) {
                return BatchResponse(
                    HttpStatusCode.InternalServerError.value,
                    e.message?.let(::listOf),
                )
            }
            return BatchResponse(
                HttpStatusCode.InternalServerError.value,
                e.message?.let(::listOf),
            )
        }
    }
}
