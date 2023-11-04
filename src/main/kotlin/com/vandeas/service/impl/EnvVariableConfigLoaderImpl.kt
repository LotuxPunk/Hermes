package com.vandeas.service.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vandeas.dto.ContactFormConfig
import com.vandeas.dto.MailConfig
import com.vandeas.service.ConfigLoader

class EnvVariableConfigLoaderImpl : ConfigLoader {

    private val configs: Map<String, ContactFormConfig> = System.getenv("CONTACT_FORM_CONFIGS").let {
        (jacksonObjectMapper().readValue(it, object : TypeReference<List<ContactFormConfig>>() {})).associateBy { config ->
            config.id
        }
    }

    private val mailConfigs: Map<String, MailConfig> = System.getenv("MAIL_CONFIGS").let {
        (jacksonObjectMapper().readValue(it, object : TypeReference<List<MailConfig>>() {})).associateBy { config ->
            config.id
        }
    }

    override fun getContactFormConfig(id: String): ContactFormConfig {
        return configs[id] ?: throw IllegalArgumentException("No config found for id $id")
    }

    override fun getMailConfig(id: String): MailConfig {
        return mailConfigs[id] ?: throw IllegalArgumentException("No config found for id $id")
    }
}
