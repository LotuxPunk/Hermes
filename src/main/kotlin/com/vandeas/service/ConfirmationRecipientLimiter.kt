package com.vandeas.service

/**
 * The Confirmation recipient limit: one address receives at most a fixed number of Visitor
 * confirmations per day, across every contact form config of the instance.
 *
 * A Visitor confirmation goes to an address the visitor typed and nobody verified, so
 * without this limit a contact form could be used to flood a third party's inbox.
 */
interface ConfirmationRecipientLimiter {
    /**
     * Takes one unit of today's allowance for [address].
     *
     * Addresses that differ only by case, surrounding whitespace, display name or a `+tag`
     * in the local part share one allowance.
     *
     * @return true when a unit was granted and the confirmation may be sent, false when the
     * address has used up today's allowance.
     */
    fun tryAcquire(address: String): Boolean
}
