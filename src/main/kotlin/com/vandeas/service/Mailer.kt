package com.vandeas.service

import com.vandeas.dto.MailConfig
import com.vandeas.dto.enums.Providers
import com.vandeas.service.impl.mailer.ResendMailer
import com.vandeas.service.impl.mailer.SendGridMailer

interface Mailer {
    val apiKey: String

    fun sendEmail(
        to: String,
        from: String,
        subject: String,
        content: String,
    ) : Response
}

data class Response(
    val statusCode: Int,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299
}


