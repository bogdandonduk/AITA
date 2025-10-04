package kz.aita.server.route

import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.routing
import java.io.File

fun Application.filesRoutes() {
  routing {
    staticFiles("config/app/global", File("AITA/server/config/app/global.json"))
    staticFiles("config/app/exception", File("AITA/server/config/app/exceptions.json"))

    staticFiles("res/string", File("AITA/server/assets/values/strings.json"))
    staticFiles("res/dimension", File("AITA/server/assets/values/dimensions.json"))
    staticFiles("res/color", File("AITA/server/assets/values/colors.json"))
    staticFiles("res/drawableConfig", File("AITA/server/assets/drawable/drawables.json"))
    staticFiles("res/drawable", File("AITA/server/assets/drawable"))
  }
}