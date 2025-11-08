# Contributing to Hermes Client Library

Thank you for your interest in contributing to the Hermes client library!

## Project Structure

The Hermes project consists of several modules:

```
Hermes/
├── src/                    # Main Hermes server application
├── hermes-shared/          # Shared DTOs and serializers
├── hermes-client/          # Kotlin Multiplatform client library
├── desktop/                # Desktop application for managing configs
└── gradle/
    └── libs.versions.toml  # Centralized dependency management
```

## Building the Project

### Prerequisites

- JDK 21 or higher
- Gradle 8.7+ (included via Gradle Wrapper)

### Build Commands

```bash
# Build entire project
./gradlew build

# Build specific module
./gradlew :hermes-shared:build
./gradlew :hermes-client:build

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

## Working with the Modules

### hermes-shared

This module contains shared data types used by both server and client.

**Adding a new DTO:**
1. Create the DTO in `hermes-shared/src/commonMain/kotlin/com/vandeas/dto/`
2. Add `@Serializable` annotation
3. Use custom serializers if needed (e.g., `AnyMapSerializer` for dynamic maps)
4. Update the hermes-shared README with the new type

**Example:**
```kotlin
@Serializable
data class MyNewDto(
    val id: String,
    val name: String,
    @Serializable(AnyMapSerializer::class)
    val metadata: Map<String, Any?>
)
```

### hermes-client

This module provides the client library for interacting with Hermes API.

**Adding a new API method:**
1. Add the method to `HermesClient.kt`
2. Use the existing HTTP client instance
3. Follow the async/suspend pattern
4. Add KDoc documentation
5. Add tests in `HermesClientTest.kt`
6. Update the client README with the new method

**Example:**
```kotlin
/**
 * Gets user information.
 *
 * @param userId The user ID
 * @return User details
 * @throws Exception if the request fails
 */
suspend fun getUser(userId: String): User {
    return client.get("${config.baseUrl}/v1/users/$userId").body()
}
```

### Main Server Application

The main application depends on `hermes-shared` for DTOs.

**Important:** When making changes to shared DTOs:
1. Ensure backward compatibility
2. Update both server routes and client methods if needed
3. Test the integration between server and client

## Dependency Management

All versions are managed in `gradle/libs.versions.toml`.

**Adding a new dependency:**
1. Add the version in the `[versions]` section
2. Add the library in the `[libraries]` section
3. Reference it in the module's `build.gradle.kts` using `libs.` prefix

**Example:**
```toml
# In gradle/libs.versions.toml
[versions]
my-library = "1.0.0"

[libraries]
my-library = { module = "com.example:my-library", version.ref = "my-library" }

# In build.gradle.kts
dependencies {
    implementation(libs.my.library)
}
```

## Testing

### Writing Tests

- **hermes-shared**: Add tests in `commonTest` for serialization/deserialization
- **hermes-client**: Add tests in `commonTest` for client logic
- **Main server**: Add integration tests in `src/test`

### Running Tests

```bash
# All tests
./gradlew test

# Specific module
./gradlew :hermes-client:jvmTest
./gradlew :hermes-shared:jvmTest

# Main server tests
./gradlew :test
```

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and small
- Use Kotlin's language features appropriately (data classes, sealed interfaces, etc.)

## Adding Platform Targets

The client is currently JVM-only but can be extended to other platforms:

```kotlin
// In build.gradle.kts
kotlin {
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    // Add more targets as needed
}
```

Remember to test each platform target after adding it.

## Documentation

When adding new features:
1. Update the relevant README files
2. Add examples to demonstrate usage
3. Update the main project README if it's a major feature
4. Add inline KDoc comments

## Submitting Changes

1. Create a feature branch
2. Make your changes
3. Add/update tests
4. Update documentation
5. Ensure all tests pass
6. Create a pull request with a clear description

## Questions?

If you have questions about contributing, please open an issue on GitHub.

Thank you for contributing to Hermes! 🚀
