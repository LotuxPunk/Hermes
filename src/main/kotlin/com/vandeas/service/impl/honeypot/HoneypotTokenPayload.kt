package com.vandeas.service.impl.honeypot

import kotlinx.serialization.Serializable

/**
 * The signed half of a honeypot token. Kept `internal` so tests can forge tampered payloads.
 *
 * Field names are abbreviated to keep the token short.
 *
 * @property cid config id this token is bound to
 * @property n single-use nonce
 * @property iat issued-at, epoch millis
 * @property f the authoritative honeypot field names
 */
@Serializable
internal data class HoneypotTokenPayload(
    val cid: String,
    val n: String,
    val iat: Long,
    val f: List<String>,
)
