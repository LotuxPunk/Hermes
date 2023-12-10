package com.vandeas.logic.impl

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.toMailer
import com.vandeas.entities.Mail
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.pwall.mustache.Template

class MailLogicImpl(
    private val configLoader: ConfigLoader,
    private val googleReCaptcha: ReCaptcha,
    private val limiter: DailyLimiter
) : MailLogic {

    private val mailers: Map<String, Mailer> = configLoader.getMailConfigs().associate {
        it.apiKey to it.toMailer()
    } + configLoader.getContactFormConfigs().associate {
        it.apiKey to it.toMailer()
    }

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

        return mailers[config.id]?.sendEmail(
            from = config.sender,
            to = config.destination,
            subject = subjectTemplate.processToString(mapOf("form" to form)),
            content = contentTemplate.processToString(mapOf("form" to form))
        ) ?: Response(HttpStatusCode.NotFound.value, "No mailer found for ${config.id}")
    }

    override suspend fun sendMail(mailInput: MailInput): Response {
        val config = configLoader.getMailConfig(mailInput.id)
        val contentTemplate = Template.parse(configLoader.getTemplate(config.id))
        val subjectTemplate = Template.parse(config.subjectTemplate)

        return mailers[config.id]?.sendEmail(
            from = config.sender,
            to = mailInput.email,
            subject = subjectTemplate.processToString(mailInput.attributes),
            content = contentTemplate.processToString(mailInput.attributes)
        ) ?: Response(HttpStatusCode.NotFound.value, "No mailer found for ${config.id}")
    }

    override suspend fun sendMails(batch: List<MailInput>): List<Response> = withContext(Dispatchers.IO) {
        val mailsByConfigId = batch.groupBy { mailInput ->
            configLoader.getMailConfig(mailInput.id).apiKey
        }

        mailsByConfigId.entries.map { (apiKey, mails) ->
            async {
                mailers[apiKey]?.sendEmails(
                    mails = mails.map { mailInput ->
                        val config = configLoader.getMailConfig(mailInput.id)
                        val contentTemplate = Template.parse(configLoader.getTemplate(config.id))
                        val subjectTemplate = Template.parse(config.subjectTemplate)

                        Mail(
                            from = config.sender,
                            to = mailInput.email,
                            subject = subjectTemplate.processToString(mailInput.attributes),
                            content = contentTemplate.processToString(mailInput.attributes)
                        )
                    }
                ) ?: Response(HttpStatusCode.NotFound.value, "No mailer found")
            }
        }.awaitAll()
    }
}
