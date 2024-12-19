package com.vandeas.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

object AnySerializer : KSerializer<Any?> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor("Any", PolymorphicKind.OPEN)

    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("This serializer only works with JSON")
        val jsonElement = encodeToJsonElement(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("This serializer only works with JSON")
        val element = jsonDecoder.decodeJsonElement()
        return decodeFromJsonElement(element)
    }

    private fun encodeToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map { encodeToJsonElement(it) })
        is Map<*, *> -> JsonObject(value.entries.associate {
            it.key.toString() to encodeToJsonElement(it.value)
        })
        else -> JsonPrimitive(value.toString())
    }

    private fun decodeFromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.intOrNull != null -> element.int
                element.floatOrNull != null -> element.float
                element.doubleOrNull != null -> element.double
                else -> throw IllegalArgumentException("Unsupported primitive type")
            }
        }
        is JsonArray -> element.map { decodeFromJsonElement(it) }
        is JsonObject -> element.mapValues { decodeFromJsonElement(it.value) }
    }
}
