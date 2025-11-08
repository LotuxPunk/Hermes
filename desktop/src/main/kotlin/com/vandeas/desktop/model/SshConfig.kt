package com.vandeas.desktop.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SshConfig(
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    val privateKeyPath: String = "",
    val privateKeyPassphrase: String = "",
    val usePrivateKey: Boolean = false,
    val templatesPath: String = "",
    val mailConfigsPath: String = "",
    val contactFormConfigsPath: String = ""
)

object ConfigManager {
    private val configFile = File(System.getProperty("user.home"), ".hermes-desktop/config.json")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        configFile.parentFile.mkdirs()
    }
    
    fun loadConfig(): SshConfig {
        return if (configFile.exists()) {
            try {
                json.decodeFromString<SshConfig>(configFile.readText())
            } catch (e: Exception) {
                SshConfig()
            }
        } else {
            SshConfig()
        }
    }
    
    fun saveConfig(config: SshConfig) {
        try {
            configFile.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
