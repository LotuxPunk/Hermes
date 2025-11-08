plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "com.vandeas"
version = "1.2.1"

kotlin {
    jvm()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kerberus)
            }
        }
        val jvmMain by getting
    }
}
