package com.vandeas.utils

import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

/**
 * Returns the bare address of an email address, without display name or angle brackets:
 * `John <john@example.com>` gives `john@example.com`.
 *
 * Contact form addresses are validated with javax.mail, which accepts the display name form,
 * so code that keys or logs on an address unwraps it first.
 *
 * @return the bare address, or the trimmed input when javax.mail cannot parse it.
 */
fun String.bareEmailAddress(): String {
    return try {
        InternetAddress(this).address ?: trim()
    } catch (_: AddressException) {
        trim()
    }
}

/**
 * Masks an email address for log lines: `john@example.com` gives `j***@example.com`.
 *
 * Only the first character of the local part and the domain are kept, which is enough to
 * tell addresses apart while diagnosing, without writing visitor addresses in clear.
 *
 * @return the masked address, or `***` when this is not an address.
 */
fun String.maskEmailAddress(): String {
    val address = bareEmailAddress()
    val at = address.lastIndexOf('@')
    if (at <= 0) {
        return "***"
    }
    return "${address.first()}***${address.substring(at)}"
}
