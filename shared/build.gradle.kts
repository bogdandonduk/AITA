// THIS IS build.gradle of shared module
import app.cash.sqldelight.gradle.SqlDelightDatabase
import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // Android/Hilt/KSP stay available for ordinary app builds, but are not applied during
    // headless server-only or web-only builds. Ubuntu can therefore build either artifact
    // without installing the unrelated Android or Apple toolchains.
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

val aitaServerOnlyBuild = providers.gradleProperty("aita.serverOnly")
    .orNull
    ?.equals("true", ignoreCase = true)
    ?: (rootProject.name == "AITA-server")
val aitaWebOnlyBuild = providers.gradleProperty("aita.webOnly")
    .orNull
    ?.equals("true", ignoreCase = true) == true

require(!(aitaServerOnlyBuild && aitaWebOnlyBuild)) {
    "aita.serverOnly and aita.webOnly cannot both be enabled"
}

val aitaFullApplicationBuild = !aitaServerOnlyBuild && !aitaWebOnlyBuild
if (aitaFullApplicationBuild) {
    pluginManager.apply("com.android.library")
    pluginManager.apply("com.google.dagger.hilt.android")
    pluginManager.apply("com.google.devtools.ksp")
}

sqldelight {
    // Keep the SQLDelight database type explicit. This avoids the Gradle Kotlin DSL/IDE
    // choosing the generic polymorphic-container create<T>() overload and then failing
    // to infer T, while preserving the same AppDatabase configuration.
    val appDatabase: SqlDelightDatabase = databases.create("AppDatabase")
    appDatabase.packageName.set("kz.aita")
    appDatabase.generateAsync.set(true)
}


fun aitaClientBuildValue(property: String, environment: String, fallback: String = ""): String =
    providers.gradleProperty(property).orElse(providers.environmentVariable(environment)).orElse(fallback).get().trim()
val clientBuildValues = linkedMapOf(
    "version" to aitaClientBuildValue("aita.release.version", "AITA_RELEASE_VERSION", "1.0.0"),
    "build" to aitaClientBuildValue("aita.release.build", "AITA_RELEASE_BUILD", aitaClientBuildValue("aita.android.versionCode", "AITA_ANDROID_VERSION_CODE", "1")),
    "channel" to aitaClientBuildValue("aita.release.channel", "AITA_RELEASE_CHANNEL", "release"),
    "revision" to aitaClientBuildValue("aita.release.revision", "AITA_RELEASE_REVISION", runCatching {
        providers.exec { workingDir(rootProject.projectDir); commandLine("git", "rev-parse", "--short=12", "HEAD"); isIgnoreExitValue = true }.standardOutput.asText.get().trim().takeIf { Regex("[0-9a-f]{7,40}").matches(it) } ?: "development"
    }.getOrDefault("development")),
    "builtAt" to aitaClientBuildValue("aita.release.builtAt", "AITA_RELEASE_BUILT_AT", "development"),
    "distribution" to aitaClientBuildValue("aita.release.distribution", "AITA_RELEASE_DISTRIBUTION", "direct"),
    "feed" to aitaClientBuildValue("aita.update.feedBase", "AITA_UPDATE_FEED_BASE", "https://aita-api.bogdan-donduk.workers.dev/client-updates"),
    "publicKey" to aitaClientBuildValue("aita.update.publicKey", "AITA_UPDATE_PUBLIC_KEY"),
    "kotlin" to libs.versions.kotlin.get(),
    "compose" to libs.versions.composeMultiplatform.get()
)
require(clientBuildValues.getValue("channel") in setOf("release", "test")) { "AITA release channel must be release or test" }
require(clientBuildValues.getValue("distribution") in setOf("direct", "store")) { "AITA distribution must be direct or store" }
require(clientBuildValues.getValue("build").toLongOrNull()?.let { it in 1L..2_100_000_000L } == true) { "AITA release build must be a positive increasing integer" }
require(Regex("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,5}").matches(clientBuildValues.getValue("version"))) { "Invalid release version" }
require(clientBuildValues.getValue("publicKey").isEmpty() || Regex("[A-Za-z0-9+/=]{300,2048}").matches(clientBuildValues.getValue("publicKey"))) { "AITA update public key must be base64 DER SubjectPublicKeyInfo, never a private key" }
val generateAitaClientBuildInfo by tasks.registering {
    inputs.properties(clientBuildValues)
    val generated = layout.buildDirectory.dir("generated/aitaClientBuild")
    outputs.dir(generated)
    doLast {
        fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n").replace("\r", "\\r") + "\""
        val out = generated.get().asFile.resolve("kotlin/kz/aita/updates/GeneratedClientBuild.kt")
        out.parentFile.mkdirs()
        fun jsonQuote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
        generated.get().asFile.resolve("client-build.json").writeText(clientBuildValues.entries.joinToString(",\n", "{\n", "\n}\n") { (k,v) -> jsonQuote(k) + ":" + jsonQuote(v) })
        out.writeText("""
            package kz.aita.updates
            object GeneratedClientBuild {
                val identity = ClientBuildIdentity(
                    version = ${quote(clientBuildValues.getValue("version"))},
                    build = ${clientBuildValues.getValue("build")}L,
                    channel = ReleaseChannel.${clientBuildValues.getValue("channel").uppercase()},
                    revision = ${quote(clientBuildValues.getValue("revision"))},
                    builtAt = ${quote(clientBuildValues.getValue("builtAt"))},
                    distribution = ${quote(clientBuildValues.getValue("distribution"))},
                    kotlinVersion = ${quote(clientBuildValues.getValue("kotlin"))},
                    composeVersion = ${quote(clientBuildValues.getValue("compose"))}
                )
                const val feedBase = ${quote(clientBuildValues.getValue("feed"))}
                const val publicKey = ${quote(clientBuildValues.getValue("publicKey"))}
            }
        """.trimIndent() + "\n")
    }
}

kotlin {
    if (!aitaWebOnlyBuild) {
        jvmToolchain(21)
    }

    if (aitaFullApplicationBuild) {
        androidTarget {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }

        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    if (!aitaWebOnlyBuild) {
        jvm {
            testRuns["test"].executionTask.configure { useJUnit() }
        }
    }

    if (!aitaServerOnlyBuild) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs {
            browser {
                val rootDirPath = project.rootDir.path
                val projectDirPath = project.projectDir.path
                commonWebpackConfig {
                    devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                        static = (static ?: mutableListOf()).apply {
                            add(rootDirPath)
                            add(projectDirPath)
                        }
                    }
                }
            }
        }
    }

    // Materialize the standard intermediate source sets (including iosMain) before
    // configuring them below. This remains safe in server-only and web-only builds:
    // the template creates only the groups supported by the targets declared above.
    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

        getByName("commonMain").kotlin.srcDir(generateAitaClientBuildInfo.map { it.outputs.files.singleFile.resolve("kotlin") })

        commonMain.dependencies {
            implementation("io.ktor:ktor-client-logging:${property("ktor.version")}")
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.websockets)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqlDelightRuntime)
            implementation(libs.sqlDelightCoroutinesExtensions)
            implementation(libs.sqlDelightAsyncExtensions)
            implementation("io.ktor:ktor-client-core:${property("ktor.version")}")
            implementation("io.ktor:ktor-client-logging:${property("ktor.version")}")
            implementation("io.ktor:ktor-client-content-negotiation:${property("ktor.version")}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutines.version")}")
        }

        if (!aitaWebOnlyBuild) {
            getByName("jvmTest").dependencies {
                implementation("com.google.zxing:core:3.5.3") // Independent receipt barcode decoder verification only.
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.testJunit)
                implementation(libs.sqlDelightJvmDriver)
                implementation(libs.sqlDelightAsyncExtensions)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutines.version")}")
                implementation("io.ktor:ktor-client-mock:${property("ktor.version")}")
            }

            getByName("jvmMain").dependencies {
                implementation(libs.jserialcomm)
                implementation("net.java.dev.jna:jna:5.17.0")
                implementation(libs.sqlDelightJvmDriver)
                implementation("io.ktor:ktor-client-okhttp:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-core:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-netty:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-content-negotiation:${property("ktor.version")}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-cors:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-compression:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-auto-head-response:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-conditional-headers:${property("ktor.version")}")
                implementation("io.ktor:ktor-server-call-logging:${property("ktor.version")}")
                implementation("io.ktor:ktor-client-logging:${property("ktor.version")}")
                implementation(libs.logback)
            }
        }

        if (!aitaServerOnlyBuild) {
            getByName("wasmJsMain") {
                // The executable app owns its HTML shell and browser icons. Do not let this library
                // contribute competing document-root files to the final web distribution.
                resources.exclude(
                    "index.html",
                    "favicon.svg",
                    "favicon.ico",
                    "favicon-32.png",
                    "apple-touch-icon.png"
                )
                dependencies {
                    implementation(libs.kotlinx.browser)
                    implementation(libs.ktor.client.js)
                    implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.1.0"))
                    implementation(npm("sql.js", "1.13.0"))
                    implementation(libs.sqlDelightWasmJsDriver)
                    implementation(libs.sqlDelightWasmJsCoroutinesExtensions)
                }
            }
        }

        if (aitaFullApplicationBuild) {
            getByName("androidInstrumentedTest").dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit)
                implementation(libs.sqlDelightAndroidDriver)
                implementation(libs.sqlDelightAsyncExtensions)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutines.version")}")
                implementation(libs.androidx.testExt.junit)
                implementation(libs.androidx.espresso.core)
                implementation("androidx.test:core:1.7.0")
                implementation("androidx.test:runner:1.7.0")
                implementation("io.ktor:ktor-client-mock:${property("ktor.version")}")
            }

            getByName("androidMain").dependencies {
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.hilt.android)
                implementation(libs.androidx.core.ktx)
                implementation(libs.sqlcipher.android)
                implementation(libs.androidx.sqlite)
                implementation(libs.sqlDelightAndroidDriver)
                implementation("io.ktor:ktor-client-okhttp:${property("ktor.version")}")
                implementation("io.ktor:ktor-client-android:${property("ktor.version")}")
            }

            // Do not eagerly require an accessor that may not yet exist during conditional
            // target configuration. Reuse or create iosMain, then attach every declared iOS target.
            val iosMainSourceSet = maybeCreate("iosMain").apply {
                dependsOn(getByName("commonMain"))
            }
            listOf("iosX64Main", "iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
                findByName(sourceSetName)?.dependsOn(iosMainSourceSet)
            }
            iosMainSourceSet.dependencies {
                implementation(libs.sqlDelightNativeDriver)
                implementation("io.ktor:ktor-client-darwin:${property("ktor.version")}")
            }
        }
    }
}

if (aitaFullApplicationBuild) {
    dependencies {
        add("kspAndroid", libs.hilt.android.compiler)
    }

    afterEvaluate {
        val prereqNames = listOf(
            "generateComposeResClass",
            "generateResourceAccessorsForCommonMain",
            "generateCommonMainAppDatabaseInterface",
            "generateExpectResourceCollectorsForCommonMain",
            "generateResourceAccessorsForAndroidMain",
            "generateActualResourceCollectorsForAndroidMain",
            "generateResourceAccessorsForAndroidDebug"
        )
        val prereqs = prereqNames.mapNotNull { tasks.findByName(it) }

        tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("KotlinAndroid") }
            .configureEach {
                dependsOn(prereqs)
                mustRunAfter(prereqs)
            }
    }

    extensions.configure<LibraryExtension> {
        namespace = "kz.aita.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        defaultConfig {
            minSdk = libs.versions.android.minSdk.get().toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
}
