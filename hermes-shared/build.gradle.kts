plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "com.vandeas"
version = "1.2.1"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("com.icure:kerberus:1.1.5")
            }
        }
        val jvmMain by getting
    }
}
