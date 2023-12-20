package com.vandeas.dto

data class MailInput(
    val id: String,
    val email: String,
    val attributes: Map<String, Any>
)
