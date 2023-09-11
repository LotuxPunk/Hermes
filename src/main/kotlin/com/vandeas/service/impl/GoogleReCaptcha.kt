package com.vandeas.service

import com.vandeas.dto.GoogleRecaptchaResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*

class GoogleReCaptcha : ReCaptcha {

    override suspend fun verify(secret: String, userResponse: String): Boolean {
        HttpClient(CIO){
            install(ContentNegotiation) {
                jackson()
            }
        }.use { client ->
            val response =
                client.post("https://www.google.com/recaptcha/api/siteverify?secret=$secret&response=$userResponse")
            val gReCaptchaResponse = response.body<GoogleRecaptchaResponse>()
            return gReCaptchaResponse.success
        }
    }
}