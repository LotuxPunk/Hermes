package com.vandeas.logic

import com.vandeas.dto.HoneypotSession

interface HoneypotLogic {
    /**
     * @throws NoSuchElementException if no contact form config has this id.
     * @throws IllegalArgumentException if the config has no honeypot block.
     * @throws com.vandeas.exception.HoneypotMisconfiguredException if the config has a
     *   honeypot block but its settings (secretKey, fieldCount, maxAge) cannot be used to
     *   issue a session. A server-side misconfiguration, surfaced as 500 — not 400.
     */
    fun getSession(configId: String): HoneypotSession
}
