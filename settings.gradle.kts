pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "Hermes"
include("desktop")
include("hermes-shared")
include("hermes-client")
