// THIS IS build.gradle of composeApp module
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

kotlin {
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
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
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
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)

            implementation(libs.kotlinx.serialization.json)
//            implementation(libs.kamel.image.default)

            implementation(libs.kamel.image)
            implementation("io.ktor:ktor-client-core:${property("ktor.version")}")
            implementation("io.ktor:ktor-http:${property("ktor.version")}")
//            implementation(libs.kamel.fetcher.ktor)
            // Note: When using `kamel-image` a ktor engine is not included.
            // To fetch remote images you also must ensure you add your own
            // ktor engine for each target.

            // optional modules (choose what you need and add them to your kamel config)
            implementation(libs.kamel.decoder.image.bitmap)
//            implementation(libs.kamel.decoder.image.bitmap.resizing) // android only right now
            implementation(libs.kamel.decoder.image.vector)
//            implementation(libs.kamel.decoder.svg.batik)
            implementation(libs.kamel.decoder.svg.std)
//            implementation(libs.kamel.decoder.animated.image) // .gif support

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
        iosMain.dependencies {
            implementation(libs.kamel.fetcher.ktor)
        }
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        jvmMain.dependencies {
            implementation(libs.java.keyring.v103)

            implementation(libs.kamel.decoder.svg.batik)

            implementation(libs.kamel.fetcher.resources.jvm)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
}

android {
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

dependencies {
    debugImplementation(compose.uiTooling)
}

val removeMisplacedCommonAndroidVectorDrawablesAction: () -> Unit = {
    val commonDrawableDir = layout.projectDirectory
        .dir("src/commonMain/composeResources/drawable")
        .asFile

    commonDrawableDir
        .listFiles { file ->
            file.isFile &&
                file.name.startsWith("ic_aita_") &&
                file.extension.equals("xml", ignoreCase = true)
        }
        ?.forEach { file ->
            if (!file.delete()) {
                logger.warn("Unable to remove misplaced common Android vector drawable: ${file.path}")
            }
        }
}

// Run once during configuration too. Compose resource tasks can snapshot common resources
// before ordinary task actions run, so the hygiene guard must clean old bad XMLs early.
removeMisplacedCommonAndroidVectorDrawablesAction()

val removeMisplacedCommonAndroidVectorDrawables by tasks.registering {
    group = "resources"
    description = "Removes Android vector XML drawables that accidentally landed in common Compose resources."

    doLast {
        removeMisplacedCommonAndroidVectorDrawablesAction()
    }
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

compose.desktop {
    application {
        mainClass = "kz.aita.JvmMainComposeKt"

        nativeDistributions {
            // Only the formats you need:
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            modules("java.sql", "java.logging", "java.xml", "java.desktop", "java.datatransfer", "jdk.crypto.ec", "jdk.charsets")
            // MSI requires a 3-part numeric version:
            packageVersion = "1.0.0"

            // MSI metadata:
            packageName = "AITA"
            description = "AITA"
            vendor = "AITA"

            // Windows-specific:
            windows {
                // Use a proper .ico; path must exist:
                iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.ico"))
                // Optional but nice:
                console = true
                // perUserInstall = true
                // upgradeUuid = "YOUR-STABLE-GUID-HERE" // keep stable across releases
            }
        }

//    nativeDistributions {
//      targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
//
//      windows {
//        iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.png"))
//        // optional:
//        // shortcut = true
//        // menu = true
//        // menuGroup = "AITA"
//        // console = true  // see section 2 below
//      }
//      macOS {
//        iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.png"))
//      }
//      linux {
//        iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.png"))
//      }
//    }
    }
}

afterEvaluate {
    // Collect every generate*Resource* task in this module (debug/release/common/main)
    val resourceGen = tasks.matching {
        it.name.startsWith("generate") && it.name.contains("Resource")
    }
    // Also the common res class task used by compose-resources
    val composeRes = tasks.matching { it.name == "generateComposeResClass" }
    val resourcePackaging = tasks.matching { task ->
        task.name.contains("resource", ignoreCase = true) ||
            task.name.contains("resclass", ignoreCase = true)
    }

    resourceGen.configureEach { dependsOn(removeMisplacedCommonAndroidVectorDrawables) }
    composeRes.configureEach { dependsOn(removeMisplacedCommonAndroidVectorDrawables) }
    resourcePackaging.configureEach { dependsOn(removeMisplacedCommonAndroidVectorDrawables) }

    // Apply to *all* Android KSP tasks (debug/release, etc.)
    tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("KotlinAndroid") }
        .configureEach {
            dependsOn(resourceGen)
            dependsOn(composeRes)
            mustRunAfter(resourceGen)
            mustRunAfter(composeRes)
        }
}