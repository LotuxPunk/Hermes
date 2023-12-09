package com.vandeas.service.impl

import com.vandeas.dto.ContactFormConfig
import com.vandeas.dto.MailConfig
import com.vandeas.service.ConfigLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
class FilesConfigLoaderImpl: ConfigLoader {

    private var contactFormConfigs: MutableMap<String, ContactFormConfig>
    private var mailConfigs: MutableMap<String, MailConfig>
    private var templates: MutableMap<String, String>

    companion object {
        val contactFormConfigsDir = File(System.getenv("CONTACT_FORM_CONFIGS_FOLDER"))
        val mailConfigsDir = File(System.getenv("MAIL_CONFIGS_FOLDER"))
        val templateDir = File(System.getenv("TEMPLATES_FOLDER"))
    }

    init {
        runBlocking {
            contactFormConfigs = async {
                contactFormConfigsDir
                    .listFiles { _, name -> name.endsWith(".json") }
                    ?.filter { it.isFile }
                    ?.map {
                        Json.decodeFromStream<ContactFormConfig>(it.inputStream())
                    }?.associateBy {
                        it.id
                    }
            }.await()?.toMutableMap() ?: mutableMapOf()

            mailConfigs = async {
                mailConfigsDir
                    .listFiles { _, name -> name.endsWith(".json") }
                    ?.filter { it.isFile }
                    ?.map {
                        Json.decodeFromStream<MailConfig>(it.inputStream())
                    }?.associateBy {
                        it.id
                    }
            }.await()?.toMutableMap() ?: mutableMapOf()

            templates = async {
                templateDir
                    .listFiles { _, name -> name.endsWith(".hbs") }
                    ?.filter { it.isFile }
                    ?.map {
                        it.name to it.inputStream().readBytes().toString(Charsets.UTF_8)
                    }?.associate {(name, content) ->
                        name.split(".").let {
                            it.subList(0, it.size - 1).joinToString(".")
                        } to content
                    }
            }.await()?.toMutableMap() ?: mutableMapOf()
        }
    }

    override fun getContactFormConfig(id: String): ContactFormConfig {
        return contactFormConfigs[id] ?: throw IllegalArgumentException("No contact form config found for id $id")
    }

    override fun getContactFormConfigs(): List<ContactFormConfig> {
        return contactFormConfigs.values.toList()
    }

    override fun getMailConfig(id: String): MailConfig {
        return mailConfigs[id] ?: throw IllegalArgumentException("No mail config found for id $id")
    }

    override fun getMailConfigs(): List<MailConfig> {
        return mailConfigs.values.toList()
    }

    override fun getTemplate(id: String): String {
        return templates[id] ?: throw IllegalArgumentException("No template found for id $id")
    }
}
