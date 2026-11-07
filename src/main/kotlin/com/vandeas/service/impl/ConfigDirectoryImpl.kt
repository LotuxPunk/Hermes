package com.vandeas.service.impl

import com.vandeas.dto.configs.Config
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.dto.configs.validate
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.FileEventListener
import com.vandeas.service.FileHandler
import io.ktor.util.logging.*
import kotlinx.serialization.json.Json
import java.io.File

class ConfigDirectoryImpl<T: Config>(
    directory: File,
    private val mapper : (String) -> T,
    private val templateHandler: FileHandler
) : ConfigDirectory<T>, FileHandlerImpl(directory), FileEventListener {

    private val logger = KtorSimpleLogger("com.vandeas.service.impl.ConfigDirectoryImpl")

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
        return templateHandler.getFileContent(configs[id]?.second?.id ?: throw NoSuchElementException("Template with id $id not found"))
    }

    override fun getConfirmationTemplate(id: String): String? {
        return try {
            templateHandler.getFileContent("$id${ConfigDirectory.CONFIRMATION_TEMPLATE_SUFFIX}")
        } catch (_: NoSuchElementException) {
            null
        }
    }

    override fun onFileCreate(fileName: String, content: String) {
        val config = parseOrNull(fileName, content) ?: return
        configs[config.id] = fileName to config
    }

    /**
     * A modified file that no longer parses or validates is rejected, and the version loaded
     * before stays in place, so a mistake while editing does not take the form down.
     */
    override fun onFileModify(fileName: String, content: String) {
        val config = parseOrNull(fileName, content) ?: return
        configs[config.id] = fileName to config
    }

    override fun onFileDelete(fileName: String) {
        // A rejected file was never loaded, so it may have no entry to remove.
        val entryToRemove = configs.firstNotNullOfOrNull { (id, entry) -> entry.takeIf { it.first == fileName }?.let { id } }
            ?: return
        configs.remove(entryToRemove)
    }

    /**
     * Parses and validates one config file.
     *
     * An exception thrown here would escape into the directory watcher and stop every later
     * file from loading, so a bad file is logged and skipped instead.
     */
    private fun parseOrNull(fileName: String, content: String): T? {
        return try {
            mapper(content)
        } catch (e: Exception) {
            logger.error("Rejected config file $fileName: ${e.message}")
            null
        }
    }
}

fun String.toMailConfig(): MailConfig {
    return Json.decodeFromString(this)
}

/**
 * @throws IllegalArgumentException when the config is malformed or fails
 * [ContactFormConfig.validate].
 */
fun String.toContactFormConfig(): ContactFormConfig {
    return Json.decodeFromString<ContactFormConfig>(this).also { it.validate() }
}
