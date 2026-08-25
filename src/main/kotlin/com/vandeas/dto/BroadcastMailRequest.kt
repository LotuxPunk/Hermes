package com.vandeas.dto

import com.vandeas.config.AnyMapSerializer
import kotlinx.serialization.Serializable

@Serializable
data class BroadcastMailRequest(
    val to: List<String>,
    val replyTo: String? = null,
    @Serializable(AnyMapSerializer::class)
    val attributes: Map<String, Any?>
)
