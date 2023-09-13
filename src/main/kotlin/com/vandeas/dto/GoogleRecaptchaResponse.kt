package com.vandeas.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class GoogleRecaptchaResponse(
    val success: Boolean,
    @JsonProperty("challenge_ts") val challengeTs: String,
    val hostname: String,
    @JsonProperty("error-codes") val errorCodes: List<Any>? = null,
    val score: Double,
    val action: String
)
