package com.vandeas.service

import com.vandeas.dto.configs.Config

interface ConfigDirectory<T: Config> {
    fun get(id: String): T
    fun getAll(): Map<String, T>
    fun getTemplate(id: String): String
}
