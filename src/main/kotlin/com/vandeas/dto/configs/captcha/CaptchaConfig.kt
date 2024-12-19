package com.vandeas.dto.configs.captcha

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator


const val GOOGLE_RECAPTCHA_SERIAL_NAME = "GOOGLE_RECAPTCHA"
const val KERBERUS_SERIAL_NAME = "KERBERUS"

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("provider")
sealed interface CaptchaConfig {
    val secretKey: String
}

@SerialName(GOOGLE_RECAPTCHA_SERIAL_NAME)
@Serializable
data class GoogleRecaptchaConfig(
    override val secretKey: String,
    val threshold: Double
) : CaptchaConfig

@SerialName(KERBERUS_SERIAL_NAME)
@Serializable
data class KerberusConfig(
    override val secretKey: String
): CaptchaConfig

