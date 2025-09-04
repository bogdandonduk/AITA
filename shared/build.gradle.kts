import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    
    jvm()
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${property("coroutines.version")}")
            implementation("io.ktor:ktor-client-core:${property("ktor.version")}")
            implementation("io.ktor:ktor-client-content-negotiation:${property("ktor.version")}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")
        }
        commonTest.dependencies {

            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutines.version")}")

        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:${property("ktor.version")}")
        }
        jvmMain.dependencies {
            implementation("io.ktor:ktor-client-java:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-core:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-netty:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-content-negotiation:${property("ktor.version")}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-cors:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-compression:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-auto-head-response:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-conditional-headers:${property("ktor.version")}")
            implementation("io.ktor:ktor-server-call-logging:${property("ktor.version")}")
            implementation(libs.logback) // logging
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:${property("ktor.version")}")
        }
    }
}

android {
    namespace = "kz.aita.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}