package com.vandeas.plugins

import com.vandeas.config.AnyMapSerializer
import com.vandeas.dto.BroadcastMailRequest
import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.entities.Attachment
import com.vandeas.entities.MailSendStatus
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.KerberusLogic
import com.vandeas.logic.MailLogic
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val mailLogic by inject<MailLogic>()
    val kerberusLogic by inject<KerberusLogic>()

    install(ContentNegotiation) {
        json()
    }
    routing {
        route("/v1") {
            get("/challenge") {
                call.parameters["configId"]?.let { configId ->
                    val challenge = kerberusLogic.getChallenge(configId)
                    call.respond(HttpStatusCode.OK, challenge)
                } ?: call.respond(HttpStatusCode.BadRequest, "Missing configId parameter")
            }
            route("/mail") {
                post("/contact") {
                    val contactForm = call.receive<ContactForm>()

                    try {
                        val response = mailLogic.sendContactForm(contactForm)
                        call.respond(
                            response.status.toHttpStatusCode(),
                            response
                        )
                    } catch (e: Exception) {
                        application.log.error("Failed to send contact form: ${e.message}")
                        when (e) {
                            is DailyLimitExceededException -> call.respond(HttpStatusCode.TooManyRequests, e.message)
                            is RecaptchaFailedException -> call.respond(HttpStatusCode.Forbidden, e.message)
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            else -> call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
                post("/batch") {
                    val batch = call.receive<List<MailInput>>()

                    try {
                        val responses = mailLogic.sendMails(batch)

                        call.respond(
                            responses.status.toHttpStatusCode(),
                            responses
                        )
                    } catch (e: Exception) {
                        when (e) {
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            else -> {
                                application.log.error("Failed to send batch of mails: ${e.message}")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }
                post {
                    try {
                        val contentType = call.request.contentType()
                        val mailInput: MailInput
                        val attachments: List<Attachment>

                        if (contentType.match(ContentType.MultiPart.FormData)) {
                            val parts = call.receiveMultipart()
                            var dataPart: String? = null
                            val fileAttachments = mutableListOf<Attachment>()

                            parts.forEachPart { part ->
                                when (part) {
                                    is PartData.FormItem -> {
                                        if (part.name == "data") {
                                            dataPart = part.value
                                        }
                                    }
                                    is PartData.FileItem -> {
                                        val fileName = (part.originalFileName ?: "attachment")
                                            .replace("..", "")
                                            .replace("/", "")
                                            .replace("\\", "")
                                        val fileContentType = part.contentType?.toString() ?: "application/octet-stream"
                                        val fileBytes = part.provider().toByteArray()
                                        fileAttachments.add(Attachment(fileName, fileBytes, fileContentType))
                                    }
                                    else -> {}
                                }
                                part.dispose()
                            }

                            mailInput = Json.decodeFromString<MailInput>(
                                dataPart ?: throw IllegalArgumentException("Missing 'data' form field")
                            )
                            attachments = fileAttachments
                        } else {
                            mailInput = call.receive<MailInput>()
                            attachments = emptyList()
                        }

                        val response = mailLogic.sendMail(mailInput, attachments)
                        call.respond(response.status.toHttpStatusCode(), response)
                    } catch (e: Exception) {
                        when (e) {
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            else -> {
                                application.log.error("Failed to send mail: ${e.message}")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }

                post("/{configId}/broadcast") {
                    try {
                        val configId = call.parameters["configId"]
                            ?: throw IllegalArgumentException("Missing configId path parameter")

                        val contentType = call.request.contentType()
                        val broadcastRequest: BroadcastMailRequest
                        val attachments: List<Attachment>

                        if (contentType.match(ContentType.MultiPart.FormData)) {
                            val parts = call.receiveMultipart()
                            val recipients = mutableListOf<String>()
                            var attributesPart: String? = null
                            var replyToPart: String? = null
                            val fileAttachments = mutableListOf<Attachment>()

                            parts.forEachPart { part ->
                                when (part) {
                                    is PartData.FormItem -> {
                                        when (part.name) {
                                            "to" -> recipients.add(part.value)
                                            "attributes" -> attributesPart = part.value
                                            "replyTo" -> replyToPart = part.value
                                        }
                                    }
                                    is PartData.FileItem -> {
                                        val fileName = (part.originalFileName ?: "attachment")
                                            .replace("..", "")
                                            .replace("/", "")
                                            .replace("\\", "")
                                        val fileContentType = part.contentType?.toString() ?: "application/octet-stream"
                                        val fileBytes = part.provider().toByteArray()
                                        fileAttachments.add(Attachment(fileName, fileBytes, fileContentType))
                                    }
                                    else -> {}
                                }
                                part.dispose()
                            }

                            require(recipients.isNotEmpty()) { "At least one 'to' form field is required" }

                            val attributes: Map<String, Any?> = attributesPart?.let {
                                Json.decodeFromString(AnyMapSerializer, it)
                            } ?: emptyMap()

                            broadcastRequest = BroadcastMailRequest(
                                to = recipients,
                                replyTo = replyToPart,
                                attributes = attributes
                            )
                            attachments = fileAttachments
                        } else {
                            broadcastRequest = call.receive<BroadcastMailRequest>()
                            attachments = emptyList()
                        }

                        val response = mailLogic.broadcastMail(configId, broadcastRequest, attachments)
                        call.respond(response.status.toHttpStatusCode(), response)
                    } catch (e: Exception) {
                        when (e) {
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            else -> {
                                application.log.error("Failed to broadcast mail: ${e.message}")
                                call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun MailSendStatus.toHttpStatusCode(): HttpStatusCode = when (this) {
    MailSendStatus.SENT -> HttpStatusCode.OK
    MailSendStatus.PARTIAL -> HttpStatusCode.OK
    MailSendStatus.FAILED -> HttpStatusCode.InternalServerError
}
