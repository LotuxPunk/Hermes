package com.vandeas.dto.configs

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("provider")
sealed interface ContactFormConfig : Config {
    val dailyLimit: Int
    val destination: String
    val sender: String
    val threshold: Double
    val lang: String
    val subjectTemplate: String
}

@Serializable
@SerialName(RESEND_SERIAL_NAME)
data class ResendContactFormConfig(
    override val id: String,
    override val dailyLimit: Int,
    override val destination: String,
    override val sender: String,
    override val threshold: Double,
    override val lang: String,
    override val subjectTemplate: String,
    override val apiKey: String
) : ContactFormConfig, ResendProvider()

@Serializable
@SerialName(SMTP_SERIAL_NAME)
data class SMTPContactFormConfig(
    override val id: String,
    override val dailyLimit: Int,
    override val destination: String,
    override val sender: String,
    override val threshold: Double,
    override val lang: String,
    override val subjectTemplate: String,
    override val username: String,
    override val password: String,
    override val smtpHost: String,
    override val smtpPort: Int = 587
) : ContactFormConfig, SMTPProvider()
