package kz.aita

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.calllogging.CallLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    val port = System.getenv("AITA_PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("AITA_HOST") ?: "0.0.0.0"   // bind to all interfaces for LAN

    // Point to your repo's assets directory (relative to project root / working dir)
    val baseDir = File("server_assets").absoluteFile
    val imagesDir = File(baseDir, "images")
    val stringsDir = File(baseDir, "strings")

    embeddedServer(Netty, port = port, host = host) {
        install(CallLogging)
        install(AutoHeadResponse)
        install(Compression) {
            gzip()
            deflate()
        }
        install(ConditionalHeaders) // adds ETag/Last-Modified when possible
        install(CORS) {
            anyHost() // for LAN/dev; lock down in prod
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Get)
        }
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                ignoreUnknownKeys = true
            })
        }

        routing {
            // Serve localized strings
            get("res/strings/retail/{locale}") {
                val locale = call.parameters["locale"]?.lowercase() ?: "en"
                val file = File(stringsDir, "strings_$locale" + "_retail.json")
                if (file.exists()) {
                    call.response.headers.append(HttpHeaders.CacheControl, "max-age=300") // 5 min
                    call.respondFile(file)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "missing locale: $locale"))
                }
            }

            // Serve static images (svg/png/webp/etc). Ktor adds Last-Modified automatically.
            static("/images") {
                files(imagesDir) // GET /images/logo_newyear.svg
                // optional: default("logo_default.svg")
            }
        }
    }.start(wait = true)
}

@Serializable
data class AppConfig(
    val promoActive: Boolean,
    val currentLogo: String, // e.g., "logo_newyear.svg"
    val cacheSeconds: Int
)
