package com.vandeas.plugins

import com.vandeas.dto.ContactForm
import com.vandeas.dto.MailInput
import com.vandeas.entities.MailSendStatus
import com.vandeas.exception.DailyLimitExceededException
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val mailLogic by inject<MailLogic>()

    install(ContentNegotiation) {
        jackson { }
    }
    routing {
        route("/v1") {
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
                            else -> call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
                post {
                    val mailInput = call.receive<MailInput>()

                    try {
                        val response = mailLogic.sendMail(mailInput)
                        call.respond(response.status.toHttpStatusCode(), response)
                    } catch (e: Exception) {
                        when (e) {
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            else -> call.respond(HttpStatusCode.InternalServerError)
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
