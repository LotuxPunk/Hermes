package com.vandeas.logic.impl

import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.ResendContactFormConfig
import com.vandeas.dto.configs.SMTPContactFormConfig
import com.vandeas.dto.configs.VisitorConfirmationConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.dto.toResponse
import com.vandeas.entities.ContactFormResult
import com.vandeas.entities.Mail
import com.vandeas.entities.MailSendStatus
import com.vandeas.entities.SendOperationResult
import com.vandeas.entities.VisitorConfirmationStatus
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.service.ConfirmationRecipientLimiter
import com.vandeas.service.DailyLimiter
import com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter
import com.vandeas.service.impl.InMemoryDailyLimiter
import com.vandeas.support.CapturedLogs
import com.vandeas.support.FakeConfigDirectory
import com.vandeas.support.RecordingMailer
import com.vandeas.support.solvedKerberusForm
import io.ktor.serialization.kotlinx.json.DefaultJson
import kotlinx.coroutines.runBlocking
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Visitor confirmation, driven through the contact form flow with a fake mailer, a fake
 * config directory and real Kerberus captchas.
 */
class VisitorConfirmationTest {

    private companion object {
        const val ORG_INBOX = "org@acme.test"
        const val SENDER = "no-reply@acme.test"
        const val VISITOR = "jane@visitor.test"
        const val MASKED_VISITOR = "j***@visitor.test"
        const val ORG_TEMPLATE = "<p>{{form.fullName}} wrote: {{form.content}}</p>"
        const val CONFIRMATION_TEMPLATE = "<p>Thank you {{form.fullName}}, here is your copy: {{form.content}}</p>"
        val CONFIRMATION = VisitorConfirmationConfig(subjectTemplate = "We received your message, {{form.fullName}}")
        const val MAIL_LOGIC_LOGGER = "com.vandeas.logic.impl.MailLogicImpl"
        const val LIMITER_LOGGER = "com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter"
    }

    private fun smtpConfig(
        confirmation: VisitorConfirmationConfig? = CONFIRMATION,
        dailyLimit: Int = 10,
        id: String = "contact-${UUID.randomUUID()}",
    ) = SMTPContactFormConfig(
        id = id,
        dailyLimit = dailyLimit,
        destination = ORG_INBOX,
        sender = SENDER,
        lang = "en",
        subjectTemplate = "New message from {{form.fullName}}",
        username = "user",
        password = "password",
        smtpHost = "localhost",
        smtpPort = 1025,
        captcha = KerberusConfig(secretKey = "secret-$id"),
        confirmation = confirmation,
    )

    private fun resendConfig(confirmation: VisitorConfirmationConfig? = CONFIRMATION) =
        "contact-${UUID.randomUUID()}".let { id ->
            ResendContactFormConfig(
                id = id,
                dailyLimit = 10,
                destination = ORG_INBOX,
                sender = SENDER,
                lang = "en",
                subjectTemplate = "New message from {{form.fullName}}",
                apiKey = "re_test",
                captcha = KerberusConfig(secretKey = "secret-$id"),
                confirmation = confirmation,
            )
        }

    private fun directory(
        vararg configs: ContactFormConfig,
        withConfirmationTemplate: Boolean = true,
        confirmationTemplate: String = CONFIRMATION_TEMPLATE,
    ) = FakeConfigDirectory<ContactFormConfig>(configs.toList()).apply {
        configs.forEach { config ->
            templates[config.id] = ORG_TEMPLATE
            if (withConfirmationTemplate) {
                templates["${config.id}.confirmation"] = confirmationTemplate
            }
        }
    }

    private fun logic(
        configs: FakeConfigDirectory<ContactFormConfig>,
        mailer: RecordingMailer,
        recipientLimiter: ConfirmationRecipientLimiter = InMemoryConfirmationRecipientLimiter(limit = 3),
        dailyLimiter: DailyLimiter = InMemoryDailyLimiter(),
    ) = MailLogicImpl(FakeConfigDirectory<MailConfig>(), configs, dailyLimiter, recipientLimiter) { mailer }

    private suspend fun MailLogicImpl.submit(
        config: ContactFormConfig,
        email: String = VISITOR,
        fullName: String = "Jane Visitor",
        content: String = "Hello, I would like to know more.",
        topic: String? = null,
    ): ContactFormResult = sendContactForm(solvedKerberusForm(config, email, fullName, content, topic))

    // --- Sending (#15) ---

    @Test
    fun `an SMTP config with a confirmation block sends the org notification then the visitor confirmation`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer()

        val result = logic(directory(config), mailer).submit(config)

        assertEquals(
            listOf(
                Mail(
                    from = SENDER,
                    to = ORG_INBOX,
                    subject = "New message from Jane Visitor",
                    content = "<p>Jane Visitor wrote: Hello, I would like to know more.</p>",
                ),
                Mail(
                    from = SENDER,
                    to = VISITOR,
                    subject = "We received your message, Jane Visitor",
                    content = "<p>Thank you Jane Visitor, here is your copy: Hello, I would like to know more.</p>",
                    replyTo = ORG_INBOX,
                ),
            ),
            mailer.mails.toList(),
        )
        assertEquals(VisitorConfirmationStatus.SENT, result.confirmation)
        assertEquals(listOf(ORG_INBOX), result.orgNotification.sent)
    }

    @Test
    fun `a Resend config with a confirmation block sends a visitor confirmation too`() = runBlocking {
        val config = resendConfig()
        val mailer = RecordingMailer()

        val result = logic(directory(config), mailer).submit(config)

        assertEquals(listOf(ORG_INBOX, VISITOR), mailer.mails.map { it.to })
        assertEquals(ORG_INBOX, mailer.mailsTo(VISITOR).single().replyTo)
        assertEquals(VisitorConfirmationStatus.SENT, result.confirmation)
    }

    @Test
    fun `the org notification is the same with or without a confirmation block`() = runBlocking {
        val withBlock = smtpConfig()
        val withoutBlock = smtpConfig(confirmation = null)
        val mailer = RecordingMailer()
        val logic = logic(directory(withBlock, withoutBlock), mailer)

        logic.submit(withBlock)
        val orgNotificationWithBlock = mailer.mails.first()
        mailer.mails.clear()
        logic.submit(withoutBlock)

        assertEquals(listOf(orgNotificationWithBlock), mailer.mails.toList())
    }

    @Test
    fun `without a confirmation block a submission sends one mail and reports no confirmation`() = runBlocking {
        val config = smtpConfig(confirmation = null)
        val mailer = RecordingMailer()

        val result = logic(directory(config), mailer).submit(config)

        assertEquals(listOf(ORG_INBOX), mailer.mails.map { it.to })
        assertNull(result.confirmation)
        assertFalse(DefaultJson.encodeToString(result.toResponse()).contains("confirmation"))
    }

    @Test
    fun `no visitor confirmation is sent when the org notification fails`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer(outcome = { mail ->
            if (mail.to == ORG_INBOX) SendOperationResult(failed = listOf(mail.to)) else SendOperationResult(sent = listOf(mail.to))
        })

        val result = logic(directory(config), mailer).submit(config)

        assertEquals(listOf(ORG_INBOX), mailer.mails.map { it.to })
        assertEquals(MailSendStatus.FAILED, result.orgNotification.status)
        assertEquals(VisitorConfirmationStatus.NOT_SENT, result.confirmation)
    }

    @Test
    fun `a missing confirmation template skips only the confirmation and logs a masked address`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer()

        val (result, logs) = CapturedLogs(MAIL_LOGIC_LOGGER).use { logs ->
            logic(directory(config, withConfirmationTemplate = false), mailer).submit(config) to logs.messages
        }

        assertEquals(listOf(ORG_INBOX), mailer.mails.map { it.to })
        assertEquals(MailSendStatus.SENT, result.orgNotification.status)
        assertEquals(VisitorConfirmationStatus.NOT_SENT, result.confirmation)
        assertTrue(
            logs.any { it.contains(config.id) && it.contains(MASKED_VISITOR) && it.contains("not found") },
            "Expected an error line with the config id, the reason and a masked address, got $logs",
        )
        assertTrue(logs.none { it.contains(VISITOR) }, "Address logged in clear: $logs")
    }

    @Test
    fun `a failed confirmation send is reported as not sent and leaves the org notification alone`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer(outcome = { mail ->
            if (mail.to == VISITOR) SendOperationResult(failed = listOf(mail.to)) else SendOperationResult(sent = listOf(mail.to))
        })

        val result = logic(directory(config), mailer).submit(config)

        assertEquals(MailSendStatus.SENT, result.orgNotification.status)
        assertEquals(VisitorConfirmationStatus.NOT_SENT, result.confirmation)
    }

    @Test
    fun `a mailer exception on the confirmation is reported as not sent without the address in clear`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer(outcome = { mail ->
            if (mail.to == VISITOR) error("Cannot deliver to ${mail.to}") else SendOperationResult(sent = listOf(mail.to))
        })

        val (result, logs) = CapturedLogs(MAIL_LOGIC_LOGGER).use { logs ->
            logic(directory(config), mailer).submit(config) to logs.messages
        }

        assertEquals(MailSendStatus.SENT, result.orgNotification.status)
        assertEquals(VisitorConfirmationStatus.NOT_SENT, result.confirmation)
        assertTrue(logs.any { it.contains("Cannot deliver to $MASKED_VISITOR") }, "Expected the masked reason, got $logs")
        assertTrue(logs.none { it.contains(VISITOR) }, "Address logged in clear: $logs")
    }

    @Test
    fun `a confirmation template that does not render is reported as not sent`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer()

        val result = logic(directory(config, confirmationTemplate = "<p>{{#form}}never closed</p>"), mailer).submit(config)

        assertEquals(listOf(ORG_INBOX), mailer.mails.map { it.to })
        assertEquals(MailSendStatus.SENT, result.orgNotification.status)
        assertEquals(VisitorConfirmationStatus.NOT_SENT, result.confirmation)
    }

    @Test
    fun `the daily limit drops by exactly one per submission with a confirmation`() = runBlocking {
        val config = smtpConfig(dailyLimit = 2)
        val mailer = RecordingMailer()
        val logic = logic(directory(config), mailer)

        repeat(2) { assertEquals(VisitorConfirmationStatus.SENT, logic.submit(config).confirmation) }

        assertFailsWith<DailyLimitExceededException> { logic.submit(config) }
        assertEquals(2, mailer.mailsTo(VISITOR).size)
    }

    @Test
    fun `the daily limit drops by exactly one per submission without a confirmation`() = runBlocking<Unit> {
        val config = smtpConfig(confirmation = null, dailyLimit = 2)
        val logic = logic(directory(config), RecordingMailer())

        repeat(2) { logic.submit(config) }

        assertFailsWith<DailyLimitExceededException> { logic.submit(config) }
    }

    // --- Link neutralisation (#16) ---

    @Test
    fun `links and addresses are defanged in the confirmation but not in the org notification`() = runBlocking {
        val config = smtpConfig(
            confirmation = VisitorConfirmationConfig(subjectTemplate = "Copy: {{form.topic}} from {{form.fullName}}"),
        )
        val directory = directory(
            config,
            confirmationTemplate = "<p>{{form.fullName}}</p><p>{{form.topic}}</p><p>{{form.content}}</p><p>{{form.email}}</p>",
        )
        val mailer = RecordingMailer()

        logic(directory, mailer).submit(
            config,
            fullName = "Visit https://evil.example/login",
            topic = "Contact admin@evil.example",
            content = "Go to www.evil.example, 203.0.113.7 or http://evil.example/a/b and write to boss@evil.example",
        )

        val orgNotification = mailer.mailsTo(ORG_INBOX).single()
        val confirmation = mailer.mailsTo(VISITOR).single()

        assertEquals("Copy: Contact admin[at]evil[.]example from Visit hxxps://evil[.]example/login", confirmation.subject)
        assertEquals(
            "<p>Visit hxxps://evil[.]example/login</p>" +
                "<p>Contact admin[at]evil[.]example</p>" +
                "<p>Go to www[.]evil[.]example, 203[.]0[.]113[.]7 or hxxp://evil[.]example/a/b and write to boss[at]evil[.]example</p>" +
                "<p>jane[at]visitor[.]test</p>",
            confirmation.content,
        )
        listOf(confirmation.subject, confirmation.content).forEach { text ->
            listOf("http://", "https://", "@", "evil.example", "203.0.113.7", "href").forEach { forbidden ->
                assertFalse(text.contains(forbidden), "'$forbidden' found in confirmation: $text")
            }
        }

        assertEquals("New message from Visit https://evil.example/login", orgNotification.subject)
        assertTrue(orgNotification.content.contains("http://evil.example/a/b"))
        assertTrue(orgNotification.content.contains("boss@evil.example"))
    }

    @Test
    fun `markup in the visitor's text stays escaped and its links defanged`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer()

        logic(directory(config), mailer).submit(config, content = "<a href=\"https://evil.example\">click</a>")

        val confirmation = mailer.mailsTo(VISITOR).single().content
        assertFalse(confirmation.contains("<a"), confirmation)
        assertTrue(confirmation.contains("hxxps://evil[.]example"), confirmation)
    }

    // --- Confirmation recipient limit (#17) ---

    @Test
    fun `four submissions with the same address send four org notifications and three confirmations`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer()
        val logic = logic(directory(config), mailer, InMemoryConfirmationRecipientLimiter(limit = 3))

        val results = List(4) { logic.submit(config, email = "victim@x.test") }

        assertEquals(4, mailer.mailsTo(ORG_INBOX).size)
        assertEquals(3, mailer.mailsTo("victim@x.test").size)
        assertEquals(List(3) { VisitorConfirmationStatus.SENT } + VisitorConfirmationStatus.NOT_SENT, results.map { it.confirmation })
        assertEquals(MailSendStatus.SENT, results.last().orgNotification.status)
    }

    @Test
    fun `case and plus-tag variants of an address share one allowance`() = runBlocking {
        val config = smtpConfig()
        val mailer = RecordingMailer()
        val logic = logic(directory(config), mailer, InMemoryConfirmationRecipientLimiter(limit = 3))

        val results = listOf("Victim@X.test", "victim@x.test", "victim+tag@x.test", "VICTIM+other@x.TEST")
            .map { logic.submit(config, email = it) }

        assertEquals(4, mailer.mailsTo(ORG_INBOX).size)
        assertEquals(3, mailer.mails.count { it.to != ORG_INBOX })
        assertEquals(VisitorConfirmationStatus.NOT_SENT, results.last().confirmation)
    }

    @Test
    fun `two contact form configs share the allowance of one address`() = runBlocking {
        val first = smtpConfig()
        val second = smtpConfig()
        val mailer = RecordingMailer()
        val logic = logic(directory(first, second), mailer, InMemoryConfirmationRecipientLimiter(limit = 3))

        val results = listOf(first, first, second, second).map { logic.submit(it, email = "victim@x.test") }

        assertEquals(3, mailer.mailsTo("victim@x.test").size)
        assertEquals(VisitorConfirmationStatus.NOT_SENT, results.last().confirmation)
    }

    @Test
    fun `log lines about a limited address mask it`() = runBlocking {
        val config = smtpConfig()
        val logic = logic(directory(config), RecordingMailer(), InMemoryConfirmationRecipientLimiter(limit = 3))

        val logs = CapturedLogs(MAIL_LOGIC_LOGGER, LIMITER_LOGGER).use { logs ->
            repeat(4) { logic.submit(config, email = "Victim+tag@X.test") }
            logs.messages
        }

        assertTrue(logs.any { it.contains("V***@X.test") && it.contains("limit") }, "Expected a masked limit line, got $logs")
        assertTrue(logs.none { it.contains("victim", ignoreCase = true) }, "Address logged in clear: $logs")
    }
}
