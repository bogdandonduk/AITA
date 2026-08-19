rootProject.name = "AITA"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

fun enabledGradleFlag(name: String): Boolean =
    providers.gradleProperty(name).orNull?.equals("true", ignoreCase = true) == true

val aitaServerOnlyBuild = enabledGradleFlag("aita.serverOnly")
val aitaWebOnlyBuild = enabledGradleFlag("aita.webOnly")

check(!(aitaServerOnlyBuild && aitaWebOnlyBuild)) {
    "AITA build modes aita.serverOnly and aita.webOnly are mutually exclusive"
}

if (!aitaServerOnlyBuild) include(":composeApp")
if (!aitaWebOnlyBuild) include(":server")
include(":shared")
