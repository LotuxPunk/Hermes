package com.vandeas.service.impl.mailer

import javax.mail.MessagingException
import javax.mail.SendFailedException
import javax.mail.internet.InternetAddress
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SMTPMailerClassificationTest {

    private val recipient = "to@example.com"

    @Test
    fun `messaging exception should classify as temporary only, not also failed`() {
        val result = SMTPMailer.classifyMessagingException(recipient, MessagingException("network glitch"))

        assertContains(result.temporary, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "A temporary failure must not also be reported as a definitive `failed`; got $result"
        )
    }

    // SendFailedException(msg, ex, validSent, validUnsent, invalid)
    @Test
    fun `permanent SMTP error should classify as bounced only, not also failed`() {
        // Address rejected as invalid → hasInvalidAddresses → permanent
        val sfe = SendFailedException(
            "550 5.1.1 mailbox not found",
            null,
            null,
            null,
            arrayOf(InternetAddress(recipient))
        )
        val result = SMTPMailer.classifySendFailedException(recipient, sfe)

        assertContains(result.bounced, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "A bounced (permanent) failure must not also be in `failed`; got $result"
        )
    }

    @Test
    fun `transient SMTP error should classify as temporary only`() {
        // 421 = service not available — temporary; address landed in validUnsent
        val sfe = SendFailedException(
            "421 service not available",
            null,
            null,
            arrayOf(InternetAddress(recipient)),
            null
        )
        val result = SMTPMailer.classifySendFailedException(recipient, sfe)

        assertContains(result.temporary, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "A temporary failure must not also be in `failed`; got $result"
        )
    }

    @Test
    fun `unexpected exception should classify as temporary only`() {
        val result = SMTPMailer.classifyUnexpectedException(recipient, RuntimeException("boom"))

        assertContains(result.temporary, recipient)
        assertFalse(
            result.failed.contains(recipient),
            "Unexpected errors must not also be in `failed`; got $result"
        )
    }
}
