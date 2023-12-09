package com.vandeas.service.impl.mailer

import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email
import com.vandeas.service.Mailer
import com.vandeas.service.Response

class SendGridMailer(
    override val apiKey: String
) : Mailer {

    private val sg = SendGrid(apiKey)

    override fun sendEmail(to: String, from: String, subject: String, content: String): Response {
        val mail = Mail(
            from.toSendGridEmail(),
            subject,
            to.toSendGridEmail(),
            Content(
                "text/html",
                content
            )
        )
        val request = Request()

        request.method = Method.POST
        request.endpoint = "mail/send"
        request.body = mail.build()

        val sgResponse = sg.api(request)

        return Response(
            sgResponse.statusCode,
            sgResponse.body,
            sgResponse.headers
        )
    }
}

private fun String.toSendGridEmail() = Email(this)
