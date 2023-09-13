
# Hermes

 Mailer micro-service for vandeas 

## Environment Variables

To run this project, you will need to add the following environment variables to your .env file

`CONTACT_FORM_CONFIGS`

`GOOGLE_RECAPTCHA_SECRET`

`SENDGRID_API_KEY`
## Documentation

### Contact Form Config

#### Example of `CONTACT_FORM_CONFIGS` ENV variable

```json
[
    {
        "id": "UUID",
        "dailyLimit": 10,
        "destination": "john@example.com",
        "sender": "doe@example.com",
        "threshold": 0.5, // Recapthca score thresold
        "lang": "fr" // ISO 639-1
    }
]
```
## API Reference

#### Send contact form using contact form configuration

```http
  POST /v1/mail/contact
```

##### Body

| Attribute | Type     | Description                |
| :-------- | :------- | :------------------------- |
| `id` | `string` | **Required**. Your contact form id |
| `fullName`| `string`| **Required** Full name of the person that sent the form
| `email`|`string`| **Required** Email of the person that sent the form |
| `content`|`string`|**Required** Content of the message |
| `recaptchaToken`|`string`| **Required** Result token/secret of recaptcha |


## Roadmap

- [ ] Better templating system (currently stored in /resources/templates)

- [ ] Endpoint to send email

