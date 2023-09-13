package com.vandeas.service

import com.vandeas.dto.ContactFormConfig

interface ConfigLoader {
    fun getContactFormConfig(id: String): ContactFormConfig
}