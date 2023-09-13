package com.vandeas.plugins

import com.github.mustachejava.DefaultMustacheFactory
import io.ktor.server.application.*
import io.ktor.server.mustache.*

fun Application.configureTemplate() {
    install(Mustache) {
        mustacheFactory = DefaultMustacheFactory("templates")
    }
}