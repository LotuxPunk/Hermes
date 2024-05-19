package com.vandeas.exception

/**
 * Exception thrown when the daily limit of emails has been reached.
 */
class DailyLimitExceededException(
    private val limit: Int,
): Exception() {
    override val message: String
        get() = "Daily limit reached. Limit: $limit"
}
