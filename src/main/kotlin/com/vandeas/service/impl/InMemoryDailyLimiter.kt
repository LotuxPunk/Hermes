package com.vandeas.service.impl

import com.vandeas.dto.ContactFormConfig
import com.vandeas.service.DailyLimiter
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * A utility class designed to limit the number of emails sent from a web form based on its configuration.
 *
 * This class manages a daily limit for each contact form identified by its configuration ID. Old entries are cleaned
 * up during each verification to avoid memory leaks.
 */
class InMemoryDailyLimiter : DailyLimiter {

    private data class DailyInfo(var count: Int, val date: LocalDate)

    private val mailSendDatabase: ConcurrentHashMap<String, DailyInfo> = ConcurrentHashMap()

    /**
     * Cleans up old entries from the in-memory database to prevent memory leaks.
     *
     * Entries are considered old if their recorded date is different from today's date.
     */
    private fun cleanupOldEntry(id: String, today: LocalDate) {
        mailSendDatabase[id]?.let {
            if (it.date != today) {
                mailSendDatabase.remove(id)
            }
        }
    }

    override fun canSendMail(config: ContactFormConfig): Boolean {
        val today = LocalDate.now()
        cleanupOldEntry(config.id, today)

        return mailSendDatabase[config.id]?.let {
            it.count < config.dailyLimit
        } ?: true
    }

    override fun recordMailSent(config: ContactFormConfig): Boolean {
        if (canSendMail(config)) {
            val today = LocalDate.now()
            mailSendDatabase.compute(config.id) { _, currentInfo ->
                if (currentInfo == null) {
                    DailyInfo(1, today)
                } else {
                    currentInfo.count += 1
                    currentInfo
                }
            }
            return true
        }
        return false
    }
}


