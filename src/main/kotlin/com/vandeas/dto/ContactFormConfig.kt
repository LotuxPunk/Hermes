package com.vandeas.dto

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
)
