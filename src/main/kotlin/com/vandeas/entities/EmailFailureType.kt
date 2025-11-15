package com.vandeas.entities

/**
 * Types of email sending failures
 */
enum class EmailFailureType {
    /**
     * Permanent failure - email bounced, invalid address, etc.
     * Should NOT be retried.
     */
    BOUNCED,

    /**
     * Temporary failure - rate limit, network issue, server temporarily unavailable.
     * Can be retried.
     */
    TEMPORARY,

    /**
     * Unknown failure type
     */
    UNKNOWN
}

/**
 * Details about a failed email
 */
data class EmailFailure(
    val email: String,
    val failureType: EmailFailureType,
    val reason: String? = null
)

