package com.vandeas.hermes.client.examples

import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.MailInput
import com.vandeas.hermes.client.HermesClient
import com.vandeas.hermes.client.HermesClientConfig

/**
 * Simple example demonstrating how to use the HermesClient library.
 * 
 * This example shows:
 * 1. Creating a client instance
 * 2. Sending a contact form
 * 3. Sending a single mail
 * 4. Sending a batch of mails
 * 5. Properly closing the client
 */
suspend fun main() {
    // Create the Hermes client with your server's base URL
    val client = HermesClient(
        HermesClientConfig(
            baseUrl = "http://localhost:8081"
        )
    )

    try {
        // Example 1: Send a contact form
        println("Example 1: Sending contact form...")
        val contactForm = GoogleRecaptchaContactForm(
            id = "your-contact-form-config-id",
            fullName = "John Doe",
            email = "john.doe@example.com",
            content = "Hello! I would like to know more about your services.",
            phone = "+1234567890",
            topic = "General Inquiry",
            recaptchaToken = "your-recaptcha-token-here"
        )

        val contactResult = client.sendContactForm(contactForm)
        println("Contact form sent!")
        println("  - Sent to: ${contactResult.sent}")
        println("  - Status: ${contactResult.status}")
        println()

        // Example 2: Send a single mail
        println("Example 2: Sending a single mail...")
        val singleMail = MailInput(
            id = "your-mail-config-id",
            email = "recipient@example.com",
            attributes = mapOf(
                "firstName" to "Jane",
                "lastName" to "Smith",
                "orderNumber" to 12345,
                "orderDate" to "2024-01-15"
            )
        )

        val mailResult = client.sendMail(singleMail)
        println("Mail sent!")
        println("  - Sent to: ${mailResult.sent}")
        println("  - Status: ${mailResult.status}")
        println()

        // Example 3: Send a batch of mails
        println("Example 3: Sending batch of mails...")
        val batchMails = listOf(
            MailInput(
                id = "your-mail-config-id",
                email = "user1@example.com",
                attributes = mapOf(
                    "name" to "User One",
                    "verificationCode" to "ABC123"
                )
            ),
            MailInput(
                id = "your-mail-config-id",
                email = "user2@example.com",
                attributes = mapOf(
                    "name" to "User Two",
                    "verificationCode" to "DEF456"
                )
            ),
            MailInput(
                id = "your-mail-config-id",
                email = "user3@example.com",
                attributes = mapOf(
                    "name" to "User Three",
                    "verificationCode" to "GHI789"
                )
            )
        )

        val batchResult = client.sendMailBatch(batchMails)
        println("Batch sent!")
        println("  - Successfully sent: ${batchResult.sent.size} emails")
        println("  - Failed: ${batchResult.failed.size} emails")
        println("  - Status: ${batchResult.status}")
        println()

        // Example 4: Get a Kerberus challenge
        println("Example 4: Getting Kerberus challenge...")
        try {
            val challenge = client.getChallenge("your-kerberus-config-id")
            println("Challenge received: $challenge")
        } catch (e: Exception) {
            println("Error getting challenge: ${e.message}")
        }

    } catch (e: Exception) {
        println("Error occurred: ${e.message}")
        e.printStackTrace()
    } finally {
        // Always close the client when done
        client.close()
        println("\nClient closed.")
    }
}
