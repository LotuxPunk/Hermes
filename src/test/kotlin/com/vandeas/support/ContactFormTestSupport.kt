package com.vandeas.support

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.icure.kerberus.resolveChallenge
import com.icure.kryptom.crypto.defaultCryptoService
import com.vandeas.dto.KerberusContactForm
import com.vandeas.dto.configs.Config
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.entities.Attachment
import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.Mailer
import com.vandeas.service.impl.captcha.KerberusCaptcha
import org.slf4j.LoggerFactory
import java.util.Collections

/**
 * Mailer that records every mail it is asked to send.
 *
 * @param outcome Decides the result of each mail. Defaults to sent.
 */
class RecordingMailer(
    var outcome: (Mail) -> SendOperationResult = { SendOperationResult(sent = listOf(it.to)) },
) : Mailer {
    val mails: MutableList<Mail> = Collections.synchronizedList(mutableListOf())

    override suspend fun sendEmail(
        to: String,
        from: String,
        subject: String,
        content: String,
        attachments: List<Attachment>,
        replyTo: String?,
    ): SendOperationResult = sendEmails(listOf(Mail(from, to, subject, content, attachments, replyTo)))

    override suspend fun sendEmails(mails: List<Mail>): SendOperationResult =
        mails.map { mail ->
            this.mails.add(mail)
            outcome(mail)
        }.fold(SendOperationResult()) { acc, result -> acc + result }

    fun mailsTo(address: String): List<Mail> = mails.filter { it.to == address }
}

/**
 * In-memory [ConfigDirectory]. Templates are keyed like the templates folder: the file name
 * without its `.hbs` extension, so a Visitor confirmation template is `<id>.confirmation`.
 */
class FakeConfigDirectory<T : Config>(
    configs: List<T> = emptyList(),
    val templates: MutableMap<String, String> = mutableMapOf(),
) : ConfigDirectory<T> {
    private val configs = configs.associateBy { it.id }

    override fun get(id: String): T = configs[id] ?: throw NoSuchElementException("Config with id $id not found")
    override fun getAll(): Map<String, T> = configs
    override fun getTemplate(id: String): String = templates[id] ?: throw NoSuchElementException("File $id not found")
    override fun getConfirmationTemplate(id: String): String? =
        templates["$id${ConfigDirectory.CONFIRMATION_TEMPLATE_SUFFIX}"]
}

/**
 * Builds a Kerberus contact form for [config] with a real, solved captcha, so the Submission
 * goes through the captcha check like one from a browser.
 */
suspend fun solvedKerberusForm(
    config: ContactFormConfig,
    email: String,
    fullName: String = "Jane Visitor",
    content: String = "Hello, I would like to know more.",
    topic: String? = null,
    phone: String? = null,
): KerberusContactForm {
    val captcha = config.captcha as KerberusConfig
    val challenge = KerberusCaptcha.get(captcha.secretKey).generateChallenge(captcha)
    val solution = resolveChallenge(challenge, captcha.secretKey, defaultCryptoService) {}
    return KerberusContactForm(
        id = config.id,
        fullName = fullName,
        email = email,
        content = content,
        phone = phone,
        topic = topic,
        solution = solution,
    )
}

/** Captures the log lines of the named loggers until [close]. */
class CapturedLogs(vararg loggerNames: String) : AutoCloseable {
    private val appender = ListAppender<ILoggingEvent>().apply { start() }
    private val loggers = loggerNames.map { LoggerFactory.getLogger(it) as Logger }

    init {
        loggers.forEach { it.addAppender(appender) }
    }

    val messages: List<String>
        get() = synchronized(appender.list) { appender.list.map { it.formattedMessage } }

    override fun close() {
        loggers.forEach { it.detachAppender(appender) }
        appender.stop()
    }
}
