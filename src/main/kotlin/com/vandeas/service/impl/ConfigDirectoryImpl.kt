package com.vandeas.service.impl

import com.vandeas.dto.configs.Config
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.FileEventListener
import com.vandeas.service.FileHandler
import kotlinx.serialization.json.Json
import java.io.File

class ConfigDirectoryImpl<T: Config>(
    directory: File,
    private val mapper : (String) -> T,
    private val templateHandler: FileHandler
) : ConfigDirectory<T>, FileHandlerImpl(directory), FileEventListener {

    private val configs: MutableMap<String, Pair<String, T>> = mutableMapOf()

    init {
        addEventListener(this)
    }

    override fun get(id: String): T {
        return configs[id]?.second ?: throw NoSuchElementException("Config with id $id not found")
    }

    override fun getAll(): Map<String, T> {
        return configs.entries.associate { (_, entry) -> entry }
    }

    override fun getTemplate(id: String): String {
        return templateHandler.getFileContent(configs[id]?.second?.id ?: throw NoSuchElementException("Config with id $id not found"))
    }

    override fun onFileCreate(fileName: String, content: String) {
        val config = mapper(content)
        configs[config.id] = fileName to config
    }

    override fun onFileModify(fileName: String, content: String) {
        val config = mapper(content)
        configs[config.id] = fileName to config
    }

    override fun onFileDelete(fileName: String) {
        val entryToRemove = configs.firstNotNullOf { (id, entry) -> entry.takeIf { it.first == fileName }?.let { id } }
        configs.remove(entryToRemove)
    }
}

fun String.toMailConfig(): MailConfig {
    return Json.decodeFromString(this)
}

fun String.toContactFormConfig(): ContactFormConfig {
    return Json.decodeFromString(this)
}
