package com.vandeas.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureHTTP() {
    install(CORS) {
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }
}
