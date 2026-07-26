package com.vandeas.logic.impl

import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.ResendContactFormConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.entities.MailSendStatus
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.HoneypotRejectedException
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.DailyLimiter
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MailLogicImplHoneypotTest {

    private val honeypotConfig = HoneypotConfig(secretKey = "hp-secret")

    private fun config(honeypot: HoneypotConfig? = honeypotConfig) = ResendContactFormConfig(
        id = "abc-123",
        dailyLimit = 10,
        destination = "owner@example.com",
        sender = "noreply@example.com",
        lang = "en",
        subjectTemplate = "New mail",
        apiKey = "re_key",
        captcha = KerberusConfig(secretKey = "kerberus-secret"),
        honeypot = honeypot,
    )

    private fun form(destinations: List<String> = emptyList()) = GoogleRecaptchaContactForm(
        id = "abc-123",
        fullName = "John Doe",
        email = "john@example.com",
        content = "Hello",
        destinations = destinations,
        recaptchaToken = "recaptcha-token",
        honeypotToken = "payload.signature",
        honeypot = mapOf("a7f3kd" to ""),
    )

    private class FakeContactFormConfigs(private val config: ContactFormConfig) :
        ConfigDirectory<ContactFormConfig> {
        override fun get(id: String): ContactFormConfig =
            config.takeIf { it.id == id } ?: throw NoSuchElementException("Config with id $id not found")

        override fun getAll(): Map<String, ContactFormConfig> = mapOf(config.id to config)
        override fun getTemplate(id: String): String = "<p>{{form.content}}</p>"
    }

    private class UnusedMailConfigs : ConfigDirectory<MailConfig> {
        override fun get(id: String): MailConfig = error("mail configs are not used in these tests")
        override fun getAll(): Map<String, MailConfig> = emptyMap()
        override fun getTemplate(id: String): String = error("mail configs are not used in these tests")
    }

    private class RecordingLimiter(private val allow: Boolean = true) : DailyLimiter {
        val recorded = mutableListOf<String>()
        override fun canSendMail(config: ContactFormConfig): Boolean = allow
        override fun recordMailSent(config: ContactFormConfig): Boolean {
            recorded.add(config.id)
            return true
        }
    }

    private class StubHoneypot(private val result: HoneypotResult) : Honeypot {
        var validateCalls = 0
        override fun issue(configId: String, config: HoneypotConfig) =
            HoneypotSession(token = "t", fields = listOf("a7f3kd"), issuedAt = 0L)

        override suspend fun validate(
            config: HoneypotConfig,
            expectedConfigId: String,
            token: String?,
            submitted: Map<String, String>,
        ): HoneypotResult {
            validateCalls++
            return result
        }
    }

    private class ExplodingHoneypot : Honeypot {
        override fun issue(configId: String, config: HoneypotConfig) =
            error("issue must not be called")

        override suspend fun validate(
            config: HoneypotConfig,
            expectedConfigId: String,
            token: String?,
            submitted: Map<String, String>,
        ): HoneypotResult = error("validate must not be called when honeypot is not configured")
    }

    private fun logic(
        contactFormConfig: ContactFormConfig,
        honeypot: Honeypot,
        limiter: DailyLimiter,
    ) = MailLogicImpl(UnusedMailConfigs(), FakeContactFormConfigs(contactFormConfig), limiter, honeypot)

    @Test
    fun `a trapped submission reports success without sending or consuming quota`() = runBlocking {
        val limiter = RecordingLimiter()
        val result = logic(config(), StubHoneypot(HoneypotResult.Trapped), limiter)
            .sendContactForm(form())

        assertEquals(MailSendStatus.SENT, result.status)
        assertEquals(listOf("owner@example.com"), result.sent)
        assertTrue(result.failed.isEmpty())
        assertTrue(limiter.recorded.isEmpty(), "a trapped submission must not consume daily quota")
    }

    @Test
    fun `a trapped submission echoes explicit destinations`() = runBlocking {
        val result = logic(config(), StubHoneypot(HoneypotResult.Trapped), RecordingLimiter())
            .sendContactForm(form(destinations = listOf("a@example.com", "b@example.com")))

        assertEquals(listOf("a@example.com", "b@example.com"), result.sent)
    }

    @Test
    fun `an invalid token is rejected`() = runBlocking {
        val honeypot = StubHoneypot(HoneypotResult.InvalidToken("honeypot signature mismatch"))

        val failure = assertFailsWith<HoneypotRejectedException> {
            logic(config(), honeypot, RecordingLimiter()).sendContactForm(form())
        }
        assertEquals("honeypot signature mismatch", failure.message)
    }

    @Test
    fun `the honeypot layer is skipped when the config has no honeypot block`() = runBlocking {
        // Limiter denies, so the flow throws after the honeypot step and before any mailer.
        assertFailsWith<DailyLimitExceededException> {
            logic(config(honeypot = null), ExplodingHoneypot(), RecordingLimiter(allow = false))
                .sendContactForm(form())
        }
        Unit
    }

    @Test
    fun `a passing honeypot falls through to the daily limit check`() = runBlocking {
        val honeypot = StubHoneypot(HoneypotResult.Pass)
        val limiter = RecordingLimiter(allow = false)

        assertFailsWith<DailyLimitExceededException> {
            logic(config(), honeypot, limiter).sendContactForm(form())
        }
        assertEquals(1, honeypot.validateCalls)
        assertTrue(limiter.recorded.isEmpty())
    }
}
