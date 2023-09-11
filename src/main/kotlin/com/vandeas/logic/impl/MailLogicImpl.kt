package com.vandeas.logic.impl

import com.vandeas.dto.ContactForm
import com.vandeas.logic.MailerLogic
import com.vandeas.service.GoogleReCaptcha
import com.vandeas.service.Mailer
import com.vandeas.service.impl.SendGridMailer

class MailerLogicImpl : MailerLogic {

    private val googleReCaptcha = GoogleReCaptcha()
    private val mailer: Mailer = SendGridMailer()
    override suspend fun sendContactForm(form: ContactForm): Boolean {
        if (!googleReCaptcha.verify(System.getenv("GOOGLE_RECAPTCHA_SECRET"), form.recaptchaToken)) {
            throw RecaptchaFailedException()
        }
    }
}