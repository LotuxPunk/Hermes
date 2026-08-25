package com.vandeas.dto

import com.vandeas.config.AnyMapSerializer
import kotlinx.serialization.Serializable

@Serializable
data class MailInput(
    val id: String,
    val email: String,
    val replyTo: String? = null,
    @Serializable(AnyMapSerializer::class)
    val attributes: Map<String, Any?>
)
