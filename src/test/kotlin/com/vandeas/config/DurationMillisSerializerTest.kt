package com.vandeas.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationMillisSerializerTest {

    @Serializable
    private data class Holder(
        @Serializable(with = DurationMillisSerializer::class)
        val value: Duration
    )

    @Test
    fun `encodes duration as whole milliseconds`() {
        assertEquals("""{"value":2000}""", Json.encodeToString(Holder(2.seconds)))
    }

    @Test
    fun `decodes millis into duration`() {
        assertEquals(30.minutes, Json.decodeFromString<Holder>("""{"value":1800000}""").value)
    }

    @Test
    fun `round trips through json`() {
        val original = Holder(90.seconds)
        assertEquals(original, Json.decodeFromString<Holder>(Json.encodeToString(original)))
    }

    @Test
    fun `truncates sub-millisecond precision`() {
        assertEquals("""{"value":1}""", Json.encodeToString(Holder(1500.microseconds)))
    }

    @Test
    fun `decodes zero`() {
        assertEquals(Duration.ZERO, Json.decodeFromString<Holder>("""{"value":0}""").value)
    }

    @Test
    fun `decodes large millis values without overflow`() {
        assertEquals(86_400_000L.milliseconds, Json.decodeFromString<Holder>("""{"value":86400000}""").value)
    }
}
