package com.vandeas.entities

import com.vandeas.config.ByteArrayBase64Serializer
import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val filename: String,
    @Serializable(ByteArrayBase64Serializer::class)
    val content: ByteArray,
    val contentType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Attachment
        if (filename != other.filename) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false
        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}
