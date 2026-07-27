package com.vandeas.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Serializes a [Duration] as a whole number of milliseconds.
 *
 * Config files are hand-edited, so the JSON side stays a plain integer while Kotlin code
 * keeps a unit-safe [Duration]. Sub-millisecond precision is truncated.
 */
object DurationMillisSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DurationMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.inWholeMilliseconds)
    }

    override fun deserialize(decoder: Decoder): Duration =
        decoder.decodeLong().milliseconds
}
