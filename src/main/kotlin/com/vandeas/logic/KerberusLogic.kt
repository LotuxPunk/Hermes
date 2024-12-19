package com.vandeas.logic

import com.icure.kerberus.Challenge

interface KerberusLogic {
    suspend fun getChallenge(configId: String): Challenge
}
