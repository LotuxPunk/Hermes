package com.vandeas.service.impl.mailer

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ResendMailerClassificationTest {

    private val recipient = "to@example.com"

    @Test
    fun `400 should classify as bounced only`() {
        val result = ResendMailer.classifyResendStatus(recipient, statusCode = 400)
        assertContains(result.bounced, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "Bounced result must not also populate `failed`; got $result"
        )
    }

    @Test
    fun `404 should classify as bounced only`() {
        val result = ResendMailer.classifyResendStatus(recipient, statusCode = 404)
        assertContains(result.bounced, recipient)
        assertFalse(result.failed.contains(recipient))
    }

    @Test
    fun `422 should classify as bounced only`() {
        val result = ResendMailer.classifyResendStatus(recipient, statusCode = 422)
        assertContains(result.bounced, recipient)
        assertFalse(result.failed.contains(recipient))
    }

    @Test
    fun `429 rate-limit should classify as temporary only`() {
        val result = ResendMailer.classifyResendStatus(recipient, statusCode = 429)
        assertContains(result.temporary, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "Temporary result must not also populate `failed`; got $result"
        )
    }

    @Test
    fun `5xx should classify as temporary only`() {
        listOf(500, 502, 503, 504).forEach { code ->
            val result = ResendMailer.classifyResendStatus(recipient, statusCode = code)
            assertContains(result.temporary, recipient, "for code $code")
            assertFalse(result.failed.contains(recipient), "for code $code; got $result")
        }
    }

    @Test
    fun `unknown status should classify as temporary only`() {
        val result = ResendMailer.classifyResendStatus(recipient, statusCode = 418)
        assertContains(result.temporary, recipient)
        assertFalse(result.failed.contains(recipient))
    }

    @Test
    fun `unexpected exception should classify as temporary only`() {
        val result = ResendMailer.classifyUnexpectedException(recipient, RuntimeException("boom"))
        assertContains(result.temporary, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "Unexpected errors must not also populate `failed`; got $result"
        )
    }
}
