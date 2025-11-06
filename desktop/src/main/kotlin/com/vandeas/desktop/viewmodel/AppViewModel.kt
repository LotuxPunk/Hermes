package com.vandeas.desktop.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vandeas.desktop.model.ConfigManager
import com.vandeas.desktop.model.SshConfig
import com.vandeas.desktop.ssh.FileInfo
import com.vandeas.desktop.ssh.SshFileManager
import kotlinx.coroutines.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class AppViewModel {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sshManager = SshFileManager()
    
    var config by mutableStateOf(ConfigManager.loadConfig())
        private set
    
    var isConnected by mutableStateOf(false)
        private set
    
    var connectionStatus by mutableStateOf("Not connected")
        private set
    
    var currentPath by mutableStateOf("")
        private set
    
    var files by mutableStateOf<List<FileInfo>>(emptyList())
        private set
    
    var selectedFile by mutableStateOf<FileInfo?>(null)
        private set
    
    var fileContent by mutableStateOf("")
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var errorMessage by mutableStateOf<String?>(null)
        private set
    
    var showConnectionDialog by mutableStateOf(false)
        private set
    
    private var _activeTab by mutableStateOf(TabType.TEMPLATES)
    val activeTab: TabType
        get() = _activeTab
    
    enum class TabType {
        TEMPLATES,
        MAIL_CONFIGS,
        CONTACT_FORM_CONFIGS
    }
    
    fun updateConfig(newConfig: SshConfig) {
        config = newConfig
        ConfigManager.saveConfig(newConfig)
    }
    
    fun selectTab(tab: TabType) {
        _activeTab = tab
        currentPath = when (tab) {
            TabType.TEMPLATES -> config.templatesPath
            TabType.MAIL_CONFIGS -> config.mailConfigsPath
            TabType.CONTACT_FORM_CONFIGS -> config.contactFormConfigsPath
        }
        if (isConnected && currentPath.isNotEmpty()) {
            loadFiles(currentPath)
        }
    }
    
    fun openConnectionDialog() {
        showConnectionDialog = true
    }
    
    fun closeConnectionDialog() {
        showConnectionDialog = false
    }
    
    fun connect() {
        scope.launch {
            isLoading = true
            errorMessage = null
            connectionStatus = "Connecting..."
            
            try {
                sshManager.connect(
                    host = config.host,
                    port = config.port,
                    username = config.username,
                    password = config.password
                )
                
                isConnected = true
                connectionStatus = "Connected to ${config.host}"
                showConnectionDialog = false
                
                // Load initial path based on active tab
                currentPath = when (activeTab) {
                    TabType.TEMPLATES -> config.templatesPath
                    TabType.MAIL_CONFIGS -> config.mailConfigsPath
                    TabType.CONTACT_FORM_CONFIGS -> config.contactFormConfigsPath
                }
                
                if (currentPath.isNotEmpty()) {
                    loadFiles(currentPath)
                }
            } catch (e: Exception) {
                logger.error(e) { "Connection failed" }
                connectionStatus = "Connection failed"
                errorMessage = "Failed to connect: ${e.message}"
                isConnected = false
            } finally {
                isLoading = false
            }
        }
    }
    
    fun disconnect() {
        scope.launch {
            sshManager.disconnect()
            isConnected = false
            connectionStatus = "Not connected"
            files = emptyList()
            selectedFile = null
            fileContent = ""
            currentPath = ""
        }
    }
    
    fun loadFiles(path: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                currentPath = path
                val fileList = sshManager.listFiles(path)
                files = fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            } catch (e: Exception) {
                logger.error(e) { "Failed to load files" }
                errorMessage = "Failed to load files: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun selectFile(file: FileInfo) {
        if (file.isDirectory) {
            loadFiles(file.path)
            selectedFile = null
            fileContent = ""
        } else {
            scope.launch {
                isLoading = true
                errorMessage = null
                
                try {
                    selectedFile = file
                    fileContent = sshManager.readFile(file.path)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to read file" }
                    errorMessage = "Failed to read file: ${e.message}"
                    selectedFile = null
                    fileContent = ""
                } finally {
                    isLoading = false
                }
            }
        }
    }
    
    fun saveFile(content: String) {
        val file = selectedFile ?: return
        
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                sshManager.writeFile(file.path, content)
                fileContent = content
                errorMessage = null
            } catch (e: Exception) {
                logger.error(e) { "Failed to save file" }
                errorMessage = "Failed to save file: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun createFile(fileName: String, content: String) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                val filePath = "$currentPath/$fileName"
                sshManager.writeFile(filePath, content)
                loadFiles(currentPath)
            } catch (e: Exception) {
                logger.error(e) { "Failed to create file" }
                errorMessage = "Failed to create file: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun deleteFile(file: FileInfo) {
        scope.launch {
            isLoading = true
            errorMessage = null
            
            try {
                sshManager.deleteFile(file.path)
                if (selectedFile == file) {
                    selectedFile = null
                    fileContent = ""
                }
                loadFiles(currentPath)
            } catch (e: Exception) {
                logger.error(e) { "Failed to delete file" }
                errorMessage = "Failed to delete file: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun navigateUp() {
        val parentPath = currentPath.substringBeforeLast('/', "")
        if (parentPath.isNotEmpty()) {
            loadFiles(parentPath)
        }
    }
    
    fun clearError() {
        errorMessage = null
    }
    
    fun onDispose() {
        scope.cancel()
        sshManager.disconnect()
    }
}
