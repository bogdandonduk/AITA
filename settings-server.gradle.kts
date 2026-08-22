pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "AITA-server"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val requiredServerModuleBuildFiles = listOf(
    rootDir.resolve("server/build.gradle.kts"),
    rootDir.resolve("shared/build.gradle.kts"),
)
check(requiredServerModuleBuildFiles.all { it.isFile }) {
    "Incomplete AITA server project root. Run from the directory containing settings-server.gradle.kts, server, shared, and gradlew."
}

include(":server")
include(":shared")
