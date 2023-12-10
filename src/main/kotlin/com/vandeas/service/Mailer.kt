package com.vandeas.service

import com.vandeas.entities.Mail

interface Mailer {
    val apiKey: String

    fun sendEmail(
        to: String,
        from: String,
        subject: String,
        content: String,
    ) : Response

    fun sendEmails(
        mails: List<Mail>,
    ): Response
}

data class Response(
    val statusCode: Int,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299
}


