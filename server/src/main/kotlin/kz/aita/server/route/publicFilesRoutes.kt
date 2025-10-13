package kz.aita.server.route

import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import java.io.File

fun Application.filesRoutes() {
  routing {
    staticFiles("config/global", File("AITA/server/config/app/global.json"))

    staticFiles("res/string", File("AITA/server/assets/values/strings.json"))
    staticFiles("res/dimension", File("AITA/server/assets/values/dimensions.json"))
    staticFiles("res/color", File("AITA/server/assets/values/colors.json"))
    staticFiles("res/drawableConfig", File("AITA/server/assets/drawable/drawables.json"))
    staticFiles("res/drawable", File("AITA/server/assets/drawable"))
  }
}