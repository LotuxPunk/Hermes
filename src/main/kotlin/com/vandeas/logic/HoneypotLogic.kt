package com.vandeas.logic

import com.vandeas.dto.HoneypotSession

interface HoneypotLogic {
    /**
     * @throws NoSuchElementException if no contact form config has this id.
     * @throws IllegalArgumentException if the config has no honeypot block.
     */
    fun getSession(configId: String): HoneypotSession
}
