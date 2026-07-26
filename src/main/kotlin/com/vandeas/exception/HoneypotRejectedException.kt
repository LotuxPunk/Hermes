package com.vandeas.exception

/**
 * Raised when a contact form submission carries a structurally bad, expired or replayed
 * honeypot token. Surfaced as 403 — unlike a tripped trap, which is answered with a
 * response indistinguishable from success.
 */
class HoneypotRejectedException(reason: String) : RuntimeException(reason)
