package com.vandeas.dto.configs

import com.vandeas.service.Mailer
import com.vandeas.service.impl.mailer.QueuedResendMailer
import com.vandeas.service.impl.mailer.QueuedSMTPMailer
import com.vandeas.service.impl.mailer.ResendMailer
import com.vandeas.service.impl.mailer.SMTPMailer
import com.vandeas.utils.Constants
import kotlinx.serialization.Serializable

const val RESEND_SERIAL_NAME = "RESEND"
const val SMTP_SERIAL_NAME = "SMTP"

interface Config {
    val id: String

    /**
     * Instantiates a new [Mailer] using this config.
     */
    fun toMailer(): Mailer

    /**
     * @return a string that uniquely identifies this config based on the credentials.
     */
    fun identifierFromCredentials(): String
}

@Serializable
abstract class ResendProvider: Config {
    protected abstract val apiKey: String

    override fun toMailer(): Mailer {
        return if (Constants.useMailQueue) {
            QueuedResendMailer(apiKey = apiKey)
        } else {
            ResendMailer(apiKey = apiKey)
        }
    }

    override fun identifierFromCredentials() = apiKey

}

@Serializable
abstract class SMTPProvider: Config {
    protected abstract val username: String
    protected abstract val password: String
    protected abstract val smtpHost: String
    protected abstract val smtpPort: Int

    override fun toMailer(): Mailer {
        return if (Constants.useMailQueue) {
            QueuedSMTPMailer(
                username = username,
                password = password,
                host = smtpHost,
                port = smtpPort
            )
        } else {
            SMTPMailer(
                username = username,
                password = password,
                host = smtpHost,
                port = smtpPort
            )
        }
    }

    override fun identifierFromCredentials() = "smtp://${username}:${password}@$smtpHost:$smtpPort"
}
