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
    implementation("io.ktor:ktor-server-caching-headers:${property("ktor.version")}")
    // ContentNegotiation + kotlinx.serialization
    implementation("io.ktor:ktor-server-content-negotiation:${property("ktor.version")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")

    // Useful server plugins (optional but nice)
    implementation("io.ktor:ktor-server-cors:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-compression:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-auto-head-response:${property("ktor.version")}")

    implementation("io.ktor:ktor-server-call-logging:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-status-pages:${property("ktor.version")}")

    implementation("io.ktor:ktor-server-conditional-headers:${property("ktor.version")}")

    // Password hashing
    implementation(libs.bcrypt)                         // BCrypt hash & verify

    // Read YAML config (so we can keep secrets out of code)
    implementation(libs.ktor.server.config.yaml)

    implementation(libs.exposed.core)          // Exposed base
    implementation(libs.exposed.dao)           // (optional) DAO layer
    implementation(libs.exposed.jdbc)          // JDBC support
    implementation(libs.exposed.java.time)     // java.time columns (Instant, etc.)
    implementation(libs.exposed.json)          // JSON/JSONB columns
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.java.jwt)


    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)

    implementation(projects.shared)

}