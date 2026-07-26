package com.vandeas.dto.configs

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ContactFormConfigHoneypotTest {

    private val configWithoutHoneypot = """
        {
          "provider": "RESEND",
          "id": "abc-123",
          "dailyLimit": 10,
          "destination": "john@example.com",
          "sender": "doe@example.com",
          "lang": "fr",
          "subjectTemplate": "New mail",
          "apiKey": "re_key",
          "captcha": { "provider": "KERBERUS", "secretKey": "kerberus-secret" }
        }
    """.trimIndent()

    @Test
    fun `existing config without a honeypot block still deserializes`() {
        val config = Json.decodeFromString<ContactFormConfig>(configWithoutHoneypot)
        assertNull(config.honeypot)
    }

    @Test
    fun `honeypot block deserializes with millis durations`() {
        val json = configWithoutHoneypot.replace(
            """"apiKey": "re_key",""",
            """"apiKey": "re_key",
               "honeypot": {
                 "secretKey": "hp-secret",
                 "fieldCount": 3,
                 "minDwellMillis": 5000,
                 "maxAgeMillis": 600000
               },"""
        )
        val honeypot = Json.decodeFromString<ContactFormConfig>(json).honeypot
        assertEquals("hp-secret", honeypot?.secretKey)
        assertEquals(3, honeypot?.fieldCount)
        assertEquals(5.seconds, honeypot?.minDwell)
        assertEquals(10.minutes, honeypot?.maxAge)
    }

    @Test
    fun `honeypot block applies defaults when only secretKey is given`() {
        val json = configWithoutHoneypot.replace(
            """"apiKey": "re_key",""",
            """"apiKey": "re_key",
               "honeypot": { "secretKey": "hp-secret" },"""
        )
        val honeypot = Json.decodeFromString<ContactFormConfig>(json).honeypot
        assertEquals(2, honeypot?.fieldCount)
        assertEquals(2.seconds, honeypot?.minDwell)
        assertEquals(30.minutes, honeypot?.maxAge)
    }

    @Test
    fun `smtp config also carries the honeypot block`() {
        val json = """
            {
              "provider": "SMTP",
              "id": "smtp-1",
              "dailyLimit": 5,
              "destination": "john@example.com",
              "sender": "doe@example.com",
              "lang": "en",
              "subjectTemplate": "New mail",
              "username": "user",
              "password": "pass",
              "smtpHost": "smtp.example.com",
              "captcha": { "provider": "KERBERUS", "secretKey": "kerberus-secret" },
              "honeypot": { "secretKey": "hp-secret", "minDwellMillis": 1000 }
            }
        """.trimIndent()
        val config = Json.decodeFromString<ContactFormConfig>(json)
        assertEquals(1.seconds, config.honeypot?.minDwell)
    }
}
