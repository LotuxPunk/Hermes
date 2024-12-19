package com.vandeas.logic.impl

import com.icure.kerberus.Challenge
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.captcha.KerberusConfig
import com.vandeas.logic.KerberusLogic
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.impl.captcha.KerberusCaptcha

class KerberusLogicImpl(
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
) : KerberusLogic {
    override suspend fun getChallenge(configId: String): Challenge {
        val config = contactFormConfigHandler.get(configId)
        return when(val captchaConfig = config.captcha) {
            is KerberusConfig -> KerberusCaptcha.get(captchaConfig.secretKey).generateChallenge(captchaConfig)
            else -> throw IllegalArgumentException("Mail config does not have Kerberus captcha")
        }
    }
}
