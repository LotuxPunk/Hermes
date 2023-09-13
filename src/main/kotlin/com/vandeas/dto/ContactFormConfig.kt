package com.vandeas.dto

data class ContactFormConfig(
    val id: String,
    val dailyLimit: Int,
    val destination: String,
    val sender: String,
    val threshold: Double,
    val lang: String
)
