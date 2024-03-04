package com.vandeas.dto.configs

import com.vandeas.dto.enums.Providers
import com.vandeas.dto.enums.toMailer
import com.vandeas.service.Mailer
import kotlinx.serialization.Serializable

@Serializable
data class MailConfig(
    override val id: String,
    val sender: String,
    val subjectTemplate: String,
    val provider: Providers,
    override val apiKey: String,
): Config

fun MailConfig.toMailer(): Mailer {
    return provider.toMailer(apiKey)
}
