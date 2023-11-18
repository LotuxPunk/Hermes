import io.ktor.plugin.features.*

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val koin_ktor_version: String by project
val kotlinx_serialization_version: String by project

plugins {
    kotlin("jvm") version "1.9.20"
    id("io.ktor.plugin") version "2.3.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
}

group = "com.vandeas"
version = "0.0.1"

application {
    mainClass.set("com.vandeas.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-jackson-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-cio-jvm")
    implementation("io.ktor:ktor-server-mustache-jvm")

    implementation("io.ktor:ktor-client-cio-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-content-negotiation-jvm")

    implementation("io.insert-koin:koin-ktor:$koin_ktor_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_ktor_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("com.sendgrid:sendgrid-java:4.9.3")

    implementation("io.github.irgaly.kfswatch:kfswatch:1.0.0")

    implementation("net.pwall.mustache:kotlin-mustache:0.11")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinx_serialization_version")

    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}


ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_17)
        localImageName.set("hermes")
        imageTag.set("0.0.1")

        DockerImageRegistry.externalRegistry(
            username = providers.environmentVariable("DOCKER_REGISTRY_USERNAME"),
            password = providers.environmentVariable("DOCKER_REGISTRY_PASSWORD"),
            project = provider { "hermes" },
            hostname = providers.environmentVariable("DOCKER_REGISTRY_HOSTNAME")
        )
    }
}
