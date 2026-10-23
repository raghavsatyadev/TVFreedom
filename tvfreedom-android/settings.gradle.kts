@file:Suppress("UnstableApiUsage")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // libadb-android and its spake2 dependency
    }
}

rootProject.buildFileName = "project.gradle.kts"

rootProject.name = "TVFreedom"

include(":composeApp", ":androidCMP", ":tvShell")
