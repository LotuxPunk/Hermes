package com.vandeas.desktop.ssh

import io.github.oshai.kotlinlogging.KotlinLogging
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.ByteArrayOutputStream

private val logger = KotlinLogging.logger {}

/**
 * SSH client wrapper for file operations on remote server
 */
class SshFileManager {
    private var sshClient: SSHClient? = null
    private var sftpClient: SFTPClient? = null
    
    /**
     * Connect to SSH server
     */
    fun connect(host: String, port: Int, username: String, password: String) {
        disconnect()
        
        try {
            logger.info { "Connecting to SSH server: $username@$host:$port" }
            
            val client = SSHClient()
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connect(host, port)
            client.authPassword(username, password)
            
            sshClient = client
            sftpClient = client.newSFTPClient()
            
            logger.info { "Successfully connected to SSH server" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect to SSH server" }
            disconnect()
            throw e
        }
    }
    
    /**
     * Disconnect from SSH server
     */
    fun disconnect() {
        try {
            sftpClient?.close()
            sshClient?.disconnect()
        } catch (e: Exception) {
            logger.error(e) { "Error during disconnect" }
        } finally {
            sftpClient = null
            sshClient = null
        }
    }
    
    /**
     * Check if connected to SSH server
     */
    fun isConnected(): Boolean {
        return sshClient?.isConnected == true
    }
    
    /**
     * List files in a directory
     */
    fun listFiles(path: String): List<FileInfo> {
        val sftp = sftpClient ?: throw IllegalStateException("Not connected to SSH server")
        
        return try {
            sftp.ls(path).map { file ->
                FileInfo(
                    name = file.name,
                    path = file.path,
                    isDirectory = file.isDirectory,
                    size = file.attributes.size,
                    modified = file.attributes.mtime
                )
            }.filter { it.name != "." && it.name != ".." }
        } catch (e: Exception) {
            logger.error(e) { "Failed to list files in path: $path" }
            emptyList()
        }
    }
    
    /**
     * Read file content
     */
    fun readFile(path: String): String {
        val sftp = sftpClient ?: throw IllegalStateException("Not connected to SSH server")
        
        return try {
            val output = ByteArrayOutputStream()
            sftp.open(path).use { remoteFile ->
                val inputStream = remoteFile.RemoteFileInputStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            output.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error(e) { "Failed to read file: $path" }
            throw e
        }
    }
    
    /**
     * Write file content
     */
    fun writeFile(path: String, content: String) {
        val sftp = sftpClient ?: throw IllegalStateException("Not connected to SSH server")
        
        try {
            sftp.open(path, setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { remoteFile ->
                val bytes = content.toByteArray(Charsets.UTF_8)
                val fileOutputStream = remoteFile.RemoteFileOutputStream(0)
                fileOutputStream.write(bytes)
                fileOutputStream.flush()
            }
            logger.info { "Successfully wrote file: $path" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to write file: $path" }
            throw e
        }
    }
    
    /**
     * Delete file
     */
    fun deleteFile(path: String) {
        val sftp = sftpClient ?: throw IllegalStateException("Not connected to SSH server")
        
        try {
            sftp.rm(path)
            logger.info { "Successfully deleted file: $path" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete file: $path" }
            throw e
        }
    }
    
    /**
     * Create directory
     */
    fun createDirectory(path: String) {
        val sftp = sftpClient ?: throw IllegalStateException("Not connected to SSH server")
        
        try {
            sftp.mkdir(path)
            logger.info { "Successfully created directory: $path" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to create directory: $path" }
            throw e
        }
    }
    
    /**
     * Check if file or directory exists
     */
    fun exists(path: String): Boolean {
        val sftp = sftpClient ?: throw IllegalStateException("Not connected to SSH server")
        
        return try {
            sftp.stat(path)
            true
        } catch (e: Exception) {
            false
        }
    }
}

data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long
)
