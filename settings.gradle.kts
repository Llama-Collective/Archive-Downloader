pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()

        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9"
    id("dev.kikugie.loom-back-compat") version "0.2"
}

stonecutter {
    create(rootProject) {
        // See https://stonecutter.kikugie.dev/wiki/start/#choosing-minecraft-versions
        versions("1.20.4","1.20.6", "1.21.1", "1.21.2", "1.21.4" ,"1.21.5", "1.21.8", "1.21.10", "1.21.11", "26.1.2")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "Archive Downloader"