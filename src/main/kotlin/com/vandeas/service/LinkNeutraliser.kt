package com.vandeas.service

/**
 * Makes links and email addresses in free text non-clickable while keeping them readable.
 *
 * It uses the usual defanging convention:
 * - `http`/`https` schemes become `hxxp`/`hxxps`;
 * - dots inside host names (URLs, `www.` hosts, bare domains, IPv4 addresses) become `[.]`;
 * - `@` in email addresses (and in a URL's user info) becomes `[at]`.
 *
 * The Visitor confirmation goes to an address nobody has verified, so anything a visitor
 * typed is passed through here before it reaches the confirmation templates. Mail clients
 * then have nothing to turn into a link.
 *
 * Any dot that joins two labels is treated as a host-name dot when the label after it starts
 * with a letter and is at least two characters long, the shape of every real TLD. The rule
 * applies everywhere in the text, URL paths included: skipping a URL's path would let a
 * second URL hide inside it (`http://a/,https://evil.example`). Detection therefore errs on
 * the side of defanging: `Hello.World` comes out as `Hello[.]World`, `page.html` in a path
 * as `page[.]html`, and the local part of `contact.paris@example.com` as `contact[.]paris`,
 * which could otherwise pass for a domain. Numbers (`3.14`, `v1.2.3`) and abbreviations
 * (`e.g.`) stay as they are. Combining marks and invisible format characters (such as a
 * soft hyphen) count as part of a label, so they cannot shield a dot.
 *
 * Every rule looks at a short token and its immediate neighbours, so the scan is linear and
 * cannot overflow the stack on hostile input. [neutralise] is also idempotent: `hxxp`, `[.]`
 * and `[at]` never match again.
 */
object LinkNeutraliser {

    private const val DOTS = """.。．｡""" // ASCII dot, plus the variants IDN treats as dots
    private const val LABEL_CHARS = """\p{L}\p{N}\p{M}\p{Cf}-""" // letters, digits, marks, format characters, hyphen

    private val PATTERN = Regex(
        listOf(
            // The scheme of a URL.
            """(?<scheme>(?i:https?)(?=://))""",
            // An IPv4 address, which the dot rule below skips because its labels are digits.
            """(?<ip>(?<![\p{N}$DOTS])\d{1,3}(?:[$DOTS]\d{1,3}){3}(?!\p{N}|[$DOTS]\p{N}))""",
            // A dot between two labels, the second one shaped like a TLD.
            """(?<dot>(?<=[$LABEL_CHARS])[$DOTS](?=\p{Cf}*\p{L}[$LABEL_CHARS]))""",
            // The @ of an email address or of a URL's user info.
            """(?<at>(?<=[$LABEL_CHARS._%+])@(?=\p{Cf}*[\p{L}\p{N}]))""",
        ).joinToString("|")
    )

    private val IP_DOT = Regex("[$DOTS]")

    /**
     * @return [text] with every URL, domain, IPv4 address and email address defanged, and
     * everything else, accented characters included, left untouched.
     */
    fun neutralise(text: String): String = PATTERN.replace(text) { match ->
        val groups = match.groups
        when {
            groups["scheme"] != null -> if (groups["scheme"]!!.value.length == 5) "hxxps" else "hxxp"
            groups["ip"] != null -> IP_DOT.replace(groups["ip"]!!.value, "[.]")
            groups["dot"] != null -> "[.]"
            else -> "[at]"
        }
    }
}
