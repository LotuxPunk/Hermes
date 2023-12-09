package com.vandeas.dto

import com.vandeas.dto.enums.Providers
import com.vandeas.dto.enums.toMailer
import com.vandeas.service.Mailer
import kotlinx.serialization.Serializable

@Serializable
data class MailConfig(
    val id: String,
    val sender: String,
    val subjectTemplate: String,
    val provider: Providers,
    val apiKey: String,
)

fun MailConfig.toMailer(): Mailer {
    return provider.toMailer(apiKey)
}
