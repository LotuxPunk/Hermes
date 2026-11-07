package com.vandeas.service.impl

import com.vandeas.service.ConfirmationRecipientLimiter
import com.vandeas.utils.bareEmailAddress
import io.ktor.util.logging.*
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate

/**
 * In-memory [ConfirmationRecipientLimiter].
 *
 * Counters are keyed by the SHA-256 of the normalised address, so no address is held in
 * clear. They reset when the day changes and are lost on restart, like
 * [InMemoryDailyLimiter].
 *
 * @param limit Visitor confirmations one address may receive per day. Must be positive:
 * the limit cannot be disabled.
 * @param clock Source of the current day, injectable for tests.
 */
class InMemoryConfirmationRecipientLimiter(
    private val limit: Int,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ConfirmationRecipientLimiter {

    init {
        require(limit > 0) { "Confirmation recipient limit must be positive, got $limit" }
    }

    private var day: LocalDate = LocalDate.now(clock)
    private val counts: MutableMap<String, Int> = HashMap()

    @Synchronized
    override fun tryAcquire(address: String): Boolean {
        val today = LocalDate.now(clock)
        if (today != day) {
            day = today
            counts.clear()
        }

        val key = keyOf(address)
        val used = counts[key] ?: 0
        if (used >= limit) {
            return false
        }
        counts[key] = used + 1
        return true
    }

    /** The keys currently tracked, for tests that check no address is held in clear. */
    @Synchronized
    internal fun trackedKeys(): Set<String> = counts.keys.toSet()

    companion object {
        const val DEFAULT_LIMIT = 3

        private val logger = KtorSimpleLogger("com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter")

        /**
         * Parses the `CONFIRMATION_RECIPIENT_DAILY_LIMIT` environment variable.
         *
         * The limit cannot be disabled: a missing value uses [DEFAULT_LIMIT], and an
         * unparseable or non-positive one falls back to it with a warning, so a typo never
         * turns the protection off.
         */
        fun parseLimit(raw: String?): Int {
            if (raw == null) {
                return DEFAULT_LIMIT
            }
            val parsed = raw.trim().toIntOrNull()
            if (parsed == null || parsed <= 0) {
                logger.warn("Invalid CONFIRMATION_RECIPIENT_DAILY_LIMIT '$raw', falling back to $DEFAULT_LIMIT")
                return DEFAULT_LIMIT
            }
            return parsed
        }

        /**
         * Normalises [address] (bare address, trimmed, lower-cased, `+tag` removed from the
         * local part) and hashes it with SHA-256. A local part that starts with `+` has no
         * tag to remove and is kept whole.
         *
         * @return the hex-encoded hash.
         */
        internal fun keyOf(address: String): String {
            val bare = address.bareEmailAddress().trim().lowercase()
            val at = bare.lastIndexOf('@')
            val normalised = if (at > 0) {
                val local = bare.substring(0, at)
                val plus = local.indexOf('+')
                (if (plus > 0) local.substring(0, plus) else local) + bare.substring(at)
            } else {
                bare
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(normalised.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
