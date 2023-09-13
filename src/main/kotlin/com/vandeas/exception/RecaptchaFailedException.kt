package com.vandeas.exception

class RecaptchaFailedException: Exception() {
    override val message: String
        get() = "Recaptcha failed"
}