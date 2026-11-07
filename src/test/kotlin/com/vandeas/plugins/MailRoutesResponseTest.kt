package com.vandeas.plugins

import com.icure.kerberus.Challenge
import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.entities.Attachment
import com.vandeas.entities.ContactFormResult
import com.vandeas.entities.SendOperationResult
import com.vandeas.entities.VisitorConfirmationStatus
import com.vandeas.logic.KerberusLogic
import com.vandeas.logic.MailLogic
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

/**
 * Pins the JSON bodies of the mail endpoints: the contact endpoint gains an optional
 * `confirmation` field, and the other endpoints must not change by a single byte.
 */
class MailRoutesResponseTest {

    private class FakeMailLogic(
        private val contactResult: ContactFormResult = ContactFormResult(SendOperationResult()),
        private val sendResult: SendOperationResult = SendOperationResult(),
    ) : MailLogic {
        override suspend fun sendContactForm(form: ContactForm) = contactResult
        override suspend fun sendMail(mailInput: MailInput, attachments: List<Attachment>) = sendResult
        override suspend fun sendMails(batch: List<MailInput>) = sendResult
        override suspend fun broadcastMail(configId: String, request: BroadcastMailRequest, attachments: List<Attachment>) = sendResult
    }

    private val noKerberus = object : KerberusLogic {
        override suspend fun getChallenge(configId: String): Challenge = error("Not used")
    }

    @AfterTest
    fun cleanup() {
        stopKoin()
    }

    private fun Application.routesWith(logic: MailLogic) {
        install(Koin) {
            modules(module {
                single<MailLogic> { logic }
                single<KerberusLogic> { noKerberus }
            })
        }
        configureRouting()
    }

    private fun withRoutes(logic: MailLogic, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { routesWith(logic) }
        block()
    }

    private suspend fun ApplicationTestBuilder.post(path: String, json: String) = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    private val contactBody = """
        {"captcha":"KERBERUS","id":"form","fullName":"Jane","email":"jane@visitor.test","content":"Hi",
         "solution":{"id":"challenge","nonces":[]}}
    """.trimIndent()

    private val orgSent = SendOperationResult(sent = listOf("org@acme.test"))

    @Test
    fun `the contact response has no confirmation key when the config has no confirmation`() = withRoutes(
        FakeMailLogic(contactResult = ContactFormResult(orgSent, confirmation = null))
    ) {
        val response = post("/v1/mail/contact", contactBody)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("""{"sent":["org@acme.test"],"failed":[],"bounced":[],"temporary":[]}""", response.bodyAsText())
    }

    @Test
    fun `the contact response reports a sent confirmation`() = withRoutes(
        FakeMailLogic(contactResult = ContactFormResult(orgSent, VisitorConfirmationStatus.SENT))
    ) {
        val response = post("/v1/mail/contact", contactBody)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """{"sent":["org@acme.test"],"failed":[],"bounced":[],"temporary":[],"confirmation":"SENT"}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `a confirmation not sent leaves the HTTP status of the submission unchanged`() = withRoutes(
        FakeMailLogic(contactResult = ContactFormResult(orgSent, VisitorConfirmationStatus.NOT_SENT))
    ) {
        val response = post("/v1/mail/contact", contactBody)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """{"sent":["org@acme.test"],"failed":[],"bounced":[],"temporary":[],"confirmation":"NOT_SENT"}""",
            response.bodyAsText(),
        )
    }

    @Test
    fun `the HTTP status still comes from the org notification`() = withRoutes(
        FakeMailLogic(
            contactResult = ContactFormResult(
                SendOperationResult(failed = listOf("org@acme.test")),
                VisitorConfirmationStatus.NOT_SENT,
            )
        )
    ) {
        val response = post("/v1/mail/contact", contactBody)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    private val unchangedBody = """{"sent":["john@example.com"],"failed":[],"bounced":[],"temporary":[]}"""
    private val sendResult = SendOperationResult(sent = listOf("john@example.com"))

    @Test
    fun `the mail response is unchanged`() = withRoutes(FakeMailLogic(sendResult = sendResult)) {
        val response = post("/v1/mail", """{"id":"cfg","email":"john@example.com","attributes":{}}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(unchangedBody, response.bodyAsText())
    }

    @Test
    fun `the batch response is unchanged`() = withRoutes(FakeMailLogic(sendResult = sendResult)) {
        val response = post("/v1/mail/batch", """[{"id":"cfg","email":"john@example.com","attributes":{}}]""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(unchangedBody, response.bodyAsText())
    }

    @Test
    fun `the broadcast response is unchanged`() = withRoutes(FakeMailLogic(sendResult = sendResult)) {
        val response = post("/v1/mail/cfg/broadcast", """{"to":["john@example.com"],"attributes":{}}""")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(unchangedBody, response.bodyAsText())
    }
}
