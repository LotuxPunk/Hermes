package com.vandeas.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactFormHoneypotSerializationTest {

    @Test
    fun `existing payload without honeypot fields still deserializes`() {
        val json = """
            {
              "captcha": "GOOGLE_RECAPTCHA",
              "id": "abc-123",
              "fullName": "John Doe",
              "email": "john@example.com",
              "content": "Hello",
              "recaptchaToken": "token"
            }
        """.trimIndent()
        val form = Json.decodeFromString<ContactForm>(json)
        assertNull(form.honeypotToken)
        assertTrue(form.honeypot.isEmpty())
    }

    @Test
    fun `payload with honeypot fields deserializes`() {
        val json = """
            {
              "captcha": "GOOGLE_RECAPTCHA",
              "id": "abc-123",
              "fullName": "John Doe",
              "email": "john@example.com",
              "content": "Hello",
              "recaptchaToken": "token",
              "honeypotToken": "payload.signature",
              "honeypot": { "a7f3kd": "", "qm2x9p": "" }
            }
        """.trimIndent()
        val form = Json.decodeFromString<ContactForm>(json)
        assertEquals("payload.signature", form.honeypotToken)
        assertEquals(mapOf("a7f3kd" to "", "qm2x9p" to ""), form.honeypot)
    }

    @Test
    fun `kerberus payload carries honeypot fields`() {
        val json = """
            {
              "captcha": "KERBERUS",
              "id": "abc-123",
              "fullName": "John Doe",
              "email": "john@example.com",
              "content": "Hello",
              "solution": { "id": "challenge-1", "nonces": ["1", "2", "3"] },
              "honeypotToken": "payload.signature",
              "honeypot": { "a7f3kd": "filled-by-bot" }
            }
        """.trimIndent()
        val form = Json.decodeFromString<ContactForm>(json)
        assertEquals("filled-by-bot", form.honeypot["a7f3kd"])
    }
}
