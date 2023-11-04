package com.vandeas.dto

data class Mail(
    val id: String,
    val email: String,
    val attributes: Map<String, String>
)
