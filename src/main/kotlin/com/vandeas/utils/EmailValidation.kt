package com.vandeas.utils

import javax.mail.internet.AddressException
import javax.mail.internet.InternetAddress

/**
 * Checks whether this string is a single, parseable email address.
 *
 * Parsing is delegated to javax.mail so the service does not carry its own address
 * grammar. A comma-separated list fails, and so does an RFC 822 group such as
 * `team:a@x.com,b@y.com;`, which javax.mail validates member by member and the SMTP
 * transport expands into several recipients. A caller therefore cannot smuggle several
 * addresses through a single-address field. Note that javax.mail follows RFC 822 and
 * accepts a bare hostname with no dot, such as `user@localhost`.
 *
 * @return true when the address is usable as a mail header value.
 */
fun String.isValidEmailAddress(): Boolean {
    return isNotBlank() && try {
        val address = InternetAddress(this)
        address.validate()
        !address.isGroup
    } catch (e: AddressException) {
        false
    }
}
