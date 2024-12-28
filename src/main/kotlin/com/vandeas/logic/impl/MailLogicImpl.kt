package com.vandeas.logic.impl

import com.vandeas.dto.ContactForm
import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.KerberusContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.captcha.GoogleRecaptchaConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
import com.vandeas.service.impl.captcha.GoogleReCaptcha
import com.vandeas.service.impl.captcha.KerberusCaptcha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import net.pwall.mustache.Template

class MailLogicImpl(
    private val mailConfigHandler: ConfigDirectory<MailConfig>,
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val limiter: DailyLimiter
) : MailLogic {

    companion object {
        private const val RESEND_BATCH_LIMIT = 100
    }

    private val mailers: MutableMap<String, Mailer> = mutableMapOf() //TODO: Update mailers on config change/deletion

    private fun MailConfig.getMailerOrCreate(): Mailer = mailers[id] ?: this.toMailer().also { mailers[id] = it }

    override suspend fun sendContactForm(form: ContactForm): SendOperationResult {
        val config = contactFormConfigHandler.get(form.id)

        if (!limiter.canSendMail(config)) {
            throw DailyLimitExceededException(config.dailyLimit)
        }

        val captchaResult = when (val captchaConfig = config.captcha) {
            is GoogleRecaptchaConfig if form is GoogleRecaptchaContactForm -> GoogleReCaptcha.verify(captchaConfig, form.recaptchaToken)
            is KerberusConfig if form is KerberusContactForm -> KerberusCaptcha.get(captchaConfig.secretKey).verify(captchaConfig, form.solution)
            else -> throw IllegalArgumentException("Invalid captcha config")
        }

        if (captchaResult is CaptchaResult.Failure) {
            throw RecaptchaFailedException()
        }

        limiter.recordMailSent(config)

        val contentTemplate = Template.parse(contactFormConfigHandler.getTemplate(config.id))
        val subjectTemplate = Template.parse(config.subjectTemplate)

        val mailer = mailers[config.identifierFromCredentials()] ?: config.toMailer().also { mailers[config.identifierFromCredentials()] = it }

        val subject = subjectTemplate.processToString(mapOf("form" to form))
        val content = contentTemplate.processToString(mapOf("form" to form))

        return mailer.sendEmails(
            mails = form.destinations.takeIf { it.isNotEmpty() }?.map { destination ->
                Mail(
                    from = config.sender,
                    to = destination,
                    subject = subject,
                    content = content
                )
            } ?: listOf(
                Mail(
                    from = config.sender,
                    to = config.destination,
                    subject = subject,
                    content = content
                )
            )
        )
    }

    override suspend fun sendMail(mailInput: MailInput): SendOperationResult {
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

    override suspend fun sendMails(batch: List<MailInput>): SendOperationResult = withContext(Dispatchers.Default) {
        val mailInputsByConfig = batch.groupBy { mailInput ->
            mailConfigHandler.get(mailInput.id)
        }

        val mailerByIdentifier = mailInputsByConfig.keys.groupBy { config ->
            config.identifierFromCredentials()
        }.mapValues { (_, configs) ->
            configs.first().getMailerOrCreate()
        }

        val mailInputsByIdentifier = mailInputsByConfig.entries.groupBy { (config, _) ->
            config.identifierFromCredentials()
        }.mapValues { (_, mails) ->
            mails.flatMap { (_, mailInputs) -> mailInputs }
        }

        require(mailInputsByIdentifier.values.all { mails -> mails.size <= RESEND_BATCH_LIMIT }) {
            "Resend Batch limit exceeded"
        }

        val sendResults = mailInputsByIdentifier.map { (identifier, mailInputs) ->
            async {
                mailerByIdentifier[identifier]!!.sendEmails(
                    mails = mailInputs.map { mailInput ->
                        val contentTemplate = Template.parse(mailConfigHandler.getTemplate(mailInput.id))
                        val subjectTemplate = Template.parse(mailConfigHandler.get(mailInput.id).subjectTemplate)

                        Mail(
                            from = mailConfigHandler.get(mailInput.id).sender,
                            to = mailInput.email,
                            subject = subjectTemplate.processToString(mailInput.attributes),
                            content = contentTemplate.processToString(mailInput.attributes)
                        )
                    }
                )
            }
        }.awaitAll()

        SendOperationResult(
            sent = sendResults.flatMap { it.sent },
            failed = sendResults.flatMap { it.failed }
        )
    }
}
