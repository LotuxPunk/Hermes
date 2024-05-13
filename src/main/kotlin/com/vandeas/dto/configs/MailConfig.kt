package com.vandeas.dto.configs

import com.vandeas.service.impl.mailer.ResendMailer
import com.vandeas.service.impl.mailer.SMTPMailer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("provider")
sealed interface MailConfig : Config {
    val sender: String
    val subjectTemplate: String
}

@Serializable
@SerialName(RESEND_SERIAL_NAME)
data class ResendMailConfig(
    override val id: String,
    override val sender: String,
    override val subjectTemplate: String,
    override val apiKey: String
) : MailConfig, ResendProvider()

@Serializable
@SerialName(SMTP_SERIAL_NAME)
data class SMTPMailConfig(
    override val id: String,
    override val sender: String,
    override val subjectTemplate: String,
    override val username: String,
    override val password: String,
    override val smtpHost: String,
    override val smtpPort: Int = 587
) : MailConfig, SMTPProvider()
