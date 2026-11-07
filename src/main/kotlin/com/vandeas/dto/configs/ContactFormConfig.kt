package com.vandeas.dto.configs

import com.vandeas.dto.configs.captcha.CaptchaConfig
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
    val lang: String
    val subjectTemplate: String
    val captcha: CaptchaConfig

    /**
     * Enables the Visitor confirmation: when present, every Submission also sends one mail
     * to the address the visitor typed. Null means no Visitor confirmation, as before.
     */
    val confirmation: VisitorConfirmationConfig?
}

/**
 * Visitor confirmation settings of a contact form config.
 *
 * The body template is not part of the config: it lives in the templates folder as
 * `<config id>.confirmation.hbs`.
 *
 * @property subjectTemplate Mustache template of the Visitor confirmation subject.
 */
@Serializable
data class VisitorConfirmationConfig(
    val subjectTemplate: String,
)

@Serializable
@SerialName(RESEND_SERIAL_NAME)
data class ResendContactFormConfig(
    override val id: String,
    override val dailyLimit: Int,
    override val destination: String,
    override val sender: String,
    override val lang: String,
    override val subjectTemplate: String,
    override val apiKey: String,
    override val captcha: CaptchaConfig,
    override val confirmation: VisitorConfirmationConfig? = null,
) : ContactFormConfig, ResendProvider()

@Serializable
@SerialName(SMTP_SERIAL_NAME)
data class SMTPContactFormConfig(
    override val id: String,
    override val dailyLimit: Int,
    override val destination: String,
    override val sender: String,
    override val lang: String,
    override val subjectTemplate: String,
    override val username: String,
    override val password: String,
    override val smtpHost: String,
    override val smtpPort: Int = 587,
    override val captcha: CaptchaConfig,
    override val confirmation: VisitorConfirmationConfig? = null,
) : ContactFormConfig, SMTPProvider()

/**
 * Rejects a contact form config that cannot be loaded safely.
 *
 * A Visitor confirmation goes to an address nobody has verified, so a config that enables
 * one must cap its Submissions with a finite Daily limit (`dailyLimit > 0`). Without it,
 * the form would be an unbounded mail relay.
 *
 * @throws IllegalArgumentException when the config has a `confirmation` block and no finite
 * Daily limit.
 */
fun ContactFormConfig.validate() {
    require(confirmation == null || dailyLimit > 0) {
        "Contact form config $id enables a Visitor confirmation, so its dailyLimit must be greater than 0 (got $dailyLimit)"
    }
}
