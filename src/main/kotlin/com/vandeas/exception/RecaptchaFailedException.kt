package com.vandeas.exception

/**
 * Exception thrown when the recaptcha verification fails.
 */
class RecaptchaFailedException: Exception() {
    override val message: String
        get() = "Recaptcha failed"
}
