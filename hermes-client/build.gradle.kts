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
                api(project(":hermes-shared"))
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
    }
}
