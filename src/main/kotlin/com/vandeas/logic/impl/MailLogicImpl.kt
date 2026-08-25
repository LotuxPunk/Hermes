package com.vandeas.logic.impl

import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.ContactForm
import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.KerberusContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.captcha.GoogleRecaptchaConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.entities.Attachment
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
import com.vandeas.service.impl.captcha.GoogleReCaptcha
import com.vandeas.service.impl.captcha.KerberusCaptcha
import com.vandeas.utils.isValidEmailAddress
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import com.vandeas.service.TemplateRenderer

class MailLogicImpl(
    private val mailConfigHandler: ConfigDirectory<MailConfig>,
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val limiter: DailyLimiter
) : MailLogic {

    companion object {
        private const val RESEND_BATCH_LIMIT = 100
        private const val BROADCAST_RECIPIENT_LIMIT = 50
    }

    private val logger = KtorSimpleLogger("com.vandeas.logic.impl.MailLogicImpl")

    private val mailers: MutableMap<String, Mailer> = mutableMapOf() //TODO: Update mailers on config change/deletion

    private fun MailConfig.getMailerOrCreate(): Mailer = mailers[id] ?: this.toMailer().also { mailers[id] = it }

    /**
     * Rejects a malformed address before anything is sent.
     *
     * The queue acknowledges an enqueued mail immediately, so an address that only fails
     * inside a worker never reaches the caller. Validating here turns that silent loss
     * into a bad request.
     *
     * @param field name of the request field being checked, used in the log line and the
     * error returned to the caller.
     * @throws IllegalArgumentException when [address] is present and unparseable.
     */
    private fun validateAddress(configId: String, field: String, address: String?) {
        if (address == null || address.isValidEmailAddress()) {
            return
        }

        logger.warn("Rejected mail for config $configId: invalid $field '$address'")
        throw IllegalArgumentException("Invalid $field address: $address")
    }

    override suspend fun sendContactForm(form: ContactForm): SendOperationResult {
        val config = contactFormConfigHandler.get(form.id)

        // Checked before the rate limiter and the captcha: a malformed address is a bad
        // request either way, and rejecting it here avoids a captcha round trip.
        validateAddress(form.id, "email", form.email)

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

        val mailer = mailers[config.identifierFromCredentials()] ?: config.toMailer().also { mailers[config.identifierFromCredentials()] = it }

        val context = mapOf("form" to form)
        val subject = TemplateRenderer.renderPlainText(config.subjectTemplate, context)
        val content = TemplateRenderer.renderHtml(contactFormConfigHandler.getTemplate(config.id), context)

        return mailer.sendEmailsWithRetry(
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
            ),
            maxRetries = 3,
            retryDelayMs = 1000L
        )
    }

    override suspend fun sendMail(mailInput: MailInput, attachments: List<Attachment>): SendOperationResult {
        validateAddress(mailInput.id, "replyTo", mailInput.replyTo)

        val config = mailConfigHandler.get(mailInput.id)

        val mailer = mailers[config.identifierFromCredentials()] ?: config.toMailer().also { mailers[config.identifierFromCredentials()] = it }

        return mailer.sendEmail(
            from = config.sender,
            to = mailInput.email,
            subject = TemplateRenderer.renderPlainText(config.subjectTemplate, mailInput.attributes),
            content = TemplateRenderer.renderHtml(mailConfigHandler.getTemplate(config.id), mailInput.attributes),
            attachments = attachments,
            replyTo = mailInput.replyTo
        )
    }

    override suspend fun sendMails(batch: List<MailInput>): SendOperationResult = withContext(Dispatchers.Default) {
        // Validated up front so a single malformed address cannot produce a partial send.
        val invalidReplyTos = batch.mapNotNull { mailInput ->
            mailInput.replyTo
                ?.takeUnless { it.isValidEmailAddress() }
                ?.let { "${mailInput.email} -> $it" }
        }

        if (invalidReplyTos.isNotEmpty()) {
            logger.warn("Rejected batch of ${batch.size} mails, invalid replyTo addresses: ${invalidReplyTos.joinToString()}")
        }

        require(invalidReplyTos.isEmpty()) {
            "Invalid replyTo addresses: ${invalidReplyTos.joinToString()}"
        }

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
                mailerByIdentifier[identifier]!!.sendEmailsWithRetry(
                    mails = mailInputs.map { mailInput ->
                        val mailConfig = mailConfigHandler.get(mailInput.id)

                        Mail(
                            from = mailConfig.sender,
                            to = mailInput.email,
                            subject = TemplateRenderer.renderPlainText(mailConfig.subjectTemplate, mailInput.attributes),
                            content = TemplateRenderer.renderHtml(mailConfigHandler.getTemplate(mailInput.id), mailInput.attributes),
                            replyTo = mailInput.replyTo
                        )
                    },
                    maxRetries = 3,
                    retryDelayMs = 1000L
                )
            }
        }.awaitAll()

        SendOperationResult(
            sent = sendResults.flatMap { it.sent },
            failed = sendResults.flatMap { it.failed },
            bounced = sendResults.flatMap { it.bounced },
            temporary = sendResults.flatMap { it.temporary }
        )
    }

    override suspend fun broadcastMail(
        configId: String,
        request: BroadcastMailRequest,
        attachments: List<Attachment>
    ): SendOperationResult {
        require(request.to.isNotEmpty()) { "Recipient list must not be empty" }
        require(request.to.size <= BROADCAST_RECIPIENT_LIMIT) {
            "Recipient list exceeds the maximum of $BROADCAST_RECIPIENT_LIMIT"
        }
        validateAddress(configId, "replyTo", request.replyTo)

        val config = mailConfigHandler.get(configId)
        val mailer = config.getMailerOrCreate()

        val subject = TemplateRenderer.renderPlainText(config.subjectTemplate, request.attributes)
        val content = TemplateRenderer.renderHtml(mailConfigHandler.getTemplate(config.id), request.attributes)

        return mailer.sendEmailsWithRetry(
            mails = request.to.map { recipient ->
                Mail(
                    from = config.sender,
                    to = recipient,
                    subject = subject,
                    content = content,
                    attachments = attachments,
                    replyTo = request.replyTo
                )
            },
            maxRetries = 3,
            retryDelayMs = 1000L
        )
    }
}
