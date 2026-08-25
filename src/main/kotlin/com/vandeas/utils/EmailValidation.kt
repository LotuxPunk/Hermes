package com.vandeas.utils

import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

/**
 * Checks whether this string is a single, parseable email address.
 *
 * Parsing is delegated to javax.mail so the service does not carry its own address
 * grammar. A comma-separated list fails, so a caller cannot smuggle several addresses
 * through a single-address field. Note that javax.mail follows RFC 822 and accepts a
 * bare hostname with no dot, such as `user@localhost`.
 *
 * @return true when the address is usable as a mail header value.
 */
fun String.isValidEmailAddress(): Boolean {
    return isNotBlank() && try {
        InternetAddress(this).validate()
        true
    } catch (e: AddressException) {
        false
    }
}
