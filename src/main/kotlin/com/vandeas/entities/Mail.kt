package com.vandeas.entities

import kotlinx.serialization.Serializable

/**
 * @property replyTo Optional address that receives replies instead of [from]. Null means
 * no `Reply-To` header is set and replies go to the sender.
 */
@Serializable
data class Mail(
    val from: String,
    val to: String,
    val subject: String,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val replyTo: String? = null,
)
