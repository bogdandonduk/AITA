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
//    val port = System.getenv("AITA_PORT")?.toIntOrNull() ?: 8080
    val port = 8080
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
            staticFiles("res/string", File("AITA/server/assets/values/strings.json"))
            staticFiles("res/dimension", File("AITA/server/assets/values/dimensions.json"))
            staticFiles("res/color", File("AITA/server/assets/values/colors.json"))
            staticFiles("res/drawableConfig", File("AITA/server/assets/drawable/drawables.json"))
            staticFiles("res/drawable", File("AITA/server/assets/drawable"))

            staticFiles("config/global", File("AITA/server/config/global.json"))
        }
    }.start(wait = true)
}




























