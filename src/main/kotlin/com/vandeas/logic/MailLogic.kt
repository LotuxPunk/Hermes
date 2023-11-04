package com.vandeas.logic

import com.vandeas.dto.ContactForm
import com.vandeas.dto.Mail
import com.vandeas.service.Response

interface MailLogic {
    suspend fun sendContactForm(form: ContactForm): Response
    suspend fun sendMail(mail: Mail): Response
}
