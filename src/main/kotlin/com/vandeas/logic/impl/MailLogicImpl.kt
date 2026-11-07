package com.vandeas.logic.impl

import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.ContactForm
import com.vandeas.dto.GoogleRecaptchaContactForm
import com.vandeas.dto.KerberusContactForm
import com.vandeas.dto.MailInput
import com.vandeas.dto.configs.Config
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.VisitorConfirmationConfig
import com.vandeas.dto.configs.captcha.GoogleRecaptchaConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.entities.Attachment
import com.vandeas.entities.ContactFormResult
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.entities.VisitorConfirmationStatus
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.service.*
import com.vandeas.service.impl.captcha.GoogleReCaptcha
import com.vandeas.service.impl.captcha.KerberusCaptcha
import com.vandeas.utils.bareEmailAddress
import com.vandeas.utils.isValidEmailAddress
import com.vandeas.utils.maskEmailAddress
import io.ktor.util.logging.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import com.vandeas.service.TemplateRenderer

/**
 * @param confirmationLimiter Caps the Visitor confirmations one address receives per day.
 * @param mailerFactory Builds the mailer of a config. Tests replace it with a fake mailer.
 */
class MailLogicImpl(
    private val mailConfigHandler: ConfigDirectory<MailConfig>,
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val limiter: DailyLimiter,
    private val confirmationLimiter: ConfirmationRecipientLimiter,
    private val mailerFactory: (Config) -> Mailer = Config::toMailer,
) : MailLogic {

    companion object {
        private const val RESEND_BATCH_LIMIT = 100
        private const val BROADCAST_RECIPIENT_LIMIT = 50
    }

    private val logger = KtorSimpleLogger("com.vandeas.logic.impl.MailLogicImpl")

    private val mailers: MutableMap<String, Mailer> = mutableMapOf() //TODO: Update mailers on config change/deletion

    private fun MailConfig.getMailerOrCreate(): Mailer = mailers[id] ?: mailerFactory(this).also { mailers[id] = it }

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

    override suspend fun sendContactForm(form: ContactForm): ContactFormResult {
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

        val mailer = mailers[config.identifierFromCredentials()] ?: mailerFactory(config).also { mailers[config.identifierFromCredentials()] = it }

        val context = mapOf("form" to form)
        val subject = TemplateRenderer.renderPlainText(config.subjectTemplate, context)
        val content = TemplateRenderer.renderHtml(contactFormConfigHandler.getTemplate(config.id), context)

        val orgNotification = mailer.sendEmailsWithRetry(
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

        val confirmation = config.confirmation
            ?: return ContactFormResult(orgNotification)

        return ContactFormResult(
            orgNotification = orgNotification,
            confirmation = sendVisitorConfirmation(config, confirmation, form, mailer, orgNotification),
        )
    }

    /**
     * Sends the Visitor confirmation of a Submission whose Org notification has been sent.
     *
     * Best-effort: every reason not to send is logged with the config id and a masked
     * address, and reported as [VisitorConfirmationStatus.NOT_SENT], never thrown, so the
     * Submission's HTTP status still comes from the Org notification alone.
     *
     * The steps run in this order: Org notification check, template lookup, Confirmation
     * recipient limit, render, send. A Submission therefore takes a unit of the limit only
     * when a confirmation is about to be sent, and never takes a Daily limit unit.
     */
    private suspend fun sendVisitorConfirmation(
        config: ContactFormConfig,
        confirmation: VisitorConfirmationConfig,
        form: ContactForm,
        mailer: Mailer,
        orgNotification: SendOperationResult,
    ): VisitorConfirmationStatus {
        val maskedAddress = form.email.maskEmailAddress()

        fun notSent(reason: String): VisitorConfirmationStatus {
            logger.error("Visitor confirmation not sent for config ${config.id} to $maskedAddress: $reason")
            return VisitorConfirmationStatus.NOT_SENT
        }

        // Not FAILED alone is not enough: a result can hold only bounces. The visitor must
        // not be told their message was received unless it was sent, or queued, to someone.
        // A queued mailer reports every enqueued mail as sent, so with the queue on this
        // cannot catch a delivery that fails later in a worker.
        if (orgNotification.sent.isEmpty()) {
            return notSent("the Org notification was not sent")
        }

        val template = contactFormConfigHandler.getConfirmationTemplate(config.id)
            ?: return notSent("template ${config.id}${ConfigDirectory.CONFIRMATION_TEMPLATE_SUFFIX}.hbs not found")

        if (!confirmationLimiter.tryAcquire(form.email)) {
            return notSent("Confirmation recipient limit reached")
        }

        return try {
            val context = mapOf("form" to form.neutralised())
            val result = mailer.sendEmailsWithRetry(
                mails = listOf(
                    Mail(
                        from = config.sender,
                        to = form.email,
                        subject = TemplateRenderer.renderPlainText(confirmation.subjectTemplate, context),
                        content = TemplateRenderer.renderHtml(template, context),
                        replyTo = config.destination,
                    )
                ),
                maxRetries = 3,
                retryDelayMs = 1000L
            )

            if (result.sent.isEmpty()) {
                notSent("the mailer did not send it")
            } else {
                logger.info("Visitor confirmation sent for config ${config.id} to $maskedAddress")
                VisitorConfirmationStatus.SENT
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Mailer and parser messages can quote the recipient, so it is masked here too.
            val message = listOf(form.email, form.email.bareEmailAddress())
                .fold(e.message.orEmpty()) { text, address -> text.replace(address, maskedAddress) }
            notSent("${e::class.simpleName}: $message")
        }
    }

    /**
     * Copy of this form with every free-text field passed through [LinkNeutraliser], for the
     * Visitor confirmation templates. The config `id` is kept as is: it has to match an
     * operator's config, so the visitor does not choose its content.
     */
    private fun ContactForm.neutralised(): ContactForm {
        val fullName = LinkNeutraliser.neutralise(fullName)
        val email = LinkNeutraliser.neutralise(email)
        val content = LinkNeutraliser.neutralise(content)
        val phone = phone?.let(LinkNeutraliser::neutralise)
        val topic = topic?.let(LinkNeutraliser::neutralise)
        val destinations = destinations.map(LinkNeutraliser::neutralise)

        return when (this) {
            is GoogleRecaptchaContactForm -> copy(
                fullName = fullName, email = email, content = content,
                phone = phone, topic = topic, destinations = destinations,
            )
            is KerberusContactForm -> copy(
                fullName = fullName, email = email, content = content,
                phone = phone, topic = topic, destinations = destinations,
            )
        }
    }

    override suspend fun sendMail(mailInput: MailInput, attachments: List<Attachment>): SendOperationResult {
        validateAddress(mailInput.id, "replyTo", mailInput.replyTo)

        val config = mailConfigHandler.get(mailInput.id)

        val mailer = mailers[config.identifierFromCredentials()] ?: mailerFactory(config).also { mailers[config.identifierFromCredentials()] = it }

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
