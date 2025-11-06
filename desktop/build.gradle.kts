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
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    
    // SSH Client
    implementation("com.hierynomus:sshj:0.38.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlin_coroutines")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:$kotlin_coroutines")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.12")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
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
