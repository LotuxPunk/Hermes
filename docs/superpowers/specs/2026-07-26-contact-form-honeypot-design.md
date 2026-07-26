# Contact Form Honeypot — Design

**Date:** 2026-07-26
**Status:** Approved for planning

## Goal

Add a spam-filtering layer to `POST /v1/mail/contact` that costs a bot more than it costs
a legitimate visitor: randomized honeypot fields whose names are issued by the server,
bound to an HMAC-signed single-use token that the browser can only obtain by running JS.

The layer is independent of the existing captcha providers. A contact form can run
honeypot only, captcha only, both, or neither.

## Threat model

What this catches:

- **Fill-everything form bots.** They populate every input they find, including the
  hidden honeypot fields, and trip the trap.
- **Bots that don't execute JS.** They never fetch a session, so they have no valid
  token and are rejected outright.
- **Instant submitters.** A submission arriving faster than `minDwell` after issuance
  did not involve a human typing.
- **Token farming.** Each token is single-use, so one fetch buys exactly one attempt.

What this does not catch, by design:

- A headless browser that runs the page's JS, respects the CSS-hidden fields, waits out
  `minDwell`, and fetches a fresh token per submission. That is what the existing
  captcha layer is for — the two stack.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Relationship to captcha | Independent layer, stackable | Keeps the two concerns orthogonal; a form can use either, both, or neither |
| Token delivery | New `GET` endpoint issuing randomized field names + signed token | HMAC needs a server-side secret, so the token must be server-issued |
| Replay protection | Single-use nonce in an in-memory cache, plus expiry | One fetch buys one attempt; mirrors `KerberusCaptcha.challengeCache` |
| Failure response | Trap trip → silent success; token error → `403` | A bot learns nothing from a trip; a site owner needs to see a broken integration |
| Signing secret | Per contact-form config JSON | Mirrors `CaptchaConfig.secretKey`; each form independently rotatable |
| Duration config | `kotlin.time.Duration`, serialized as millis | Matches the codebase's existing time idiom; millis stays a wire detail |
| Route | `GET /v1/mail/contact/{configId}/form-session` | Sits next to the endpoint it serves |
| Field-name style | Opaque random (`a7f3kd`) | Browser autofill can't latch on; a filled trap would silently eat a real enquiry |
| Nonce durability | In-memory, accepted as-is | Same constraint the Kerberus challenge cache already lives with |

## Flow

```
Browser                                  Hermes
   │  GET /v1/mail/contact/{configId}/form-session
   ├────────────────────────────────────────►  generate field names + nonce,
   │                                            sign payload, store nonce
   │  ◄──── { token, fields: ["a7f3kd", "qm2x9p"], issuedAt }
   │
   │  JS injects CSS-hidden inputs named a7f3kd, qm2x9p
   │
   │  POST /v1/mail/contact
   │  { …, honeypotToken, honeypot: { "a7f3kd": "", "qm2x9p": "" } }
   ├────────────────────────────────────────►  honeypot → daily limit → captcha → send
```

`MailLogicImpl.sendContactForm` gains one step, ordered **config lookup → honeypot →
daily limit → captcha**. Honeypot runs first because it is the only check with no network
call and no state mutation, and because a trapped bot then receives the indistinguishable
success response rather than a `429` that would confirm the form is live and busy.

## Token format

```
p = base64url(payloadJsonBytes)
token = p + "." + base64url(HMAC-SHA256(p.toByteArray(US_ASCII), secretKey))
```

**The HMAC is computed over the ASCII bytes of the base64url segment `p`, not over the raw
JSON bytes.** This is JWT's approach and the reason matters: signing the raw JSON would
make verification depend on re-serializing the decoded object byte-identically to how it
was written — any change in key order, whitespace, or kotlinx defaults would silently
invalidate every existing token. Signing the transmitted text has no such dependency. On
verification the signature is checked against `p` exactly as received, before `p` is
decoded at all.

Base64url is unpadded (`Base64.getUrlEncoder().withoutPadding()`). The payload is:

```kotlin
@Serializable
internal data class HoneypotTokenPayload(
    val cid: String,      // config id this token is bound to
    val n: String,        // nonce (UUID)
    val iat: Long,        // issued-at, epoch millis
    val f: List<String>,  // the authoritative honeypot field names
)
```

`iat` stays a raw epoch-millis `Long` — it is an absolute timestamp crossing the wire,
not a duration. Elapsed time is compared as a `Duration`:
`(now() - payload.iat).milliseconds >= config.minDwell`.

HMAC uses the JDK's `javax.crypto.Mac` with `HmacSHA256` and `MessageDigest.isEqual`
for the constant-time comparison. No new dependency.

## Validation order

The signature is verified **before** the payload JSON is deserialized, so unauthenticated
attacker-controlled JSON is never parsed. The nonce is consumed **before** the trap
checks, so a bot that trips the trap has burned its token and must fetch a new one for
each attempt.

| # | Check | On failure |
|---|---|---|
| 1 | Token non-null and splits into exactly two parts, both valid base64url | `InvalidToken` → `403` |
| 2 | Recomputed HMAC matches, constant-time | `InvalidToken` → `403` |
| 3 | Payload deserializes | `InvalidToken` → `403` |
| 4 | `payload.cid == expectedConfigId` (i.e. the submitted `form.id`) | `InvalidToken` → `403` |
| 5 | `0 <= now - iat <= config.maxAge` | `InvalidToken` → `403` |
| 6 | Nonce present in cache → **consume it** | `InvalidToken` → `403` |
| 7 | `now - iat >= config.minDwell` | `Trapped` → silent success |
| 8 | Every name in `payload.f` is present in `honeypot` and blank | `Trapped` → silent success |

Notes:

- Step 5 rejects future-dated tokens (`now - iat < 0`) as well as expired ones; a clock
  that has moved backwards is not a case worth accommodating silently.
- Step 8 passes a field only when the map contains its key and the value `isBlank()`
  (empty or whitespace-only). A **missing** key is a trap trip, not a pass — the client is
  expected to submit back every field it was given.
- Extra keys in `honeypot` that are not in `payload.f` are ignored. Only the signed
  field list is authoritative.
- If the config has **no** `honeypot` block, the entire layer is skipped and any
  `honeypotToken` the client sends is ignored.

### The silent success response

`SendOperationResult(sent = <the same destination list a real send would report>)`, i.e.
`form.destinations` if non-empty else `listOf(config.destination)`. This serializes
byte-identically to a genuine success, status `SENT` → `200`. No mail is sent, and
`limiter.recordMailSent` is **not** called, so trapped submissions consume no daily quota.
Logged at `WARN` with the config id and which check tripped.

## Modules

| File | Purpose |
|---|---|
| `service/Honeypot.kt` | `Honeypot` interface + `HoneypotResult` sealed interface |
| `service/impl/honeypot/HmacHoneypot.kt` | Sole implementation |
| `dto/HoneypotSession.kt` | Endpoint response DTO |
| `dto/configs/honeypot/HoneypotConfig.kt` | Per-form config block |
| `config/DurationMillisSerializer.kt` | `Duration` ⇄ millis |
| `logic/HoneypotLogic.kt`, `logic/impl/HoneypotLogicImpl.kt` | Session issuance, mirrors `KerberusLogic` |

```kotlin
interface Honeypot {
    fun issue(configId: String, config: HoneypotConfig): HoneypotSession

    suspend fun validate(
        config: HoneypotConfig,
        expectedConfigId: String,
        token: String?,
        submitted: Map<String, String>,
    ): HoneypotResult
}

sealed interface HoneypotResult {
    data object Pass : HoneypotResult
    data class InvalidToken(val reason: String) : HoneypotResult
    data object Trapped : HoneypotResult
}

@Serializable
data class HoneypotSession(
    val token: String,
    val fields: List<String>,
    val issuedAt: Long,   // epoch millis, same value as the token's `iat`
)
```

`validate` is `suspend` so that nonce consumption can be guarded (see Concurrency).
`issue` is not — `cache4k`'s `put` is synchronous, matching
`KerberusCaptcha.generateChallenge`.

```kotlin
class HmacHoneypot(
    private val now: () -> Long = System::currentTimeMillis,
    private val randomBytes: (Int) -> ByteArray = SecureRandom().let { sr ->
        { size -> ByteArray(size).also(sr::nextBytes) }
    },
) : Honeypot
```

Both dependencies are constructor params with production defaults so tests can drive time
and randomness directly, and a `(Int) -> ByteArray` function is trivially faked without a
mock library. One random source feeds both the nonce (16 bytes, base64url) and the field
names.

`java.security.SecureRandom` rather than kryptom's `defaultCryptoService.strongRandom`
(used by `KerberusCaptcha`): this component needs arbitrary-length byte generation, and it
already reaches for JDK crypto in `javax.crypto.Mac`. Keeping one component on one crypto
provider is the more coherent choice than splitting it across two.

`MailLogicImpl` takes `Honeypot` as a fourth constructor parameter. Koin registers
`Honeypot` and `HoneypotLogic` as singletons in `appModule`.

### Field name generation

`config.fieldCount` names, each 8 characters: first from `[a-z]`, remainder from
`[a-z0-9]`. Names within one session must be distinct — regenerate on collision. No fixed
prefix, so a bot cannot regex the trap fields out of the form.

### Concurrency

Nonces live in one `Cache<String, Unit>` inside `HmacHoneypot`:
`expireAfterWrite(1.hours)` as a ceiling, with the per-config `maxAge` enforced from
`iat`, plus `maximumCacheSize(100_000)` so hammering the session endpoint cannot grow
memory without bound.

`cache4k` offers no atomic remove-and-return, so two concurrent submissions carrying the
same nonce could both pass step 6. The `get` + `invalidate` pair is therefore guarded by a
`kotlinx.coroutines.sync.Mutex`, which is why `validate` is `suspend`. The critical
section is a map lookup and a removal; contention is negligible.

## Config schema

```kotlin
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

The `Millis` suffix stays on the JSON keys only. These files are hand-edited through the
desktop SSH app, where a bare `maxAge: 1800000` would be ambiguous; the Kotlin side stays
unit-free and type-safe.

```json
{
  "id": "UUID",
  "dailyLimit": 10,
  "destination": "john@example.com",
  "sender": "doe@example.com",
  "lang": "fr",
  "subjectTemplate": "New mail from {{form.firstName}}",
  "provider": "RESEND",
  "apiKey": "...",
  "captcha": { "provider": "KERBERUS", "secretKey": "..." },
  "honeypot": {
    "secretKey": "...",
    "fieldCount": 2,
    "minDwellMillis": 2000,
    "maxAgeMillis": 1800000
  }
}
```

## Backward compatibility

Both additions are nullable/defaulted, so **every existing config file and every existing
client keeps working untouched**:

- `ContactFormConfig` gains `val honeypot: HoneypotConfig?`, overridden as `= null` in
  `ResendContactFormConfig` and `SMTPContactFormConfig`. `null` skips the layer entirely.
- `ContactForm` gains `val honeypotToken: String?` and `val honeypot: Map<String, String>`,
  defaulted to `null` and `emptyMap()` in `GoogleRecaptchaContactForm` and
  `KerberusContactForm`.

Adding a `honeypot` block to a config is the single action that turns a missing token into
a `403` for that form.

## Endpoints

### `GET /v1/mail/contact/{configId}/form-session`

Issues a session. No request body.

**200**
```json
{
  "token": "eyJjaWQiOi...<base64url>.<base64url hmac>",
  "fields": ["a7f3kd", "qm2x9p"],
  "issuedAt": 1774483200000
}
```

| Status | Condition | Raised as |
|---|---|---|
| `200` | Session issued | — |
| `400` | Missing `configId` path parameter | `IllegalArgumentException` |
| `400` | Config exists but has no `honeypot` block | `IllegalArgumentException` |
| `404` | No config with that id | `NoSuchElementException` |
| `500` | Anything else | — |

A missing `honeypot` block arguably deserves `409` — the request is well-formed and it is
the server-side config that cannot satisfy it. It gets `400` anyway, to match
`KerberusLogicImpl`, which already throws `IllegalArgumentException` for the exact
analogous case ("Mail config does not have Kerberus captcha"). Consistency with the
sibling logic class beats REST purity, and it avoids introducing a new exception type for
one status code.

### `POST /v1/mail/contact` (extended)

Two new optional body fields, `honeypotToken` and `honeypot`. Behaviour per the
validation table. CORS needs no change — the plugin is already `anyHost()` and Ktor allows
`GET` and `POST` by default.

## Targeted fix: `NoSuchElementException` mapping

`ConfigDirectory.get` throws `NoSuchElementException` for an unknown id, which
`Routing.kt`'s `when (e)` blocks do not handle — so today an unknown `configId` returns
**500** on `POST /v1/mail/contact`. The new endpoint takes `configId` in the path and would
inherit the same wrong status, so this work adds
`is NoSuchElementException -> call.respond(HttpStatusCode.NotFound, …)` to the contact
route's handler and to the new endpoint's.

Scope is deliberately limited to the two contact-form routes. The other routes have the
same latent gap; fixing them is not part of this change.

## Testing

`HmacHoneypotTest` — fixed clock and deterministic random injected, one case per row of
the validation table:

- issue → validate round trip passes
- `issue` returns `fieldCount` distinct names matching `[a-z][a-z0-9]{7}`
- malformed token: null, no separator, three parts, non-base64 segment
- tampered payload (valid base64, wrong signature)
- tampered signature
- token minted for a different `cid`
- expired (`now` advanced past `maxAge`)
- future-dated (`iat` ahead of `now`)
- replay: second `validate` of the same token fails at step 6
- submitted too fast (below `minDwell`) → `Trapped`
- a declared field with content → `Trapped`
- a declared field missing from the map → `Trapped`
- whitespace-only value → `Trapped`
- undeclared extra keys present → `Pass`

`MailLogicImplHoneypotTest` — hand-written `ConfigDirectory`, `DailyLimiter` and `Mailer`
fakes (no mock library is on the test classpath; deps are `kotlin-test-junit` and
`ktor-server-test-host`). Asserts the boundary behaviours:

- trapped submission returns a `SENT`-shaped result **and** the fake mailer records zero
  sends **and** `recordMailSent` was not called
- invalid token propagates so the route yields `403`
- config with `honeypot == null` ignores a supplied token and sends normally
- honeypot passing still runs the captcha check

## Documentation

- `README.md`: the new endpoint, the `honeypot` config block, and a client-side snippet
  showing the fetch plus hidden-input injection with `autocomplete="off"`,
  `tabindex="-1"` and `aria-hidden="true"`.
- `.bruno/Hermes/`: a request for the new endpoint, alongside the existing
  `Kerberus Challenge.bru`.

## Known limitations

- **The nonce cache is in-memory.** Tokens do not survive a restart and do not work behind
  more than one instance — a restart makes in-flight forms return `403` and the visitor
  must refetch. This is the same constraint `KerberusCaptcha.challengeCache` already lives
  with, and is accepted for now.
- **`maximumCacheSize` eviction is LRU, not TTL-ordered.** Sustained hammering of the
  session endpoint can evict legitimate nonces, producing spurious `403`s. The cap is the
  lesser evil against unbounded memory growth.
- **The session endpoint is unauthenticated and free to call.** Nothing rate-limits
  issuance; the cache cap and the existing `dailyLimit` are the only backstops.

## Out of scope

- Rate-limiting the session endpoint.
- A shared/durable nonce store (Redis or similar).
- Extending the honeypot to `POST /v1/mail`, `/batch`, or `/broadcast` — those are
  server-to-server routes, not browser-facing forms.
- Fixing `NoSuchElementException` mapping on routes other than the two contact-form ones.
- `ConfigDirectoryImpl`'s non-thread-safe `configs` map (pre-existing, unrelated).
