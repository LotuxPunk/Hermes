package com.vandeas.utils

import java.io.File

object Constants {
    val contactFormConfigsDir = File(System.getenv("CONTACT_FORM_CONFIGS_FOLDER"))
    val mailConfigsDir = File(System.getenv("MAIL_CONFIGS_FOLDER"))
    val templateDir = File(System.getenv("TEMPLATES_FOLDER"))
}
