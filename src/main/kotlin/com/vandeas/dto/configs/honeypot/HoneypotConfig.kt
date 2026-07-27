package com.vandeas.dto.configs.honeypot

import com.vandeas.config.DurationMillisSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Honeypot settings for a contact form. Absent from a config means the layer is disabled.
 *
 * @property secretKey HMAC-SHA256 signing key for this form's session tokens.
 * @property fieldCount How many hidden trap fields to issue per session.
 * @property minDwell A submission arriving sooner than this after issuance is treated as a bot.
 * @property maxAge How long an issued token stays valid.
 */
@Serializable
data class HoneypotConfig(
    val secretKey: String,
    val fieldCount: Int = 2,
    @SerialName("minDwellMillis")
    @Serializable(with = DurationMillisSerializer::class)
    val minDwell: Duration = 2.seconds,
    @SerialName("maxAgeMillis")
    @Serializable(with = DurationMillisSerializer::class)
    val maxAge: Duration = 30.minutes,
)
