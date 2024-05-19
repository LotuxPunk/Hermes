package com.vandeas.service.impl

import com.vandeas.service.FileEventListener
import com.vandeas.service.FileHandler
import io.github.irgaly.kfswatch.KfsDirectoryWatcher
import io.github.irgaly.kfswatch.KfsEvent
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import java.io.File

open class FileHandlerImpl(
    private val directory: File,
): FileHandler {
    private val files: MutableMap<String, String> = mutableMapOf()
    private val listeners = mutableListOf<FileEventListener>()
    private val logger = KtorSimpleLogger("com.vandeas.service.impl.FileHandlerImpl")

    protected fun addEventListener(listener: FileEventListener) {
        listeners.add(listener)
    }

    protected fun removeEventListener(listener: FileEventListener) {
        listeners.remove(listener)
    }

    init {
        CoroutineScope(Dispatchers.Default).launch {
            val watcher = KfsDirectoryWatcher(this)

            async(Dispatchers.IO) {
                directory
                    .listFiles()
                    ?.filter { it.isFile }
                    ?.associate {
                        it.nameWithoutExtension to it.inputStream().readBytes().toString(Charsets.UTF_8)
                    }?.toMutableMap()
            }.await()?.forEach { (t, u) ->
                logger.info("Loading file: $t")
                files[t] = u
                listeners.forEach { listener ->
                    listener.onFileCreate(t, u)
                }
                logger.info("File loaded: $t")
            }

            watcher.add(directory.absolutePath)

            launch {
                watcher.onEventFlow.collect {
                    val file = File("${it.targetDirectory}/${it.path}")

                    if (file.isFile) {
                        when (it.event) {
                            KfsEvent.Create, KfsEvent.Modify -> {
                                logger.info("File created or updated: ${file.name}")
                                val content = file.inputStream().readBytes().toString(Charsets.UTF_8)
                                files[file.nameWithoutExtension] = content

                                listeners.forEach { listener ->
                                    when (it.event) {
                                        KfsEvent.Modify -> listener.onFileModify(file.nameWithoutExtension, content)
                                        KfsEvent.Create -> listener.onFileCreate(file.nameWithoutExtension, content)
                                        else -> throw IllegalStateException("Invalid event")
                                    }
                                }
                            }
                            KfsEvent.Delete -> {
                                logger.info("File deleted: ${file.name}")
                                files.remove(file.nameWithoutExtension)
                                listeners.forEach { listener ->
                                    listener.onFileDelete(file.nameWithoutExtension)
                                }
                            }
                        }
                    }
                }
            }.invokeOnCompletion {
                runBlocking {
                    watcher.removeAll()
                }
            }
        }
    }
    override fun getFileContent(name: String): String {
        return files[name] ?: throw NoSuchElementException("File $name not found")
    }
}
