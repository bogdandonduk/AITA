package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAccountUpdateDataModel
import kz.aita.server.db.Users
import kz.aita.server.encrypt.Pw
import kz.aita.server.util.genericResponse
import kz.aita.server.util.getResponse
import kz.aita.server.util.getResponses
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.util.*

fun Application.userRoute() {
  routing {
    route("/user") {
      authenticate("auth-jwt") {
        get("/get") {

          val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(UnauthorizedResponse())
          val uuid = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@get call.respond(
            UnauthorizedResponse()
          )

          val user = newSuspendedTransaction(Dispatchers.IO) {
            Users
              .selectAll()
              .where {
                Users.id eq uuid
              }
              .limit(1)
              .singleOrNull()
          } ?: return@get call.respond(UnauthorizedResponse())

          call.genericResponse(
            HttpStatusCode.OK,
            UserAccountDataModel(
              id = user[Users.id].toString(),
              phoneNumber = user[Users.phoneNumber],
              email = user[Users.email],
              firstName = user[Users.firstName],
              lastName = user[Users.lastName],
              countryLocale = user[Users.countryLocale],
              workerAccountIds = user[Users.workerIds],
              supplierAccountIds = user[Users.supplierIds],
              createdAt = user[Users.createdAt].toEpochMilli(),
              isActive = user[Users.isActive]
            )
          )
        }

        put("/update") {
          val principal = call.principal<JWTPrincipal>() ?: return@put call.respond(UnauthorizedResponse())
          val uuid = runCatching { UUID.fromString(principal.subject) }.getOrNull() ?: return@put call.respond(
            UnauthorizedResponse()
          )

          val body = call.receive<UserAccountUpdateDataModel>()
          val newAccount = body.account

          val phoneNumber = newAccount.phoneNumber.trim().lowercase()
          val email = newAccount.email.trim().lowercase()
          val firstName = newAccount.firstName.trim()
          val lastName = newAccount.lastName.trim()
          val countryLocale = newAccount.countryLocale.trim().lowercase()
          val isActive = newAccount.isActive

          val updated = newSuspendedTransaction(Dispatchers.IO) {
            val existingUser =
              Users
                .selectAll()
                .where {
                  Users.id eq uuid
                }
                .forUpdate()
                .limit(1)
                .singleOrNull() ?: return@newSuspendedTransaction "unauthorized"

            if (!Pw.verify(body.password.toCharArray(), existingUser[Users.passwordHash]))
              return@newSuspendedTransaction "password_mismatch"

            val phoneNumberClash = Users
              .select(Users.id, Users.phoneNumber)
              .where {
                (Users.phoneNumber eq newAccount.phoneNumber) and (Users.id neq uuid)
              }
              .empty()
              .not()


            val emailClash = Users
              .select(Users.id, Users.email)
              .where {
                (Users.email eq newAccount.email) and (Users.id neq uuid)
              }
              .empty()
              .not()

            if (phoneNumberClash && emailClash)
              return@newSuspendedTransaction "phone_number_and_email_clash"
            else if (phoneNumberClash)
              return@newSuspendedTransaction "phone_number_clash"
            else if (emailClash)
              return@newSuspendedTransaction "email_clash"

            val newHash = body.newPassword
              ?.takeIf {
                it.isNotEmpty()
                    && it.isNotBlank()
                    && !Pw.verify(it.toCharArray(), existingUser[Users.passwordHash])
              }?.let {
                Pw.hash(it.toCharArray())
              }

            Users.update({ Users.id eq uuid }) {
              if (existingUser[Users.phoneNumber] != phoneNumber)
                it[Users.phoneNumber] = phoneNumber

              if (existingUser[Users.email] != email)
                it[Users.email] = email

              if (existingUser[Users.firstName] != firstName)
                it[Users.firstName] = firstName

              if (existingUser[Users.lastName] != lastName)
                it[Users.lastName] = lastName

              if (existingUser[Users.countryLocale] != countryLocale)
                it[Users.countryLocale] = countryLocale

              newHash?.run {
                it[Users.passwordHash] = this
              }

              if (existingUser[Users.isActive] != isActive)
                it[Users.isActive] = isActive
            }

            "ok"
          }

          when (updated) {
            "ok" -> call.genericResponse(
              HttpStatusCode.OK,
              body.account,
              message = getResponse("9").message
            )

            "unauthorized", "password_mismatch" -> call.respond(UnauthorizedResponse())

            "phone_number_and_email_clash" -> call.genericResponse(
              HttpStatusCode.Conflict,
              message = getResponse("2").message
            )

            "phone_number_clash" -> call.genericResponse(
              HttpStatusCode.Conflict,
              message = getResponse("0").message
            )

            "email_clash" -> call.genericResponse(
              HttpStatusCode.Conflict,
              message = getResponse("1").message
            )

            else -> call.genericResponse(
              status = HttpStatusCode.InternalServerError,
              message = getResponse("3").message
            )
          }
        }
      }
    }
  }
}