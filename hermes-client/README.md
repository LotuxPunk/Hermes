# Hermes Client Library

A Kotlin Multiplatform client library for interacting with the Hermes mail API.

## Features

- 🔄 **Kotlin Multiplatform**: Works on JVM and other Kotlin targets
- 📦 **Shared DTOs**: Common data types between server and client
- 🔌 **Flexible HTTP Client**: Use your own Ktor HTTP client or let the library create one
- 🎯 **Type-Safe**: Fully typed API with sealed interfaces for different form types
- ⚡ **Coroutines**: Async/await support with Kotlin coroutines

## Installation

### Gradle (Kotlin DSL)

Add the hermes-client module to your project:

```kotlin
dependencies {
    implementation(project(":hermes-client"))
}
```

## Usage

### Basic Example

```kotlin
import com.vandeas.hermes.client.HermesClient
import com.vandeas.hermes.client.HermesClientConfig
import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.MailInput

// Create a client
val client = HermesClient(
    HermesClientConfig(
        baseUrl = "https://your-hermes-instance.com"
    )
)

// Send a contact form
val contactForm = GoogleRecaptchaContactForm(
    id = "your-config-id",
    fullName = "John Doe",
    email = "john@example.com",
    content = "Hello, this is a test message!",
    recaptchaToken = "your-recaptcha-token"
)

val result = client.sendContactForm(contactForm)
println("Sent to: ${result.sent}")
println("Failed: ${result.failed}")

// Send a single mail
val mailInput = MailInput(
    id = "your-mail-config-id",
    email = "recipient@example.com",
    attributes = mapOf(
        "firstName" to "Jane",
        "lastName" to "Smith",
        "orderNumber" to 12345
    )
)

val mailResult = client.sendMail(mailInput)
println("Mail sent: ${mailResult.status}")

// Don't forget to close the client when done
client.close()
```

### Using a Custom HTTP Client

You can provide your own configured Ktor HTTP client:

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import java.util.concurrent.TimeUnit

val customHttpClient = HttpClient(OkHttp) {
    engine {
        config {
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
        }
    }
    
    install(Logging) {
        level = LogLevel.INFO
    }
    
    install(ContentNegotiation) {
        json()
    }
}

val client = HermesClient(
    HermesClientConfig(
        baseUrl = "https://your-hermes-instance.com",
        httpClient = customHttpClient
    )
)

// Use the client...

// When providing a custom client, you're responsible for closing it
customHttpClient.close()
```

### Sending Batch Emails

```kotlin
val batch = listOf(
    MailInput(
        id = "your-mail-config-id",
        email = "user1@example.com",
        attributes = mapOf("name" to "User 1", "code" to "ABC123")
    ),
    MailInput(
        id = "your-mail-config-id",
        email = "user2@example.com",
        attributes = mapOf("name" to "User 2", "code" to "DEF456")
    )
)

val batchResult = client.sendMailBatch(batch)
println("Batch result - Sent: ${batchResult.sent.size}, Failed: ${batchResult.failed.size}")
```

### Getting Kerberus Challenge

```kotlin
val challenge = client.getChallenge("your-kerberus-config-id")
println("Challenge: $challenge")
```

### Using Kerberus Contact Form

```kotlin
import com.vandeas.dto.KerberusContactForm
import com.icure.kerberus.Solution

val kerberusForm = KerberusContactForm(
    id = "your-config-id",
    fullName = "John Doe",
    email = "john@example.com",
    content = "Message with Kerberus verification",
    solution = Solution(/* ... */), // Provide Kerberus solution
    phone = "+1234567890",
    topic = "Support Request"
)

val result = client.sendContactForm(kerberusForm)
```

## API Reference

### HermesClient

#### Constructor
- `HermesClient(config: HermesClientConfig)`: Creates a new client instance

#### Methods
- `suspend fun sendContactForm(contactForm: ContactForm): SendOperationResult`
  - Sends a contact form (supports both GoogleRecaptcha and Kerberus)
  
- `suspend fun sendMail(mailInput: MailInput): SendOperationResult`
  - Sends a single mail using a mail configuration
  
- `suspend fun sendMailBatch(batch: List<MailInput>): SendOperationResult`
  - Sends multiple mails in a single batch request
  
- `suspend fun getChallenge(configId: String): String`
  - Gets a Kerberus challenge for a specific configuration
  
- `fun close()`
  - Closes the HTTP client (only if it was created by the library)

### Data Classes

#### ContactForm (Sealed Interface)
- `GoogleRecaptchaContactForm`: Contact form with Google reCAPTCHA verification
- `KerberusContactForm`: Contact form with Kerberus verification

#### MailInput
- `id`: Configuration ID
- `email`: Recipient email address
- `attributes`: Map of template attributes

#### SendOperationResult
- `sent`: List of successfully sent email addresses
- `failed`: List of failed email addresses
- `status`: Overall operation status (SENT, PARTIAL, or FAILED)

## Error Handling

The client throws exceptions for HTTP errors. It's recommended to wrap calls in try-catch blocks:

```kotlin
try {
    val result = client.sendMail(mailInput)
    // Handle success
} catch (e: Exception) {
    // Handle error
    println("Error sending mail: ${e.message}")
}
```

## Thread Safety

The HermesClient is thread-safe and can be shared across multiple coroutines.

## License

This project is part of the Hermes mail service.
