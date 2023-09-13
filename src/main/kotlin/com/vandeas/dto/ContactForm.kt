package com.vandeas.dto

data class ContactForm(
    val id: String,
    val fullName: String,
    val email: String,
    val content: String,
    val recaptchaToken: String
)