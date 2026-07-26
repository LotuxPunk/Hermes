package com.vandeas.service.impl.honeypot

import com.vandeas.dto.configs.honeypot.HoneypotConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Deterministic stand-in for SecureRandom.
 *
 * It MUST return different bytes on each call: [HmacHoneypot] regenerates field names until
 * it has [HoneypotConfig.fieldCount] distinct ones, so a fake returning a constant would
 * loop forever.
 */
internal class SequentialBytes : (Int) -> ByteArray {
    private var counter = 0
    override fun invoke(size: Int): ByteArray = ByteArray(size) { (counter++ % 251).toByte() }
}

class HmacHoneypotIssueTest {

    private val config = HoneypotConfig(secretKey = "hp-secret")

    @Test
    fun `issues the configured number of distinct field names`() {
        val session = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config.copy(fieldCount = 4))

        assertEquals(4, session.fields.size)
        assertEquals(4, session.fields.toSet().size)
    }

    @Test
    fun `field names are lowercase alphanumeric starting with a letter`() {
        val session = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)

        val pattern = Regex("^[a-z][a-z0-9]{7}$")
        session.fields.forEach { name ->
            assertTrue(pattern.matches(name), "field name '$name' does not match $pattern")
        }
    }

    @Test
    fun `session issuedAt is the current clock reading`() {
        val session = HmacHoneypot(now = { 1_774_483_200_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)

        assertEquals(1_774_483_200_000L, session.issuedAt)
    }

    @Test
    fun `token has exactly two dot-separated segments`() {
        val session = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)

        assertEquals(2, session.token.split('.').size)
    }

    @Test
    fun `two sessions get different tokens`() {
        val honeypot = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())

        assertNotEquals(
            honeypot.issue("abc-123", config).token,
            honeypot.issue("abc-123", config).token
        )
    }

    @Test
    fun `a different secret produces a different signature for the same payload`() {
        val first = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)
        val second = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config.copy(secretKey = "other-secret"))

        // Same clock and same deterministic randomness, so the payloads are identical...
        assertEquals(first.token.substringBefore('.'), second.token.substringBefore('.'))
        // ...but the signatures must differ.
        assertNotEquals(first.token.substringAfter('.'), second.token.substringAfter('.'))
    }
}
