package com.vandeas.service.impl

import com.sendgrid.Method
import com.sendgrid.Request
import com.vandeas.service.Mailer
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email
import com.vandeas.service.Response


class SendGridMailer : Mailer {

    private val sg = SendGrid(System.getenv("SENDGRID_API_KEY"))

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

fun String.toSendGridEmail() = Email(this)
