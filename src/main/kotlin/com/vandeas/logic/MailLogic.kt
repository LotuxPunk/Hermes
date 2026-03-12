package com.vandeas.logic

import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.entities.Attachment
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
     * @param attachments Optional list of attachments
     */
    suspend fun sendMail(mailInput: MailInput, attachments: List<Attachment> = emptyList()): SendOperationResult

    /**
     * Sends a batch of mails
     * @param batch The batch of mails to send
     */
    suspend fun sendMails(batch: List<MailInput>): SendOperationResult

    /**
     * Broadcasts the same email to multiple recipients using a single mail configuration.
     * @param configId The mail configuration ID
     * @param request The broadcast request containing recipients and template attributes
     * @param attachments Optional list of attachments sent to all recipients
     * @return The result of the send operation
     */
    suspend fun broadcastMail(configId: String, request: BroadcastMailRequest, attachments: List<Attachment> = emptyList()): SendOperationResult
}
