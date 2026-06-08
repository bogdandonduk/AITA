// THIS IS build.gradle of server module
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "kz.aita"
version = "1.0.0"
application {
    mainClass.set("kz.aita.server.ServerKt")

    applicationDefaultJvmArgs = listOf(
        "-Dio.ktor.development=false",
        "-Dktor.development=false",
        "-Dfile.encoding=UTF-8",
        "-Duser.timezone=UTC",
        "-XX:+ExitOnOutOfMemoryError",
        "-XX:MaxRAMPercentage=75.0"
    )
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    implementation(libs.hikaricp)

    implementation("io.ktor:ktor-server-auth:${property("ktor.version")}")
    implementation(libs.ktor.server.websockets)
    implementation("io.ktor:ktor-server-auth-jwt:${property("ktor.version")}")

    implementation("io.ktor:ktor-server-default-headers:${property("ktor.version")}")
    implementation("io.ktor:ktor-server-caching-headers:${property("ktor.version")}")
    // ContentNegotiation + kotlinx.serialization
    implementation("io.ktor:ktor-server-content-negotiation:${property("ktor.version")}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${property("ktor.version")}")
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

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


    // Let Kotlin/Gradle choose the correct shared JVM variants:
    // jvmApiElements for compile classpath and jvmRuntimeElements for runtime classpath.
    implementation(project(":shared"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)


}
ktor {
    fatJar {
        archiveFileName.set("aita-server-all.jar")
    }
}

tasks.withType<JavaExec>().configureEach {
    systemProperty("io.ktor.development", "false")
    systemProperty("ktor.development", "false")
    jvmArgs("-Dio.ktor.development=false", "-Dktor.development=false")
    classpath = sourceSets.main.get().runtimeClasspath
}

tasks.withType<Test>().configureEach {
    systemProperty("io.ktor.development", "false")
    systemProperty("ktor.development", "false")
}
