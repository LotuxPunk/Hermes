package com.vandeas.hermes.client

import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.MailInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Basic tests for the HermesClient configuration and data classes.
 * These tests verify the client can be instantiated and configured correctly.
 */
class HermesClientTest {

    @Test
    fun testClientConfiguration() {
        val config = HermesClientConfig(
            baseUrl = "https://test.example.com"
        )
        
        assertNotNull(config)
        assertEquals("https://test.example.com", config.baseUrl)
    }

    @Test
    fun testMailInputCreation() {
        val mailInput = MailInput(
            id = "test-config-id",
            email = "test@example.com",
            attributes = mapOf(
                "name" to "Test User",
                "code" to 12345
            )
        )
        
        assertNotNull(mailInput)
        assertEquals("test-config-id", mailInput.id)
        assertEquals("test@example.com", mailInput.email)
        assertEquals(2, mailInput.attributes.size)
    }

    @Test
    fun testContactFormCreation() {
        val contactForm = GoogleRecaptchaContactForm(
            id = "test-config-id",
            fullName = "John Doe",
            email = "john@example.com",
            content = "Test message",
            recaptchaToken = "test-token"
        )
        
        assertNotNull(contactForm)
        assertEquals("test-config-id", contactForm.id)
        assertEquals("John Doe", contactForm.fullName)
        assertEquals("john@example.com", contactForm.email)
        assertEquals("Test message", contactForm.content)
        assertEquals("test-token", contactForm.recaptchaToken)
    }

    @Test
    fun testMailInputWithNestedAttributes() {
        val mailInput = MailInput(
            id = "test-id",
            email = "user@example.com",
            attributes = mapOf(
                "user" to mapOf(
                    "name" to "Alice",
                    "age" to 30
                ),
                "items" to listOf("item1", "item2", "item3")
            )
        )
        
        assertNotNull(mailInput)
        assertEquals(2, mailInput.attributes.size)
    }
}
