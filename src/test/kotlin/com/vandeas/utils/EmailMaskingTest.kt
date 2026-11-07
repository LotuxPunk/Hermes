package com.vandeas.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailMaskingTest {

    @Test
    fun `keeps the first character and the domain`() {
        assertEquals("j***@example.com", "john@example.com".maskEmailAddress())
    }

    @Test
    fun `masks a one-character local part`() {
        assertEquals("j***@example.com", "j@example.com".maskEmailAddress())
    }

    @Test
    fun `masks the bare address of the display name form`() {
        assertEquals("j***@example.com", "John Doe <john.doe@example.com>".maskEmailAddress())
    }

    @Test
    fun `hides a value that is not an address`() {
        assertEquals("***", "not an address".maskEmailAddress())
        assertEquals("***", "@example.com".maskEmailAddress())
        assertEquals("***", "".maskEmailAddress())
    }

    @Test
    fun `unwraps the display name form`() {
        assertEquals("john@example.com", "John <john@example.com>".bareEmailAddress())
        assertEquals("john@example.com", "  john@example.com ".bareEmailAddress())
    }
}
