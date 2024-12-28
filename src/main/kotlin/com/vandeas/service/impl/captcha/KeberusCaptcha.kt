package com.vandeas.service.impl.captcha

import com.icure.kerberus.Challenge
import com.icure.kerberus.Solution
import com.icure.kerberus.validateSolution
import com.icure.kryptom.crypto.defaultCryptoService
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.service.Captcha
import com.vandeas.service.CaptchaResult
import io.github.reactivecircus.cache4k.Cache
import kotlin.math.log10
import kotlin.time.Duration.Companion.minutes

class KerberusCaptcha(
    private val secretKey: String
): Captcha<Solution, KerberusConfig> {
    companion object {
        private val kerberusCache = Cache.Builder<String, KerberusCaptcha>().build()

        suspend fun get(secretKey: String): KerberusCaptcha {
            return kerberusCache.get(secretKey) {
                KerberusCaptcha(secretKey)
            }
        }
    }

    private val challengeCache = Cache.Builder<String, Challenge>()
        .expireAfterWrite(10.minutes)
        .build()

    override suspend fun verify(
        config: KerberusConfig,
        userResponse: Solution
    ): CaptchaResult {
        return challengeCache.get(userResponse.id)?.let {
            if (validateSolution(it, userResponse, secretKey)) {
                challengeCache.invalidate(userResponse.id)
                CaptchaResult.Success
            } else {
                CaptchaResult.Failure
            }
        } ?: CaptchaResult.Failure
    }

    fun generateChallenge(config: KerberusConfig): Challenge {
        val countForKey = challengeCache.asMap().size

        val baseSalts = 10
        val scalingFactor = 20
        val saltCount = (baseSalts + log10(countForKey.takeIf { it > 0 }?.toDouble() ?: 1.toDouble()) * scalingFactor).toInt()

        return Challenge(
            id = defaultCryptoService.strongRandom.randomUUID(),
            salts = List(saltCount) { defaultCryptoService.strongRandom.randomUUID() },
            difficultyFactor = 5000
        ).also {
            challengeCache.put(it.id, it)
        }
    }
}
