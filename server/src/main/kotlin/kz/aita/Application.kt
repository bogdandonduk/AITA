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
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.calllogging.CallLogging
import kotlinx.serialization.json.Json
import java.io.File

fun main() {
    val port = System.getenv("AITA_PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("AITA_HOST") ?: "0.0.0.0"

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
            staticFiles("res/drawable/svg", File("AITA/server/assets/drawable/svg"))
            staticFiles("res/values/string", File("AITA/server/assets/values/string"))
            staticFiles("config", File("AITA/server/config"))

        }
    }.start(wait = true)
}




























