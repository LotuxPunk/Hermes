package com.vandeas.entities

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a mail item in the queue with a unique reference.
 *
 * @property reference Unique identifier for tracking this mail operation
 * @property mail The mail to be sent
 * @property priority Priority of the mail (higher values = higher priority)
 * @property retryCount Number of times this mail has been retried
 * @property maxRetries Maximum number of retry attempts for temporary failures
 */
@Serializable
data class MailQueueItem(
    val reference: String = UUID.randomUUID().toString(),
    val mail: Mail,
    val priority: Int = 0,
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
)

