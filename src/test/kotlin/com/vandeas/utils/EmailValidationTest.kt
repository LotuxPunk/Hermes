package com.vandeas.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins down what [isValidEmailAddress] accepts, because it decides which requests
 * get rejected with a bad request response.
 */
class EmailValidationTest {

    @Test
    fun `accepts a plain address`() {
        assertTrue("support@acme.com".isValidEmailAddress())
    }

    @Test
    fun `accepts a subdomain and a multi-part TLD`() {
        assertTrue("no-reply@mail.acme.co.uk".isValidEmailAddress())
    }

    @Test
    fun `accepts the display name form`() {
        assertTrue("Support <support@acme.com>".isValidEmailAddress())
    }

    @Test
    fun `rejects an empty string`() {
        assertFalse("".isValidEmailAddress())
    }

    @Test
    fun `rejects a whitespace only string`() {
        assertFalse("   ".isValidEmailAddress())
    }

    @Test
    fun `rejects a value with no at sign`() {
        assertFalse("support".isValidEmailAddress())
    }

    @Test
    fun `rejects a missing domain`() {
        assertFalse("support@".isValidEmailAddress())
    }

    @Test
    fun `rejects a missing local part`() {
        assertFalse("@acme.com".isValidEmailAddress())
    }

    @Test
    fun `rejects a second at sign`() {
        assertFalse("support@acme@com".isValidEmailAddress())
    }

    @Test
    fun `rejects whitespace inside the local part`() {
        assertFalse("sup port@acme.com".isValidEmailAddress())
    }

    @Test
    fun `rejects a comma separated list so one field cannot carry two addresses`() {
        assertFalse("support@acme.com, sales@acme.com".isValidEmailAddress())
    }

    @Test
    fun `accepts a bare hostname, per RFC 822`() {
        // Documented, not desired: javax.mail does not require a dotted domain.
        assertTrue("support@localhost".isValidEmailAddress())
    }
}
