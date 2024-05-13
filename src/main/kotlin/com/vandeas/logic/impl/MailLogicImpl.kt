package com.vandeas.logic.impl

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
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
    private val mailConfigHandler: ConfigDirectory<MailConfig>,
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val googleReCaptcha: ReCaptcha,
    private val limiter: DailyLimiter
) : MailLogic {

    private val mailers: MutableMap<String, Mailer> = mutableMapOf() //TODO: Update mailers on config change/deletion

    override suspend fun sendContactForm(form: ContactForm): Response {
        val config = contactFormConfigHandler.get(form.id)

        if (!limiter.canSendMail(config)) {
            return Response(HttpStatusCode.TooManyRequests.value, "Daily limit reached")
        }

        val grResponse = googleReCaptcha.verify(System.getenv("GOOGLE_RECAPTCHA_SECRET"), form.recaptchaToken)

        if (!grResponse.success && grResponse.score >= config.threshold) {
            throw RecaptchaFailedException()
        }

        limiter.recordMailSent(config)

        val contentTemplate = Template.parse(contactFormConfigHandler.getTemplate(config.id))
        val subjectTemplate = Template.parse(config.subjectTemplate)

        val mailer = mailers[config.identifierFromCredentials()] ?: config.toMailer().also { mailers[config.identifierFromCredentials()] = it }

        return mailer.sendEmail(
            from = config.sender,
            to = config.destination,
            subject = subjectTemplate.processToString(mapOf("form" to form)),
            content = contentTemplate.processToString(mapOf("form" to form))
        )
    }

    override suspend fun sendMail(mailInput: MailInput): Response {
        val config = mailConfigHandler.get(mailInput.id)
        val contentTemplate = Template.parse(mailConfigHandler.getTemplate(config.id))
        val subjectTemplate = Template.parse(config.subjectTemplate)

        val mailer = mailers[config.identifierFromCredentials()] ?: config.toMailer().also { mailers[config.identifierFromCredentials()] = it }

        return mailer.sendEmail(
            from = config.sender,
            to = mailInput.email,
            subject = subjectTemplate.processToString(mailInput.attributes),
            content = contentTemplate.processToString(mailInput.attributes)
        )
    }

    override suspend fun sendMails(batch: List<MailInput>): List<BatchResponse> = withContext(Dispatchers.Default) {
        val mailsByConfigId = batch.groupBy { mailInput ->
            mailConfigHandler.get(mailInput.id)
        }

        mailsByConfigId.entries.map { (config, mails) ->
            async {
                (mailers[config.identifierFromCredentials()] ?: config.toMailer().also { mailers[config.identifierFromCredentials()] = it }).sendEmails(
                    mails = mails.map { mailInput ->
                        val contentTemplate = Template.parse(mailConfigHandler.getTemplate(config.id))
                        val subjectTemplate = Template.parse(config.subjectTemplate)

                        Mail(
                            from = config.sender,
                            to = mailInput.email,
                            subject = subjectTemplate.processToString(mailInput.attributes),
                            content = contentTemplate.processToString(mailInput.attributes)
                        )
                    }
                )
            }
        }.awaitAll()
    }
}
