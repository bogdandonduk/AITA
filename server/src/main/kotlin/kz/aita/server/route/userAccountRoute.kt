package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.server.db.Users
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun Application.userAccountRoute() {
  routing {
    authenticate("auth-jwt") {
      get("/userAccount") {

        val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())
        val uuid = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(UnauthorizedResponse())

        val user = transaction {
          Users
            .selectAll()
            .where {
              Users.id eq uuid
            }
            .limit(1)
            .singleOrNull()
        } ?: return@get call.respond(UnauthorizedResponse())

        call.respond(
          HttpStatusCode.OK,
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
      }
    }
  }
}