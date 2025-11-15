package com.vandeas.entities

import kotlinx.serialization.Serializable

/**
 * Represents the result of a queued mail operation with reference tracking.
 *
 * @property reference Unique identifier for this mail operation
 * @property result The send operation result
 */
@Serializable
data class QueuedMailResult(
    val reference: String,
    val result: SendOperationResult,
)

