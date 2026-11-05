plugins { kotlin("jvm") version "2.2.21" }
repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("net.sourceforge.tess4j:tess4j:5.18.0")
}

val repository = projectDir.resolve("../../..").canonicalFile
val generated = layout.buildDirectory.dir("generated/probe")
val generateProbeTypes = tasks.register("generateProbeTypes") {
    val model = repository.resolve("composeApp/src/commonMain/kotlin/kz/aita/StockScreenRecorder.kt")
    inputs.file(model)
    outputs.dir(generated)
    doLast {
        // Use the production word type without compiling unrelated Compose/KMP entrypoints.
        val declaration = "data class StockScreenWord(" + model.readText()
            .substringAfter("data class StockScreenWord(").substringBefore("\n)") + "\n)\n"
        check(declaration.contains("val confidence: Float"))
        val folder = generated.get().asFile.resolve("kz/aita").apply { mkdirs() }
        folder.resolve("StockScreenWord.kt").writeText("package kz.aita\n\n$declaration")
        folder.resolve("OcrProbeLauncher.kt").writeText("""
            package kz.aita
            object OcrProbeLauncher {
                @JvmStatic fun main(args: Array<String>) {
                    require(args.firstOrNull() == "--aita-local-ocr")
                    StockOcrWorker.main(args.drop(1).toTypedArray())
                }
            }
        """.trimIndent())
    }
}
kotlin.sourceSets.main {
    kotlin.srcDirs(repository.resolve("composeApp/src/jvmMain/kotlin"), generated)
    kotlin.include("kz/aita/StockOcrWorker.kt", "kz/aita/StockCaptureProtocol.kt",
        "kz/aita/StockScreenWord.kt", "kz/aita/OcrProbeLauncher.kt")
}
tasks.named("compileKotlin") { dependsOn(generateProbeTypes) }
tasks.jar { archiveFileName.set("aita-stock-ocr-probe.jar") }
tasks.register<Copy>("probePackage") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("package"))
}
