package com.vandeas.logic

import com.vandeas.dto.ContactForm

interface MailerLogic {
    suspend fun sendContactForm(form: ContactForm): Boolean
}