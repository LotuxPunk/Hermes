package com.vandeas.service

import com.vandeas.dto.configs.Config

interface ConfigDirectory<T: Config> {
    fun get(id: String): T
    fun getAll(): Map<String, T>
    fun getTemplate(id: String): String

    /**
     * Returns the Visitor confirmation body template of config [id], stored in the templates
     * folder as `<id>.confirmation.hbs`.
     *
     * Configs and templates reload independently, so a missing template is a normal state
     * while an operator edits them. It is reported as null rather than thrown.
     *
     * @return the template, or null when the file does not exist.
     */
    fun getConfirmationTemplate(id: String): String?

    companion object {
        /** Suffix of a Visitor confirmation template name, before the `.hbs` extension. */
        const val CONFIRMATION_TEMPLATE_SUFFIX = ".confirmation"
    }
}
