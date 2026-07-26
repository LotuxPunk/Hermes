package com.vandeas.service.impl.honeypot

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.hours

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

        /** Memory ceiling only — real expiry is enforced per-config from the token's `iat`. */
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
        require(config.fieldCount > 0) { "honeypot fieldCount must be positive" }

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
    ): HoneypotResult = HoneypotResult.InvalidToken("not implemented")

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
