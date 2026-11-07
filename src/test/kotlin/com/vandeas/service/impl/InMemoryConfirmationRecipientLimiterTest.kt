package com.vandeas.service.impl

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryConfirmationRecipientLimiterTest {

    /** A clock the test moves by hand. */
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = instant
    }

    @Test
    fun `grants up to the limit and refuses after`() {
        val limiter = InMemoryConfirmationRecipientLimiter(limit = 3)

        assertEquals(listOf(true, true, true, false, false), List(5) { limiter.tryAcquire("victim@x.com") })
    }

    @Test
    fun `case, whitespace, display name and plus-tag variants share one counter`() {
        val limiter = InMemoryConfirmationRecipientLimiter(limit = 3)

        assertTrue(limiter.tryAcquire("Victim@X.com"))
        assertTrue(limiter.tryAcquire("victim@x.com"))
        assertTrue(limiter.tryAcquire("victim+tag@x.com"))
        assertFalse(limiter.tryAcquire("  Victim Name <VICTIM+other@x.com>  "))
    }

    @Test
    fun `different addresses have their own counters`() {
        val limiter = InMemoryConfirmationRecipientLimiter(limit = 1)

        assertTrue(limiter.tryAcquire("alice@x.com"))
        assertTrue(limiter.tryAcquire("bob@x.com"))
        assertTrue(limiter.tryAcquire("alice@y.com"))
        assertFalse(limiter.tryAcquire("alice@x.com"))
    }

    @Test
    fun `local parts that start with a plus sign keep their own counters`() {
        val limiter = InMemoryConfirmationRecipientLimiter(limit = 1)

        assertTrue(limiter.tryAcquire("+a@x.com"))
        assertTrue(limiter.tryAcquire("+b@x.com"))
        assertFalse(limiter.tryAcquire("+a@x.com"))
    }

    @Test
    fun `counters reset when the clock moves to the next day`() {
        val clock = MutableClock(Instant.parse("2026-11-06T23:59:00Z"))
        val limiter = InMemoryConfirmationRecipientLimiter(limit = 1, clock = clock)

        assertTrue(limiter.tryAcquire("victim@x.com"))
        assertFalse(limiter.tryAcquire("victim@x.com"))

        clock.instant = Instant.parse("2026-11-07T00:01:00Z")

        assertTrue(limiter.tryAcquire("victim@x.com"))
        assertFalse(limiter.tryAcquire("victim@x.com"))
    }

    @Test
    fun `holds no address in clear`() {
        val limiter = InMemoryConfirmationRecipientLimiter(limit = 3)

        limiter.tryAcquire("Victim+tag@X.com")
        limiter.tryAcquire("other.person@example.org")

        val keys = limiter.trackedKeys()
        assertEquals(2, keys.size)
        keys.forEach { key ->
            assertTrue(key.matches(Regex("[0-9a-f]{64}")), "Not a SHA-256 hex digest: $key")
            listOf("victim", "x.com", "other", "example").forEach { fragment ->
                assertFalse(key.contains(fragment, ignoreCase = true), "Key $key contains '$fragment'")
            }
        }
    }

    @Test
    fun `a missing configured limit uses the default`() {
        assertEquals(3, InMemoryConfirmationRecipientLimiter.parseLimit(null))
    }

    @Test
    fun `a valid configured limit is used`() {
        assertEquals(5, InMemoryConfirmationRecipientLimiter.parseLimit("5"))
        assertEquals(1, InMemoryConfirmationRecipientLimiter.parseLimit(" 1 "))
    }

    @Test
    fun `an unparseable configured limit falls back to 3`() {
        listOf("", "three", "3.5", "1e3").forEach { raw ->
            assertEquals(3, InMemoryConfirmationRecipientLimiter.parseLimit(raw), "For '$raw'")
        }
    }

    @Test
    fun `a non-positive configured limit falls back to 3`() {
        listOf("0", "-1", "-100").forEach { raw ->
            assertEquals(3, InMemoryConfirmationRecipientLimiter.parseLimit(raw), "For '$raw'")
        }
    }

    @Test
    fun `the limiter refuses to be built without a positive limit`() {
        assertFailsWith<IllegalArgumentException> { InMemoryConfirmationRecipientLimiter(limit = 0) }
    }
}
