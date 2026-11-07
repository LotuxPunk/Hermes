package com.vandeas.service.impl.mailer

import com.vandeas.entities.Mail
import javax.mail.internet.InternetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `From` each mailer builds from a config sender and a request's sender name, checked on
 * the message or payload just before it would be sent.
 */
class SenderNameMailerTest {

    private companion object {
        const val SENDER = "Courtly <ne-pas-repondre@courtly.be>"
        const val ADDRESS = "ne-pas-repondre@courtly.be"

        /** Names that need encoding or quoting, each of which must reach the recipient as sent. */
        val TRICKY_NAMES = listOf(
            "Château d’Ixelles via Courtly",
            "Tennis Club \"Les Pins\" via Courtly",
            "Uccle, Forest via Courtly",
            "Château d’Ixelles, \"Les Pins\" (Uccle) via Courtly",
            "Évènement à l’Œuvre ".repeat(5).trim(),
        )
    }

    private val smtp = SMTPMailer(username = "user", password = "secret", host = "localhost")

    private fun mail(from: String = SENDER, senderName: String? = null) = Mail(
        from = from,
        to = "player@example.com",
        subject = "Subject",
        content = "<p>Content</p>",
        senderName = senderName,
    )

    private fun smtpFromHeader(mail: Mail): String = smtp.createMessage(mail).getHeader("From", ",")

    // SMTP

    @Test
    fun `smtp without a sender name keeps the config sender`() {
        assertEquals("Courtly <ne-pas-repondre@courtly.be>", smtpFromHeader(mail()))
        assertEquals("ne-pas-repondre@courtly.be", smtpFromHeader(mail(from = ADDRESS)))
        // The header is whatever javax.mail wrote for the config sender before sender names existed.
        val quoted = "\"Courtly, Belgium\" <ne-pas-repondre@courtly.be>"
        assertEquals(InternetAddress(quoted).toString(), smtpFromHeader(mail(from = quoted)))
    }

    @Test
    fun `smtp uses the sender name with the config address`() {
        assertEquals(
            "Spy Club via Courtly <ne-pas-repondre@courtly.be>",
            smtpFromHeader(mail(senderName = "Spy Club via Courtly")),
        )
    }

    @Test
    fun `smtp gives a bare config sender the sender name`() {
        assertEquals(
            "Spy Club via Courtly <ne-pas-repondre@courtly.be>",
            smtpFromHeader(mail(from = ADDRESS, senderName = "Spy Club via Courtly")),
        )
    }

    @Test
    fun `smtp encodes the name so it parses back exactly as sent`() {
        TRICKY_NAMES.forEach { name ->
            val header = smtpFromHeader(mail(senderName = name))

            assertTrue(header.all { it.code < 128 }, "Non-ASCII in header for '$name': $header")
            val parsed = InternetAddress.parseHeader(header, true).single() as InternetAddress
            assertEquals(name, parsed.personal, "Header was: $header")
            assertEquals(ADDRESS, parsed.address)
        }
    }

    @Test
    fun `smtp writes non-ASCII as an RFC 2047 encoded word`() {
        val header = smtpFromHeader(mail(senderName = "Château d’Ixelles via Courtly"))

        assertTrue(header.startsWith("=?UTF-8?"), header)
        assertTrue(header.endsWith("<ne-pas-repondre@courtly.be>"), header)
    }

    @Test
    fun `smtp never takes the address from the sender name`() {
        val parsed = InternetAddress.parseHeader(
            smtpFromHeader(mail(senderName = "Evil <evil@attacker.test>")),
            true,
        ).single() as InternetAddress

        assertEquals(ADDRESS, parsed.address)
        assertEquals("Evil <evil@attacker.test>", parsed.personal)
    }

    // Resend

    private fun resendFrom(mail: Mail): String = ResendMailer.createEmailOptions(mail).from

    @Test
    fun `resend without a sender name sends the config sender byte for byte`() {
        listOf(SENDER, ADDRESS, "\"Courtly\"   <ne-pas-repondre@courtly.be>").forEach { sender ->
            assertEquals(sender, resendFrom(mail(from = sender)))
        }
    }

    @Test
    fun `resend uses the sender name with the config address`() {
        assertEquals(
            "Spy Club via Courtly <ne-pas-repondre@courtly.be>",
            resendFrom(mail(senderName = "Spy Club via Courtly")),
        )
        assertEquals(
            "Spy Club via Courtly <ne-pas-repondre@courtly.be>",
            resendFrom(mail(from = ADDRESS, senderName = "Spy Club via Courtly")),
        )
    }

    @Test
    fun `resend quotes and escapes the name instead of encoding it`() {
        assertEquals(
            "\"Château d’Ixelles via Courtly\" <ne-pas-repondre@courtly.be>",
            resendFrom(mail(senderName = "Château d’Ixelles via Courtly")),
        )
        assertEquals(
            "\"Tennis Club \\\"Les Pins\\\" via Courtly\" <ne-pas-repondre@courtly.be>",
            resendFrom(mail(senderName = "Tennis Club \"Les Pins\" via Courtly")),
        )
        assertEquals(
            "\"Uccle, Forest via Courtly\" <ne-pas-repondre@courtly.be>",
            resendFrom(mail(senderName = "Uccle, Forest via Courtly")),
        )
    }

    @Test
    fun `resend from parses back exactly as sent`() {
        TRICKY_NAMES.forEach { name ->
            val from = resendFrom(mail(senderName = name))

            val parsed = InternetAddress(from)
            assertEquals(name, parsed.personal, "from was: $from")
            assertEquals(ADDRESS, parsed.address)
        }
    }

    @Test
    fun `resend never takes the address from the sender name`() {
        val parsed = InternetAddress(resendFrom(mail(senderName = "Evil <evil@attacker.test>")))

        assertEquals(ADDRESS, parsed.address)
        assertEquals("Evil <evil@attacker.test>", parsed.personal)
    }
}
