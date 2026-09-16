// THIS IS build.gradle of composeApp module
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

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

val aitaMacDesktopHost = System.getProperty("os.name")
    .orEmpty()
    .contains("mac", ignoreCase = true)

fun aitaBuildValue(propertyName: String, environmentName: String) =
    providers.gradleProperty(propertyName)
        .orElse(providers.environmentVariable(environmentName))

val aitaReleaseVersion = aitaBuildValue("aita.release.version", "AITA_RELEASE_VERSION")
    .orElse("1.0.0")
    .get()
    .trim()
require(Regex("^[0-9]+\\.[0-9]+\\.[0-9]+$").matches(aitaReleaseVersion)) {
    "AITA release version must use MAJOR.MINOR.PATCH numeric form, for example 1.0.0"
}
val aitaDesktopVersionParts = aitaReleaseVersion.split('.').map(String::toInt)
require(
    aitaDesktopVersionParts[0] in 0..255 &&
        aitaDesktopVersionParts[1] in 0..255 &&
        aitaDesktopVersionParts[2] in 0..65_535
) {
    "Windows package version requires major/minor in 0..255 and patch in 0..65535"
}

val aitaAndroidVersionName = aitaBuildValue("aita.android.versionName", "AITA_ANDROID_VERSION_NAME")
    .orElse(aitaReleaseVersion)
    .get()
    .trim()
val aitaAndroidVersionCode = aitaBuildValue("aita.android.versionCode", "AITA_ANDROID_VERSION_CODE")
    .orElse(aitaBuildValue("aita.release.build", "AITA_RELEASE_BUILD").orElse("1"))
    .get()
    .trim()
    .toIntOrNull()
    ?.takeIf { it in 1..2_100_000_000 }
    ?: error("AITA Android versionCode must be an integer from 1 to 2100000000")

require(aitaAndroidVersionName == aitaReleaseVersion) {
    "Android versionName must equal AITA_RELEASE_VERSION so update identity matches the installed package"
}
val explicitClientBuild = aitaBuildValue("aita.release.build", "AITA_RELEASE_BUILD").orNull
require(explicitClientBuild == null || explicitClientBuild.trim().toIntOrNull() == aitaAndroidVersionCode) {
    "Android versionCode and AITA_RELEASE_BUILD must agree"
}

val aitaAndroidKeystorePath = aitaBuildValue("aita.android.keystorePath", "AITA_ANDROID_KEYSTORE_PATH")
    .orNull
    ?.trim()
    .orEmpty()
val aitaAndroidKeystorePassword = aitaBuildValue("aita.android.keystorePassword", "AITA_ANDROID_KEYSTORE_PASSWORD")
    .orNull
    .orEmpty()
val aitaAndroidKeyAlias = aitaBuildValue("aita.android.keyAlias", "AITA_ANDROID_KEY_ALIAS")
    .orNull
    ?.trim()
    .orEmpty()
val aitaAndroidKeyPassword = aitaBuildValue("aita.android.keyPassword", "AITA_ANDROID_KEY_PASSWORD")
    .orNull
    .orEmpty()
val aitaAndroidSigningValues = listOf(
    aitaAndroidKeystorePath,
    aitaAndroidKeystorePassword,
    aitaAndroidKeyAlias,
    aitaAndroidKeyPassword
)
val aitaAndroidSigningConfigured = aitaAndroidSigningValues.all { it.isNotBlank() }
require(aitaAndroidSigningValues.none { it.isNotBlank() } || aitaAndroidSigningConfigured) {
    "Android release signing is partially configured. Provide keystore path, store password, key alias, and key password together."
}

// Native packages already use packageName = AITA. These arguments also give Gradle/IntelliJ
// desktop debug launches the same macOS menu-bar and Dock identity instead of JvmMainCompose.
tasks.withType<JavaExec>().configureEach {
    if (aitaMacDesktopHost) {
        jvmArgs("-Xdock:name=AITA")
        systemProperty("apple.awt.application.name", "AITA")
        systemProperty("com.apple.mrj.application.apple.menu.about.name", "AITA")
    }
}

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

    // Materialize the standard intermediate source sets (including iosMain) before
    // configuring them below. Conditional target registration otherwise allows an eager
    // getByName("iosMain") lookup to run before the hierarchy has been created.
    applyDefaultHierarchyTemplate()

    sourceSets {
        if (!aitaWebOnlyBuild) {
            getByName("jvmMain").kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
            getByName("androidMain").kotlin.srcDir("src/jvmAndAndroidMain/kotlin")
        }
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

            // Keep the shared iOS source set available even when Gradle sync evaluates this
            // conditional target block before the default hierarchy has materialized its accessors.
            // The explicit target wiring is idempotent when the hierarchy template already created it.
            val iosMainSourceSet = maybeCreate("iosMain").apply {
                dependsOn(getByName("commonMain"))
            }
            listOf("iosX64Main", "iosArm64Main", "iosSimulatorArm64Main").forEach { sourceSetName ->
                findByName(sourceSetName)?.dependsOn(iosMainSourceSet)
            }
            iosMainSourceSet.dependencies {
                // HTTP fetching is provided by kamel-image/core; iOS still needs its Darwin engine.
            }

            getByName("jvmTest").dependencies {
                implementation(kotlin("test-junit"))
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

    // tools:node is parsed before manifest placeholder substitution. Generate a complete
    // manifest from the canonical template instead of using an invalid dynamic tools enum.
    val generateAitaAndroidManifest by tasks.registering {
        val template = layout.projectDirectory.file("src/androidMain/AndroidManifest.xml")
        val output = layout.buildDirectory.file("generated/aitaManifest/AndroidManifest.xml")
        val distribution = aitaBuildValue("aita.release.distribution", "AITA_RELEASE_DISTRIBUTION").orElse("direct")
        inputs.file(template); inputs.property("distribution", distribution); outputs.file(output)
        doLast {
            require(distribution.get() in setOf("direct", "store"))
            val permission = if (distribution.get() == "direct")
                "<uses-permission android:name=\"android.permission.REQUEST_INSTALL_PACKAGES\" />" else ""
            output.get().asFile.apply { parentFile.mkdirs(); writeText(template.asFile.readText().replace("<!-- AITA_DIRECT_INSTALL_PERMISSION -->", permission)) }
        }
    }
    tasks.matching { it.name.startsWith("process") && it.name.endsWith("MainManifest") }.configureEach { dependsOn(generateAitaAndroidManifest) }
    extensions.configure<ApplicationExtension> {
        sourceSets.getByName("main").manifest.srcFile(generateAitaAndroidManifest.map { it.outputs.files.singleFile })
        namespace = "kz.aita"
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        defaultConfig {
            applicationId = "kz.aita"
            minSdk = libs.versions.android.minSdk.get().toInt()
            targetSdk = libs.versions.android.targetSdk.get().toInt()
            versionCode = aitaAndroidVersionCode
            versionName = aitaAndroidVersionName
        }
        packaging {
            resources {
                excludes += "/META-INF/{AL2.0,LGPL2.1}"
            }
        }
        // Use the Android DSL's concrete container element type, not the generic
        // Kotlin DSL create<T> overload during Gradle sync and script compilation.
        val aitaReleaseSigningConfig: com.android.build.api.dsl.ApkSigningConfig? =
            if (aitaAndroidSigningConfigured) {
                val releaseSigning: com.android.build.api.dsl.ApkSigningConfig =
                    signingConfigs.maybeCreate("aitaRelease")
                releaseSigning.storeFile = project.file(aitaAndroidKeystorePath)
                releaseSigning.storePassword = aitaAndroidKeystorePassword
                releaseSigning.keyAlias = aitaAndroidKeyAlias
                releaseSigning.keyPassword = aitaAndroidKeyPassword
                releaseSigning
            } else {
                null
            }

        buildTypes {
            getByName("release") {
                isDebuggable = false
                isMinifyEnabled = false
                aitaReleaseSigningConfig?.let { signingConfig = it }
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
                targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Dmg, TargetFormat.Pkg)
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
                packageVersion = aitaReleaseVersion
                packageName = "AITA"
                description = "AITA"
                vendor = "AITA"

                windows {
                    iconFile.set(project.file("src/jvmMain/resources/drawable/app_icon.ico"))
                    console = false
                    dirChooser = true
                    menuGroup = "AITA"
                    // Never change after the first public Windows release: this stable identity
                    // lets MSI/EXE installers recognize later AITA versions as upgrades.
                    upgradeUuid = "f100f3af-cba2-42e5-928d-8165d1a271a5"
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
