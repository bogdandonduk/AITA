// THIS IS build.gradle of shared module
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
    databases {
        create("AppDatabase") {
            packageName.set("kz.aita")
            generateAsync.set(true)
        }
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

    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }

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
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.testJunit)
                implementation(libs.sqlDelightJvmDriver)
                implementation(libs.sqlDelightAsyncExtensions)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutines.version")}")
                implementation("io.ktor:ktor-client-mock:${property("ktor.version")}")
            }

            getByName("jvmMain").dependencies {
                implementation(libs.jserialcomm)
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
                    implementation(npm("sql.js", "1.8.0"))
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

            getByName("iosMain").dependencies {
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
