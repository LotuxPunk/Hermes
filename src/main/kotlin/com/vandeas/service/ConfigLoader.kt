package com.vandeas.service

import com.vandeas.dto.ContactFormConfig
import com.vandeas.dto.MailConfig

interface ConfigLoader {
    fun getContactFormConfig(id: String): ContactFormConfig
    fun getMailConfig(id: String): MailConfig
}
