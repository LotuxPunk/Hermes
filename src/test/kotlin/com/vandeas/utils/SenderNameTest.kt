package com.vandeas.utils

import javax.mail.internet.InternetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SenderNameTest {

    @Test
    fun `club names are accepted`() {
        listOf(
            "Spy Club via Courtly",
            "Château d’Ixelles via Courtly",
            "Tennis Club \"Les Pins\" via Courtly",
            "Uccle, Forest via Courtly",
            "Evil <evil@attacker.test>",
        ).forEach { name -> assertNull(senderNameRefusal(name), "For '$name'") }
    }

    @Test
    fun `an empty name is refused`() {
        assertEquals("empty", senderNameRefusal(""))
    }

    @Test
    fun `the length limit counts code points, not UTF-16 units`() {
        assertNull(senderNameRefusal("a".repeat(100)))
        assertEquals("longer than 100 characters", senderNameRefusal("a".repeat(101)))

        // 100 emoji are 200 UTF-16 units but 100 code points.
        val emoji = "🎾"
        assertNull(senderNameRefusal(emoji.repeat(100)))
        assertEquals("longer than 100 characters", senderNameRefusal(emoji.repeat(101)))
    }

    @Test
    fun `control characters are refused`() {
        listOf("\r", "\n", "\r\nBcc: victim@x.test", "\t", "\u0000", "\u0007", "\u007F", "\u0085", "\u009F")
            .forEach { control ->
                assertEquals("contains a control character", senderNameRefusal("Spy${control}Club"), "For U+%04X".format(control[0].code))
            }
    }

    @Test
    fun `bidi controls are refused`() {
        ((0x202A..0x202E) + (0x2066..0x2069)).forEach { codePoint ->
            val name = "Spy Club ${Character.toString(codePoint)}via Courtly"
            assertEquals("contains a bidi control", senderNameRefusal(name), "For U+%04X".format(codePoint))
        }
    }

    @Test
    fun `the refusal reason never quotes the name`() {
        val name = "Jane Doe\u0007"
        val reason = senderNameRefusal(name)!!
        assertEquals(false, reason.contains("Jane"))
    }

    @Test
    fun `without a name the config sender is parsed as it is`() {
        val sender = "Courtly <ne-pas-repondre@courtly.be>"
        assertEquals(InternetAddress(sender), senderAddress(sender, null))
        assertEquals("Courtly", senderAddress(sender, null).personal)
    }

    @Test
    fun `a name replaces the config name and keeps its address`() {
        val address = senderAddress("Courtly <ne-pas-repondre@courtly.be>", "Spy Club via Courtly")

        assertEquals("ne-pas-repondre@courtly.be", address.address)
        assertEquals("Spy Club via Courtly", address.personal)
    }

    @Test
    fun `a bare config sender takes the name`() {
        val address = senderAddress("ne-pas-repondre@courtly.be", "Spy Club via Courtly")

        assertEquals("ne-pas-repondre@courtly.be", address.address)
        assertEquals("Spy Club via Courtly", address.personal)
    }

    @Test
    fun `a name that looks like an address does not change the address`() {
        val address = senderAddress("Courtly <ne-pas-repondre@courtly.be>", "Evil <evil@attacker.test>")

        assertEquals("ne-pas-repondre@courtly.be", address.address)
        assertEquals("Evil <evil@attacker.test>", address.personal)
    }
}
