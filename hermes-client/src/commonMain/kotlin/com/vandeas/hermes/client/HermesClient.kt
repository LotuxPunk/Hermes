package com.vandeas.hermes.client

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.entities.SendOperationResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Configuration for the Hermes client.
 *
 * @property baseUrl The base URL of the Hermes API (e.g., "https://api.example.com")
 * @property httpClient Optional custom Ktor HTTP client. If not provided, a default client will be created.
 */
data class HermesClientConfig(
    val baseUrl: String,
    val httpClient: HttpClient? = null
)

/**
 * Client library for interacting with the Hermes mail API.
 *
 * @property config Configuration for the client
 */
class HermesClient(private val config: HermesClientConfig) {
    private val client: HttpClient = config.httpClient ?: createDefaultClient()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    /**
     * Creates a default HTTP client with JSON serialization configured.
     */
    private fun createDefaultClient(): HttpClient {
        return HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    /**
     * Send a contact form.
     *
     * @param contactForm The contact form to send
     * @return The result of the send operation
     * @throws Exception if the request fails
     */
    suspend fun sendContactForm(contactForm: ContactForm): SendOperationResult {
        return client.post("${config.baseUrl}/v1/mail/contact") {
            contentType(ContentType.Application.Json)
            setBody(contactForm)
        }.body()
    }

    /**
     * Send a single mail.
     *
     * @param mailInput The mail input containing the configuration ID, recipient email, and attributes
     * @return The result of the send operation
     * @throws Exception if the request fails
     */
    suspend fun sendMail(mailInput: MailInput): SendOperationResult {
        return client.post("${config.baseUrl}/v1/mail") {
            contentType(ContentType.Application.Json)
            setBody(mailInput)
        }.body()
    }

    /**
     * Send a batch of mails.
     *
     * @param batch List of mail inputs to send
     * @return The result of the batch send operation
     * @throws Exception if the request fails
     */
    suspend fun sendMailBatch(batch: List<MailInput>): SendOperationResult {
        return client.post("${config.baseUrl}/v1/mail/batch") {
            contentType(ContentType.Application.Json)
            setBody(batch)
        }.body()
    }

    /**
     * Get a Kerberus challenge for a specific configuration.
     *
     * @param configId The configuration ID to get the challenge for
     * @return The challenge response as a string
     * @throws Exception if the request fails
     */
    suspend fun getChallenge(configId: String): String {
        return client.get("${config.baseUrl}/v1/challenge") {
            parameter("configId", configId)
        }.body()
    }

    /**
     * Close the HTTP client if it was created by this instance.
     * Should be called when the client is no longer needed.
     */
    fun close() {
        if (config.httpClient == null) {
            client.close()
        }
    }
}
