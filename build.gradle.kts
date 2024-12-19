import org.jetbrains.kotlin.gradle.internal.config.LanguageFeature

val ktor_version: String by project
val kotlin_version: String by project
val kotlin_coroutines: String by project
val logback_version: String by project
val koin_ktor_version: String by project
val kotlinx_serialization_version: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    id("io.ktor.plugin") version "3.0.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

group = "com.vandeas"
version = "1.2.1"

application {
    mainClass.set("com.vandeas.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

kotlin {
    sourceSets.all {
        languageSettings {
            enableLanguageFeature(LanguageFeature.WhenGuards.name)
        }
    }


    compilerOptions {
        extraWarnings.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-mustache")

    implementation("io.ktor:ktor-client-okhttp")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-content-negotiation")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlin_coroutines")

    implementation("com.icure:kerberus:1.1.5")
    implementation("com.icure.kryptom:kryptom:1.3.0")

    implementation("io.github.reactivecircus.cache4k:cache4k:0.13.0")

    implementation("io.insert-koin:koin-ktor:$koin_ktor_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_ktor_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("com.resend:resend-java:3.1.0")

    implementation("io.github.irgaly.kfswatch:kfswatch:1.3.0")

    implementation("net.pwall.mustache:kotlin-mustache:0.12")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")

    implementation("com.sun.mail:javax.mail:1.6.2")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}
