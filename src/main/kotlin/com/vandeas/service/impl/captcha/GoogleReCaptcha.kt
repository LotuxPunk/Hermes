package com.vandeas.service.impl.captcha

import com.vandeas.dto.GoogleRecaptchaResponse
import com.vandeas.dto.configs.captcha.GoogleRecaptchaConfig
import com.vandeas.service.Captcha
import com.vandeas.service.CaptchaResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*

object GoogleReCaptcha: Captcha<String, GoogleRecaptchaConfig> {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json()
        }
    }

    override suspend fun verify(config: GoogleRecaptchaConfig, userResponse: String): CaptchaResult {
        val (secretKey, threshold) = config
        val response = client.post {
                url {
                    url("https://www.google.com/recaptcha/api/siteverify")
                    parameter("secret", secretKey)
                    parameter("response", userResponse)
                }
                headers {
                    accept(ContentType.Application.Json)
                    contentType(ContentType.Application.Json)
                }
            }.body<GoogleRecaptchaResponse>()

        return when (response.success) {
            true if response.score >= threshold -> CaptchaResult.Success
            else -> CaptchaResult.Failure
        }
    }
}
