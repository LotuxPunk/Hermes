
# Hermes

Mailer micro-service for vandeas 

## Table of content

- [Environment Variables](#environment-variables)
- [Desktop Application](#desktop-application)
- [Documentation](#documentation)
  - [Supported Mail Providers](#supported-mail-providers)
  - [Contact Form](#contact-form)
    - [Example of `CONTACT_FORM_CONFIGS_FOLDER` Configuration Files](#example-of-contact_form_configs_folder-configuration-files)
  - [Mail Config](#mail-config)
    - [Example of `MAIL_CONFIGS_FOLDER` Configuration Files](#example-of-mail_configs_folder-configuration-files)
  - [Mail Template](#mail-template)
- [API Reference](#api-reference)
  - [Get a Honeypot Form Session](#get-a-honeypot-form-session)
    - [GET `/v1/mail/contact/{configId}/form-session`](#get-v1mailcontactconfigidform-session)
  - [Send Contact Form Using Contact Form Configuration](#send-contact-form-using-contact-form-configuration)
    - [POST `/v1/mail/contact`](#post-v1mailcontact)
    - [Body Parameters](#body-parameters)
  - [Send Mail Using Mail Configuration](#send-mail-using-mail-configuration)
    - [POST `/v1/mail`](#post-v1mail)
    - [Body Parameters](#body-parameters-1)
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
    "lang": "fr",
    "subjectTemplate": "New mail from {{form.firstName}}",
    "provider": "RESEND",
    "apiKey": "<YOUR_RESEND_API_KEY>",
    "captcha": {
        "provider": "GOOGLE_RECAPTCHA",
        "secretKey": "<YOUR_RECAPTCHA_SECRET>",
        "threshold": 0.5
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
    "lang": "fr",
    "subjectTemplate": "New mail from {{form.firstName}}",
    "provider": "SMTP",
    "username": "<SMTP_USERNAME>",
    "password": "<SMTP_PASSWORD>",
    "smtpHost": "<SMTP_SERVER_IP>",
    "smtpPort": 587,
    "captcha": {
        "provider": "KERBERUS",
        "secretKey": "<YOUR_KERBERUS_SECRET>"
    }
}
```

`lang` is an ISO 639-1 code (e.g. `fr`, `en`). `captcha` is **required** on every contact
form config; its `provider` is either `GOOGLE_RECAPTCHA` (with a `threshold` score) or
`KERBERUS`, per the two captcha providers listed above.

Filename does not have to respect any convention.

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
| `fieldCount` | `2` | Number of hidden trap fields issued per session (max `32`) |
| `minDwellMillis` | `2000` | Submissions faster than this are treated as bots |
| `maxAgeMillis` | `1800000` | How long an issued token stays valid. Capped at one hour (`3600000`) — the nonce cache that backs single-use enforcement evicts entries after an hour regardless of this value, so a config setting a longer `maxAge` is rejected at session issuance |

Captcha (Google ReCaptcha or Kerberus) is mandatory on every contact form config; the
honeypot is an additional, optional layer on top of it. A form always runs its captcha
check, with or without the honeypot.

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

### API Reference

#### Get a honeypot form session

**GET** `/v1/mail/contact/{configId}/form-session`

Required before submitting a contact form whose config has a `honeypot` block. Returns the
hidden field names to render and the signed token to submit back. Each token is valid for
a single submission.

##### Response

```json
{
    "token": "eyJjaWQiOiJhYmMtMTIzIi...<payload>.<signature>",
    "fields": ["a7f3kdx9", "qm2x9pz1"],
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

// Fetch on page load so the minDwell timer starts when the visitor arrives. Keep the
// promise itself (not just its resolved value) so a submit that races ahead of this
// fetch can await it below instead of reading a not-yet-assigned session and throwing.
const sessionPromise = fetch(`https://hermes.example.com/v1/mail/contact/${CONFIG_ID}/form-session`)
  .then(r => r.json())
  .then(session => {
    document.getElementById("hp").innerHTML = session.fields.map(name =>
      `<input name="${name}" autocomplete="off" tabindex="-1" aria-hidden="true"
              style="position:absolute;left:-9999px">`
    ).join("");
    return session;
  });

document.getElementById("contact").addEventListener("submit", async event => {
  event.preventDefault();
  const data = new FormData(event.target);
  const session = await sessionPromise;
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

Config changes (a rotated `secretKey`, a newly added `honeypot` block) take effect
immediately and hard-403 any visitor who already has the page open with a session issued
under the old settings — so on a 403 from `POST /v1/mail/contact`, re-fetch `form-session`
once and retry the submission before surfacing an error to the visitor.

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
| `honeypotToken`  | `string` | Required when the config has a `honeypot` block. Token from the form-session endpoint |
| `honeypot`       | `object` | Required when the config has a `honeypot` block. Map of the issued field names to their submitted values |

#### Send mail using mail configuration

**POST** `/v1/mail`

##### Body

| Attribute    | Type                                | Description                                          |
|:-------------|:------------------------------------|:-----------------------------------------------------|
| `id`         | `string`                            | **Required**. Your mail config id                    |
| `email`      | `string`                            | **Required** Email of the person to sent the mail to |
| `attributes` | `Map<string, string> / JSON Object` | **Required** Attributes to hydrate the mail template |

#### Send batch of mails using mail configurations

**POST** `/v1/mail/batch`

##### Body

| Attribute | Type          | Description                          |
|:----------|:--------------|:-------------------------------------|
| `mails`   | `Array<Mail>` | **Required**. Array of mails to send |

```json
[
    {
        "id": "UUID", // Mail config id
        "email": "johndoe@example.com",
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

| Attribute    | Type                                | Description                                          |
|:-------------|:------------------------------------|:-----------------------------------------------------|
| `to`         | `Array<string>`                     | **Required** List of recipient email addresses       |
| `attributes` | `Map<string, string> / JSON Object` | **Required** Attributes to hydrate the mail template |

```json
{
    "to": ["alice@example.com", "bob@example.com"],
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
| `attributes` | `string` | **Optional** JSON object of template attributes              |
| `attachment` | `file`   | **Optional** File attachment (repeat for multiple)           |


## Roadmap

- [x] Better templating system (currently stored in /resources/templates)
- [x] Endpoint to send email
- [x] Desktop application to manage templates and configs via SSH
- [x] Queue system with rate limiting for email sending
- [ ] Watch and reload configuration files and templates

