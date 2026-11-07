package com.vandeas.dto

import com.vandeas.entities.ContactFormResult
import com.vandeas.entities.VisitorConfirmationStatus
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Response body of `POST /v1/mail/contact`.
 *
 * It carries the same fields as [com.vandeas.entities.SendOperationResult], plus
 * [confirmation]. That field lives here rather than on the shared send result because
 * Ktor's JSON configuration encodes default values: a nullable field there would show up as
 * `"confirmation": null` on every mail endpoint.
 *
 * @property confirmation Outcome of the Visitor confirmation. Omitted from the JSON when the
 * contact form config has no Visitor confirmation, so existing clients see no change.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ContactFormResponse(
    val sent: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
    val bounced: List<String> = emptyList(),
    val temporary: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val confirmation: VisitorConfirmationStatus? = null,
)

fun ContactFormResult.toResponse(): ContactFormResponse = ContactFormResponse(
    sent = orgNotification.sent,
    failed = orgNotification.failed,
    bounced = orgNotification.bounced,
    temporary = orgNotification.temporary,
    confirmation = confirmation,
)
