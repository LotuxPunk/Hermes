package com.vandeas.dto

data class MailConfig(
    val id: String,
    val sender: String,
    val subjectTemplate: String,
    val contentTemplate: String,
)
