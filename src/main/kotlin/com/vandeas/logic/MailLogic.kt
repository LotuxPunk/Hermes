package com.vandeas.logic

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.entities.SendOperationResult

interface MailLogic {
    /**
     * Sends a contact form
     * @param form The contact form to send
     * @return The result of the send operation
     */
    suspend fun sendContactForm(form: ContactForm): SendOperationResult

    /**
     * Sends a mail
     * @param mailInput The mail to send
     */
    suspend fun sendMail(mailInput: MailInput): SendOperationResult

    /**
     * Sends a batch of mails
     * @param batch The batch of mails to send
     */
    suspend fun sendMails(batch: List<MailInput>): SendOperationResult
}
