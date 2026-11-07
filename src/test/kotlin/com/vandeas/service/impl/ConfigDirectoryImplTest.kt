package com.vandeas.service.impl

import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.ContactFormConfigTest.Companion.smtpJson
import com.vandeas.service.FileHandler
import com.vandeas.support.CapturedLogs
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ConfigDirectoryImplTest {

    private val dirs = mutableListOf<File>()

    private fun tempDir(): File = Files.createTempDirectory("hermes-configs").toFile().also { dirs += it }

    @AfterTest
    fun cleanup() {
        dirs.forEach { it.deleteRecursively() }
    }

    private val noTemplates = object : FileHandler {
        override fun getFileContent(name: String): String = throw NoSuchElementException("File $name not found")
    }

    private val invalidJson = smtpJson(id = "unlimited", dailyLimit = 0, confirmation = """{ "subjectTemplate": "Thanks" }""")

    @Test
    fun `a rejected config is logged and skipped, and later configs still load`() {
        val directory = ConfigDirectoryImpl<ContactFormConfig>(tempDir(), String::toContactFormConfig, noTemplates)

        val logs = CapturedLogs("com.vandeas.service.impl.ConfigDirectoryImpl").use { logs ->
            directory.onFileCreate("unlimited", invalidJson)
            directory.onFileCreate("not-json", "{ this is not json")
            directory.onFileCreate("valid", smtpJson(id = "valid", dailyLimit = 5, confirmation = """{ "subjectTemplate": "Thanks" }"""))
            logs.messages
        }

        assertEquals(setOf("valid"), directory.getAll().keys)
        assertFailsWith<NoSuchElementException> { directory.get("unlimited") }
        assertTrue(logs.any { it.contains("unlimited") && it.contains("dailyLimit") }, "Expected a rejection line, got $logs")
    }

    @Test
    fun `a modification that is rejected keeps the version loaded before`() {
        val directory = ConfigDirectoryImpl<ContactFormConfig>(tempDir(), String::toContactFormConfig, noTemplates)
        directory.onFileCreate("form", smtpJson(id = "unlimited", dailyLimit = 0))

        directory.onFileModify("form", invalidJson)

        assertNull(directory.get("unlimited").confirmation)
        assertEquals(0, directory.get("unlimited").dailyLimit)
    }

    @Test
    fun `deleting a rejected config file does not fail`() {
        val directory = ConfigDirectoryImpl<ContactFormConfig>(tempDir(), String::toContactFormConfig, noTemplates)
        directory.onFileCreate("unlimited", invalidJson)

        directory.onFileDelete("unlimited")

        assertTrue(directory.getAll().isEmpty())
    }

    @Test
    fun `configs on disk next to a rejected one still load`() = runBlocking {
        val dir = tempDir()
        File(dir, "a-unlimited.json").writeText(invalidJson)
        File(dir, "b-valid.json").writeText(smtpJson(id = "b-valid", dailyLimit = 5))
        File(dir, "c-unlimited.json").writeText(smtpJson(id = "c-unlimited", dailyLimit = -1, confirmation = """{ "subjectTemplate": "x" }"""))
        File(dir, "d-valid.json").writeText(smtpJson(id = "d-valid", dailyLimit = 0))

        val directory = ConfigDirectoryImpl<ContactFormConfig>(dir, String::toContactFormConfig, noTemplates)

        withTimeout(5.seconds) {
            while (directory.getAll().size < 2) delay(20.milliseconds)
        }
        assertEquals(setOf("b-valid", "d-valid"), directory.getAll().keys)
    }

    @Test
    fun `the confirmation template is read from the templates folder as id dot confirmation dot hbs`() = runBlocking {
        val templatesDir = tempDir()
        File(templatesDir, "form.hbs").writeText("<p>org</p>")
        File(templatesDir, "form.confirmation.hbs").writeText("<p>visitor</p>")
        val templates = FileHandlerImpl(templatesDir)
        val directory = ConfigDirectoryImpl<ContactFormConfig>(tempDir(), String::toContactFormConfig, templates)
        directory.onFileCreate("form", smtpJson(id = "form", dailyLimit = 5, confirmation = """{ "subjectTemplate": "Thanks" }"""))
        directory.onFileCreate("other", smtpJson(id = "other", dailyLimit = 5, confirmation = """{ "subjectTemplate": "Thanks" }"""))

        withTimeout(5.seconds) {
            while (directory.getConfirmationTemplate("form") == null) delay(20.milliseconds)
        }

        assertEquals("<p>visitor</p>", directory.getConfirmationTemplate("form"))
        assertEquals("<p>org</p>", directory.getTemplate("form"))
        assertNull(directory.getConfirmationTemplate("other"))
    }
}
