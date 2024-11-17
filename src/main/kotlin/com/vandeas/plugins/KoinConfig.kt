package com.vandeas.plugins

import com.vandeas.dto.configs.ContactFormConfig
import com.vandeas.dto.configs.MailConfig
import com.vandeas.logic.MailLogic
import com.vandeas.logic.impl.MailLogicImpl
import com.vandeas.service.ConfigDirectory
import com.vandeas.service.DailyLimiter
import com.vandeas.service.FileHandler
import com.vandeas.service.ReCaptcha
import com.vandeas.service.impl.*
import com.vandeas.utils.Constants
import io.ktor.server.application.*
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.KoinApplicationStarted
import org.koin.ktor.plugin.KoinApplicationStopPreparing
import org.koin.ktor.plugin.KoinApplicationStopped
import org.koin.logger.slf4jLogger

val appModule = module {
    single<ReCaptcha> {
        GoogleReCaptcha()
    }
    single<DailyLimiter> {
        InMemoryDailyLimiter()
    }
    single<MailLogic> {
        MailLogicImpl(get(named("mailConfig")), get(named("contactFormConfig")), get(), get())
    }
    single<FileHandler>(named("template"), true) {
        FileHandlerImpl(Constants.templateDir)
    }
    single<ConfigDirectory<MailConfig>>(named("mailConfig"), true) {
        ConfigDirectoryImpl(Constants.mailConfigsDir, (String::toMailConfig), get(named("template")))
    }
    single<ConfigDirectory<ContactFormConfig>>(named("contactFormConfig"), true) {
        ConfigDirectoryImpl(Constants.contactFormConfigsDir, (String::toContactFormConfig), get(named("template")))
    }
}

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    this.monitor.subscribe(KoinApplicationStarted) {
        log.info("Koin started.")
    }

    this.monitor.subscribe(KoinApplicationStopPreparing) {
        log.info("Koin stopping...")
    }

    this.monitor.subscribe(KoinApplicationStopped) {
        log.info("Koin stopped.")
    }
}
