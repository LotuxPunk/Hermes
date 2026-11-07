package com.vandeas.utils

import javax.mail.internet.InternetAddress

/** Longest sender name a request may set, in code points. */
const val SENDER_NAME_MAX_LENGTH = 100

/**
 * Says why [name] cannot be the display name of a sender, or returns null when it can.
 *
 * [name] must already be trimmed. It is refused when it is empty, longer than
 * [SENDER_NAME_MAX_LENGTH] code points, or holds a control character (CR and LF included,
 * so it cannot inject a header) or a bidi control, which could make the rest of the name
 * read differently on screen.
 *
 * The reason never quotes the name, so it can go into a log line: in some clients the
 * name is a person's.
 */
fun senderNameRefusal(name: String): String? = when {
    name.isEmpty() -> "empty"
    name.codePointCount(0, name.length) > SENDER_NAME_MAX_LENGTH ->
        "longer than $SENDER_NAME_MAX_LENGTH characters"
    name.codePoints().anyMatch { Character.getType(it) == Character.CONTROL.toInt() } ->
        "contains a control character"
    name.codePoints().anyMatch { it in 0x202A..0x202E || it in 0x2066..0x2069 } ->
        "contains a bidi control"
    else -> null
}

/**
 * Builds the `From` address of a mail whose config sender is [sender].
 *
 * Without a [senderName], this is [sender] parsed as it is, so the header is exactly what it
 * was before requests could set a name. With one, only the display name changes. The
 * address always comes from [sender], so a request cannot send from another address.
 *
 * The name goes into the address object, never into a string, so javax.mail writes it as an
 * RFC 2047 encoded word when it holds non-ASCII characters, and quotes it when it holds
 * specials such as `,` or `"`.
 */
fun senderAddress(sender: String, senderName: String?): InternetAddress =
    if (senderName == null) {
        InternetAddress(sender)
    } else {
        InternetAddress(InternetAddress(sender).address, senderName, Charsets.UTF_8.name())
    }
