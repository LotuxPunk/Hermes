package com.vandeas.entities

import kotlinx.serialization.Serializable

/**
 * @property replyTo Optional address that receives replies instead of [from]. Null means
 * no `Reply-To` header is set and replies go to the sender.
 * @property senderName Optional display name that replaces the name in [from] for this mail.
 * The address stays the one in [from]. Null means [from] is used as it is.
 */
@Serializable
data class Mail(
    val from: String,
    val to: String,
    val subject: String,
    val content: String,
    val attachments: List<Attachment> = emptyList(),
    val replyTo: String? = null,
    val senderName: String? = null,
)
