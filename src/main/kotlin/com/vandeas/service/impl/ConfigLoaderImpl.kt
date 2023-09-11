package com.vandeas.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vandeas.dto.ContactFormConfig

class ConfigLoaderImpl : ConfigLoader {

    private val configs: Map<String, ContactFormConfig> = System.getenv("CONTACT_FORM_CONFIGS").let {
        (jacksonObjectMapper().readValue(it, List::class.java) as List<ContactFormConfig>).associateBy { config ->
            config.id
        }
    }

    override fun getContactFormConfig(id: String): ContactFormConfig {
        return configs[id] ?: throw IllegalArgumentException("No config found for id $id")
    }
}