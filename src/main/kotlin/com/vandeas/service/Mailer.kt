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

    suspend fun sendEmails(
        mails: List<Mail>,
    ): BatchResponse
}

data class Response(
    val statusCode: Int,
    val body: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299
}

data class BatchResponse(
    val statusCode: Int,
    val body: List<String>? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299
}

