package com.vandeas.logic

import com.vandeas.dto.ContactForm
import com.vandeas.service.Response

interface MailLogic {
    suspend fun sendContactForm(form: ContactForm): Response
}