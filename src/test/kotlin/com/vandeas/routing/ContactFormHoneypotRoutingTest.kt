package com.vandeas.routing

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.ResendContactFormConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.logic.HoneypotLogic
import com.vandeas.logic.KerberusLogic
import com.vandeas.logic.MailLogic
import com.vandeas.logic.impl.HoneypotLogicImpl
import com.vandeas.logic.impl.KerberusLogicImpl
import com.vandeas.logic.impl.MailLogicImpl
import com.vandeas.plugins.configureRouting
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.DailyLimiter
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import com.vandeas.service.impl.honeypot.HmacHoneypot
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.minutes

/**
 * HTTP-level verification of the honeypot layer through the real [configureRouting] wiring.
 *
 * The rest of the honeypot test suite (`HmacHoneypotIssueTest`, `HmacHoneypotValidateTest`,
 * `MailLogicImplHoneypotTest`) never goes through Ktor's routing or content negotiation, so
 * none of it proves the HTTP contract — status codes, JSON shapes, the discriminated
 * `ContactForm` body — actually behaves as documented. These tests do, by driving real
 * requests through `testApplication`.
 *
 * Koin is installed here with a hand-built test module instead of `configureKoin()`'s real
 * `appModule`, so `configureRouting()` runs without ever touching
 * `com.vandeas.utils.Constants` (whose initializer reads required env vars and throws
 * without them) — the app module itself is never booted, only the routes are.
 */
class ContactFormHoneypotRoutingTest {

    private val hpConfigId = "hp-form"
    private val plainConfigId = "plain-form"

    private fun contactConfig(id: String, honeypot: HoneypotConfig?, dailyLimit: Int): ContactFormConfig =
        ResendContactFormConfig(
            id = id,
            dailyLimit = dailyLimit,
            destination = "owner@example.com",
            sender = "noreply@example.com",
            lang = "en",
            subjectTemplate = "New mail",
            apiKey = "re_key",
            captcha = KerberusConfig(secretKey = "kerberus-secret"),
            honeypot = honeypot,
        )

    private val configs: Map<String, ContactFormConfig> = mapOf(
        hpConfigId to contactConfig(
            hpConfigId,
            HoneypotConfig(secretKey = "hp-secret", fieldCount = 2, minDwell = ZERO, maxAge = 30.minutes),
            // The limiter below always denies, so a 429 is unambiguous proof the honeypot
            // step already passed and the request fell through to the next check.
            dailyLimit = 0,
        ),
        plainConfigId to contactConfig(plainConfigId, honeypot = null, dailyLimit = 0),
    )

    private inner class FakeContactFormConfigs : ConfigDirectory<ContactFormConfig> {
        override fun get(id: String): ContactFormConfig =
            configs[id] ?: throw NoSuchElementException("Config with id $id not found")
        override fun getAll(): Map<String, ContactFormConfig> = configs
        override fun getTemplate(id: String): String = "<p>{{form.content}}</p>"
    }

    private class UnusedMailConfigs : ConfigDirectory<MailConfig> {
        override fun get(id: String): MailConfig = error("mail configs are not used in these tests")
        override fun getAll(): Map<String, MailConfig> = emptyMap()
        override fun getTemplate(id: String): String = error("mail configs are not used in these tests")
    }

    private class DenyingLimiter : DailyLimiter {
        override fun canSendMail(config: ContactFormConfig): Boolean = config.dailyLimit > 0
        override fun recordMailSent(config: ContactFormConfig): Boolean = true
    }

    private fun testModule() = module {
        single<DailyLimiter> { DenyingLimiter() }
        single<Honeypot> { HmacHoneypot() }
        single<ConfigDirectory<ContactFormConfig>>(named("contactFormConfig")) { FakeContactFormConfigs() }
        single<KerberusLogic> { KerberusLogicImpl(get(named("contactFormConfig"))) }
        single<MailLogic> { MailLogicImpl(UnusedMailConfigs(), get(named("contactFormConfig")), get(), get()) }
        single<HoneypotLogic> { HoneypotLogicImpl(get(named("contactFormConfig")), get()) }
    }

    private fun submission(configId: String, token: String?, honeypot: Map<String, String>): JsonObject =
        buildJsonObject {
            put("captcha", "GOOGLE_RECAPTCHA")
            put("id", configId)
            put("fullName", "John Doe")
            put("email", "john@example.com")
            put("content", "Hello there")
            put("recaptchaToken", "rc-token")
            if (token != null) put("honeypotToken", token)
            put("honeypot", buildJsonObject { honeypot.forEach { (key, value) -> put(key, value) } })
        }

    private fun e2e(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        install(Koin) { modules(testModule()) }
        application { configureRouting() }
        block()
    }

    @Test
    fun `form-session issues a usable session whose blank fields pass and cannot be replayed`() = e2e {
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val sessionResponse = client.get("/v1/mail/contact/$hpConfigId/form-session")
        assertEquals(HttpStatusCode.OK, sessionResponse.status)
        val session = Json.decodeFromString<HoneypotSession>(sessionResponse.bodyAsText())
        assertEquals(2, session.fields.size)
        assertEquals(2, session.token.split('.').size)
        assertTrue(session.fields.all { Regex("^[a-z][a-z0-9]{7}$").matches(it) }, "field names: ${session.fields}")

        // All trap fields blank -> honeypot passes -> daily limit denies -> 429 proves the pass.
        val pass = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(hpConfigId, session.token, session.fields.associateWith { "" }).toString())
        }
        assertEquals(HttpStatusCode.TooManyRequests, pass.status)

        // Replaying the same token must now be rejected: its nonce was already consumed.
        val replay = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(hpConfigId, session.token, session.fields.associateWith { "" }).toString())
        }
        assertEquals(HttpStatusCode.Forbidden, replay.status)
    }

    @Test
    fun `a tripped trap answers with a success indistinguishable from a real send`() = e2e {
        val client = createClient { install(ClientContentNegotiation) { json() } }
        val session: HoneypotSession = client.get("/v1/mail/contact/$hpConfigId/form-session").body()

        val filled = session.fields.associateWith { "" } + (session.fields.first() to "http://spam.example")
        val trapped = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(hpConfigId, session.token, filled).toString())
        }

        // Byte-identical to a real send: same 200, same body shape, no hint a trap tripped.
        assertEquals(HttpStatusCode.OK, trapped.status)
        assertEquals(
            """{"sent":["owner@example.com"],"failed":[],"bounced":[],"temporary":[]}""",
            trapped.bodyAsText(),
        )
    }

    @Test
    fun `missing, garbage and oversized tokens are all rejected with 403`() = e2e {
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val noToken = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(hpConfigId, null, emptyMap()).toString())
        }
        assertEquals(HttpStatusCode.Forbidden, noToken.status)

        val garbage = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(hpConfigId, "aaa.bbb", mapOf("x" to "")).toString())
        }
        assertEquals(HttpStatusCode.Forbidden, garbage.status)

        val oversized = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(hpConfigId, "a".repeat(2_000_000) + "." + "b".repeat(16), emptyMap()).toString())
        }
        assertEquals(HttpStatusCode.Forbidden, oversized.status)
    }

    @Test
    fun `existing clients on a config without a honeypot block are unaffected by a legacy payload`() = e2e {
        val client = createClient { install(ClientContentNegotiation) { json() } }

        // Legacy payload: no honeypotToken field, no honeypot map at all.
        val legacy = """
            {"captcha":"GOOGLE_RECAPTCHA","id":"$plainConfigId","fullName":"John Doe",
             "email":"john@example.com","content":"Hello","recaptchaToken":"rc"}
        """.trimIndent()
        val response = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(legacy)
        }
        // No honeypot block on this config, so validation is skipped entirely and the
        // request falls straight through to the (denying) daily limit -> 429.
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
    }

    @Test
    fun `form-session 404s for an unknown config and 400s for one without a honeypot block`() = e2e {
        val client = createClient { install(ClientContentNegotiation) { json() } }

        val missing = client.get("/v1/mail/contact/does-not-exist/form-session")
        assertEquals(HttpStatusCode.NotFound, missing.status)

        val noHoneypot = client.get("/v1/mail/contact/$plainConfigId/form-session")
        assertEquals(HttpStatusCode.BadRequest, noHoneypot.status)
    }

    @Test
    fun `a token issued for one config is ignored by another config with no honeypot block`() = e2e {
        val client = createClient { install(ClientContentNegotiation) { json() } }
        val session: HoneypotSession = client.get("/v1/mail/contact/$hpConfigId/form-session").body()

        val crossed = client.post("/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(submission(plainConfigId, session.token, session.fields.associateWith { "" }).toString())
        }
        // plain-form has no honeypot block, so the token is simply ignored -> falls through
        // to the (denying) daily limit -> 429, not a 403.
        assertEquals(HttpStatusCode.TooManyRequests, crossed.status)
    }

    /**
     * The one guarantee the stateful nonce buys: two racing submissions, exactly one wins.
     * Exercises real concurrency against the real [HmacHoneypot] rather than a fake clock,
     * so it belongs alongside the other real-behaviour checks in this file even though it
     * calls [Honeypot] directly rather than through HTTP.
     */
    @Test
    fun `concurrent validate calls on one nonce let exactly one through`() = runBlocking {
        val honeypot = HmacHoneypot()
        val hp = HoneypotConfig(secretKey = "hp-secret", fieldCount = 2, minDwell = ZERO, maxAge = 30.minutes)

        repeat(200) { round ->
            val session = honeypot.issue("race", hp)
            val blanks = session.fields.associateWith { "" }
            val results = coroutineScope {
                (1..8).map { async(Dispatchers.Default) { honeypot.validate(hp, "race", session.token, blanks) } }
                    .map { it.await() }
            }
            val passes = results.count { it is HoneypotResult.Pass }
            assertEquals(1, passes, "round $round: $results")
        }
    }
}
