# Hermes Shared Module

This module contains shared data transfer objects (DTOs), entities, and serializers used by both the Hermes server and client library.

## Purpose

The `hermes-shared` module serves as a common dependency for:
- The main Hermes server application
- The `hermes-client` library

This ensures type consistency and reduces code duplication between server and client implementations.

## Contents

### DTOs (Data Transfer Objects)

#### ContactForm
A sealed interface representing different types of contact forms with captcha verification:

- `GoogleRecaptchaContactForm` - Contact form with Google reCAPTCHA verification
- `KerberusContactForm` - Contact form with Kerberus verification

Common fields:
- `id`: Configuration ID
- `fullName`: Sender's full name
- `email`: Sender's email address
- `content`: Message content
- `phone`: Optional phone number
- `topic`: Optional message topic
- `destinations`: List of recipient email addresses

#### MailInput
Represents a mail to be sent using a mail configuration:
- `id`: Mail configuration ID
- `email`: Recipient email address
- `attributes`: Map of template attributes (supports nested objects and lists)

### Entities

#### SendOperationResult
Represents the result of a mail send operation:
- `sent`: List of successfully sent email addresses
- `failed`: List of failed email addresses
- `status`: Computed status (SENT, PARTIAL, or FAILED)

#### Mail
Basic email entity:
- `from`: Sender email address
- `to`: Recipient email address
- `subject`: Email subject
- `content`: Email content

### Serializers

#### AnySerializer
Custom Kotlinx Serialization serializer that handles dynamic JSON types:
- Supports primitives (String, Number, Boolean)
- Supports collections (List, Map)
- Supports nested structures
- JSON-only serializer

#### AnyMapSerializer
Wrapper around `AnySerializer` specifically for `Map<String, Any?>` types, used in `MailInput.attributes`.

### Configuration Types

#### CaptchaConfig
Sealed interface for captcha provider configurations:
- `GoogleRecaptchaConfig` - Configuration for Google reCAPTCHA
- `KerberusConfig` - Configuration for Kerberus

## Build

This is a Kotlin Multiplatform module currently targeting JVM.

```bash
./gradlew :hermes-shared:build
```

## Dependencies

- `kotlinx-serialization-json`: For JSON serialization
- `kerberus`: For Kerberus captcha integration

## Usage

Add as a dependency in your module:

```kotlin
dependencies {
    implementation(project(":hermes-shared"))
}
```

Then import and use the shared types:

```kotlin
import com.vandeas.dto.MailInput
import com.vandeas.entities.SendOperationResult

val mailInput = MailInput(
    id = "config-id",
    email = "user@example.com",
    attributes = mapOf("name" to "John")
)
```
