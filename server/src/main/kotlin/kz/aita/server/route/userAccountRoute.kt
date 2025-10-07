package kz.aita.server.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.server.db.Users
import kz.aita.server.util.getException
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

fun Application.userAccountRoute() {
  routing {
    authenticate("auth-jwt") {
      get("/userAccount") {
        val defaultMsg = "Please log in first"
        println("fuckingReceived1")

        val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized,getException(5) ?: defaultMsg)
        println("fuckingReceived2")

        val uuid = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(HttpStatusCode.Unauthorized,getException(5) ?: defaultMsg)

        println("fuckingReceived3 ${principal.subject}")

        val user = transaction {
          Users
            .selectAll()
            .where {
              Users.id eq uuid
            }
            .limit(1)
            .singleOrNull()
            .apply {
              println("here we go2 $this")
            }
        } ?: return@get call.respond(HttpStatusCode.Unauthorized,getException(5) ?: defaultMsg)

        println("fuckingReceived4 $user")

        call.respond(
          UserAccountDataModel(
            id = user[Users.id].toString(),
            phoneNumber = user[Users.phoneNumber],
            email = user[Users.email],
            firstName = user[Users.firstName],
            lastName = user[Users.lastName],
            countryLocale = user[Users.countryLocale],
            storeWorkerAccountId = user[Users.storeWorkerAccountId]?.toString(),
            storeSupplierAccountId = user[Users.storeSupplierAccountId]?.toString(),
            createdAt = user[Users.createdAt].toEpochMilli(),
            isActive = user[Users.isActive]
          )
        )

        println("fuckingReceived5 $user")
      }
    }
  }
}