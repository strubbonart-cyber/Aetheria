plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.6.0"
}

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "skyblockqol-fabric-1.21"
