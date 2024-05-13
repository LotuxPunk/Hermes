
# Hermes

 Mailer micro-service for vandeas 

## Table of content

- [Environment Variables](#environment-variables)
- [Documentation](#documentation)
  - [Supported Mail Providers](#supported-mail-providers)
  - [Contact Form](#contact-form)
    - [Example of `CONTACT_FORM_CONFIGS_FOLDER` Configuration Files](#example-of-contact_form_configs_folder-configuration-files)
  - [Mail Config](#mail-config)
    - [Example of `MAIL_CONFIGS_FOLDER` Configuration Files](#example-of-mail_configs_folder-configuration-files)
  - [Mail Template](#mail-template)
    - [Filename Requirements](#filename-requirements)
- [API Reference](#api-reference)
  - [Send Contact Form Using Contact Form Configuration](#send-contact-form-using-contact-form-configuration)
    - [POST `/v1/mail/contact`](#post-v1mailcontact)
    - [Body Parameters](#body-parameters)
  - [Send Mail Using Mail Configuration](#send-mail-using-mail-configuration)
    - [POST `/v1/mail`](#post-v1mail)
    - [Body Parameters](#body-parameters-1)
- [Roadmap](#roadmap)
  - [Completed and Pending Features](#completed-and-pending-features)


## Environment Variables

To run this project, you will need to add the following environment variables

- `CONTACT_FORM_CONFIGS_FOLDER`: An existing folder in your file system where the contact form configs will be stored.
- `MAIL_CONFIGS_FOLDER`: An existing folder in your file system where the email configs will be stored.
- `TEMPLATES_FOLDER`: An existing folder in your file system where the email templates will be stored.
- `GOOGLE_RECAPTCHA_SECRET`: A Google ReCaptcha secret (required only when using forms).

## Documentation

### Supported Mail Providers

- ~~[Sendgrid](https://sendgrid.com/)~~ Removed due to lack of support for batch emails requests
- [Resend](https://resend.io/)
- Custom SMTP server, that can be configured in the email & contact form configs

### Contact Form

#### Example of `CONTACT_FORM_CONFIGS_FOLDER` configuration files

**Resend-based config**
```json
{
    "id": "UUID",
    "dailyLimit": 10,
    "destination": "john@example.com",
    "sender": "doe@example.com",
    "threshold": 0.5, // Recapthca score thresold
    "lang": "fr", // ISO 639-1
    "subjectTemplate": "New mail from {{form.firstName}}",
    "provider": "RESEND",
    "apiKey": "<YOUR_RESEND_API_KEY>"
}
```

**SMTP-based config**
```json
{
    "id": "UUID",
    "dailyLimit": 10,
    "destination": "john@example.com",
    "sender": "doe@example.com",
    "threshold": 0.5, // Recapthca score thresold
    "lang": "fr", // ISO 639-1
    "subjectTemplate": "New mail from {{form.firstName}}",
    "provider": "SMTP",
    "username": "<SMTP_USERNAME>",
    "password": "<SMTP_PASSWORD>",
    "smtpHost": "<SMTP_SERVER_IP>",
    "smtpPort": "<SMTP_SERVER_PORT>"
}
```

Filename does not have to respect any convention.

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


## Roadmap

- [x] Better templating system (currently stored in /resources/templates)
- [x] Endpoint to send email
- [ ] Watch and reload configuration files and templates

