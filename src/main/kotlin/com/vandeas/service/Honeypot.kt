package com.vandeas.service

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig

interface Honeypot {

    /** Issues a session: random field names bound to a signed, single-use token. */
    fun issue(configId: String, config: HoneypotConfig): HoneypotSession

    /**
     * Verifies a submission against the token it carries.
     *
     * @param expectedConfigId the config id the submission claims; must match the token's.
     * @param submitted the honeypot field names and values the client sent back.
     */
    suspend fun validate(
        config: HoneypotConfig,
        expectedConfigId: String,
        token: String?,
        submitted: Map<String, String>,
    ): HoneypotResult
}

sealed interface HoneypotResult {
    /** Token valid, nonce consumed, all trap fields blank. */
    data object Pass : HoneypotResult

    /** Structurally bad, expired or replayed token — surfaced to the caller as 403. */
    data class InvalidToken(val reason: String) : HoneypotResult

    /**
     * A trap field was filled or the form came back too fast — answered with a fake success
     * indistinguishable from [Pass] at the HTTP layer.
     *
     * @property reason which check tripped (dwell vs. field), for the server log only. It
     * must never reach the client, or the indistinguishability the design relies on breaks.
     */
    data class Trapped(val reason: String) : HoneypotResult
}
