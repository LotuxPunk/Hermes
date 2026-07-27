package com.vandeas.exception

/**
 * Raised when a contact form config's honeypot settings cannot be used to issue a session —
 * e.g. a `secretKey` the HMAC provider rejects, or a `fieldCount`/`maxAge` outside the bounds
 * `HmacHoneypot` enforces.
 *
 * This is a server-side misconfiguration, not a client error: unlike a config with no
 * `honeypot` block at all (a genuine 400), this is surfaced as 500, and the underlying
 * — possibly JCE-internal — message is logged rather than echoed to the client.
 */
class HoneypotMisconfiguredException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
