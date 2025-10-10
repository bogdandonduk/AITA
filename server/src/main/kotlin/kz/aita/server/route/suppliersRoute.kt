package kz.aita.server.route

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kz.aita.server.db.Users
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.suppliersRoute() {
  routing {
    authenticate("auth-jwt") {
      get("/suppliers") {
        val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())

        val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(UnauthorizedResponse())
      }
    }
  }
}