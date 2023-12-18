package com.vandeas.dto.enums

import com.vandeas.service.Mailer
import com.vandeas.service.impl.mailer.ResendMailer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Providers {
    @SerialName("RESEND")
    RESEND
}

fun Providers.toMailer(apiKey: String): Mailer {
    return when (this) {
        Providers.RESEND -> ResendMailer(apiKey)
    }
}
