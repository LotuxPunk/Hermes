import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val kotlin_version: String by project
val kotlin_coroutines: String by project
val kotlinx_serialization_version: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
}

group = "com.vandeas"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation(compose.materialIconsExtended)

    // SSH Client
    implementation(libs.sshj)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Serialization
    implementation(libs.bundles.serialization)

    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging.jvm)
}

compose.desktop {
    application {
        mainClass = "com.vandeas.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Hermes Desktop"
            packageVersion = "1.0.0"

            description = "Desktop application for managing Hermes mail templates and configurations via SSH"
            vendor = "Vandeas"

            macOS {
                iconFile.set(project.file("src/main/resources/icon.icns"))
            }
            windows {
                iconFile.set(project.file("src/main/resources/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

