package com.vandeas.logic.impl

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
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

    companion object {
        private const val RESEND_BATCH_LIMIT = 100
    }

    private val mailers: MutableMap<String, Mailer> = mutableMapOf() //TODO: Update mailers on config change/deletion

    override suspend fun sendContactForm(form: ContactForm): SendOperationResult {
        val config = contactFormConfigHandler.get(form.id)

        if (!limiter.canSendMail(config)) {
            throw DailyLimitExceededException(config.dailyLimit)
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

        val mailInputsByConfigByIdentifier = mailInputsByConfig.entries.map {
            it.toPair()
        }.groupBy { (config, _) ->
            config.identifierFromCredentials()
        }

        if (
            mailInputsByConfigByIdentifier.entries.any { (_, mails) ->
                mails.sumOf { (_, mailInputs) -> mailInputs.size } > RESEND_BATCH_LIMIT
            }
        ) {
            throw IllegalArgumentException("Resend Batch limit exceeded")
        }

        mailInputsByConfigByIdentifier.map { (identifier, mailInputsByConfig) ->
            async {
                (mailers[identifier] ?: mailConfigHandler.get(identifier).toMailer().also { mailers[identifier] = it }).sendEmails(
                    mails = mailInputsByConfig.flatMap { (config, mailInputs) ->
                        val contentTemplate = Template.parse(mailConfigHandler.getTemplate(config.id))
                        val subjectTemplate = Template.parse(config.subjectTemplate)

                       mailInputs.map { mailInput ->
                            Mail(
                                from = config.sender,
                                to = mailInput.email,
                                subject = subjectTemplate.processToString(mailInput.attributes),
                                content = contentTemplate.processToString(mailInput.attributes)
                            )
                        }
                    }
                )
            }
        }.awaitAll().let { sendResults ->
            SendOperationResult(
                sent = sendResults.flatMap { it.sent },
                failed = sendResults.flatMap { it.failed }
            )
        }
    }
}
