plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
}

group = "kz.aita"
version = "1.0.0"
application {
    mainClass.set("kz.aita.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    // ContentNegotiation + kotlinx.serialization
    implementation("io.ktor:ktor-server-content-negotiation:${property("ktor.version")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")

    // Useful server plugins (optional but nice)
    implementation("io.ktor:ktor-server-cors:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-compression:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-auto-head-response:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-call-logging:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-conditional-headers:${property("ktor.version")}")

    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}