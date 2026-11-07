
# Hermes

Mailer micro-service for vandeas 

## Table of content

- [Environment Variables](#environment-variables)
- [Desktop Application](#desktop-application)
- [Documentation](#documentation)
  - [Supported Mail Providers](#supported-mail-providers)
  - [Contact Form](#contact-form)
    - [Example of `CONTACT_FORM_CONFIGS_FOLDER` Configuration Files](#example-of-contact_form_configs_folder-configuration-files)
    - [Visitor Confirmation](#visitor-confirmation)
  - [Mail Config](#mail-config)
    - [Example of `MAIL_CONFIGS_FOLDER` Configuration Files](#example-of-mail_configs_folder-configuration-files)
  - [Mail Template](#mail-template)
- [API Reference](#api-reference)
  - [Send Contact Form Using Contact Form Configuration](#send-contact-form-using-contact-form-configuration)
    - [POST `/v1/mail/contact`](#post-v1mailcontact)
    - [Body Parameters](#body-parameters)
  - [Send Mail Using Mail Configuration](#send-mail-using-mail-configuration)
    - [POST `/v1/mail`](#post-v1mail)
    - [Body Parameters](#body-parameters-1)
    - [Sender Name](#sender-name)
  - [Send Batch of Mails Using Mail Configurations](#send-batch-of-mails-using-mail-configurations)
    - [POST `/v1/mail/batch`](#post-v1mailbatch)
    - [Body Parameters](#body-parameters-2)
  - [Broadcast Mail to Multiple Recipients](#broadcast-mail-to-multiple-recipients)
    - [POST `/v1/mail/{configId}/broadcast`](#post-v1mailconfigidbroadcast)
    - [Body Parameters](#body-parameters-3)
- [Roadmap](#roadmap)
  - [Completed and Pending Features](#completed-and-pending-features)


## Environment Variables

To run this project, you will need to add the following environment variables

- `CONTACT_FORM_CONFIGS_FOLDER`: An existing folder in your file system where the contact form configs will be stored.
- `MAIL_CONFIGS_FOLDER`: An existing folder in your file system where the email configs will be stored.
- `TEMPLATES_FOLDER`: An existing folder in your file system where the email templates will be stored.
- `GOOGLE_RECAPTCHA_SECRET`: A Google ReCaptcha secret (required only when using Google ReCaptcha in contact forms).
- `USE_MAIL_QUEUE`: (Optional, default: `true`) Enable queue-based email sending with rate limiting.
- `MAIL_RATE_LIMIT`: (Optional, default: `10`) Maximum number of emails to send per second.
- `CONFIRMATION_RECIPIENT_DAILY_LIMIT`: (Optional, default: `3`) Maximum number of [Visitor confirmations](#visitor-confirmation) one address receives per day, across every contact form config of the instance. A value that is not a positive integer falls back to `3` with a warning: the limit cannot be disabled.

See [.env.example](.env.example) for a complete configuration template.

### Mail Queue System

Hermes includes a built-in queue system with rate limiting for email sending. See [MAIL_QUEUE_SYSTEM.md](docs/MAIL_QUEUE_SYSTEM.md) for detailed documentation.

## Desktop Application

Hermes now includes a **Compose Multiplatform desktop application** for managing mail templates and configurations via SSH!

### Features

- **Remote Management**: Connect to your server via SSH and manage files remotely
- **Template Editor**: Browse, edit, create, and delete mail templates (.hbs files)
- **Config Management**: Manage mail and contact form configuration files (JSON)
- **Modern UI**: Material 3 design with intuitive tabbed navigation
- **Persistent Settings**: Saves your SSH connection settings locally

### Quick Start

```bash
# Run the desktop application
./gradlew desktop:run

# Build native installer
./gradlew desktop:packageDistributionForCurrentOS
```

For detailed documentation, see [desktop/README.md](desktop/README.md).

## Documentation

### Supported Mail Providers

- ~~[Sendgrid](https://sendgrid.com/)~~ Removed due to lack of support for batch emails requests
- [Resend](https://resend.io/)
- Custom SMTP server, that can be configured in the email & contact form configs

### Contact Form

Contact forms support captcha validation with the following providers:
- **Google ReCaptcha** - Requires `GOOGLE_RECAPTCHA_SECRET` environment variable
- **Kerberus** - Alternative captcha provider

#### Example of `CONTACT_FORM_CONFIGS_FOLDER` configuration files

**Resend-based config**
```json
{
    "id": "UUID",
    "dailyLimit": 10,
    "destination": "john@example.com",
    "sender": "doe@example.com",
    "lang": "fr", // ISO 639-1
    "subjectTemplate": "New mail from {{form.fullName}}",
    "provider": "RESEND",
    "apiKey": "<YOUR_RESEND_API_KEY>",
    "captcha": {
        "provider": "GOOGLE_RECAPTCHA",
        "secretKey": "<YOUR_RECAPTCHA_SECRET>",
        "threshold": 0.5 // ReCaptcha score threshold
    }
}
```

**SMTP-based config**
```json
{
    "id": "UUID",
    "dailyLimit": 10,
    "destination": "john@example.com",
    "sender": "doe@example.com",
    "lang": "fr", // ISO 639-1
    "subjectTemplate": "New mail from {{form.fullName}}",
    "provider": "SMTP",
    "username": "<SMTP_USERNAME>",
    "password": "<SMTP_PASSWORD>",
    "smtpHost": "<SMTP_SERVER_IP>",
    "smtpPort": "<SMTP_SERVER_PORT>",
    "captcha": {
        "provider": "KERBERUS",
        "secretKey": "<YOUR_KERBERUS_SECRET>"
    }
}
```

Filename does not have to respect any convention.

A config that fails to parse or validate is rejected: Hermes logs an error naming the file
and does not load it, and every other config keeps working. If the file was already loaded,
the previous version stays in place until the file is fixed.

#### Visitor confirmation

A contact form config can send a **Visitor confirmation**: on every Submission, after the
mail to your inbox (the Org notification), Hermes sends one mail to the address the visitor
typed. It acknowledges receipt and can include a copy of what they wrote.

To enable it:

1. Add a `confirmation` block to the contact form config, with the subject template of the
   confirmation:

   ```json
   {
       "id": "UUID",
       "dailyLimit": 10,
       "destination": "john@example.com",
       "sender": "doe@example.com",
       "lang": "en",
       "subjectTemplate": "New mail from {{form.fullName}}",
       "confirmation": {
           "subjectTemplate": "We received your message, {{form.fullName}}"
       },
       "provider": "RESEND",
       "apiKey": "<YOUR_RESEND_API_KEY>",
       "captcha": { "provider": "KERBERUS", "secretKey": "<YOUR_KERBERUS_SECRET>" }
   }
   ```

2. Add the body template to the templates folder as `{{UUID}}.confirmation.hbs`, next to
   `{{UUID}}.hbs`. It sees the same fields as the Org notification template (`form.fullName`,
   `form.email`, `form.phone`, `form.topic`, `form.content`).

How it behaves:

- It is sent through the same provider and from the same `sender` as the Org notification,
  with `Reply-To` set to your inbox (`destination`), so a visitor who replies reaches you.
- It is sent only when the Org notification was sent, or queued for sending, to at least
  one recipient. With the mail queue on (the default), a queued Org notification counts even
  if its delivery fails later.
- It is best-effort. If the template is missing, rendering or sending fails, or the address
  has reached its daily limit, only the confirmation is skipped: the Submission still
  succeeds with the same HTTP status, the response says `"confirmation": "NOT_SENT"`, and the
  reason is logged with a masked address (`j***@example.com`).
- It does not use up the `dailyLimit`, which keeps counting Submissions.
- Configs without a `confirmation` block behave exactly as before.

Safeguards:

- **A finite `dailyLimit` is required.** A config with a `confirmation` block and a
  `dailyLimit` of `0` or less is rejected at load time.
- **One address receives at most `CONFIRMATION_RECIPIENT_DAILY_LIMIT` confirmations per day**
  (default `3`), across every contact form config of the instance. Addresses that differ only
  by case or by a `+tag` count as one. Hermes keeps only a SHA-256 hash of each address, in
  memory, and resets the counters daily and on restart.
- **Links are shown defanged.** Every field the visitor typed is defanged before it reaches
  the confirmation templates (subject and body), so mail clients cannot turn it into a link:
  `https://example.com/page.html` becomes `hxxps://example[.]com/page[.]html`, `www.example.com` becomes
  `www[.]example[.]com`, `192.168.0.1` becomes `192[.]168[.]0[.]1` and `jane@example.com`
  becomes `jane[at]example[.]com`. The Org notification shows the original text.

> **Warning:** Hermes cannot verify that the address typed in the form belongs to the
> visitor. A mistyped or malicious address sends the confirmation, and anything your
> template copies into it, to a stranger. On sensitive sites, leave sensitive fields (such
> as `form.phone` or `form.content`) out of the confirmation template.

### Mail config

#### Example of `MAIL_CONFIGS_FOLDER` configuration files

**Resend-based config**
```json
{
    "id": "UUID",
    "sender": "no-reply@example.com",
    "subjectTemplate": "New mail from {{form.firstName}}",
    "provider": "RESEND",
    "apiKey": "<YOUR_RESEND_API_KEY>"
}
```

**SMTP-based config**
```json
{
    "id": "UUID",
    "sender": "no-reply@example.com",
    "subjectTemplate": "New mail from {{form.firstName}}",
    "provider": "SMTP",
    "username": "<SMTP_USERNAME>",
    "password": "<SMTP_PASSWORD>",
    "smtpHost": "<SMTP_SERVER_IP>",
    "smtpPort": "<SMTP_SERVER_PORT>"
}
```

### Mail template

Filename should be `{{UUID}}.hbs` (same UUID as the `id` field in the Contact Form or Mail config) 

The body of a contact form's [Visitor confirmation](#visitor-confirmation) goes in `{{UUID}}.confirmation.hbs`.

### API Reference

#### Send contact form using contact form configuration

**POST** `/v1/mail/contact`

##### Body

| Attribute        | Type     | Description                                             |
|:-----------------|:---------|:--------------------------------------------------------|
| `id`             | `string` | **Required**. Your contact form config id               |
| `fullName`       | `string` | **Required** Full name of the person that sent the form |
| `email`          | `string` | **Required** Email of the person that sent the form     |
| `content`        | `string` | **Required** Content of the message                     |
| `recaptchaToken` | `string` | **Required** Result token/secret of recaptcha           |

##### Response

The response lists the Org notification recipients by outcome, and its HTTP status depends on
the Org notification only.

```json
{
    "sent": ["john@example.com"],
    "failed": [],
    "bounced": [],
    "temporary": [],
    "confirmation": "SENT"
}
```

`confirmation` is present only when the contact form config has a
[Visitor confirmation](#visitor-confirmation). `SENT` means the confirmation was sent or
queued for sending. `NOT_SENT` means it was not, without saying why: the reason is in the
logs. Show "a copy has been sent to you" only on `SENT`.

#### Send mail using mail configuration

**POST** `/v1/mail`

##### Body

| Attribute    | Type                                | Description                                                      |
|:-------------|:------------------------------------|:-----------------------------------------------------------------|
| `id`         | `string`                            | **Required**. Your mail config id                                |
| `email`      | `string`                            | **Required** Email of the person to sent the mail to             |
| `replyTo`    | `string`                            | **Optional** Address that receives replies instead of the sender |
| `senderName` | `string`                            | **Optional** Display name of the sender, for this mail only      |
| `attributes` | `Map<string, string> / JSON Object` | **Required** Attributes to hydrate the mail template             |

Set `replyTo` when recipients should reply to a monitored address rather than the
no-reply sender configured in the mail config. Omit it and no `Reply-To` header is set.
A malformed address is rejected with `400 Bad Request` and the mail is not sent.

##### Sender name

Set `senderName` when one mail config sends on behalf of several parties, for example
`"Spy Club via Courtly"`. It replaces only the display name of the config's `sender`:

- The address always stays the one in the config's `sender`, so a request cannot send
  from another address. A config whose `sender` is a bare address takes `senderName` as
  its name.
- Omit it, or send `null`, and the `From` header is the config's `sender`, unchanged.
- Accents, apostrophes such as `’`, commas and double quotes are encoded for you and reach
  the recipient as sent.
- The name is trimmed, then refused with `400 Bad Request` and the body `Invalid senderName`
  when it is empty, longer than 100 characters (Unicode code points), or contains a control
  character (CR and LF included) or a bidi control (U+202A–U+202E, U+2066–U+2069). Nothing
  is sent.

#### Send batch of mails using mail configurations

**POST** `/v1/mail/batch`

##### Body

| Attribute | Type          | Description                          |
|:----------|:--------------|:-------------------------------------|
| `mails`   | `Array<Mail>` | **Required**. Array of mails to send |

Each entry accepts the same fields as `POST /v1/mail`, including the optional
`replyTo` and [`senderName`](#sender-name). Each entry gets its own `senderName`; an entry
without one keeps the config's sender. If any entry carries a malformed `replyTo` or a
refused `senderName`, the whole batch is rejected with `400 Bad Request` and no mail is
sent.

```json
[
    {
        "id": "UUID", // Mail config id
        "email": "johndoe@example.com",
        "replyTo": "support@example.com", // Optional
        "senderName": "Spy Club via Courtly", // Optional
        "attributes": {
            "firstName": "John",
            "lastName": "Doe"
        }
    }
]
```

#### Broadcast mail to multiple recipients

**POST** `/v1/mail/{configId}/broadcast`

Send the same email to multiple recipients using a single mail configuration. The template is rendered once with the provided attributes and sent to all recipients. Maximum **50 recipients** per request.

##### Path Parameters

| Parameter  | Type     | Description                       |
|:-----------|:---------|:----------------------------------|
| `configId` | `string` | **Required**. Your mail config id |

##### Body (JSON)

| Attribute    | Type                                | Description                                                      |
|:-------------|:------------------------------------|:-----------------------------------------------------------------|
| `to`         | `Array<string>`                     | **Required** List of recipient email addresses                   |
| `replyTo`    | `string`                            | **Optional** Address that receives replies, for every recipient  |
| `senderName` | `string`                            | **Optional** Display name of the sender, for every recipient     |
| `attributes` | `Map<string, string> / JSON Object` | **Required** Attributes to hydrate the mail template             |

`senderName` follows the [same rules](#sender-name) as on `POST /v1/mail`: only the display
name changes, the address stays the config's.

```json
{
    "to": ["alice@example.com", "bob@example.com"],
    "replyTo": "support@example.com",
    "senderName": "Spy Club via Courtly",
    "attributes": {
        "firstName": "Team",
        "eventName": "Launch Day"
    }
}
```

##### Body (Multipart Form Data — with attachments)

| Field        | Type     | Description                                                  |
|:-------------|:---------|:-------------------------------------------------------------|
| `to`         | `string` | **Required** Recipient email (repeat the field for multiple) |
| `replyTo`    | `string` | **Optional** Address that receives replies                   |
| `senderName` | `string` | **Optional** Display name of the sender, for every recipient |
| `attributes` | `string` | **Optional** JSON object of template attributes              |
| `attachment` | `file`   | **Optional** File attachment (repeat for multiple)           |


## Roadmap

- [x] Better templating system (currently stored in /resources/templates)
- [x] Endpoint to send email
- [x] Desktop application to manage templates and configs via SSH
- [x] Queue system with rate limiting for email sending
- [ ] Watch and reload configuration files and templates

