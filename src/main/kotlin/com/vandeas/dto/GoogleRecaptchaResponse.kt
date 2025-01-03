package com.vandeas.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GoogleRecaptchaResponse(
    val success: Boolean,
    val score: Double,
    val action: String,
    @SerialName("challenge_ts") val challengeTs: String,
    val hostname: String,
    @SerialName("error-codes") val errorCodes: List<JsonElement>? = null,
)
