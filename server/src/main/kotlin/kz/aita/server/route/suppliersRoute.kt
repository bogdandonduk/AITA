package kz.aita.server.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kz.aita.server.util.getException
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Application.suppliersRoute() {
  routing {
    authenticate("auth-jwt") {
      get("/suppliers") {
        val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())

        val userId = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(UnauthorizedResponse())

        val suppliers = transaction {

        }
      }
    }
  }
}