package com.vandeas.service

interface Mailer {
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