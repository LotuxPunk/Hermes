package com.vandeas.service

import com.vandeas.dto.configs.captcha.CaptchaConfig

interface Captcha<T, U: CaptchaConfig> {
    suspend fun verify(config: U, userResponse: T): CaptchaResult
}

sealed interface CaptchaResult {
    data object Success : CaptchaResult
    data object Failure : CaptchaResult
}
