package com.vandeas.integration

import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.SMTPContactFormConfig
import com.vandeas.dto.configs.VisitorConfirmationConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.entities.VisitorConfirmationStatus
import com.vandeas.logic.impl.MailLogicImpl
import com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter
import com.vandeas.service.impl.InMemoryDailyLimiter
import com.vandeas.support.FakeConfigDirectory
import com.vandeas.support.solvedKerberusForm
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end SMTP tests of the Visitor confirmation against a real SMTP server: Mailpit in a
 * Testcontainers container.
 *
 * Each test drives a real Submission, with a solved Kerberus captcha, through the contact
 * form flow and the production mailers, then reads what Mailpit received through its HTTP
 * API. The mail queue (`USE_MAIL_QUEUE`, on by default) makes delivery asynchronous, so the
 * tests poll Mailpit instead of asserting right away.
 *
 * Skipped, not failed, when Docker is not available.
 */
class VisitorConfirmationMailpitTest {

    private class MailpitContainer : GenericContainer<MailpitContainer>(DockerImageName.parse(MAILPIT_IMAGE))

    companion object {
        /** Pinned so that a Mailpit release cannot change what the suite checks. */
        private const val MAILPIT_IMAGE = "axllent/mailpit:v1.32.0"
        private const val SMTP_PORT = 1025
        private const val HTTP_PORT = 8025

        private const val ORG_INBOX = "org@acme.test"
        private const val SENDER = "no-reply@acme.test"

        private var mailpit: MailpitContainer? = null

        /** Starts Mailpit when Docker is available. Without it, each test skips itself. */
        @BeforeClass
        @JvmStatic
        fun startMailpit() {
            if (!isDockerAvailable()) {
                return
            }
            mailpit = MailpitContainer()
                .withExposedPorts(SMTP_PORT, HTTP_PORT)
                // The SMTP mailer always authenticates, over a connection without TLS here.
                .withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "1")
                .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "1")
                .waitingFor(Wait.forHttp("/api/v1/info").forPort(HTTP_PORT))
                .also { it.start() }
        }

        @AfterClass
        @JvmStatic
        fun stopMailpit() {
            mailpit?.stop()
        }

        private fun isDockerAvailable(): Boolean = try {
            DockerClientFactory.instance().isDockerAvailable
        } catch (_: Throwable) {
            false
        }
    }

    private val http = HttpClient.newHttpClient()
    private val container get() = checkNotNull(mailpit)
    private val apiBase get() = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}/api/v1"

    @BeforeTest
    fun deleteAllMessages() {
        Assume.assumeTrue("Docker is not available, skipping the Mailpit suite", mailpit != null)
        http.send(
            HttpRequest.newBuilder(URI("$apiBase/messages")).DELETE().build(),
            HttpResponse.BodyHandlers.discarding(),
        )
    }

    // --- Mailpit API ---

    private data class ReceivedMail(
        val from: String,
        val to: List<String>,
        val replyTo: List<String>,
        val subject: String,
        val html: String,
    )

    private fun getJson(path: String): JsonObject {
        val response = http.send(HttpRequest.newBuilder(URI("$apiBase$path")).GET().build(), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Mailpit answered ${response.statusCode()} on $path: ${response.body()}" }
        return Json.parseToJsonElement(response.body()).jsonObject
    }

    private fun JsonArray?.addresses(): List<String> =
        this?.map { it.jsonObject["Address"]!!.jsonPrimitive.content }.orEmpty()

    private fun receivedMails(): List<ReceivedMail> =
        getJson("/messages?limit=100")["messages"]!!.jsonArray.map { summary ->
            val message = getJson("/message/${summary.jsonObject["ID"]!!.jsonPrimitive.content}")
            ReceivedMail(
                from = message["From"]!!.jsonObject["Address"]!!.jsonPrimitive.content,
                to = (message["To"] as? JsonArray).addresses(),
                replyTo = (message["ReplyTo"] as? JsonArray).addresses(),
                subject = message["Subject"]!!.jsonPrimitive.content,
                html = message["HTML"]!!.jsonPrimitive.content,
            )
        }

    /**
     * Waits until Mailpit holds [count] messages, then a little longer so that a message that
     * should not exist has time to show up.
     */
    private suspend fun awaitMails(count: Int, timeout: Duration = 20.seconds, settle: Duration = 1.seconds): List<ReceivedMail> {
        withTimeout(timeout) {
            while (getJson("/messages?limit=1")["messages_count"]!!.jsonPrimitive.content.toInt() < count) {
                delay(100.milliseconds)
            }
        }
        delay(settle)
        return receivedMails()
    }

    // --- Hermes side ---

    private fun config(confirmation: VisitorConfirmationConfig? = VisitorConfirmationConfig("We received your message, {{form.fullName}}")) =
        "contact-${UUID.randomUUID()}".let { id ->
            SMTPContactFormConfig(
                id = id,
                dailyLimit = 10,
                destination = ORG_INBOX,
                sender = SENDER,
                lang = "en",
                subjectTemplate = "New message from {{form.fullName}}",
                username = "hermes",
                password = "any",
                smtpHost = container.host,
                smtpPort = container.getMappedPort(SMTP_PORT),
                captcha = KerberusConfig(secretKey = "secret-$id"),
                confirmation = confirmation,
            )
        }

    private fun logic(config: ContactFormConfig, recipientLimit: Int = 3) = MailLogicImpl(
        mailConfigHandler = FakeConfigDirectory<MailConfig>(),
        contactFormConfigHandler = FakeConfigDirectory<ContactFormConfig>(listOf(config)).apply {
            templates[config.id] = "<p>{{form.fullName}} wrote:</p><p>{{form.content}}</p>"
            templates["${config.id}.confirmation"] = "<p>Thank you {{form.fullName}}. Your message:</p><p>{{form.content}}</p>"
        },
        limiter = InMemoryDailyLimiter(),
        confirmationLimiter = InMemoryConfirmationRecipientLimiter(recipientLimit),
    )

    // --- Scenarios ---

    @Test
    fun `with a confirmation block the org inbox and the visitor each receive their mail`() = runBlocking {
        val config = config()

        val result = logic(config).sendContactForm(solvedKerberusForm(config, email = "jane@visitor.test"))

        assertEquals(VisitorConfirmationStatus.SENT, result.confirmation)
        val mails = awaitMails(2)
        assertEquals(2, mails.size)

        val orgNotification = mails.single { it.to == listOf(ORG_INBOX) }
        assertEquals("New message from Jane Visitor", orgNotification.subject)
        assertEquals(SENDER, orgNotification.from)

        val confirmation = mails.single { it.to == listOf("jane@visitor.test") }
        assertEquals("We received your message, Jane Visitor", confirmation.subject)
        assertEquals(SENDER, confirmation.from)
        assertEquals(listOf(ORG_INBOX), confirmation.replyTo)
    }

    @Test
    fun `without a confirmation block exactly one message is delivered`() = runBlocking {
        val config = config(confirmation = null)

        val result = logic(config).sendContactForm(solvedKerberusForm(config, email = "jane@visitor.test"))

        assertEquals(null, result.confirmation)
        val mails = awaitMails(1, settle = 2.seconds)
        assertEquals(listOf(listOf(ORG_INBOX)), mails.map { it.to })
    }

    @Test
    fun `links in the visitor's copy are delivered defanged and without href`() = runBlocking {
        val config = config()

        logic(config).sendContactForm(
            solvedKerberusForm(
                config,
                email = "jane@visitor.test",
                content = "Please check https://evil.example/login and www.evil.example, or write to boss@evil.example",
            )
        )

        val confirmation = awaitMails(2).single { it.to == listOf("jane@visitor.test") }
        assertTrue(confirmation.html.contains("hxxps://evil[.]example/login"), confirmation.html)
        assertTrue(confirmation.html.contains("www[.]evil[.]example"), confirmation.html)
        assertTrue(confirmation.html.contains("boss[at]evil[.]example"), confirmation.html)
        listOf("href", "https://", "evil.example", "@").forEach { forbidden ->
            assertFalse(confirmation.html.contains(forbidden), "'$forbidden' found in ${confirmation.html}")
        }
    }

    @Test
    fun `four submissions with the same visitor address deliver four org notifications and three confirmations`() = runBlocking {
        val config = config()
        val logic = logic(config, recipientLimit = 3)

        val results = List(4) { logic.sendContactForm(solvedKerberusForm(config, email = "victim@visitor.test")) }

        assertEquals(List(3) { VisitorConfirmationStatus.SENT } + VisitorConfirmationStatus.NOT_SENT, results.map { it.confirmation })
        val mails = awaitMails(7, settle = 2.seconds)
        assertEquals(4, mails.count { it.to == listOf(ORG_INBOX) })
        assertEquals(3, mails.count { it.to == listOf("victim@visitor.test") })
        assertEquals(7, mails.size)
    }
}
