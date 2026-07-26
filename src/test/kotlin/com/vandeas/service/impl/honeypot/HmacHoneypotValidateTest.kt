package com.vandeas.service.impl.honeypot

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.service.HoneypotResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HmacHoneypotValidateTest {

    private val config = HoneypotConfig(
        secretKey = "hp-secret",
        fieldCount = 2,
        minDwell = 2.seconds,
        maxAge = 30.minutes,
    )

    private val issuedAt = 1_774_483_200_000L
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** Clock the test moves by hand. */
    private class MovableClock(var millis: Long) : () -> Long {
        override fun invoke(): Long = millis
    }

    private fun honeypotAt(clock: MovableClock) = HmacHoneypot(clock, SequentialBytes())

    /** Issues a session, then advances the clock past `minDwell` so the happy path passes. */
    private fun issueAndSettle(
        clock: MovableClock = MovableClock(issuedAt),
        configId: String = "abc-123",
        honeypotConfig: HoneypotConfig = config,
    ): Triple<HmacHoneypot, MovableClock, HoneypotSession> {
        val honeypot = honeypotAt(clock)
        val session = honeypot.issue(configId, honeypotConfig)
        clock.millis = issuedAt + 8_000
        return Triple(honeypot, clock, session)
    }

    private fun blankFor(fields: List<String>) = fields.associateWith { "" }

    @Test
    fun `accepts a well-formed submission`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        assertEquals(
            HoneypotResult.Pass,
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a null token`(): Unit = runBlocking {
        val (honeypot, _, _) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(honeypot.validate(config, "abc-123", null, emptyMap()))
    }

    @Test
    fun `rejects a blank token`(): Unit = runBlocking {
        val (honeypot, _, _) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(honeypot.validate(config, "abc-123", "   ", emptyMap()))
    }

    @Test
    fun `rejects a token with no separator`(): Unit = runBlocking {
        val (honeypot, _, _) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", "notatoken", emptyMap())
        )
    }

    @Test
    fun `rejects a token with three segments`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", "${session.token}.extra", blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a non-base64 signature`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val forged = "${session.token.substringBefore('.')}.!!!not-base64!!!"

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", forged, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a tampered signature`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val signature = session.token.substringAfter('.')
        val flipped = signature.replaceRange(0, 1, if (signature[0] == 'A') "B" else "A")
        val forged = "${session.token.substringBefore('.')}.$flipped"

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", forged, blankFor(session.fields))
        )
    }

    /** The attack the signature exists to stop: editing the trap-field list out of the token. */
    @Test
    fun `rejects a payload edited to drop the trap fields`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        val original = Json.decodeFromString<HoneypotTokenPayload>(
            String(decoder.decode(session.token.substringBefore('.')), UTF_8)
        )
        val edited = encoder.encodeToString(
            Json.encodeToString(original.copy(f = emptyList())).toByteArray(UTF_8)
        )
        val forged = "$edited.${session.token.substringAfter('.')}"

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", forged, emptyMap())
        )
    }

    @Test
    fun `rejects a token signed with a different secret`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(
                config.copy(secretKey = "wrong-secret"),
                "abc-123",
                session.token,
                blankFor(session.fields)
            )
        )
    }

    @Test
    fun `rejects a token issued for a different config`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle(configId = "other-form")

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects an expired token`(): Unit = runBlocking {
        val clock = MovableClock(issuedAt)
        val (honeypot, _, session) = issueAndSettle(clock)
        clock.millis = issuedAt + 30.minutes.inWholeMilliseconds + 1

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `accepts a token exactly at maxAge`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val (honeypot, _, session) = issueAndSettle(clock)
        clock.millis = issuedAt + 30.minutes.inWholeMilliseconds

        assertEquals(
            HoneypotResult.Pass,
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a future-dated token`(): Unit = runBlocking {
        val clock = MovableClock(issuedAt)
        val (honeypot, _, session) = issueAndSettle(clock)
        clock.millis = issuedAt - 5_000

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a replayed token`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val fields = blankFor(session.fields)

        assertEquals(HoneypotResult.Pass, honeypot.validate(config, "abc-123", session.token, fields))
        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, fields)
        )
    }

    @Test
    fun `rejects a token this instance never issued`(): Unit = runBlocking {
        val clock = MovableClock(issuedAt)
        val session = honeypotAt(clock).issue("abc-123", config)
        clock.millis = issuedAt + 8_000
        val otherInstance = honeypotAt(clock)

        assertIs<HoneypotResult.InvalidToken>(
            otherInstance.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `traps a submission faster than minDwell`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val honeypot = honeypotAt(clock)
        val session = honeypot.issue("abc-123", config)
        clock.millis = issuedAt + 500

        assertEquals(
            HoneypotResult.Trapped,
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `traps a filled trap field`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val filled = blankFor(session.fields) + (session.fields.first() to "http://spam.example")

        assertEquals(HoneypotResult.Trapped, honeypot.validate(config, "abc-123", session.token, filled))
    }

    /** Blank means `isBlank()`, so whitespace is blank and must pass — not trap. */
    @Test
    fun `treats a whitespace-only trap field as blank`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val whitespace = blankFor(session.fields) + (session.fields.first() to "   ")

        assertEquals(HoneypotResult.Pass, honeypot.validate(config, "abc-123", session.token, whitespace))
    }

    @Test
    fun `traps a missing trap field`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val incomplete = blankFor(session.fields) - session.fields.first()

        assertEquals(HoneypotResult.Trapped, honeypot.validate(config, "abc-123", session.token, incomplete))
    }

    @Test
    fun `ignores undeclared extra fields`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val extra = blankFor(session.fields) + ("someOtherInput" to "legitimate value")

        assertEquals(HoneypotResult.Pass, honeypot.validate(config, "abc-123", session.token, extra))
    }

    @Test
    fun `a trapped submission still consumes its token`(): Unit = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val filled = blankFor(session.fields) + (session.fields.first() to "spam")

        assertEquals(HoneypotResult.Trapped, honeypot.validate(config, "abc-123", session.token, filled))
        // Second attempt with correct blanks must fail: the nonce is already gone.
        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejection reasons are non-empty`() = runBlocking {
        val (honeypot, _, _) = issueAndSettle()
        val result = honeypot.validate(config, "abc-123", null, emptyMap())

        assertTrue(assertIs<HoneypotResult.InvalidToken>(result).reason.isNotBlank())
    }
}
