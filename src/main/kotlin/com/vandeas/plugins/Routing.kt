package com.vandeas.plugins

import com.vandeas.dto.ContactForm
import com.vandeas.dto.Mail
import com.vandeas.exception.RecaptchaFailedException
import com.vandeas.logic.MailLogic
import com.vandeas.logic.impl.MailLogicImpl
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
                        if (response.isSuccessful) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.fromValue(response.statusCode), response.body ?: "")
                        }
                    } catch (e: Exception) {
                        when (e) {
                            is IllegalArgumentException -> call.respond(HttpStatusCode.BadRequest, e.message ?: "")
                            is RecaptchaFailedException -> call.respond(HttpStatusCode.Forbidden, e.message)
                            else -> call.respond(HttpStatusCode.InternalServerError)
                        }
                    }
                }
                post {
                    val mail = call.receive<Mail>()

                    try {
                        val response = mailLogic.sendMail(mail)
                        if (response.isSuccessful) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            call.respond(HttpStatusCode.fromValue(response.statusCode), response.body ?: "")
                        }
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


