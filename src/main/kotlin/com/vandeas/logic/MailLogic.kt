package com.vandeas.logic

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.service.BatchResponse
import com.vandeas.service.Response

interface MailLogic {
    suspend fun sendContactForm(form: ContactForm): Response
    suspend fun sendMail(mailInput: MailInput): Response
    suspend fun sendMails(batch: List<MailInput>): List<BatchResponse>
}
