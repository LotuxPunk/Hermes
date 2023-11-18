package com.vandeas.dto

import kotlinx.serialization.Serializable

@Serializable
data class MailConfig(
    val id: String,
    val sender: String,
    val subjectTemplate: String,
)
