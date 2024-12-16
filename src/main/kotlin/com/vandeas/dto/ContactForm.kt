package com.vandeas.dto

import com.icure.kerberus.Solution
import com.vandeas.dto.configs.captcha.GOOGLE_RECAPTCHA_SERIAL_NAME
import com.vandeas.dto.configs.captcha.KERBERUS_SERIAL_NAME
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("captcha")
sealed interface ContactForm {
    val id: String
    val fullName: String
    val email: String
    val content: String
}

@SerialName(GOOGLE_RECAPTCHA_SERIAL_NAME)
@Serializable
data class GoogleRecaptchaContactForm(
    override val id: String,
    override val fullName: String,
    override val email: String,
    override val content: String,
    val recaptchaToken: String
) : ContactForm

@SerialName(KERBERUS_SERIAL_NAME)
@Serializable
data class KerberusContactForm(
    override val id: String,
    override val fullName: String,
    override val email: String,
    override val content: String,
    val solution: Solution
) : ContactForm
