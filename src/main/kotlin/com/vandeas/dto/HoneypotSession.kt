package com.vandeas.dto

import kotlinx.serialization.Serializable

/**
 * Issued honeypot session. The client renders one hidden input per entry in [fields] and
 * submits [token] plus those fields back with the contact form.
 *
 * @property issuedAt epoch millis, the same value the token carries internally.
 */
@Serializable
data class HoneypotSession(
    val token: String,
    val fields: List<String>,
    val issuedAt: Long,
)
