package com.vandeas.service

import com.vandeas.entities.Mail
import com.vandeas.entities.SendOperationResult

interface Mailer {
    fun sendEmail(
        to: String,
        from: String,
        subject: String,
        content: String,
    ) : SendOperationResult

    suspend fun sendEmails(
        mails: List<Mail>,
    ): SendOperationResult
}

