package com.vandeas.service.impl.honeypot

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stateless-except-for-nonces honeypot.
 *
 * The field names a visitor was given travel inside an HMAC-signed token rather than being
 * stored server-side, so the server can trust them on the way back without remembering them.
 * The nonce is the one piece of state, and exists only to make a token single-use.
 *
 * @param now epoch-millis clock, injectable so tests can drive time.
 * @param randomBytes randomness source, injectable so tests are deterministic.
 */
class HmacHoneypot(
    private val now: () -> Long = System::currentTimeMillis,
    private val randomBytes: (Int) -> ByteArray = SecureRandom().let { secureRandom ->
        { size -> ByteArray(size).also(secureRandom::nextBytes) }
    },
) : Honeypot {

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val NONCE_BYTES = 16
        const val FIELD_NAME_LENGTH = 8
        const val FIELD_NAME_FIRST_CHARS = "abcdefghijklmnopqrstuvwxyz"
        const val FIELD_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
        const val MAX_FIELD_COUNT = 32

        /**
         * Real ceiling on token lifetime, not just a memory bound: cache4k expires nonce
         * entries on its own wall-clock `TimeSource`, disconnected from the injected [now].
         * A token older than this has its nonce evicted from underneath it and is rejected
         * as "already used or unknown" regardless of what `config.maxAge` allows. `issue()`
         * enforces `config.maxAge <= NONCE_TTL_CEILING` so a config that would silently be
         * capped at this ceiling fails loudly at session issuance instead.
         */
        val NONCE_TTL_CEILING = 1.hours
        const val NONCE_CACHE_MAX = 100_000L
    }

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    private val nonces = Cache.Builder<String, Unit>()
        .expireAfterWrite(NONCE_TTL_CEILING)
        .maximumCacheSize(NONCE_CACHE_MAX)
        .build()

    private val nonceMutex = Mutex()

    override fun issue(configId: String, config: HoneypotConfig): HoneypotSession {
        require(config.fieldCount in 1..MAX_FIELD_COUNT) {
            "honeypot fieldCount must be between 1 and $MAX_FIELD_COUNT"
        }
        require(config.maxAge <= NONCE_TTL_CEILING) {
            "honeypot maxAge must not exceed the nonce cache ceiling of $NONCE_TTL_CEILING"
        }

        val issuedAt = now()
        val nonce = encoder.encodeToString(randomBytes(NONCE_BYTES))
        val fields = generateFieldNames(config.fieldCount)

        val encodedPayload = encoder.encodeToString(
            Json.encodeToString(HoneypotTokenPayload(cid = configId, n = nonce, iat = issuedAt, f = fields))
                .toByteArray(UTF_8)
        )

        nonces.put(nonce, Unit)

        return HoneypotSession(
            token = "$encodedPayload.${encoder.encodeToString(sign(encodedPayload, config.secretKey))}",
            fields = fields,
            issuedAt = issuedAt,
        )
    }

    override suspend fun validate(
        config: HoneypotConfig,
        expectedConfigId: String,
        token: String?,
        submitted: Map<String, String>,
    ): HoneypotResult {
        // 1 — structure
        if (token.isNullOrBlank()) return HoneypotResult.InvalidToken("missing honeypot token")
        val segments = token.split('.')
        if (segments.size != 2) return HoneypotResult.InvalidToken("malformed honeypot token")
        val (encodedPayload, encodedSignature) = segments

        // 2 — authenticate before decoding anything the client controls
        val providedSignature = runCatching { decoder.decode(encodedSignature) }.getOrNull()
            ?: return HoneypotResult.InvalidToken("malformed honeypot signature")
        if (!MessageDigest.isEqual(sign(encodedPayload, config.secretKey), providedSignature)) {
            return HoneypotResult.InvalidToken("honeypot signature mismatch")
        }

        // 3 — now the payload is trustworthy enough to parse
        val payload = runCatching {
            Json.decodeFromString<HoneypotTokenPayload>(String(decoder.decode(encodedPayload), UTF_8))
        }.getOrNull() ?: return HoneypotResult.InvalidToken("malformed honeypot payload")

        // 4 — bound to one form
        if (payload.cid != expectedConfigId) {
            return HoneypotResult.InvalidToken("honeypot token issued for a different config")
        }

        // 5 — freshness
        val age = (now() - payload.iat).milliseconds
        if (age.isNegative()) return HoneypotResult.InvalidToken("honeypot token issued in the future")
        if (age > config.maxAge) return HoneypotResult.InvalidToken("honeypot token expired")

        // 6 — single use. Consumed before the trap checks, so tripping a trap burns the token.
        val consumed = nonceMutex.withLock {
            (nonces.get(payload.n) != null).also { present -> if (present) nonces.invalidate(payload.n) }
        }
        if (!consumed) return HoneypotResult.InvalidToken("honeypot token already used or unknown")

        // 7 — no human fills a form this fast
        if (age < config.minDwell) return HoneypotResult.Trapped("dwell: submission arrived before minDwell elapsed")

        // 8 — every declared trap field must have come back present and blank
        if (payload.f.any { field -> submitted[field]?.isBlank() != true }) {
            return HoneypotResult.Trapped("field: a trap field was filled in or missing")
        }

        return HoneypotResult.Pass
    }

    /**
     * HMAC over the ASCII bytes of the base64url segment, not the raw JSON — so verification
     * never depends on re-serializing the payload byte-identically.
     */
    private fun sign(encodedPayload: String, secretKey: String): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(secretKey.toByteArray(UTF_8), HMAC_ALGORITHM))
        }.doFinal(encodedPayload.toByteArray(US_ASCII))

    private fun generateFieldNames(count: Int): List<String> {
        val names = LinkedHashSet<String>(count)
        while (names.size < count) {
            names.add(randomFieldName())
        }
        return names.toList()
    }

    /**
     * No fixed prefix, so a bot cannot regex the trap fields out of the form, and nothing for
     * browser autofill to recognise. The modulo bias here is irrelevant — these names are
     * obfuscation, not a security parameter.
     */
    private fun randomFieldName(): String {
        val bytes = randomBytes(FIELD_NAME_LENGTH)
        return buildString(FIELD_NAME_LENGTH) {
            append(FIELD_NAME_FIRST_CHARS[(bytes[0].toInt() and 0xFF) % FIELD_NAME_FIRST_CHARS.length])
            for (index in 1 until FIELD_NAME_LENGTH) {
                append(FIELD_NAME_CHARS[(bytes[index].toInt() and 0xFF) % FIELD_NAME_CHARS.length])
            }
        }
    }
}
