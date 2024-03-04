package com.vandeas.service

import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig

interface ConfigLoader {
    fun getContactFormConfig(id: String): ContactFormConfig
    fun getContactFormConfigs(): List<ContactFormConfig>
    fun getMailConfig(id: String): MailConfig
    fun getMailConfigs(): List<MailConfig>
    fun getTemplate(id: String): String
}
