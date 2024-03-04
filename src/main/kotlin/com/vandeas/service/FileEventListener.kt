package com.vandeas.service

interface FileEventListener {
    fun onFileCreate(fileName: String, content: String)
    fun onFileModify(fileName: String, content: String)
    fun onFileDelete(fileName: String)
}
