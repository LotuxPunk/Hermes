package com.vandeas.entities

import kotlinx.serialization.Serializable

@Serializable
data class Mail(
    val from: String,
    val to: String,
    val subject: String,
    val content: String,
)
