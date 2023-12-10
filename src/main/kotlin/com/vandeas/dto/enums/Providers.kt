package com.vandeas.dto.enums

import com.vandeas.service.Mailer
import com.vandeas.service.impl.mailer.ResendMailer
import com.vandeas.service.impl.mailer.SendGridMailer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Providers {
    @SerialName("SENDGRID")
    SENDGRID,
    @SerialName("RESEND")
    RESEND
}

fun Providers.toMailer(apiKey: String): Mailer {
    return when (this) {
        Providers.SENDGRID -> SendGridMailer(apiKey)
        Providers.RESEND -> ResendMailer(apiKey)
    }
}
