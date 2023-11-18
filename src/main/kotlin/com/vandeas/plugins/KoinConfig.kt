package com.vandeas.plugins

import com.vandeas.logic.MailLogic
import com.vandeas.logic.impl.MailLogicImpl
import com.vandeas.service.ConfigLoader
import com.vandeas.service.DailyLimiter
import com.vandeas.service.Mailer
import com.vandeas.service.ReCaptcha
import com.vandeas.service.impl.FilesConfigLoaderImpl
import com.vandeas.service.impl.GoogleReCaptcha
import com.vandeas.service.impl.InMemoryDailyLimiter
import com.vandeas.service.impl.SendGridMailer
import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

val appModule = module {
    single<Mailer> { SendGridMailer() }
    single<ConfigLoader> { FilesConfigLoaderImpl() }
    single<ReCaptcha> { GoogleReCaptcha() }
    single<DailyLimiter> { InMemoryDailyLimiter() }
    single<MailLogic> { MailLogicImpl(get(), get(), get(), get()) }
}

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}
