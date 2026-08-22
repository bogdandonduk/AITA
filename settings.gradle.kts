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

rootProject.name = "AITA"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Fail with one precise explanation if a complete AITA repository was extracted inside
// another AITA repository. That layout makes IntelliJ sync the stale outer build while the
// repaired project sits unused in a child directory.
val childGradleRoots = listOf(
    rootDir.resolve("AITA/settings.gradle.kts"),
    rootDir.resolve("AITA/settings.gradle"),
).filter { it.isFile }
check(childGradleRoots.isEmpty()) {
    "Nested AITA Gradle project detected: ${childGradleRoots.joinToString()}. " +
        "Close IntelliJ, move this repository to an empty standalone directory, and open the " +
        "directory that directly contains settings.gradle.kts."
}

val parentGradleRoot = rootDir.parentFile?.let { parent ->
    listOf(parent.resolve("settings.gradle.kts"), parent.resolve("settings.gradle"))
        .firstOrNull { it.isFile }
}
check(parentGradleRoot == null) {
    "AITA is nested inside another Gradle root at $parentGradleRoot. " +
        "Move AITA to a standalone directory before syncing it."
}

val requiredModuleBuildFiles = listOf(
    rootDir.resolve("composeApp/build.gradle.kts"),
    rootDir.resolve("shared/build.gradle.kts"),
    rootDir.resolve("server/build.gradle.kts"),
)
check(requiredModuleBuildFiles.all { it.isFile }) {
    "Incomplete AITA project root. Open the directory that directly contains settings.gradle.kts, " +
        "composeApp, shared, server, and gradlew."
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
