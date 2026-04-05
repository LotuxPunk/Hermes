package com.vandeas.service

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.MustacheFactory
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer

object TemplateRenderer {

    private val htmlFactory: MustacheFactory = DefaultMustacheFactory()

    private val plainTextFactory: MustacheFactory = object : DefaultMustacheFactory() {
        override fun encode(value: String, writer: Writer) {
            writer.write(value)
        }
    }

    fun renderHtml(template: String, context: Any): String {
        val mustache = htmlFactory.compile(StringReader(template), "html-template")
        val writer = StringWriter()
        mustache.execute(writer, context).flush()
        return writer.toString()
    }

    fun renderPlainText(template: String, context: Any): String {
        val mustache = plainTextFactory.compile(StringReader(template), "text-template")
        val writer = StringWriter()
        mustache.execute(writer, context).flush()
        return writer.toString()
    }
}
