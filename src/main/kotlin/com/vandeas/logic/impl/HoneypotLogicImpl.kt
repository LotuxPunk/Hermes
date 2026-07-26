package com.vandeas.logic.impl

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.logic.HoneypotLogic
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.Honeypot

class HoneypotLogicImpl(
    private val contactFormConfigHandler: ConfigDirectory<ContactFormConfig>,
    private val honeypot: Honeypot,
) : HoneypotLogic {
    override fun getSession(configId: String): HoneypotSession {
        val config = contactFormConfigHandler.get(configId)
        val honeypotConfig = requireNotNull(config.honeypot) {
            "Contact form config $configId does not have honeypot configured"
        }
        return honeypot.issue(configId, honeypotConfig)
    }
}
