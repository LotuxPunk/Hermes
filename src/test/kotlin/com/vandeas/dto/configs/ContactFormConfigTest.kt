package com.vandeas.dto.configs

import com.vandeas.dto.configs.captcha.GoogleRecaptchaConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.service.impl.toContactFormConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ContactFormConfigTest {

    @Test
    fun `a Resend config without a confirmation block deserialises as before`() {
        val json = """
            {
                "provider": "RESEND",
                "id": "resend-form",
                "dailyLimit": 10,
                "destination": "org@acme.test",
                "sender": "no-reply@acme.test",
                "lang": "fr",
                "subjectTemplate": "New mail from {{form.fullName}}",
                "apiKey": "re_123",
                "captcha": { "provider": "GOOGLE_RECAPTCHA", "secretKey": "s", "threshold": 0.5 }
            }
        """.trimIndent()

        assertEquals(
            ResendContactFormConfig(
                id = "resend-form",
                dailyLimit = 10,
                destination = "org@acme.test",
                sender = "no-reply@acme.test",
                lang = "fr",
                subjectTemplate = "New mail from {{form.fullName}}",
                apiKey = "re_123",
                captcha = GoogleRecaptchaConfig(secretKey = "s", threshold = 0.5),
            ),
            json.toContactFormConfig(),
        )
        assertNull(json.toContactFormConfig().confirmation)
    }

    @Test
    fun `an SMTP config without a confirmation block deserialises as before`() {
        val json = """
            {
                "provider": "SMTP",
                "id": "smtp-form",
                "dailyLimit": 0,
                "destination": "org@acme.test",
                "sender": "no-reply@acme.test",
                "lang": "en",
                "subjectTemplate": "New mail",
                "username": "user",
                "password": "pass",
                "smtpHost": "smtp.acme.test",
                "captcha": { "provider": "KERBERUS", "secretKey": "k" }
            }
        """.trimIndent()

        assertEquals(
            SMTPContactFormConfig(
                id = "smtp-form",
                dailyLimit = 0,
                destination = "org@acme.test",
                sender = "no-reply@acme.test",
                lang = "en",
                subjectTemplate = "New mail",
                username = "user",
                password = "pass",
                smtpHost = "smtp.acme.test",
                captcha = KerberusConfig(secretKey = "k"),
            ),
            json.toContactFormConfig(),
        )
    }

    @Test
    fun `a config with a confirmation block and a finite daily limit loads`() {
        val config = smtpJson(dailyLimit = 5, confirmation = """{ "subjectTemplate": "Thanks {{form.fullName}}" }""")
            .toContactFormConfig()

        assertEquals(VisitorConfirmationConfig(subjectTemplate = "Thanks {{form.fullName}}"), config.confirmation)
    }

    @Test
    fun `a config with a confirmation block and no finite daily limit is rejected`() {
        listOf(0, -1).forEach { dailyLimit ->
            assertFailsWith<IllegalArgumentException>("dailyLimit $dailyLimit") {
                smtpJson(dailyLimit = dailyLimit, confirmation = """{ "subjectTemplate": "Thanks" }""").toContactFormConfig()
            }
        }
    }

    @Test
    fun `a Resend config with a confirmation block and no finite daily limit is rejected`() {
        val json = """
            {
                "provider": "RESEND", "id": "r", "dailyLimit": 0, "destination": "org@acme.test",
                "sender": "no-reply@acme.test", "lang": "en", "subjectTemplate": "New mail", "apiKey": "re_123",
                "captcha": { "provider": "KERBERUS", "secretKey": "k" },
                "confirmation": { "subjectTemplate": "Thanks" }
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> { json.toContactFormConfig() }
    }

    companion object {
        fun smtpJson(id: String = "smtp-form", dailyLimit: Int, confirmation: String? = null) = """
            {
                "provider": "SMTP",
                "id": "$id",
                "dailyLimit": $dailyLimit,
                "destination": "org@acme.test",
                "sender": "no-reply@acme.test",
                "lang": "en",
                "subjectTemplate": "New mail",
                "username": "user",
                "password": "pass",
                "smtpHost": "smtp.acme.test",
                "captcha": { "provider": "KERBERUS", "secretKey": "k" }
                ${confirmation?.let { """, "confirmation": $it""" } ?: ""}
            }
        """.trimIndent()
    }
}
