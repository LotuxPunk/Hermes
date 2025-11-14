# Mail Queue System

## Overview

Hermes provides a queue-based email sending system with configurable rate limiting for both **Resend** and **SMTP** providers. All queued mailers share a common base (`AbstractQueuedMailer`).

Key capabilities:
1. Queue emails asynchronously with immediate return.
2. Rate limiting (configurable per second throughput).
3. Reference tracking (each queued mail gets a UUID reference).
4. Automatic retries for temporary failures with exponential backoff.
5. Failure categorization: permanent (bounced) vs temporary (retryable).

## Configuration

Environment variables:

- `USE_MAIL_QUEUE` (boolean, default: `true`)
  - When `true`, configs create queued mailers (`QueuedResendMailer` / `QueuedSMTPMailer`).
  - When `false`, configs create direct mailers (`ResendMailer`, `SMTPMailer`) without queue.
- `MAIL_RATE_LIMIT` (integer, default: `10`)
  - Maximum emails per second processed from the queue.

Example:
```bash
USE_MAIL_QUEUE=true
MAIL_RATE_LIMIT=10
```

## Architecture

### Components

1. `MailQueueItem` – queued email wrapper (reference, retry counts, priority).
2. `RateLimitedMailQueue` – token-bucket based processor consuming items and invoking underlying mailer.
3. `AbstractQueuedMailer` – base class implementing `sendEmail` / `sendEmails` by enqueueing.
4. `QueuedResendMailer` / `QueuedSMTPMailer` – concrete queued mailers extending the base.
5. `ResendMailer` / `SMTPMailer` – non-queued implementations used when queue is disabled or as the underlying sender.

### Flow Diagram
```
Client Code
    ↓
QueuedMailer.sendEmail()
    ↓
Enqueue into RateLimitedMailQueue (returns immediately)
    ↓
Token acquisition (rate limit)
    ↓
Underlying Mailer (ResendMailer / SMTPMailer)
    ↓
Provider API (Resend / SMTP)
    ↓
Result categorization & retries
    ↓
Emit QueuedMailResult via Flow
```

## Usage

### Basic (Queued)
If `USE_MAIL_QUEUE=true`, `config.toMailer()` returns a queued mailer. Calling `sendEmail` completes immediately after enqueuing.

```kotlin
val mailer = config.toMailer() // QueuedResendMailer or QueuedSMTPMailer

val result = mailer.sendEmail(
    to = "user@example.com",
    from = "noreply@yourapp.com",
    subject = "Welcome!",
    content = "<h1>Welcome</h1>"
)
// result.sent contains the address marking successful enqueue
```

### Direct (Non-Queued)
If `USE_MAIL_QUEUE=false`, `sendEmail` executes immediately:
```kotlin
val mailer = config.toMailer() // ResendMailer or SMTPMailer
val result = mailer.sendEmail(...)
```

### Tracking Results
Collect emitted queue processing outcomes:
```kotlin
val queuedMailer = when (val m = config.toMailer()) {
  is QueuedResendMailer -> m
  is QueuedSMTPMailer -> m
  else -> null // direct mailer, no queue results
}

queuedMailer?.getQueue()?.results?.collect { r ->
  println("Reference=${r.reference} status=${r.result.status}")
}
```

### Queue Statistics
```kotlin
val stats = queuedMailer!!.getQueue().getStats()
println("Queued=${stats.queued} Sent=${stats.sent} Rate=${stats.rateLimit}/s")
```

### Graceful Shutdown
```kotlin
queuedMailer.shutdown() // stops processor after draining channel
```

## Rate Limiting
Token bucket algorithm:
- Bucket size = `MAIL_RATE_LIMIT` tokens per second.
- Each processed mail consumes one token.
- If empty, processor waits until refill.

## Retry Strategy
Temporary failures retried up to 3 times (1s, 2s, 3s backoff). Permanent failures (bounces / validation) are not retried.

## Migration Guide
No code changes needed:
```kotlin
val mailer = config.toMailer()
mailer.sendEmail(...)
```

Queue behavior is controlled via environment variable only.

Disable queue:
```bash
USE_MAIL_QUEUE=false
```

## Performance Notes
- Enqueue is O(1) and fast.
- Memory per queued mail ~1 KB.
- Tune `MAIL_RATE_LIMIT` based on provider quotas.

## Troubleshooting
| Issue | Check |
|-------|-------|
| Emails slow | Rate limit low → increase MAIL_RATE_LIMIT |
| No results collected | Using direct mailer (queue disabled) |
| Many retries | Provider temporary failures (429 / 5xx) |
| Permanent failures | Validate recipient addresses |

## Future Enhancements
- Persistent queue
- Multiple workers
- Dead letter queue
- Metrics endpoint
- Scheduled delivery
- Priority weighting logic

## API Reference

### sendEmail(...): SendOperationResult
- Queued mailers: enqueues and returns immediately.
- Direct mailers: sends immediately.

### MailQueueItem
```kotlin
data class MailQueueItem(
  val reference: String = UUID.randomUUID().toString(),
  val mail: Mail,
  val priority: Int = 0,
  val retryCount: Int = 0,
  val maxRetries: Int = 3,
)
```

### QueuedMailResult
```kotlin
data class QueuedMailResult(
  val reference: String,
  val result: SendOperationResult,
)
```

### RateLimitedMailQueue (excerpt)
```kotlin
class RateLimitedMailQueue(
  private val mailer: Mailer,
  private val rateLimit: Int = 10,
  private val scope: CoroutineScope
) {
  suspend fun enqueue(item: MailQueueItem): String
  suspend fun enqueueAll(items: List<MailQueueItem>): List<String>
  fun getStats(): QueueStats
  val results: SharedFlow<QueuedMailResult>
  fun shutdown()
}
```

### Queued Mailers
```kotlin
class QueuedResendMailer(...): AbstractQueuedMailer(...)
class QueuedSMTPMailer(...): AbstractQueuedMailer(...)
```

### Direct Mailers
```kotlin
class ResendMailer(...): Mailer
class SMTPMailer(...): Mailer
```

## Concurrency Model
- Queue processor runs in dedicated coroutine.
- Backpressure handled by channel capacity (UNLIMITED by default – can be bounded later).
- Fully non-blocking for queued mailers.
