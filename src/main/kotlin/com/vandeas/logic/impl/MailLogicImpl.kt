package com.vandeas.logic.impl

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.Mustache
import com.vandeas.dto.ContactForm
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
import io.ktor.http.*
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

        return mailer.sendEmail(
            from = config.sender,
            to = config.destination,
            subject = getMustacheTemplate("${config.lang}/subject/contact_form.hbs", mapOf("form" to form)),
            content = getMustacheTemplate("${config.lang}/content/contact_form.hbs", mapOf("form" to form))
        )
    }

    private fun getMustacheFactory(): DefaultMustacheFactory {
        return DefaultMustacheFactory("templates")
    }

    private fun getMustacheTemplate(template: String): Mustache {
        return getMustacheFactory().compile(template)
    }

    private fun getMustacheTemplate(template: String, model: Map<String, Any>): String {
        val mustache = getMustacheTemplate(template)
        val writer = StringWriter()
        mustache.execute(writer, model).flush()
        return writer.toString()
    }
}