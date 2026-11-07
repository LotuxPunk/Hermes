package com.vandeas.service

import kotlin.test.Test
import kotlin.test.assertEquals

class LinkNeutraliserTest {

    private fun assertNeutralised(expected: String, input: String) {
        assertEquals(expected, LinkNeutraliser.neutralise(input))
    }

    @Test
    fun `defangs an https URL`() {
        assertNeutralised("hxxps://example[.]com", "https://example.com")
    }

    @Test
    fun `defangs an http URL`() {
        assertNeutralised("hxxp://example[.]com", "http://example.com")
    }

    @Test
    fun `defangs an upper-case scheme`() {
        assertNeutralised("hxxps://EXAMPLE[.]COM/Path", "HTTPS://EXAMPLE.COM/Path")
    }

    @Test
    fun `defangs a URL with path, query and fragment and keeps it readable`() {
        assertNeutralised(
            "hxxps://shop[.]example[.]com/path/page[.]html?q=1&next=a.b#top",
            "https://shop.example.com/path/page.html?q=1&next=a.b#top",
        )
    }

    @Test
    fun `defangs a link hidden in the path or query of another URL`() {
        assertNeutralised("hxxp://a/,hxxps://evil[.]com", "http://a/,https://evil.com")
        assertNeutralised("hxxp://a/?u=hxxps://evil[.]com", "http://a/?u=https://evil.com")
        assertNeutralised("hxxp://a/;www[.]evil[.]com", "http://a/;www.evil.com")
        assertNeutralised("hxxp://a/,victim[at]evil[.]com", "http://a/,victim@evil.com")
        assertNeutralised("hxxp://a,hxxps://evil[.]com", "http://a,https://evil.com")
    }

    @Test
    fun `defangs a link after a non-breaking space`() {
        assertNeutralised("hxxp://a/\u00A0hxxps://evil[.]com", "http://a/\u00A0https://evil.com")
    }

    @Test
    fun `a combining mark or a format character does not shield a dot`() {
        assertNeutralised("cafe\u0301[.]com", "cafe\u0301.com")
        assertNeutralised("evil\u00AD[.]com", "evil\u00AD.com")
        assertNeutralised("evil[.]\u00ADcom", "evil.\u00ADcom")
        assertNeutralised("evil\u200D[at]x[.]com", "evil\u200D@x.com")
    }

    @Test
    fun `defangs a URL with a port`() {
        assertNeutralised("hxxp://example[.]com:8080/admin", "http://example.com:8080/admin")
    }

    @Test
    fun `defangs the user info of a URL`() {
        assertNeutralised("hxxps://paypal[.]com[at]evil[.]example/", "https://paypal.com@evil.example/")
    }

    @Test
    fun `defangs a www host`() {
        assertNeutralised("see www[.]example[.]com/offer for details", "see www.example.com/offer for details")
    }

    @Test
    fun `defangs a bare domain`() {
        assertNeutralised("go to scam[.]example now", "go to scam.example now")
    }

    @Test
    fun `defangs a domain with a multi-part TLD`() {
        assertNeutralised("bank[.]co[.]uk", "bank.co.uk")
    }

    @Test
    fun `defangs an internationalised domain`() {
        assertNeutralised("пример[.]рф and café[.]fr", "пример.рф and café.fr")
    }

    @Test
    fun `defangs an IPv4 address`() {
        assertNeutralised("server 192[.]168[.]0[.]1 is down", "server 192.168.0.1 is down")
    }

    @Test
    fun `defangs a URL on an IPv4 address`() {
        assertNeutralised("hxxp://203[.]0[.]113[.]7/login", "http://203.0.113.7/login")
    }

    @Test
    fun `defangs an email address`() {
        assertNeutralised("write to jane+news[at]example[.]com", "write to jane+news@example.com")
    }

    @Test
    fun `defangs a dotted local part, which could pass for a domain`() {
        assertNeutralised("contact[.]paris[at]example[.]com", "contact.paris@example.com")
    }

    @Test
    fun `defangs ideographic and full-width dots, which IDN treats as dots`() {
        assertNeutralised("evil[.]example and evil[.]example", "evil\u3002example and evil\uFF0Eexample")
    }

    @Test
    fun `leaves a lone at sign alone`() {
        assertNeutralised("ping @team or meet @ 5pm", "ping @team or meet @ 5pm")
    }

    @Test
    fun `defangs an email address on a dotless host`() {
        assertNeutralised("root[at]localhost", "root@localhost")
    }

    @Test
    fun `keeps sentence punctuation around a link`() {
        assertNeutralised("Visit example[.]com. Or (hxxps://example[.]org)!", "Visit example.com. Or (https://example.org)!")
    }

    @Test
    fun `defangs every link in a longer text`() {
        assertNeutralised(
            "Hi, see hxxps://a[.]example/x and b[.]example, or mail me at me[at]c[.]example.",
            "Hi, see https://a.example/x and b.example, or mail me at me@c.example.",
        )
    }

    @Test
    fun `leaves text without links unchanged`() {
        listOf(
            "Bonjour, je voudrais réserver deux places pour le concert du 12 avril. Merci !",
            "Pi is 3.14, the version is v1.2.3 and it costs 12.50 EUR at 10.30h.",
            "e.g. this, i.e. that, etc.",
            "Line one\nLine two\n\n- bullet",
            "",
        ).forEach { text -> assertNeutralised(text, text) }
    }

    @Test
    fun `preserves accented characters`() {
        assertNeutralised("Répétition — vendredi 10 avril 2026, café crème", "Répétition — vendredi 10 avril 2026, café crème")
        assertNeutralised("Voir hxxps://exemple[.]fr/été et à bientôt, Hélène", "Voir https://exemple.fr/été et à bientôt, Hélène")
    }

    @Test
    fun `is idempotent`() {
        listOf(
            "https://example.com",
            "HTTP://EXAMPLE.COM/a.b/c.d?x=y.z#w.v",
            "https://user@evil.example/path",
            "www.example.com/offer/file.pdf",
            "scam.example",
            "192.168.0.1",
            "john.doe@example.com",
            "Visit example.com. Or (https://example.org)!",
            "Bonjour, café crème à 12.50 EUR",
            "a.a.a.a.a.a",
        ).forEach { text ->
            val once = LinkNeutraliser.neutralise(text)
            assertEquals(once, LinkNeutraliser.neutralise(once), "Not idempotent on '$text'")
        }
    }

    @Test
    fun `handles a long dotted input in linear time`() {
        val text = "a.".repeat(200_000) + "example"
        val start = System.nanoTime()

        LinkNeutraliser.neutralise(text)

        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        kotlin.test.assertTrue(elapsedMs < 2_000, "Took ${elapsedMs}ms")
    }
}
