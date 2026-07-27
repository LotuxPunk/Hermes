# Contact Form Honeypot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a spam-filtering layer to `POST /v1/mail/contact` using server-issued randomized honeypot field names bound to an HMAC-signed single-use token, stacking with the existing captcha providers.

**Architecture:** A new `GET /v1/mail/contact/{configId}/form-session` endpoint issues randomized hidden-field names plus a token containing those names, the config id, a nonce, and an issued-at timestamp — all covered by an HMAC-SHA256 signature. The browser renders the fields, submits them back with the token, and `MailLogicImpl.sendContactForm` verifies the signature (so the field list cannot be forged), consumes the nonce (so the token is single-use), and checks that every declared field came back blank. A tripped trap returns a response indistinguishable from success; a malformed or replayed token returns `403`.

**Tech Stack:** Kotlin 2.2.21, Ktor 3.3.2, kotlinx.serialization 1.7.1, Koin, cache4k 0.13.0, JDK `javax.crypto.Mac` (no new dependencies), `kotlin-test-junit` for tests.

**Spec:** `docs/superpowers/specs/2026-07-26-contact-form-honeypot-design.md`

## Global Constraints

- **`JAVA_HOME` must point at JDK 21.** Gradle 8.13 cannot run on the machine's default JDK 25 — `./gradlew` fails with a bare `25.0.2` error before doing any work. Export this in every shell:
  `export JAVA_HOME=/Users/lotuxpunk/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home`
- **Baseline is green: 30 tests, 0 failures.** Verify with `./gradlew test` before starting. Never finish a task with fewer than the tests you added plus 30 passing.
- **No new dependencies.** HMAC uses JDK `javax.crypto.Mac` + `javax.crypto.spec.SecretKeySpec`; randomness uses `java.security.SecureRandom`; the nonce cache uses the already-present `cache4k`.
- **No mock library on the test classpath.** Test deps are exactly `kotlin-test-junit` and `ktor-server-test-host`. Every fake in this plan is hand-written. Follow the existing style in `src/test/kotlin/com/vandeas/service/RateLimitedMailQueueTest.kt` (`kotlin.test.*`, `runBlocking`, hand-written `MockMailer`).
- **`extraWarnings.set(true)` is on** in `build.gradle.kts`. Avoid unused variables and unused catch parameters — this plan uses `runCatching { }.getOrNull()` instead of `try/catch` where a caught exception would go unreferenced.
- **Never touch `com.vandeas.utils.Constants` from a test.** Its initializer runs `File(System.getenv("CONTACT_FORM_CONFIGS_FOLDER"))`, which throws `NullPointerException` when the env var is unset. `Config.toMailer()` reads `Constants.useMailQueue`, so **any test that reaches a mailer will NPE.** Every test in this plan is designed to return or throw before a mailer is constructed.
- **Backward compatibility is a hard requirement.** Every existing contact-form config file and every existing client must keep working untouched. New config and DTO fields are nullable or defaulted.
- Test naming follows the codebase convention: backtick-quoted descriptive names, e.g. `` fun `decodes millis into duration`() ``.

---

## File Structure

**Create:**

| File | Responsibility |
|---|---|
| `src/main/kotlin/com/vandeas/config/DurationMillisSerializer.kt` | `kotlin.time.Duration` ⇄ JSON millis |
| `src/main/kotlin/com/vandeas/dto/configs/honeypot/HoneypotConfig.kt` | Per-form honeypot settings |
| `src/main/kotlin/com/vandeas/dto/HoneypotSession.kt` | Response DTO for the session endpoint |
| `src/main/kotlin/com/vandeas/service/Honeypot.kt` | `Honeypot` interface + `HoneypotResult` |
| `src/main/kotlin/com/vandeas/service/impl/honeypot/HoneypotTokenPayload.kt` | Internal signed-payload shape |
| `src/main/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypot.kt` | Issuance + validation |
| `src/main/kotlin/com/vandeas/exception/HoneypotRejectedException.kt` | Maps to `403` |
| `src/main/kotlin/com/vandeas/logic/HoneypotLogic.kt` | Session-issuance logic interface |
| `src/main/kotlin/com/vandeas/logic/impl/HoneypotLogicImpl.kt` | Config lookup + issuance |

**Modify:**

| File | Change |
|---|---|
| `src/main/kotlin/com/vandeas/dto/configs/ContactFormConfig.kt` | Add `honeypot: HoneypotConfig?` |
| `src/main/kotlin/com/vandeas/dto/ContactForm.kt` | Add `honeypotToken` + `honeypot` |
| `src/main/kotlin/com/vandeas/logic/impl/MailLogicImpl.kt` | Honeypot step + silent-success helper |
| `src/main/kotlin/com/vandeas/plugins/KoinConfig.kt` | Register `Honeypot`, `HoneypotLogic` |
| `src/main/kotlin/com/vandeas/plugins/Routing.kt` | New route + `NoSuchElementException` → `404` |
| `README.md` | Endpoint, config block, client snippet |

**Test:** `DurationMillisSerializerTest`, `ContactFormConfigHoneypotTest`, `ContactFormHoneypotSerializationTest`, `HmacHoneypotTest`, `MailLogicImplHoneypotTest`.

**Not tested, deliberately:** the HTTP routes. `ktor-server-test-host` would boot the application module, which calls `configureKoin()` → `Constants` → NPE without env vars. The existing `ApplicationTest` doesn't test routes either. Route wiring is verified by compilation and the Bruno request in Task 8.

---

### Task 1: Duration-as-millis serializer

**Files:**
- Create: `src/main/kotlin/com/vandeas/config/DurationMillisSerializer.kt`
- Test: `src/test/kotlin/com/vandeas/config/DurationMillisSerializerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object DurationMillisSerializer : KSerializer<kotlin.time.Duration>` in package `com.vandeas.config`. Used via `@Serializable(with = DurationMillisSerializer::class)`.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/vandeas/config/DurationMillisSerializerTest.kt`:

```kotlin
package com.vandeas.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationMillisSerializerTest {

    @Serializable
    private data class Holder(
        @Serializable(with = DurationMillisSerializer::class)
        val value: Duration
    )

    @Test
    fun `encodes duration as whole milliseconds`() {
        assertEquals("""{"value":2000}""", Json.encodeToString(Holder(2.seconds)))
    }

    @Test
    fun `decodes millis into duration`() {
        assertEquals(30.minutes, Json.decodeFromString<Holder>("""{"value":1800000}""").value)
    }

    @Test
    fun `round trips through json`() {
        val original = Holder(90.seconds)
        assertEquals(original, Json.decodeFromString<Holder>(Json.encodeToString(original)))
    }

    @Test
    fun `truncates sub-millisecond precision`() {
        assertEquals("""{"value":1}""", Json.encodeToString(Holder(1500.microseconds)))
    }

    @Test
    fun `decodes zero`() {
        assertEquals(Duration.ZERO, Json.decodeFromString<Holder>("""{"value":0}""").value)
    }

    @Test
    fun `decodes large millis values without overflow`() {
        assertEquals(86_400_000L.milliseconds, Json.decodeFromString<Holder>("""{"value":86400000}""").value)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
export JAVA_HOME=/Users/lotuxpunk/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home
./gradlew test --tests 'com.vandeas.config.DurationMillisSerializerTest' --console=plain
```

Expected: compilation failure — `Unresolved reference: DurationMillisSerializer`.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/kotlin/com/vandeas/config/DurationMillisSerializer.kt`:

```kotlin
package com.vandeas.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Serializes a [Duration] as a whole number of milliseconds.
 *
 * Config files are hand-edited, so the JSON side stays a plain integer while Kotlin code
 * keeps a unit-safe [Duration]. Sub-millisecond precision is truncated.
 */
object DurationMillisSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DurationMillis", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Duration) {
        encoder.encodeLong(value.inWholeMilliseconds)
    }

    override fun deserialize(decoder: Decoder): Duration =
        decoder.decodeLong().milliseconds
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests 'com.vandeas.config.DurationMillisSerializerTest' --console=plain
```

Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/vandeas/config/DurationMillisSerializer.kt \
        src/test/kotlin/com/vandeas/config/DurationMillisSerializerTest.kt
git commit -m "feat(config): add Duration-as-millis serializer"
```

---

### Task 2: Honeypot config block

**Files:**
- Create: `src/main/kotlin/com/vandeas/dto/configs/honeypot/HoneypotConfig.kt`
- Modify: `src/main/kotlin/com/vandeas/dto/configs/ContactFormConfig.kt`
- Test: `src/test/kotlin/com/vandeas/dto/configs/ContactFormConfigHoneypotTest.kt`

**Interfaces:**
- Consumes: `DurationMillisSerializer` from Task 1.
- Produces: `HoneypotConfig(secretKey: String, fieldCount: Int = 2, minDwell: Duration = 2.seconds, maxAge: Duration = 30.minutes)` in package `com.vandeas.dto.configs.honeypot`. JSON keys are `secretKey`, `fieldCount`, `minDwellMillis`, `maxAgeMillis`. Also `ContactFormConfig.honeypot: HoneypotConfig?`.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/vandeas/dto/configs/ContactFormConfigHoneypotTest.kt`:

```kotlin
package com.vandeas.dto.configs

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ContactFormConfigHoneypotTest {

    private val configWithoutHoneypot = """
        {
          "provider": "RESEND",
          "id": "abc-123",
          "dailyLimit": 10,
          "destination": "john@example.com",
          "sender": "doe@example.com",
          "lang": "fr",
          "subjectTemplate": "New mail",
          "apiKey": "re_key",
          "captcha": { "provider": "KERBERUS", "secretKey": "kerberus-secret" }
        }
    """.trimIndent()

    @Test
    fun `existing config without a honeypot block still deserializes`() {
        val config = Json.decodeFromString<ContactFormConfig>(configWithoutHoneypot)
        assertNull(config.honeypot)
    }

    @Test
    fun `honeypot block deserializes with millis durations`() {
        val json = configWithoutHoneypot.replace(
            """"apiKey": "re_key",""",
            """"apiKey": "re_key",
               "honeypot": {
                 "secretKey": "hp-secret",
                 "fieldCount": 3,
                 "minDwellMillis": 5000,
                 "maxAgeMillis": 600000
               },"""
        )
        val honeypot = Json.decodeFromString<ContactFormConfig>(json).honeypot
        assertEquals("hp-secret", honeypot?.secretKey)
        assertEquals(3, honeypot?.fieldCount)
        assertEquals(5.seconds, honeypot?.minDwell)
        assertEquals(10.minutes, honeypot?.maxAge)
    }

    @Test
    fun `honeypot block applies defaults when only secretKey is given`() {
        val json = configWithoutHoneypot.replace(
            """"apiKey": "re_key",""",
            """"apiKey": "re_key",
               "honeypot": { "secretKey": "hp-secret" },"""
        )
        val honeypot = Json.decodeFromString<ContactFormConfig>(json).honeypot
        assertEquals(2, honeypot?.fieldCount)
        assertEquals(2.seconds, honeypot?.minDwell)
        assertEquals(30.minutes, honeypot?.maxAge)
    }

    @Test
    fun `smtp config also carries the honeypot block`() {
        val json = """
            {
              "provider": "SMTP",
              "id": "smtp-1",
              "dailyLimit": 5,
              "destination": "john@example.com",
              "sender": "doe@example.com",
              "lang": "en",
              "subjectTemplate": "New mail",
              "username": "user",
              "password": "pass",
              "smtpHost": "smtp.example.com",
              "captcha": { "provider": "KERBERUS", "secretKey": "kerberus-secret" },
              "honeypot": { "secretKey": "hp-secret", "minDwellMillis": 1000 }
            }
        """.trimIndent()
        val config = Json.decodeFromString<ContactFormConfig>(json)
        assertEquals(1.seconds, config.honeypot?.minDwell)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests 'com.vandeas.dto.configs.ContactFormConfigHoneypotTest' --console=plain
```

Expected: compilation failure — `Unresolved reference: honeypot`.

- [ ] **Step 3: Create the config class**

Create `src/main/kotlin/com/vandeas/dto/configs/honeypot/HoneypotConfig.kt`:

```kotlin
package com.vandeas.dto.configs.honeypot

import com.vandeas.config.DurationMillisSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Honeypot settings for a contact form. Absent from a config means the layer is disabled.
 *
 * @property secretKey HMAC-SHA256 signing key for this form's session tokens.
 * @property fieldCount How many hidden trap fields to issue per session.
 * @property minDwell A submission arriving sooner than this after issuance is treated as a bot.
 * @property maxAge How long an issued token stays valid.
 */
@Serializable
data class HoneypotConfig(
    val secretKey: String,
    val fieldCount: Int = 2,
    @SerialName("minDwellMillis")
    @Serializable(with = DurationMillisSerializer::class)
    val minDwell: Duration = 2.seconds,
    @SerialName("maxAgeMillis")
    @Serializable(with = DurationMillisSerializer::class)
    val maxAge: Duration = 30.minutes,
)
```

- [ ] **Step 4: Wire it into the config interface**

In `src/main/kotlin/com/vandeas/dto/configs/ContactFormConfig.kt`, add the import
`import com.vandeas.dto.configs.honeypot.HoneypotConfig`, then add to the sealed interface
after `val captcha: CaptchaConfig`:

```kotlin
    val honeypot: HoneypotConfig?
```

And add this as the **last** constructor parameter of both `ResendContactFormConfig` and
`SMTPContactFormConfig`:

```kotlin
    override val honeypot: HoneypotConfig? = null
```

The default is what keeps every existing config file deserializing unchanged.

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests 'com.vandeas.dto.configs.ContactFormConfigHoneypotTest' --console=plain
```

Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/vandeas/dto/configs/honeypot/HoneypotConfig.kt \
        src/main/kotlin/com/vandeas/dto/configs/ContactFormConfig.kt \
        src/test/kotlin/com/vandeas/dto/configs/ContactFormConfigHoneypotTest.kt
git commit -m "feat(config): add optional honeypot block to contact form configs"
```

---

### Task 3: Contact form request fields

**Files:**
- Modify: `src/main/kotlin/com/vandeas/dto/ContactForm.kt`
- Test: `src/test/kotlin/com/vandeas/dto/ContactFormHoneypotSerializationTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `ContactForm.honeypotToken: String?` and `ContactForm.honeypot: Map<String, String>` on the sealed interface, defaulted to `null` and `emptyMap()` in `GoogleRecaptchaContactForm` and `KerberusContactForm`.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/vandeas/dto/ContactFormHoneypotSerializationTest.kt`:

```kotlin
package com.vandeas.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactFormHoneypotSerializationTest {

    @Test
    fun `existing payload without honeypot fields still deserializes`() {
        val json = """
            {
              "captcha": "GOOGLE_RECAPTCHA",
              "id": "abc-123",
              "fullName": "John Doe",
              "email": "john@example.com",
              "content": "Hello",
              "recaptchaToken": "token"
            }
        """.trimIndent()
        val form = Json.decodeFromString<ContactForm>(json)
        assertNull(form.honeypotToken)
        assertTrue(form.honeypot.isEmpty())
    }

    @Test
    fun `payload with honeypot fields deserializes`() {
        val json = """
            {
              "captcha": "GOOGLE_RECAPTCHA",
              "id": "abc-123",
              "fullName": "John Doe",
              "email": "john@example.com",
              "content": "Hello",
              "recaptchaToken": "token",
              "honeypotToken": "payload.signature",
              "honeypot": { "a7f3kd": "", "qm2x9p": "" }
            }
        """.trimIndent()
        val form = Json.decodeFromString<ContactForm>(json)
        assertEquals("payload.signature", form.honeypotToken)
        assertEquals(mapOf("a7f3kd" to "", "qm2x9p" to ""), form.honeypot)
    }

    @Test
    fun `kerberus payload carries honeypot fields`() {
        val json = """
            {
              "captcha": "KERBERUS",
              "id": "abc-123",
              "fullName": "John Doe",
              "email": "john@example.com",
              "content": "Hello",
              "solution": { "id": "challenge-1", "solutions": [1, 2, 3], "expires": 0 },
              "honeypotToken": "payload.signature",
              "honeypot": { "a7f3kd": "filled-by-bot" }
            }
        """.trimIndent()
        val form = Json.decodeFromString<ContactForm>(json)
        assertEquals("filled-by-bot", form.honeypot["a7f3kd"])
    }
}
```

**Note on the third test:** the `solution` object must match `com.icure.kerberus.Solution`'s
actual fields. Before running, open the `Solution` class (`com.icure:kerberus:1.1.5`) — in
IntelliJ, or via
`unzip -p $(find ~/.gradle/caches -name 'kerberus-jvm-1.1.5.jar' | head -1) 'com/icure/kerberus/Solution.class' > /tmp/Solution.class && javap -p /tmp/Solution.class`
— and correct the JSON to match. If the shape is awkward to hand-write, delete this third
test; tests 1 and 2 already cover both new fields, and Task 6 exercises the Kerberus path.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests 'com.vandeas.dto.ContactFormHoneypotSerializationTest' --console=plain
```

Expected: compilation failure — `Unresolved reference: honeypotToken`.

- [ ] **Step 3: Add the fields**

In `src/main/kotlin/com/vandeas/dto/ContactForm.kt`, add to the sealed interface after
`val destinations: List<String>`:

```kotlin
    /** Signed session token from `GET /v1/mail/contact/{configId}/form-session`. */
    val honeypotToken: String?

    /** The issued honeypot field names and whatever the client submitted for them. */
    val honeypot: Map<String, String>
```

Then add these as the **last two** constructor parameters of both
`GoogleRecaptchaContactForm` and `KerberusContactForm`:

```kotlin
    override val honeypotToken: String? = null,
    override val honeypot: Map<String, String> = emptyMap(),
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests 'com.vandeas.dto.ContactFormHoneypotSerializationTest' --console=plain
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/vandeas/dto/ContactForm.kt \
        src/test/kotlin/com/vandeas/dto/ContactFormHoneypotSerializationTest.kt
git commit -m "feat(dto): accept honeypot token and fields on contact form requests"
```

---

### Task 4: Honeypot contract and token issuance

**Files:**
- Create: `src/main/kotlin/com/vandeas/service/Honeypot.kt`
- Create: `src/main/kotlin/com/vandeas/dto/HoneypotSession.kt`
- Create: `src/main/kotlin/com/vandeas/service/impl/honeypot/HoneypotTokenPayload.kt`
- Create: `src/main/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypot.kt`
- Test: `src/test/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypotIssueTest.kt`

**Interfaces:**
- Consumes: `HoneypotConfig` from Task 2.
- Produces:
  - `interface Honeypot { fun issue(configId: String, config: HoneypotConfig): HoneypotSession; suspend fun validate(config: HoneypotConfig, expectedConfigId: String, token: String?, submitted: Map<String, String>): HoneypotResult }`
  - `sealed interface HoneypotResult { data object Pass; data class InvalidToken(val reason: String); data object Trapped }`
  - `data class HoneypotSession(val token: String, val fields: List<String>, val issuedAt: Long)` in `com.vandeas.dto`
  - `internal data class HoneypotTokenPayload(val cid: String, val n: String, val iat: Long, val f: List<String>)`
  - `class HmacHoneypot(now: () -> Long = System::currentTimeMillis, randomBytes: (Int) -> ByteArray = <SecureRandom>)`

`validate` is fully implemented in Task 5; this task stubs it so the file compiles.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypotIssueTest.kt`:

```kotlin
package com.vandeas.service.impl.honeypot

import com.vandeas.dto.configs.honeypot.HoneypotConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Deterministic stand-in for SecureRandom.
 *
 * It MUST return different bytes on each call: [HmacHoneypot] regenerates field names until
 * it has [HoneypotConfig.fieldCount] distinct ones, so a fake returning a constant would
 * loop forever.
 */
internal class SequentialBytes : (Int) -> ByteArray {
    private var counter = 0
    override fun invoke(size: Int): ByteArray = ByteArray(size) { (counter++ % 251).toByte() }
}

class HmacHoneypotIssueTest {

    private val config = HoneypotConfig(secretKey = "hp-secret")

    @Test
    fun `issues the configured number of distinct field names`() {
        val session = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config.copy(fieldCount = 4))

        assertEquals(4, session.fields.size)
        assertEquals(4, session.fields.toSet().size)
    }

    @Test
    fun `field names are lowercase alphanumeric starting with a letter`() {
        val session = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)

        val pattern = Regex("^[a-z][a-z0-9]{7}$")
        session.fields.forEach { name ->
            assertTrue(pattern.matches(name), "field name '$name' does not match $pattern")
        }
    }

    @Test
    fun `session issuedAt is the current clock reading`() {
        val session = HmacHoneypot(now = { 1_774_483_200_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)

        assertEquals(1_774_483_200_000L, session.issuedAt)
    }

    @Test
    fun `token has exactly two dot-separated segments`() {
        val session = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)

        assertEquals(2, session.token.split('.').size)
    }

    @Test
    fun `two sessions get different tokens`() {
        val honeypot = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())

        assertNotEquals(
            honeypot.issue("abc-123", config).token,
            honeypot.issue("abc-123", config).token
        )
    }

    @Test
    fun `a different secret produces a different signature for the same payload`() {
        val first = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config)
        val second = HmacHoneypot(now = { 1_000L }, randomBytes = SequentialBytes())
            .issue("abc-123", config.copy(secretKey = "other-secret"))

        // Same clock and same deterministic randomness, so the payloads are identical...
        assertEquals(first.token.substringBefore('.'), second.token.substringBefore('.'))
        // ...but the signatures must differ.
        assertNotEquals(first.token.substringAfter('.'), second.token.substringAfter('.'))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests 'com.vandeas.service.impl.honeypot.HmacHoneypotIssueTest' --console=plain
```

Expected: compilation failure — `Unresolved reference: HmacHoneypot`.

- [ ] **Step 3: Create the response DTO**

Create `src/main/kotlin/com/vandeas/dto/HoneypotSession.kt`:

```kotlin
package com.vandeas.dto

import kotlinx.serialization.Serializable

/**
 * Issued honeypot session. The client renders one hidden input per entry in [fields] and
 * submits [token] plus those fields back with the contact form.
 *
 * @property issuedAt epoch millis, the same value the token carries internally.
 */
@Serializable
data class HoneypotSession(
    val token: String,
    val fields: List<String>,
    val issuedAt: Long,
)
```

- [ ] **Step 4: Create the service contract**

Create `src/main/kotlin/com/vandeas/service/Honeypot.kt`:

```kotlin
package com.vandeas.service

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig

interface Honeypot {

    /** Issues a session: random field names bound to a signed, single-use token. */
    fun issue(configId: String, config: HoneypotConfig): HoneypotSession

    /**
     * Verifies a submission against the token it carries.
     *
     * @param expectedConfigId the config id the submission claims; must match the token's.
     * @param submitted the honeypot field names and values the client sent back.
     */
    suspend fun validate(
        config: HoneypotConfig,
        expectedConfigId: String,
        token: String?,
        submitted: Map<String, String>,
    ): HoneypotResult
}

sealed interface HoneypotResult {
    /** Token valid, nonce consumed, all trap fields blank. */
    data object Pass : HoneypotResult

    /** Structurally bad, expired or replayed token — surfaced to the caller as 403. */
    data class InvalidToken(val reason: String) : HoneypotResult

    /** A trap field was filled or the form came back too fast — answered with a fake success. */
    data object Trapped : HoneypotResult
}
```

- [ ] **Step 5: Create the token payload**

Create `src/main/kotlin/com/vandeas/service/impl/honeypot/HoneypotTokenPayload.kt`:

```kotlin
package com.vandeas.service.impl.honeypot

import kotlinx.serialization.Serializable

/**
 * The signed half of a honeypot token. Kept `internal` so tests can forge tampered payloads.
 *
 * Field names are abbreviated to keep the token short.
 *
 * @property cid config id this token is bound to
 * @property n single-use nonce
 * @property iat issued-at, epoch millis
 * @property f the authoritative honeypot field names
 */
@Serializable
internal data class HoneypotTokenPayload(
    val cid: String,
    val n: String,
    val iat: Long,
    val f: List<String>,
)
```

- [ ] **Step 6: Implement issuance**

Create `src/main/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypot.kt`:

```kotlin
package com.vandeas.service.impl.honeypot

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.US_ASCII
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.hours

/**
 * Stateless-except-for-nonces honeypot.
 *
 * The field names a visitor was given travel inside an HMAC-signed token rather than being
 * stored server-side, so the server can trust them on the way back without remembering them.
 * The nonce is the one piece of state, and exists only to make a token single-use.
 *
 * @param now epoch-millis clock, injectable so tests can drive time.
 * @param randomBytes randomness source, injectable so tests are deterministic.
 */
class HmacHoneypot(
    private val now: () -> Long = System::currentTimeMillis,
    private val randomBytes: (Int) -> ByteArray = SecureRandom().let { secureRandom ->
        { size -> ByteArray(size).also(secureRandom::nextBytes) }
    },
) : Honeypot {

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val NONCE_BYTES = 16
        const val FIELD_NAME_LENGTH = 8
        const val FIELD_NAME_FIRST_CHARS = "abcdefghijklmnopqrstuvwxyz"
        const val FIELD_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

        /** Memory ceiling only — real expiry is enforced per-config from the token's `iat`. */
        val NONCE_TTL_CEILING = 1.hours
        const val NONCE_CACHE_MAX = 100_000L
    }

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    private val nonces = Cache.Builder<String, Unit>()
        .expireAfterWrite(NONCE_TTL_CEILING)
        .maximumCacheSize(NONCE_CACHE_MAX)
        .build()

    private val nonceMutex = Mutex()

    override fun issue(configId: String, config: HoneypotConfig): HoneypotSession {
        require(config.fieldCount > 0) { "honeypot fieldCount must be positive" }

        val issuedAt = now()
        val nonce = encoder.encodeToString(randomBytes(NONCE_BYTES))
        val fields = generateFieldNames(config.fieldCount)

        val encodedPayload = encoder.encodeToString(
            Json.encodeToString(HoneypotTokenPayload(cid = configId, n = nonce, iat = issuedAt, f = fields))
                .toByteArray(UTF_8)
        )

        nonces.put(nonce, Unit)

        return HoneypotSession(
            token = "$encodedPayload.${encoder.encodeToString(sign(encodedPayload, config.secretKey))}",
            fields = fields,
            issuedAt = issuedAt,
        )
    }

    override suspend fun validate(
        config: HoneypotConfig,
        expectedConfigId: String,
        token: String?,
        submitted: Map<String, String>,
    ): HoneypotResult = HoneypotResult.InvalidToken("not implemented")

    /**
     * HMAC over the ASCII bytes of the base64url segment, not the raw JSON — so verification
     * never depends on re-serializing the payload byte-identically.
     */
    private fun sign(encodedPayload: String, secretKey: String): ByteArray =
        Mac.getInstance(HMAC_ALGORITHM).apply {
            init(SecretKeySpec(secretKey.toByteArray(UTF_8), HMAC_ALGORITHM))
        }.doFinal(encodedPayload.toByteArray(US_ASCII))

    private fun generateFieldNames(count: Int): List<String> {
        val names = LinkedHashSet<String>(count)
        while (names.size < count) {
            names.add(randomFieldName())
        }
        return names.toList()
    }

    /**
     * No fixed prefix, so a bot cannot regex the trap fields out of the form, and nothing for
     * browser autofill to recognise. The modulo bias here is irrelevant — these names are
     * obfuscation, not a security parameter.
     */
    private fun randomFieldName(): String {
        val bytes = randomBytes(FIELD_NAME_LENGTH)
        return buildString(FIELD_NAME_LENGTH) {
            append(FIELD_NAME_FIRST_CHARS[(bytes[0].toInt() and 0xFF) % FIELD_NAME_FIRST_CHARS.length])
            for (index in 1 until FIELD_NAME_LENGTH) {
                append(FIELD_NAME_CHARS[(bytes[index].toInt() and 0xFF) % FIELD_NAME_CHARS.length])
            }
        }
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
./gradlew test --tests 'com.vandeas.service.impl.honeypot.HmacHoneypotIssueTest' --console=plain
```

Expected: PASS, 6 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/vandeas/dto/HoneypotSession.kt \
        src/main/kotlin/com/vandeas/service/Honeypot.kt \
        src/main/kotlin/com/vandeas/service/impl/honeypot/ \
        src/test/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypotIssueTest.kt
git commit -m "feat(honeypot): add Honeypot contract and HMAC session issuance"
```

---

### Task 5: Token validation

**Files:**
- Modify: `src/main/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypot.kt`
- Test: `src/test/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypotValidateTest.kt`

**Interfaces:**
- Consumes: everything from Task 4, including the `internal SequentialBytes` fake declared in `HmacHoneypotIssueTest.kt` (same package, so it is directly usable).
- Produces: a working `HmacHoneypot.validate`. No signature change.

This replaces the Task 4 stub with the eight ordered checks from the spec. Signature
verification happens **before** the payload is decoded, so unauthenticated JSON is never
parsed. The nonce is consumed **before** the trap checks, so tripping a trap still burns
the token.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypotValidateTest.kt`:

```kotlin
package com.vandeas.service.impl.honeypot

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.service.HoneypotResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HmacHoneypotValidateTest {

    private val config = HoneypotConfig(
        secretKey = "hp-secret",
        fieldCount = 2,
        minDwell = 2.seconds,
        maxAge = 30.minutes,
    )

    private val issuedAt = 1_774_483_200_000L
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder: Base64.Decoder = Base64.getUrlDecoder()

    /** Clock the test moves by hand. */
    private class MovableClock(var millis: Long) : () -> Long {
        override fun invoke(): Long = millis
    }

    private fun honeypotAt(clock: MovableClock) = HmacHoneypot(clock, SequentialBytes())

    /** Issues a session, then advances the clock past `minDwell` so the happy path passes. */
    private fun issueAndSettle(
        clock: MovableClock = MovableClock(issuedAt),
        configId: String = "abc-123",
        honeypotConfig: HoneypotConfig = config,
    ): Triple<HmacHoneypot, MovableClock, HoneypotSession> {
        val honeypot = honeypotAt(clock)
        val session = honeypot.issue(configId, honeypotConfig)
        clock.millis = issuedAt + 8_000
        return Triple(honeypot, clock, session)
    }

    private fun blankFor(fields: List<String>) = fields.associateWith { "" }

    @Test
    fun `accepts a well-formed submission`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        assertEquals(
            HoneypotResult.Pass,
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a null token`() = runBlocking {
        val (honeypot, _, _) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(honeypot.validate(config, "abc-123", null, emptyMap()))
    }

    @Test
    fun `rejects a blank token`() = runBlocking {
        val (honeypot, _, _) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(honeypot.validate(config, "abc-123", "   ", emptyMap()))
    }

    @Test
    fun `rejects a token with no separator`() = runBlocking {
        val (honeypot, _, _) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", "notatoken", emptyMap())
        )
    }

    @Test
    fun `rejects a token with three segments`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", "${session.token}.extra", blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a non-base64 signature`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val forged = "${session.token.substringBefore('.')}.!!!not-base64!!!"

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", forged, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a tampered signature`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val signature = session.token.substringAfter('.')
        val flipped = signature.replaceRange(0, 1, if (signature[0] == 'A') "B" else "A")
        val forged = "${session.token.substringBefore('.')}.$flipped"

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", forged, blankFor(session.fields))
        )
    }

    /** The attack the signature exists to stop: editing the trap-field list out of the token. */
    @Test
    fun `rejects a payload edited to drop the trap fields`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        val original = Json.decodeFromString<HoneypotTokenPayload>(
            String(decoder.decode(session.token.substringBefore('.')), UTF_8)
        )
        val edited = encoder.encodeToString(
            Json.encodeToString(original.copy(f = emptyList())).toByteArray(UTF_8)
        )
        val forged = "$edited.${session.token.substringAfter('.')}"

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", forged, emptyMap())
        )
    }

    @Test
    fun `rejects a token signed with a different secret`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(
                config.copy(secretKey = "wrong-secret"),
                "abc-123",
                session.token,
                blankFor(session.fields)
            )
        )
    }

    @Test
    fun `rejects a token issued for a different config`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle(configId = "other-form")

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects an expired token`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val (honeypot, _, session) = issueAndSettle(clock)
        clock.millis = issuedAt + 30.minutes.inWholeMilliseconds + 1

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `accepts a token exactly at maxAge`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val (honeypot, _, session) = issueAndSettle(clock)
        clock.millis = issuedAt + 30.minutes.inWholeMilliseconds

        assertEquals(
            HoneypotResult.Pass,
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a future-dated token`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val (honeypot, _, session) = issueAndSettle(clock)
        clock.millis = issuedAt - 5_000

        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejects a replayed token`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val fields = blankFor(session.fields)

        assertEquals(HoneypotResult.Pass, honeypot.validate(config, "abc-123", session.token, fields))
        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, fields)
        )
    }

    @Test
    fun `rejects a token this instance never issued`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val session = honeypotAt(clock).issue("abc-123", config)
        clock.millis = issuedAt + 8_000
        val otherInstance = honeypotAt(clock)

        assertIs<HoneypotResult.InvalidToken>(
            otherInstance.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `traps a submission faster than minDwell`() = runBlocking {
        val clock = MovableClock(issuedAt)
        val honeypot = honeypotAt(clock)
        val session = honeypot.issue("abc-123", config)
        clock.millis = issuedAt + 500

        assertEquals(
            HoneypotResult.Trapped,
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `traps a filled trap field`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val filled = blankFor(session.fields) + (session.fields.first() to "http://spam.example")

        assertEquals(HoneypotResult.Trapped, honeypot.validate(config, "abc-123", session.token, filled))
    }

    /** Blank means `isBlank()`, so whitespace is blank and must pass — not trap. */
    @Test
    fun `treats a whitespace-only trap field as blank`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val whitespace = blankFor(session.fields) + (session.fields.first() to "   ")

        assertEquals(HoneypotResult.Pass, honeypot.validate(config, "abc-123", session.token, whitespace))
    }

    @Test
    fun `traps a missing trap field`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val incomplete = blankFor(session.fields) - session.fields.first()

        assertEquals(HoneypotResult.Trapped, honeypot.validate(config, "abc-123", session.token, incomplete))
    }

    @Test
    fun `ignores undeclared extra fields`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val extra = blankFor(session.fields) + ("someOtherInput" to "legitimate value")

        assertEquals(HoneypotResult.Pass, honeypot.validate(config, "abc-123", session.token, extra))
    }

    @Test
    fun `a trapped submission still consumes its token`() = runBlocking {
        val (honeypot, _, session) = issueAndSettle()
        val filled = blankFor(session.fields) + (session.fields.first() to "spam")

        assertEquals(HoneypotResult.Trapped, honeypot.validate(config, "abc-123", session.token, filled))
        // Second attempt with correct blanks must fail: the nonce is already gone.
        assertIs<HoneypotResult.InvalidToken>(
            honeypot.validate(config, "abc-123", session.token, blankFor(session.fields))
        )
    }

    @Test
    fun `rejection reasons are non-empty`() = runBlocking {
        val (honeypot, _, _) = issueAndSettle()
        val result = honeypot.validate(config, "abc-123", null, emptyMap())

        assertTrue(assertIs<HoneypotResult.InvalidToken>(result).reason.isNotBlank())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests 'com.vandeas.service.impl.honeypot.HmacHoneypotValidateTest' --console=plain
```

Expected: FAIL — the stub returns `InvalidToken("not implemented")`, so every `Pass` and
`Trapped` assertion fails.

- [ ] **Step 3: Replace the stub**

In `HmacHoneypot.kt`, replace the `validate` stub with:

```kotlin
    override suspend fun validate(
        config: HoneypotConfig,
        expectedConfigId: String,
        token: String?,
        submitted: Map<String, String>,
    ): HoneypotResult {
        // 1 — structure
        if (token.isNullOrBlank()) return HoneypotResult.InvalidToken("missing honeypot token")
        val segments = token.split('.')
        if (segments.size != 2) return HoneypotResult.InvalidToken("malformed honeypot token")
        val (encodedPayload, encodedSignature) = segments

        // 2 — authenticate before decoding anything the client controls
        val providedSignature = runCatching { decoder.decode(encodedSignature) }.getOrNull()
            ?: return HoneypotResult.InvalidToken("malformed honeypot signature")
        if (!MessageDigest.isEqual(sign(encodedPayload, config.secretKey), providedSignature)) {
            return HoneypotResult.InvalidToken("honeypot signature mismatch")
        }

        // 3 — now the payload is trustworthy enough to parse
        val payload = runCatching {
            Json.decodeFromString<HoneypotTokenPayload>(String(decoder.decode(encodedPayload), UTF_8))
        }.getOrNull() ?: return HoneypotResult.InvalidToken("malformed honeypot payload")

        // 4 — bound to one form
        if (payload.cid != expectedConfigId) {
            return HoneypotResult.InvalidToken("honeypot token issued for a different config")
        }

        // 5 — freshness
        val age = (now() - payload.iat).milliseconds
        if (age.isNegative()) return HoneypotResult.InvalidToken("honeypot token issued in the future")
        if (age > config.maxAge) return HoneypotResult.InvalidToken("honeypot token expired")

        // 6 — single use. Consumed before the trap checks, so tripping a trap burns the token.
        val consumed = nonceMutex.withLock {
            (nonces.get(payload.n) != null).also { present -> if (present) nonces.invalidate(payload.n) }
        }
        if (!consumed) return HoneypotResult.InvalidToken("honeypot token already used or unknown")

        // 7 — no human fills a form this fast
        if (age < config.minDwell) return HoneypotResult.Trapped

        // 8 — every declared trap field must have come back present and blank
        if (payload.f.any { field -> submitted[field]?.isBlank() != true }) return HoneypotResult.Trapped

        return HoneypotResult.Pass
    }
```

Add these imports to the file:

```kotlin
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import kotlin.time.Duration.Companion.milliseconds
```

`MessageDigest.isEqual` is the constant-time comparison — a plain `==` on `ByteArray`
would compare references, and `contentEquals` would short-circuit on the first differing
byte, leaking timing information.

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests 'com.vandeas.service.impl.honeypot.HmacHoneypotValidateTest' --console=plain
```

Expected: PASS, 22 tests.

- [ ] **Step 5: Run the whole suite**

```bash
./gradlew test --console=plain
```

Expected: PASS. Total should now be 30 baseline + Tasks 1–5 additions.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypot.kt \
        src/test/kotlin/com/vandeas/service/impl/honeypot/HmacHoneypotValidateTest.kt
git commit -m "feat(honeypot): validate signed single-use tokens and trap fields"
```

---

### Task 6: Wire the honeypot into contact form handling

**Files:**
- Create: `src/main/kotlin/com/vandeas/exception/HoneypotRejectedException.kt`
- Modify: `src/main/kotlin/com/vandeas/logic/impl/MailLogicImpl.kt`
- Test: `src/test/kotlin/com/vandeas/logic/impl/MailLogicImplHoneypotTest.kt`

**Interfaces:**
- Consumes: `Honeypot`, `HoneypotResult` (Task 4); `ContactFormConfig.honeypot` (Task 2); `ContactForm.honeypotToken` / `.honeypot` (Task 3).
- Produces: `MailLogicImpl` constructor gains a fourth parameter `honeypot: Honeypot`. **Task 7 must update the Koin registration accordingly.** New `HoneypotRejectedException(reason: String) : RuntimeException`.

**Why these tests avoid mailers:** every case below returns or throws before
`config.toMailer()` is reached, because that call touches `Constants` and would NPE. The
two "layer is skipped" / "layer passes" cases prove ordering by denying the daily limiter
and asserting `DailyLimitExceededException` — that is reached after the honeypot step but
still before any mailer.

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/vandeas/logic/impl/MailLogicImplHoneypotTest.kt`:

```kotlin
package com.vandeas.logic.impl

import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.ResendContactFormConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.dto.configs.honeypot.HoneypotConfig
import com.vandeas.entities.MailSendStatus
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.HoneypotRejectedException
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.DailyLimiter
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MailLogicImplHoneypotTest {

    private val honeypotConfig = HoneypotConfig(secretKey = "hp-secret")

    private fun config(honeypot: HoneypotConfig? = honeypotConfig) = ResendContactFormConfig(
        id = "abc-123",
        dailyLimit = 10,
        destination = "owner@example.com",
        sender = "noreply@example.com",
        lang = "en",
        subjectTemplate = "New mail",
        apiKey = "re_key",
        captcha = KerberusConfig(secretKey = "kerberus-secret"),
        honeypot = honeypot,
    )

    private fun form(destinations: List<String> = emptyList()) = GoogleRecaptchaContactForm(
        id = "abc-123",
        fullName = "John Doe",
        email = "john@example.com",
        content = "Hello",
        destinations = destinations,
        recaptchaToken = "recaptcha-token",
        honeypotToken = "payload.signature",
        honeypot = mapOf("a7f3kd" to ""),
    )

    private class FakeContactFormConfigs(private val config: ContactFormConfig) :
        ConfigDirectory<ContactFormConfig> {
        override fun get(id: String): ContactFormConfig =
            config.takeIf { it.id == id } ?: throw NoSuchElementException("Config with id $id not found")

        override fun getAll(): Map<String, ContactFormConfig> = mapOf(config.id to config)
        override fun getTemplate(id: String): String = "<p>{{form.content}}</p>"
    }

    private class UnusedMailConfigs : ConfigDirectory<MailConfig> {
        override fun get(id: String): MailConfig = error("mail configs are not used in these tests")
        override fun getAll(): Map<String, MailConfig> = emptyMap()
        override fun getTemplate(id: String): String = error("mail configs are not used in these tests")
    }

    private class RecordingLimiter(private val allow: Boolean = true) : DailyLimiter {
        val recorded = mutableListOf<String>()
        override fun canSendMail(config: ContactFormConfig): Boolean = allow
        override fun recordMailSent(config: ContactFormConfig): Boolean {
            recorded.add(config.id)
            return true
        }
    }

    private class StubHoneypot(private val result: HoneypotResult) : Honeypot {
        var validateCalls = 0
        override fun issue(configId: String, config: HoneypotConfig) =
            HoneypotSession(token = "t", fields = listOf("a7f3kd"), issuedAt = 0L)

        override suspend fun validate(
            config: HoneypotConfig,
            expectedConfigId: String,
            token: String?,
            submitted: Map<String, String>,
        ): HoneypotResult {
            validateCalls++
            return result
        }
    }

    private class ExplodingHoneypot : Honeypot {
        override fun issue(configId: String, config: HoneypotConfig) =
            error("issue must not be called")

        override suspend fun validate(
            config: HoneypotConfig,
            expectedConfigId: String,
            token: String?,
            submitted: Map<String, String>,
        ): HoneypotResult = error("validate must not be called when honeypot is not configured")
    }

    private fun logic(
        contactFormConfig: ContactFormConfig,
        honeypot: Honeypot,
        limiter: DailyLimiter,
    ) = MailLogicImpl(UnusedMailConfigs(), FakeContactFormConfigs(contactFormConfig), limiter, honeypot)

    @Test
    fun `a trapped submission reports success without sending or consuming quota`() = runBlocking {
        val limiter = RecordingLimiter()
        val result = logic(config(), StubHoneypot(HoneypotResult.Trapped), limiter)
            .sendContactForm(form())

        assertEquals(MailSendStatus.SENT, result.status)
        assertEquals(listOf("owner@example.com"), result.sent)
        assertTrue(result.failed.isEmpty())
        assertTrue(limiter.recorded.isEmpty(), "a trapped submission must not consume daily quota")
    }

    @Test
    fun `a trapped submission echoes explicit destinations`() = runBlocking {
        val result = logic(config(), StubHoneypot(HoneypotResult.Trapped), RecordingLimiter())
            .sendContactForm(form(destinations = listOf("a@example.com", "b@example.com")))

        assertEquals(listOf("a@example.com", "b@example.com"), result.sent)
    }

    @Test
    fun `an invalid token is rejected`() = runBlocking {
        val honeypot = StubHoneypot(HoneypotResult.InvalidToken("honeypot signature mismatch"))

        val failure = assertFailsWith<HoneypotRejectedException> {
            logic(config(), honeypot, RecordingLimiter()).sendContactForm(form())
        }
        assertEquals("honeypot signature mismatch", failure.message)
    }

    @Test
    fun `the honeypot layer is skipped when the config has no honeypot block`() = runBlocking {
        // Limiter denies, so the flow throws after the honeypot step and before any mailer.
        assertFailsWith<DailyLimitExceededException> {
            logic(config(honeypot = null), ExplodingHoneypot(), RecordingLimiter(allow = false))
                .sendContactForm(form())
        }
    }

    @Test
    fun `a passing honeypot falls through to the daily limit check`() = runBlocking {
        val honeypot = StubHoneypot(HoneypotResult.Pass)
        val limiter = RecordingLimiter(allow = false)

        assertFailsWith<DailyLimitExceededException> {
            logic(config(), honeypot, limiter).sendContactForm(form())
        }
        assertEquals(1, honeypot.validateCalls)
        assertTrue(limiter.recorded.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests 'com.vandeas.logic.impl.MailLogicImplHoneypotTest' --console=plain
```

Expected: compilation failure — `Unresolved reference: HoneypotRejectedException`, and
`MailLogicImpl` takes 3 arguments not 4.

- [ ] **Step 3: Create the exception**

Create `src/main/kotlin/com/vandeas/exception/HoneypotRejectedException.kt`:

```kotlin
package com.vandeas.exception

/**
 * Raised when a contact form submission carries a structurally bad, expired or replayed
 * honeypot token. Surfaced as 403 — unlike a tripped trap, which is answered with a
 * response indistinguishable from success.
 */
class HoneypotRejectedException(reason: String) : RuntimeException(reason)
```

- [ ] **Step 4: Add the honeypot step to `MailLogicImpl`**

In `src/main/kotlin/com/vandeas/logic/impl/MailLogicImpl.kt`:

Add imports:

```kotlin
import com.vandeas.exception.HoneypotRejectedException
import com.vandeas.service.Honeypot
import com.vandeas.service.HoneypotResult
import org.slf4j.LoggerFactory
```

Add the constructor parameter:

```kotlin
class MailLogicImpl(
    private val mailConfigHandler: ConfigDirectory<MailConfig>,
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val limiter: DailyLimiter,
    private val honeypot: Honeypot,
) : MailLogic {
```

Add to the existing `companion object`:

```kotlin
        private val logger = LoggerFactory.getLogger(MailLogicImpl::class.java)
```

Insert the honeypot step at the very top of `sendContactForm`, immediately after the
`config` lookup and **before** the `limiter.canSendMail` check:

```kotlin
        config.honeypot?.let { honeypotConfig ->
            when (val result = honeypot.validate(honeypotConfig, form.id, form.honeypotToken, form.honeypot)) {
                is HoneypotResult.InvalidToken -> throw HoneypotRejectedException(result.reason)
                HoneypotResult.Trapped -> {
                    logger.warn("Honeypot trapped a contact form submission for config {}", config.id)
                    return SendOperationResult(sent = form.resolveDestinations(config))
                }
                HoneypotResult.Pass -> Unit
            }
        }
```

Add this private helper to the class — the destination-resolution logic is currently
inlined in `sendContactForm`'s `mailer.sendEmailsWithRetry` call, and the fake-success
response needs the identical rule:

```kotlin
    private fun ContactForm.resolveDestinations(config: ContactFormConfig): List<String> =
        destinations.takeIf { it.isNotEmpty() } ?: listOf(config.destination)
```

Then simplify the existing send call to use it, replacing the
`form.destinations.takeIf { it.isNotEmpty() }?.map { … } ?: listOf(Mail(…))` expression:

```kotlin
        return mailer.sendEmailsWithRetry(
            mails = form.resolveDestinations(config).map { destination ->
                Mail(
                    from = config.sender,
                    to = destination,
                    subject = subject,
                    content = content
                )
            },
            maxRetries = 3,
            retryDelayMs = 1000L
        )
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests 'com.vandeas.logic.impl.MailLogicImplHoneypotTest' --console=plain
```

Expected: PASS, 5 tests. `KoinConfig.kt` will not compile yet — that is Task 7. If the
Gradle build fails on `KoinConfig.kt` before running tests, do Task 7 Step 2 now and
return here.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/vandeas/exception/HoneypotRejectedException.kt \
        src/main/kotlin/com/vandeas/logic/impl/MailLogicImpl.kt \
        src/test/kotlin/com/vandeas/logic/impl/MailLogicImplHoneypotTest.kt
git commit -m "feat(mail): check honeypot before daily limit on contact form submissions"
```

---

### Task 7: Session endpoint, DI wiring, and status codes

**Files:**
- Create: `src/main/kotlin/com/vandeas/logic/HoneypotLogic.kt`
- Create: `src/main/kotlin/com/vandeas/logic/impl/HoneypotLogicImpl.kt`
- Modify: `src/main/kotlin/com/vandeas/plugins/KoinConfig.kt`
- Modify: `src/main/kotlin/com/vandeas/plugins/Routing.kt`

**Interfaces:**
- Consumes: `Honeypot` / `HmacHoneypot` (Task 4), `ContactFormConfig.honeypot` (Task 2), `HoneypotRejectedException` and the 4-arg `MailLogicImpl` (Task 6).
- Produces: `interface HoneypotLogic { fun getSession(configId: String): HoneypotSession }` and route `GET /v1/mail/contact/{configId}/form-session`.

`getSession` is deliberately **not** `suspend`: both `ConfigDirectory.get` and
`Honeypot.issue` are synchronous. (`KerberusLogic.getChallenge` is `suspend` only because
`KerberusCaptcha.get` is.)

No unit test: exercising the route needs `ktor-server-test-host`, which boots the
application module → `configureKoin()` → `Constants` → NPE without env vars. Verification
is compilation plus the manual Bruno request in Task 8.

- [ ] **Step 1: Create the logic layer**

Create `src/main/kotlin/com/vandeas/logic/HoneypotLogic.kt`:

```kotlin
package com.vandeas.logic

import com.vandeas.dto.HoneypotSession

interface HoneypotLogic {
    /**
     * @throws NoSuchElementException if no contact form config has this id.
     * @throws IllegalArgumentException if the config has no honeypot block.
     */
    fun getSession(configId: String): HoneypotSession
}
```

Create `src/main/kotlin/com/vandeas/logic/impl/HoneypotLogicImpl.kt`:

```kotlin
package com.vandeas.logic.impl

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.logic.HoneypotLogic
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.Honeypot

class HoneypotLogicImpl(
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val honeypot: Honeypot,
) : HoneypotLogic {
    override fun getSession(configId: String): HoneypotSession {
        val config = contactFormConfigHandler.get(configId)
        val honeypotConfig = requireNotNull(config.honeypot) {
            "Contact form config $configId does not have honeypot configured"
        }
        return honeypot.issue(configId, honeypotConfig)
    }
}
```

- [ ] **Step 2: Register in Koin**

In `src/main/kotlin/com/vandeas/plugins/KoinConfig.kt`, add imports:

```kotlin
import com.vandeas.logic.HoneypotLogic
import com.vandeas.logic.impl.HoneypotLogicImpl
import com.vandeas.service.Honeypot
import com.vandeas.service.impl.honeypot.HmacHoneypot
```

Add to `appModule`:

```kotlin
    single<Honeypot> {
        HmacHoneypot()
    }
    single<HoneypotLogic> {
        HoneypotLogicImpl(get(named("contactFormConfig")), get())
    }
```

And update the existing `MailLogic` registration to pass the new fourth argument:

```kotlin
    single<MailLogic> {
        MailLogicImpl(get(named("mailConfig")), get(named("contactFormConfig")), get(), get())
    }
```

- [ ] **Step 3: Add the route and fix the status codes**

In `src/main/kotlin/com/vandeas/plugins/Routing.kt`, add imports:

```kotlin
import com.vandeas.exception.HoneypotRejectedException
import com.vandeas.logic.HoneypotLogic
```

Add the injection next to the existing two:

```kotlin
    val honeypotLogic by inject<HoneypotLogic>()
```

Inside `route("/mail") { … }`, add this route before the existing `post("/contact")`:

```kotlin
                get("/contact/{configId}/form-session") {
                    try {
                        val configId = call.parameters["configId"]
                            ?: throw IllegalArgumentException("Missing configId path parameter")

                        call.respond(HttpStatusCode.OK, honeypotLogic.getSession(configId))
                    } catch (e: Exception) {
                        when (e) {
                            is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, e.message ?: "")
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            else -> {
                                application.log.error("Failed to issue honeypot session: ${e.message}")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }
```

Then extend the `when (e)` inside the existing `post("/contact")` handler. Add these two
branches **above** the `is IllegalArgumentException` branch:

```kotlin
                            is HoneypotRejectedException -> call.respond(HttpStatusCode.Forbidden, e.message ?: "")
                            is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, e.message ?: "")
```

The `NoSuchElementException` branch is the targeted fix from the spec: `ConfigDirectory.get`
throws it for an unknown id, and without a branch it fell through to `else` → **500**.
Order matters — `NoSuchElementException` must be listed before any broader branch.

- [ ] **Step 4: Verify the whole build and suite**

```bash
./gradlew build --console=plain
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/vandeas/logic/HoneypotLogic.kt \
        src/main/kotlin/com/vandeas/logic/impl/HoneypotLogicImpl.kt \
        src/main/kotlin/com/vandeas/plugins/KoinConfig.kt \
        src/main/kotlin/com/vandeas/plugins/Routing.kt
git commit -m "feat(api): add honeypot form-session endpoint and map missing configs to 404"
```

---

### Task 8: Documentation and manual verification

**Files:**
- Modify: `README.md`
- Create: `.bruno/Hermes/Honeypot Form Session.bru`
- Modify: `docs/superpowers/specs/2026-07-26-contact-form-honeypot-design.md`

**Interfaces:**
- Consumes: the finished feature.
- Produces: no code.

- [ ] **Step 1: Document the config block**

In `README.md`, in the `### Contact Form` section, after the two example config files, add:

````markdown
##### Honeypot (optional)

Add a `honeypot` block to a contact form config to enable spam filtering with randomized
hidden fields bound to an HMAC-signed single-use token. Omit the block to disable it.

```json
"honeypot": {
    "secretKey": "<A_LONG_RANDOM_STRING>",
    "fieldCount": 2,
    "minDwellMillis": 2000,
    "maxAgeMillis": 1800000
}
```

| Field | Default | Description |
|:------|:--------|:------------|
| `secretKey` | **required** | HMAC-SHA256 signing key for this form's tokens |
| `fieldCount` | `2` | Number of hidden trap fields issued per session |
| `minDwellMillis` | `2000` | Submissions faster than this are treated as bots |
| `maxAgeMillis` | `1800000` | How long an issued token stays valid |

The honeypot stacks with captcha — a form can use either, both, or neither.
````

- [ ] **Step 2: Document the endpoint**

In `README.md`, in the `### API Reference` section, immediately before
`#### Send contact form using contact form configuration`, add:

````markdown
#### Get a honeypot form session

**GET** `/v1/mail/contact/{configId}/form-session`

Required before submitting a contact form whose config has a `honeypot` block. Returns the
hidden field names to render and the signed token to submit back. Each token is valid for
a single submission.

##### Response

```json
{
    "token": "eyJjaWQiOiJhYmMtMTIzIi...<payload>.<signature>",
    "fields": ["a7f3kd", "qm2x9p"],
    "issuedAt": 1774483200000
}
```

| Status | Meaning |
|:-------|:--------|
| `200`  | Session issued |
| `400`  | Config exists but has no `honeypot` block |
| `404`  | No contact form config with that id |

##### Client integration

```html
<form id="contact">
  <input name="fullName" required>
  <input name="email" type="email" required>
  <textarea name="content" required></textarea>
  <div id="hp"></div>
</form>

<script>
const CONFIG_ID = "your-config-id";
let session;

// Fetch on page load so the minDwell timer starts when the visitor arrives.
fetch(`https://hermes.example.com/v1/mail/contact/${CONFIG_ID}/form-session`)
  .then(r => r.json())
  .then(s => {
    session = s;
    document.getElementById("hp").innerHTML = s.fields.map(name =>
      `<input name="${name}" autocomplete="off" tabindex="-1" aria-hidden="true"
              style="position:absolute;left:-9999px">`
    ).join("");
  });

document.getElementById("contact").addEventListener("submit", async event => {
  event.preventDefault();
  const data = new FormData(event.target);
  const honeypot = {};
  session.fields.forEach(name => honeypot[name] = data.get(name) ?? "");

  await fetch("https://hermes.example.com/v1/mail/contact", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      captcha: "GOOGLE_RECAPTCHA",
      id: CONFIG_ID,
      fullName: data.get("fullName"),
      email: data.get("email"),
      content: data.get("content"),
      recaptchaToken: await grecaptcha.execute(),
      honeypotToken: session.token,
      honeypot
    })
  });
});
</script>
```

Hide the trap fields with off-screen positioning rather than `type="hidden"` or
`display:none` — some bots skip both. Always set `autocomplete="off"`, or a browser may
autofill a trap field and get a real enquiry silently discarded.
````

- [ ] **Step 3: Add the new body parameters to the contact form table**

In `README.md`, in the `POST /v1/mail/contact` **Body** table, add two rows:

```markdown
| `honeypotToken`  | `string` | Required when the config has a `honeypot` block. Token from the form-session endpoint |
| `honeypot`       | `object` | Required when the config has a `honeypot` block. Map of the issued field names to their submitted values |
```

- [ ] **Step 4: Add the Bruno request**

Create `.bruno/Hermes/Honeypot Form Session.bru` — open the existing
`.bruno/Hermes/Kerberus Challenge.bru` first and mirror its exact structure, `meta` block
and `seq` numbering (use the next unused `seq`):

```
meta {
  name: Honeypot Form Session
  type: http
  seq: 8
}

get {
  url: {{baseUrl}}/v1/mail/contact/{{configId}}/form-session
  body: none
  auth: none
}
```

If `Kerberus Challenge.bru` uses different variable names than `baseUrl` / `configId`, use
its names instead.

- [ ] **Step 5: Sync the spec with what was actually built**

Two details in the spec drifted during implementation. Update
`docs/superpowers/specs/2026-07-26-contact-form-honeypot-design.md`:

1. In the **Modules** section, the note says `HoneypotLogic` "mirrors `KerberusLogic`" and
   implies `suspend`. Replace with: `getSession` is not `suspend` — neither
   `ConfigDirectory.get` nor `Honeypot.issue` suspends.
2. In the **Testing** section, replace the four `MailLogicImplHoneypotTest` bullets with
   the five cases actually implemented, and add the reason the other shapes are impossible:
   `Config.toMailer()` reads `Constants`, whose initializer NPEs when the config-folder env
   vars are unset, so no test may reach a mailer. Note that route-level tests are excluded
   for the same reason.

- [ ] **Step 6: Manual smoke test**

```bash
export JAVA_HOME=/Users/lotuxpunk/Library/Java/JavaVirtualMachines/corretto-21.0.8/Contents/Home
export CONTACT_FORM_CONFIGS_FOLDER=/tmp/hermes-smoke/contact
export MAIL_CONFIGS_FOLDER=/tmp/hermes-smoke/mail
export TEMPLATES_FOLDER=/tmp/hermes-smoke/templates
mkdir -p "$CONTACT_FORM_CONFIGS_FOLDER" "$MAIL_CONFIGS_FOLDER" "$TEMPLATES_FOLDER"

cat > "$CONTACT_FORM_CONFIGS_FOLDER/smoke.json" <<'JSON'
{
  "provider": "RESEND",
  "id": "smoke-form",
  "dailyLimit": 10,
  "destination": "owner@example.com",
  "sender": "noreply@example.com",
  "lang": "en",
  "subjectTemplate": "New mail from {{form.fullName}}",
  "apiKey": "re_not_a_real_key",
  "captcha": { "provider": "KERBERUS", "secretKey": "kerberus-secret" },
  "honeypot": { "secretKey": "honeypot-secret", "minDwellMillis": 2000 }
}
JSON

echo '<p>{{form.content}}</p>' > "$TEMPLATES_FOLDER/smoke-form"
./gradlew run --console=plain
```

In a second terminal, verify each status code:

```bash
# 200 — issues a session
curl -s localhost:8080/v1/mail/contact/smoke-form/form-session

# 404 — unknown config (this is the NoSuchElementException fix; was 500 before)
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/v1/mail/contact/nope/form-session

# 403 — submitting with no honeypot token
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/v1/mail/contact \
  -H 'Content-Type: application/json' \
  -d '{"captcha":"KERBERUS","id":"smoke-form","fullName":"J","email":"j@e.com","content":"hi","solution":{}}'
```

Expected: a JSON session with two 8-character field names, then `404`, then `403`.

The template filename must equal the config `id` with no extension —
`FileHandlerImpl.getFileContent` is called with the raw config id. If the smoke test fails
on a missing template, list `$TEMPLATES_FOLDER` and match the name the log reports.

- [ ] **Step 7: Commit**

```bash
git add README.md '.bruno/Hermes/Honeypot Form Session.bru' \
        docs/superpowers/specs/2026-07-26-contact-form-honeypot-design.md
git commit -m "docs: document honeypot config, form-session endpoint and client integration"
```

---

## Self-Review

**Spec coverage:**

| Spec section | Task |
|---|---|
| Token format, base64url, HMAC over the encoded segment | 4 (sign), 5 (verify) |
| Payload shape `{cid, n, iat, f}` | 4 |
| Validation order, all 8 checks | 5 |
| Signature verified before payload decoded | 5 (step 2 precedes step 3) |
| Nonce consumed before trap checks | 5 (step 6 precedes 7–8) |
| Silent success response shape | 6 |
| Trapped submissions consume no daily quota | 6 (asserted in test 1) |
| `Honeypot` / `HoneypotResult` / `HoneypotSession` | 4 |
| `HmacHoneypot` injectable clock + randomness | 4 |
| Field-name generation, opaque, distinct | 4 |
| Nonce cache: TTL ceiling, size cap, `Mutex` | 4 (cache), 5 (`Mutex` use) |
| `HoneypotConfig` with `Duration`-as-millis | 1, 2 |
| Backward compatibility of config and DTO | 2, 3 |
| `GET …/form-session` endpoint + status codes | 7 |
| `POST /v1/mail/contact` new fields | 3, 6 |
| `NoSuchElementException` → 404 fix | 7 |
| Ordering: honeypot → daily limit → captcha | 6 |
| README + Bruno | 8 |
| Known limitations | documented in spec; no code |

No gaps.

**Deviations from the spec, all deliberate and recorded in Task 8 Step 5:**

1. `HoneypotLogic.getSession` is not `suspend` — nothing it calls suspends.
2. `MailLogicImplHoneypotTest` has five cases, not the spec's four, and different ones.
   The spec listed "config with `honeypot == null` sends normally" and "honeypot passing
   still runs the captcha check"; both would reach `Config.toMailer()` → `Constants` →
   NPE. Replaced with limiter-denial assertions that prove the same ordering without
   constructing a mailer.
3. Route-level tests are excluded, for the same `Constants` reason. Covered by the Task 8
   manual smoke test instead.

**Placeholder scan:** no TBDs. Two places ask the implementer to check an existing file
before writing — the `Solution` JSON shape in Task 3 and the `.bru` variable names in
Task 8 — both with the exact command to run and an explicit fallback.

**Type consistency:** `validate(config, expectedConfigId, token, submitted)` has the same
parameter order in the interface (Task 4), the implementation (Task 5), and both test
stubs (Task 6). `HoneypotSession(token, fields, issuedAt)` is constructed identically in
Task 4 and Task 6. `HoneypotResult.InvalidToken` carries `reason` everywhere, and
`MailLogicImpl` passes it straight into `HoneypotRejectedException(reason)`, which
`Routing.kt` surfaces as the 403 body.

One knowing rough edge: Task 6's tests cannot compile until Task 7 Step 2 updates the Koin
registration to the 4-arg `MailLogicImpl`, because Gradle compiles all of `main` before
`test`. Task 6 Step 5 says so and points at the fix.
