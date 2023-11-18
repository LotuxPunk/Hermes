package com.vandeas.logic.impl

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.vandeas.dto.ContactForm
import com.vandeas.dto.Mail
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
import io.ktor.http.*
import net.pwall.mustache.Template
import java.io.StringWriter

class MailLogicImpl(
    private val mailer: Mailer,
    private val configLoader: ConfigLoader,
    private val googleReCaptcha: ReCaptcha,
    private val limiter: DailyLimiter
) : MailLogic {

    override suspend fun sendContactForm(form: ContactForm): Response {
        val config = configLoader.getContactFormConfig(form.id)

        if (!limiter.canSendMail(config)) {
            return Response(HttpStatusCode.TooManyRequests.value, "Daily limit reached")
        }

        val grResponse = googleReCaptcha.verify(System.getenv("GOOGLE_RECAPTCHA_SECRET"), form.recaptchaToken)

        if (!grResponse.success && grResponse.score >= config.threshold) {
            throw RecaptchaFailedException()
        }

        limiter.recordMailSent(config)

        val contentTemplate = Template.parse(configLoader.getTemplate(config.id))
        val subjectTemplate = Template.parse(config.subjectTemplate)

        return mailer.sendEmail(
            from = config.sender,
            to = config.destination,
            subject = subjectTemplate.processToString(mapOf("form" to form)),
            content = contentTemplate.processToString(mapOf("form" to form))
        )
    }

    override suspend fun sendMail(mail: Mail): Response {
        val config = configLoader.getMailConfig(mail.id)
        val contentTemplate = Template.parse(configLoader.getTemplate(config.id))
        val subjectTemplate = Template.parse(config.subjectTemplate)

        return mailer.sendEmail(
            from = config.sender,
            to = mail.email,
            subject = subjectTemplate.processToString(mail.attributes),
            content = contentTemplate.processToString(mail.attributes)
        )
    }

}
