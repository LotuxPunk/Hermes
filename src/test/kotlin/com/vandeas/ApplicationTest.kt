package com.vandeas

import com.icure.kryptom.crypto.defaultCryptoService
import com.vandeas.dto.MailInput
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {
    @Test
    fun testSerialization() {
        val mailInput = MailInput(
            defaultCryptoService.strongRandom.randomUUID(),
            email = "john@doe.be",
            attributes = mapOf(
                "name" to "John Doe",
                "phone" to "+32123456789",
                "location" to mapOf(
                    "city" to "Brussels",
                    "country" to "Belgium"
                ),
                "preferences" to listOf("newsletter", "offers")
            )
        )

        val serialized = Json.encodeToString(MailInput.serializer(), mailInput)
        println(serialized)
        val deserialized = Json.decodeFromString(MailInput.serializer(), serialized)
        assertEquals(mailInput, deserialized)
    }
}
