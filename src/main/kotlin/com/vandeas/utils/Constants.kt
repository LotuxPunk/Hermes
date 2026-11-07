package com.vandeas.utils

import com.vandeas.service.impl.InMemoryConfirmationRecipientLimiter
import java.io.File

object Constants {
    // Lazy so that reading the mail settings below does not require the folder variables,
    // which tests that build mailers from configs do not set.
    val contactFormConfigsDir by lazy { File(System.getenv("CONTACT_FORM_CONFIGS_FOLDER")) }
    val mailConfigsDir by lazy { File(System.getenv("MAIL_CONFIGS_FOLDER")) }
    val templateDir by lazy { File(System.getenv("TEMPLATES_FOLDER")) }

    // Mail queue configuration
    val useMailQueue = System.getenv("USE_MAIL_QUEUE")?.toBoolean() ?: true
    val mailRateLimit = System.getenv("MAIL_RATE_LIMIT")?.toIntOrNull() ?: 10

    // Visitor confirmations one address may receive per day, across every contact form config
    val confirmationRecipientDailyLimit by lazy {
        InMemoryConfirmationRecipientLimiter.parseLimit(System.getenv("CONFIRMATION_RECIPIENT_DAILY_LIMIT"))
    }
}
