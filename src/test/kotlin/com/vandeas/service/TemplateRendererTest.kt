package com.vandeas.service

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateRendererTest {

    @Test
    fun `renderPlainText preserves accented characters`() {
        val template = "Résumé des absences : {{eventName}}"
        val context = mapOf("eventName" to "Répétition — vendredi 10 avril 2026")
        val result = TemplateRenderer.renderPlainText(template, context)
        assertEquals("Résumé des absences : Répétition — vendredi 10 avril 2026", result)
    }

    @Test
    fun `renderPlainText does not escape HTML special characters`() {
        val template = "Subject: {{value}}"
        val context = mapOf("value" to "A <b>bold</b> & \"quoted\" subject")
        val result = TemplateRenderer.renderPlainText(template, context)
        assertEquals("Subject: A <b>bold</b> & \"quoted\" subject", result)
    }

    @Test
    fun `renderHtml preserves accented characters`() {
        val template = "<p>{{eventName}}</p>"
        val context = mapOf("eventName" to "Répétition — vendredi 10 avril 2026")
        val result = TemplateRenderer.renderHtml(template, context)
        assertEquals("<p>Répétition — vendredi 10 avril 2026</p>", result)
    }

    @Test
    fun `renderHtml escapes HTML special characters`() {
        val template = "<p>{{content}}</p>"
        val context = mapOf("content" to "<script>alert('xss')</script> & \"test\"")
        val result = TemplateRenderer.renderHtml(template, context)
        assertEquals("<p>&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt; &amp; &quot;test&quot;</p>", result)
    }

    @Test
    fun `renderHtml with nested map context`() {
        val template = "Hello {{form.fullName}}, your topic is {{form.topic}}"
        val context = mapOf("form" to mapOf("fullName" to "André", "topic" to "Général"))
        val result = TemplateRenderer.renderHtml(template, context)
        assertEquals("Hello André, your topic is Général", result)
    }
}
