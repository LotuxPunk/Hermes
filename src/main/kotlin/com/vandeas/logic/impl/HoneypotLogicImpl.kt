package com.vandeas.logic.impl

import com.vandeas.dto.HoneypotSession
import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.exception.HoneypotMisconfiguredException
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
        return try {
            honeypot.issue(configId, honeypotConfig)
        } catch (e: IllegalArgumentException) {
            // A bad secretKey (JCE) or an out-of-bounds fieldCount/maxAge (HmacHoneypot's own
            // requires) are both operator misconfiguration, not a client error — surfaced as
            // 500 by Routing.kt, with this detail logged server-side rather than echoed back.
            throw HoneypotMisconfiguredException(
                "Contact form config $configId has unusable honeypot settings: ${e.message}",
                e,
            )
        }
    }
}
