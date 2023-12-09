package com.vandeas.dto

import com.vandeas.dto.enums.Providers
import com.vandeas.dto.enums.toMailer
import com.vandeas.service.Mailer
import kotlinx.serialization.Serializable

@Serializable
data class ContactFormConfig(
    val id: String,
    val dailyLimit: Int,
    val destination: String,
    val sender: String,
    val threshold: Double,
    val lang: String,
    val subjectTemplate: String,
    val provider: Providers,
    val apiKey: String,
)

fun ContactFormConfig.toMailer(): Mailer {
    return provider.toMailer(apiKey)
}
