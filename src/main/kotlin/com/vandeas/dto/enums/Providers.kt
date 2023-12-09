package com.vandeas.dto.enums

import com.vandeas.service.Mailer
import com.vandeas.service.impl.mailer.ResendMailer
import com.vandeas.service.impl.mailer.SendGridMailer

enum class Providers {
    SENDGRID,
    RESEND
}

fun Providers.toMailer(apiKey: String): Mailer {
    return when (this) {
        Providers.SENDGRID -> SendGridMailer(apiKey)
        Providers.RESEND -> ResendMailer(apiKey)
    }
}
