package com.vandeas.logic.impl

import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.ResendMailConfig
import com.vandeas.dto.configs.SMTPMailConfig
import com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter
import com.vandeas.service.impl.InMemoryDailyLimiter
import com.vandeas.support.CapturedLogs
import com.vandeas.support.FakeConfigDirectory
import com.vandeas.support.RecordingMailer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The per-request sender name, driven through [MailLogicImpl] with a fake mailer. */
class SenderNameTest {

    private companion object {
        const val SENDER = "Courtly <ne-pas-repondre@courtly.be>"
        const val MAIL_LOGIC_LOGGER = "com.vandeas.logic.impl.MailLogicImpl"

        /** One name per refusal reason. */
        val REFUSED_NAMES = listOf(
            "   ",
            "a".repeat(101),
            "Spy Club\r\nBcc: victim@example.com",
            "Spy Club\u0000",
            "Spy Club ‮ruoc via",
        )
    }

    private val smtpConfig = SMTPMailConfig(
        id = "smtp-cfg",
        sender = SENDER,
        subjectTemplate = "Your booking",
        username = "user",
        password = "secret",
        smtpHost = "localhost",
    )

    private val resendConfig = ResendMailConfig(
        id = "resend-cfg",
        sender = SENDER,
        subjectTemplate = "Your booking",
        apiKey = "re_test",
    )

    private val mailer = RecordingMailer()

    private val logic = MailLogicImpl(
        mailConfigHandler = FakeConfigDirectory(
            listOf<MailConfig>(smtpConfig, resendConfig),
            mutableMapOf(smtpConfig.id to "<p>Booked</p>", resendConfig.id to "<p>Booked</p>"),
        ),
        contactFormConfigHandler = FakeConfigDirectory<ContactFormConfig>(),
        limiter = InMemoryDailyLimiter(),
        confirmationLimiter = InMemoryConfirmationRecipientLimiter(3),
    ) { mailer }

    private fun input(email: String, senderName: String?, id: String = smtpConfig.id) =
        MailInput(id = id, email = email, senderName = senderName, attributes = emptyMap())

    @Test
    fun `a mail without a sender name keeps the config sender`() = runBlocking {
        listOf(smtpConfig, resendConfig).forEach { config ->
            logic.sendMail(input("player@example.com", null, config.id), emptyList())
        }

        assertEquals(2, mailer.mails.size)
        mailer.mails.forEach { mail ->
            assertEquals(SENDER, mail.from)
            assertNull(mail.senderName)
        }
    }

    @Test
    fun `a mail carries its sender name and the config sender`() = runBlocking {
        logic.sendMail(input("player@example.com", "Spy Club via Courtly"), emptyList())

        val mail = mailer.mails.single()
        assertEquals(SENDER, mail.from)
        assertEquals("Spy Club via Courtly", mail.senderName)
    }

    @Test
    fun `the sender name is trimmed`() = runBlocking {
        logic.sendMail(input("player@example.com", "  Spy Club via Courtly \t"), emptyList())

        assertEquals("Spy Club via Courtly", mailer.mails.single().senderName)
    }

    @Test
    fun `each batch entry has its own sender name`() = runBlocking {
        logic.sendMails(
            listOf(
                input("a@example.com", "Spy Club via Courtly"),
                input("b@example.com", " TC Uccle via Courtly "),
                input("c@example.com", null),
                input("d@example.com", "Château d’Ixelles via Courtly", id = resendConfig.id),
            )
        )

        assertEquals(
            mapOf(
                "a@example.com" to "Spy Club via Courtly",
                "b@example.com" to "TC Uccle via Courtly",
                "c@example.com" to null,
                "d@example.com" to "Château d’Ixelles via Courtly",
            ),
            mailer.mails.associate { it.to to it.senderName },
        )
        assertTrue(mailer.mails.all { it.from == SENDER })
    }

    @Test
    fun `a broadcast gives every recipient its sender name`() = runBlocking {
        logic.broadcastMail(
            smtpConfig.id,
            BroadcastMailRequest(
                to = listOf("a@example.com", "b@example.com", "c@example.com"),
                senderName = "Spy Club via Courtly",
                attributes = emptyMap(),
            ),
            emptyList(),
        )

        assertEquals(3, mailer.mails.size)
        assertTrue(mailer.mails.all { it.senderName == "Spy Club via Courtly" && it.from == SENDER })
    }

    @Test
    fun `a broadcast without a sender name keeps the config sender`() = runBlocking {
        logic.broadcastMail(
            smtpConfig.id,
            BroadcastMailRequest(to = listOf("a@example.com"), attributes = emptyMap()),
            emptyList(),
        )

        assertNull(mailer.mails.single().senderName)
    }

    @Test
    fun `a refused sender name sends nothing`() = runBlocking<Unit> {
        REFUSED_NAMES.forEach { name ->
            val error = assertFailsWith<IllegalArgumentException> {
                logic.sendMail(input("player@example.com", name), emptyList())
            }
            assertEquals("Invalid senderName", error.message)
        }

        assertTrue(mailer.mails.isEmpty())
    }

    @Test
    fun `one refused sender name rejects the whole batch`() = runBlocking<Unit> {
        REFUSED_NAMES.forEach { name ->
            val error = assertFailsWith<IllegalArgumentException> {
                logic.sendMails(
                    listOf(
                        input("a@example.com", "Spy Club via Courtly"),
                        input("b@example.com", name),
                        input("c@example.com", null),
                    )
                )
            }
            assertEquals("Invalid senderName", error.message)
        }

        assertTrue(mailer.mails.isEmpty())
    }

    @Test
    fun `a refused broadcast sender name sends nothing`() = runBlocking<Unit> {
        REFUSED_NAMES.forEach { name ->
            val error = assertFailsWith<IllegalArgumentException> {
                logic.broadcastMail(
                    smtpConfig.id,
                    BroadcastMailRequest(to = listOf("a@example.com"), senderName = name, attributes = emptyMap()),
                    emptyList(),
                )
            }
            assertEquals("Invalid senderName", error.message)
        }

        assertTrue(mailer.mails.isEmpty())
    }

    @Test
    fun `the refusal log names the config and the reason, not the name`() = runBlocking<Unit> {
        val name = "Jane Private\u0007"

        val messages = CapturedLogs(MAIL_LOGIC_LOGGER).use { logs ->
            assertFailsWith<IllegalArgumentException> {
                logic.sendMail(input("player@example.com", name), emptyList())
            }
            assertFailsWith<IllegalArgumentException> {
                logic.sendMails(listOf(input("a@example.com", name, id = resendConfig.id)))
            }
            assertFailsWith<IllegalArgumentException> {
                logic.broadcastMail(
                    smtpConfig.id,
                    BroadcastMailRequest(to = listOf("a@example.com"), senderName = "a".repeat(101), attributes = emptyMap()),
                    emptyList(),
                )
            }
            logs.messages
        }

        val refusals = messages.filter { "senderName" in it }
        assertEquals(3, refusals.size, "Log lines: $messages")
        assertTrue(refusals[0].contains(smtpConfig.id) && refusals[0].contains("contains a control character"), refusals[0])
        assertTrue(refusals[1].contains(resendConfig.id) && refusals[1].contains("contains a control character"), refusals[1])
        assertTrue(refusals[2].contains(smtpConfig.id) && refusals[2].contains("longer than 100 characters"), refusals[2])
        refusals.forEach { line ->
            assertFalse(line.contains("Jane") || line.contains("Private") || line.contains("aaaa"), line)
        }
    }
}
