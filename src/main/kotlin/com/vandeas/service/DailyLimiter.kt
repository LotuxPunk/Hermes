package com.vandeas.service

import com.vandeas.dto.configs.ContactFormConfig

/**
 * A utility class designed to limit the number of emails sent from a web form based on its configuration.
 */
interface DailyLimiter {
    /**
     * Records an email sent based on a specific contact form configuration.
     *
     * If the contact form identified by the configuration ID has not reached its daily limit for sending emails,
     * the email is recorded and the method returns true. If the daily limit has been reached, the method returns false
     * and the email is not recorded.
     *
     * @param config The configuration of the contact form which is trying to send the email.
     * @return true if the email was successfully recorded, false otherwise.
     */
    fun recordMailSent(config: ContactFormConfig): Boolean

    /**
     * Checks if an email can be sent based on a given contact form configuration considering the daily limit.
     *
     * If the contact form identified by the configuration ID has not reached its daily limit for sending emails,
     * the method returns true, indicating that the email can be sent. If the daily limit has been reached,
     * the method returns false.
     *
     * @param config The configuration of the contact form which intends to send the email.
     * @return true if the email can be sent, false otherwise.
     */
    fun canSendMail(config: ContactFormConfig): Boolean
}
