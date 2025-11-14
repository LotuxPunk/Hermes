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
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kerberus)
    implementation(libs.kryptom)
    implementation(libs.cache4k)
    implementation(libs.bundles.koin)
    implementation(libs.logback.classic)
    implementation(libs.resend.java)
    implementation(libs.kfswatch)
    implementation(libs.kotlin.mustache)
    implementation(libs.bundles.serialization)
    implementation(libs.javax.mail)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

