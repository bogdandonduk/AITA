import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.kotlinSerialization)
  alias(libs.plugins.sqldelight)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)

}

sqldelight {
  databases {
    create("KeyValueDatabase") {
      packageName.set("kz.aita")
      generateAsync.set(true)
    }
  }
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
    sourceSets.all {
      languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    commonMain.dependencies {
//            api(libs.kotlinx.serialization.core)
      implementation(libs.kotlinx.serialization.json)

//      implementation(libs.kotlinx.datetime)

      implementation(libs.ktor.client.auth)

      implementation(libs.sqlDelightRuntime)
      implementation(libs.sqlDelightCoroutinesExtensions)
      implementation(libs.sqlDelightAsyncExtensions)

      implementation("io.ktor:ktor-client-core:${property("ktor.version")}")
      implementation("io.ktor:ktor-client-content-negotiation:${property("ktor.version")}")
      implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")

    }
    commonTest.dependencies {

      implementation(libs.kotlin.test)
      implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${property("coroutines.version")}")

    }
    androidMain.dependencies {
      implementation(libs.androidx.datastore.preferences)
      implementation(libs.hilt.android)
      implementation(libs.androidx.core.ktx)

      implementation(libs.sqlcipher.android)
      implementation(libs.androidx.sqlite)

      implementation(libs.sqlDelightAndroidDriver)

      implementation("io.ktor:ktor-client-okhttp:${property("ktor.version")}")
      implementation("io.ktor:ktor-client-android:${property("ktor.version")}")
    }
    jvmMain.dependencies {
      implementation("com.github.javakeyring:java-keyring:1.0.4")
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
      implementation(libs.logback) // logging
    }
    iosMain.dependencies {
      implementation(libs.sqlDelightNativeDriver)
      implementation("io.ktor:ktor-client-darwin:${property("ktor.version")}")
    }
    wasmJsMain.dependencies {
      implementation(libs.kotlinx.browser)
      implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.1.0"))
      implementation(npm("sql.js", "1.8.0"))
      implementation(libs.sqlDelightWasmJsDriver)
      implementation(libs.sqlDelightWasmJsCoroutinesExtensions)
    }
  }
}

dependencies {
  add("kspAndroid", libs.hilt.android.compiler)
}

afterEvaluate {
  // tasks we need to finish before KSP touches their outputs
  val prereqNames = listOf(
    "generateComposeResClass",
    "generateResourceAccessorsForCommonMain",
    "generateCommonMainKeyValueDatabaseInterface",
    "generateExpectResourceCollectorsForCommonMain",
    "generateResourceAccessorsForAndroidMain",
    "generateActualResourceCollectorsForAndroidMain",
    "generateResourceAccessorsForAndroidDebug" // debug variant
  )

  val prereqs = prereqNames.mapNotNull { tasks.findByName(it) }

  // make ALL Android KSP tasks wait for those
  tasks.matching { it.name.startsWith("ksp") && it.name.endsWith("KotlinAndroid") }
    .configureEach {
      dependsOn(prereqs)
      mustRunAfter(prereqs)
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
