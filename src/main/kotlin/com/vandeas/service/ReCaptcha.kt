package com.vandeas.service

import com.vandeas.dto.GoogleRecaptchaResponse

interface ReCaptcha {
    suspend fun verify(secret: String, userResponse: String): GoogleRecaptchaResponse
}