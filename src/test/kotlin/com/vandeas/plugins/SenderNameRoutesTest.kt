package com.vandeas.plugins

import com.icure.kerberus.Challenge
import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.SMTPMailConfig
import com.vandeas.entities.Attachment
import com.vandeas.entities.ContactFormResult
import com.vandeas.entities.SendOperationResult
import com.vandeas.logic.KerberusLogic
import com.vandeas.logic.MailLogic
import com.vandeas.logic.impl.MailLogicImpl
import com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter
import com.vandeas.service.impl.InMemoryDailyLimiter
import com.vandeas.support.FakeConfigDirectory
import com.vandeas.support.RecordingMailer
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `senderName` from the HTTP request to the mail logic, in JSON and multipart bodies. */
class SenderNameRoutesTest {

    private class CapturingMailLogic : MailLogic {
        val mailInputs = mutableListOf<MailInput>()
        val broadcasts = mutableListOf<BroadcastMailRequest>()

        override suspend fun sendContactForm(form: ContactForm) = ContactFormResult(SendOperationResult())
        override suspend fun sendMail(mailInput: MailInput, attachments: List<Attachment>) =
            SendOperationResult(sent = listOf(mailInput.email)).also { mailInputs += mailInput }
        override suspend fun sendMails(batch: List<MailInput>) =
            SendOperationResult(sent = batch.map { it.email }).also { mailInputs += batch }
        override suspend fun broadcastMail(configId: String, request: BroadcastMailRequest, attachments: List<Attachment>) =
            SendOperationResult(sent = request.to).also { broadcasts += request }
    }

    private val noKerberus = object : KerberusLogic {
        override suspend fun getChallenge(configId: String): Challenge = error("Not used")
    }

    @AfterTest
    fun cleanup() {
        stopKoin()
    }

    private fun withRoutes(logic: MailLogic, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(Koin) {
                modules(module {
                    single<MailLogic> { logic }
                    single<KerberusLogic> { noKerberus }
                })
            }
            configureRouting()
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, json: String) = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    @Test
    fun `json bodies carry the sender name`() {
        val logic = CapturingMailLogic()
        withRoutes(logic) {
            post("/v1/mail", """{"id":"cfg","email":"a@example.com","senderName":"Spy Club via Courtly","attributes":{}}""")
            post("/v1/mail", """{"id":"cfg","email":"b@example.com","attributes":{}}""")
            post(
                "/v1/mail/batch",
                """[{"id":"cfg","email":"c@example.com","senderName":"TC Uccle via Courtly","attributes":{}},
                    {"id":"cfg","email":"d@example.com","senderName":null,"attributes":{}}]""",
            )
            post("/v1/mail/cfg/broadcast", """{"to":["e@example.com"],"senderName":"Château d’Ixelles via Courtly","attributes":{}}""")
        }

        assertEquals(
            mapOf(
                "a@example.com" to "Spy Club via Courtly",
                "b@example.com" to null,
                "c@example.com" to "TC Uccle via Courtly",
                "d@example.com" to null,
            ),
            logic.mailInputs.associate { it.email to it.senderName },
        )
        assertEquals("Château d’Ixelles via Courtly", logic.broadcasts.single().senderName)
    }

    @Test
    fun `the multipart broadcast carries the sender name field`() {
        val logic = CapturingMailLogic()
        withRoutes(logic) {
            client.submitFormWithBinaryData(
                url = "/v1/mail/cfg/broadcast",
                formData = formData {
                    append("to", "a@example.com")
                    append("senderName", "Tennis Club \"Les Pins\" via Courtly")
                },
            )
            client.submitFormWithBinaryData(
                url = "/v1/mail/cfg/broadcast",
                formData = formData { append("to", "b@example.com") },
            )
        }

        assertEquals("Tennis Club \"Les Pins\" via Courtly", logic.broadcasts[0].senderName)
        assertNull(logic.broadcasts[1].senderName)
    }

    @Test
    fun `a refused sender name answers 400 and sends nothing`() {
        val mailer = RecordingMailer()
        val config = SMTPMailConfig(
            id = "cfg",
            sender = "Courtly <ne-pas-repondre@courtly.be>",
            subjectTemplate = "Subject",
            username = "user",
            password = "secret",
            smtpHost = "localhost",
        )
        val logic = MailLogicImpl(
            FakeConfigDirectory(listOf<MailConfig>(config), mutableMapOf("cfg" to "<p>Hi</p>")),
            FakeConfigDirectory<ContactFormConfig>(),
            InMemoryDailyLimiter(),
            InMemoryConfirmationRecipientLimiter(3),
        ) { mailer }

        withRoutes(logic) {
            val responses = listOf(
                post("/v1/mail", """{"id":"cfg","email":"a@example.com","senderName":"Spy\r\nBcc: x@example.com","attributes":{}}"""),
                post(
                    "/v1/mail/batch",
                    """[{"id":"cfg","email":"a@example.com","senderName":"Spy Club","attributes":{}},
                        {"id":"cfg","email":"b@example.com","senderName":"","attributes":{}}]""",
                ),
                post("/v1/mail/cfg/broadcast", """{"to":["a@example.com"],"senderName":"Spy ‮","attributes":{}}"""),
            )

            responses.forEach { response ->
                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertEquals("Invalid senderName", response.bodyAsText())
            }
        }

        assertTrue(mailer.mails.isEmpty())
    }
}
