// THIS IS build.gradle of composeApp module
import com.android.build.api.dsl.ApplicationExtension
import com.google.common.jimfs.Configuration.windows
import com.sun.imageio.plugins.jpeg.JPEG.vendor
import org.gradle.api.tasks.Delete
import org.gradle.declarative.dsl.schema.FqName.Empty.packageName
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import java.lang.System.console
import java.lang.module.ModuleFinder.compose
import java.net.InetAddress.getByName

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

val aitaWebOnlyBuild = providers.gradleProperty("aita.webOnly")
    .map { value -> value.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()

if (!aitaWebOnlyBuild) {
    pluginManager.apply("com.android.application")
    pluginManager.apply("com.google.dagger.hilt.android")
    pluginManager.apply("com.google.devtools.ksp")
}

kotlin {
    if (!aitaWebOnlyBuild) {
        jvmToolchain(21)
    }
    if (!aitaWebOnlyBuild) {
        androidTarget {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }

        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
                linkerOpts.addAll(listOf("-framework", "AVFoundation", "-framework", "Speech"))
            }
        }

        jvm()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kamel.image)
            implementation("io.ktor:ktor-client-core:${property("ktor.version")}")
            implementation("io.ktor:ktor-http:${property("ktor.version")}")
            implementation(libs.kamel.decoder.image.bitmap)
            implementation(libs.kamel.decoder.image.vector)
            implementation(libs.kamel.decoder.svg.std)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }

        if (!aitaWebOnlyBuild) {
            getByName("androidMain").dependencies {
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.sqlDelightAndroidDriver)
                implementation(libs.androidx.core.ktx)
                implementation(libs.hilt.android)
                implementation(libs.kamel.fetcher.resources.android)
                implementation(compose.preview)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.camera.core)
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)
                implementation(libs.mlkit.barcode.scanning)
            }

            getByName("iosMain").dependencies {
                implementation(libs.kamel.fetcher.ktor)
            }

            getByName("jvmMain").dependencies {
                implementation(libs.java.keyring)
                implementation(libs.kamel.decoder.svg.batik)
                implementation(libs.kamel.fetcher.resources.jvm)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
            }
        }
    }
}

if (!aitaWebOnlyBuild) {
    dependencies {
        add("kspAndroid", libs.hilt.android.compiler)
        add("debugImplementation", compose.uiTooling)
    }

    extensions.configure<ApplicationExtension> {
        namespace = "kz.aita"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        defaultConfig {
            applicationId = "kz.aita"
            minSdk = libs.versions.android.minSdk.get().toInt()
            targetSdk = libs.versions.android.targetSdk.get().toInt()
            versionCode = 1
            versionName = "1.0"
        }
        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }
}

val misplacedCommonAndroidVectorDrawables = fileTree(
    layout.projectDirectory.dir("src/commonMain/composeResources/drawable")
) {
    include("ic_aita_*.xml")
}

val removeMisplacedCommonAndroidVectorDrawables by tasks.registering(Delete::class) {
    group = "resources"
    description = "Removes Android vector XML drawables that accidentally landed in common Compose resources."
    delete(misplacedCommonAndroidVectorDrawables)
}

tasks.configureEach {
    val lowerTaskName = name.lowercase()
    if (
        name != removeMisplacedCommonAndroidVectorDrawables.name &&
        (
            lowerTaskName.contains("processresources") ||
                lowerTaskName.contains("composeresources") ||
                lowerTaskName.contains("resourceaccessors")
        )
    ) {
        dependsOn(removeMisplacedCommonAndroidVectorDrawables)
    }
}

if (!aitaWebOnlyBuild) {
    compose.desktop {
        application {
            mainClass = "kz.aita.JvmMainComposeKt"

            nativeDistributions {
                targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
                modules(
                    "java.sql",
                    "java.logging",
                    "java.xml",
                    "java.desktop",
                    "java.datatransfer",
                    "java.prefs",
                    "java.management",
                    "java.naming",
                    "jdk.crypto.ec",
                    "jdk.charsets",
                    "jdk.unsupported"
                )
                packageVersion = "1.0.0"
                packageName = "AITA"
                description = "AITA"
                vendor = "AITA"

                windows {
                    iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.ico"))
                    console = false
                }
                macOS {
                    iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.icns"))
                }
                linux {
                    iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.png"))
                }
            }
        }
    }

    afterEvaluate {
        val resourceGen = tasks.matching {
            it.name.startsWith("generate") && it.name.contains("Resource")
        }
        val composeRes = tasks.matching { it.name == "generateComposeResClass" }
        val resourcePackaging = tasks.matching { task ->
            task.name.contains("resource", ignoreCase = true) ||
                task.name.contains("resclass", ignoreCase = true)
        }

        resourceGen.configureEach { dependsOn(removeMisplacedCommonAndroidVectorDrawables) }
        composeRes.configureEach { dependsOn(removeMisplacedCommonAndroidVectorDrawables) }
        resourcePackaging.configureEach { dependsOn(removeMisplacedCommonAndroidVectorDrawables) }

        tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("KotlinAndroid") }
            .configureEach {
                dependsOn(resourceGen)
                dependsOn(composeRes)
                mustRunAfter(resourceGen)
                mustRunAfter(composeRes)
            }
    }
}
