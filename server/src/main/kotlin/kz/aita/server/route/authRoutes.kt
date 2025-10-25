package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.wrapper.TokenPair
import kz.aita.server.db.Users
import kz.aita.server.encrypt.Pw
import kz.aita.server.jwt.TokenService
import kz.aita.server.util.genericResponse
import kz.aita.server.util.genericResponseNoPayload
import kz.aita.server.util.getResponse
import kz.aita.server.util.metaFrom
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.postgresql.util.PSQLException
import java.time.Instant
import java.util.*

fun Application.authRoutes(tokenService: TokenService) {

  routing {
    route("/auth") {
      post("/signUp") {
        try {
          val body = call.receive<UserAuthSignUpDataModel>()
          val phoneNumber = body.phoneNumber.trim().lowercase()
          val email = body.email.trim().lowercase()

          val conflictResult = newSuspendedTransaction(Dispatchers.IO) {
            val userWithPhoneNumberExists = Users
              .select(Users.phoneNumber)
              .where { Users.phoneNumber eq phoneNumber }
              .empty()
              .not()

            val userWithEmailExists = Users
              .select(Users.email)
              .where { Users.email eq email }
              .empty()
              .not()

            if (userWithPhoneNumberExists && userWithEmailExists)
              return@newSuspendedTransaction 1
            else if (userWithPhoneNumberExists)
              return@newSuspendedTransaction 2
            else if (userWithEmailExists)
              return@newSuspendedTransaction 3

            return@newSuspendedTransaction 0
          }

          when (conflictResult) {
            1 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("2").message
              )
            }

            2 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("0").message
              )
            }

            3 -> {
              return@post call.genericResponseNoPayload(
                HttpStatusCode.Conflict,
                message = getResponse("1").message
              )
            }
          }

          val firstName = body.firstName.trim()
          val lastName = body.lastName.trim()
          val countryLocale = body.countryLocale.trim().lowercase()

          var id = UUID.randomUUID()

          val hash = Pw.hash(body.password.toCharArray())
          val instant = Instant.now()

          var state23505Reached: Boolean

          do {
            state23505Reached = try {
              id = UUID.randomUUID()

              newSuspendedTransaction(Dispatchers.IO) {
                Users.insert {
                  it[Users.id] = id
                  it[Users.phoneNumber] = phoneNumber
                  it[Users.email] = email
                  it[Users.firstName] = firstName
                  it[Users.lastName] = lastName
                  it[Users.countryLocale] = countryLocale
                  it[Users.workerIds] = null
                  it[Users.supplierIds] = null
                  it[Users.passwordHash] = hash
                  it[Users.createdAt] = instant
                  it[Users.isActive] = true
                }

                false
              }
            } catch (exception: ExposedSQLException) {
              val constraint = (exception.cause as? PSQLException)?.serverErrorMessage?.constraint
              val isPkCollision = exception.sqlState == "23505" && constraint?.equals("users_pkey", true) == true

              isPkCollision
            }
          } while (state23505Reached)

          id?.run {
            val tokenPair: TokenPair = tokenService.newPair(this, metaFrom(call))

            call.genericResponse(
              status = HttpStatusCode.Created,
              tokenPair
            )
          } ?: call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
        } catch (throwable: Throwable) {
          call.genericResponseNoPayload(
            status = HttpStatusCode.InternalServerError,
            message = getResponse("3").message
          )
          throwable.printStackTrace()
        }
      }

      post("/logIn") {
        val body = call.receive<UserAuthLogInDataModel>()

        val login = body.login.trim().lowercase()

        val user = newSuspendedTransaction(Dispatchers.IO) {
          Users.selectAll().where { (Users.phoneNumber eq login) or (Users.email eq login) }.singleOrNull()
        } ?: return@post call.respond(UnauthorizedResponse())

        val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

        if (!ok)
          return@post call.respond(UnauthorizedResponse())

        val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call))

        print("issued tokens are $tokenPair")
        call.genericResponse<TokenPair>(HttpStatusCode.OK, tokenPair)
      }

      delete("/logOut") {
        val body = call.receive<String>()

        try {
          tokenService.revoke(body)
        } catch (throwable: Throwable) {
          throwable.printStackTrace()
        }

        call.genericResponseNoPayload(HttpStatusCode.OK, message = getResponse("8").message)
      }

      post("/refresh") {
        val body = call.receive<String>()
        try {
          val newTokens = tokenService.rotate(body, metaFrom(call))
          call.genericResponse(HttpStatusCode.OK, newTokens)
        } catch (throwable: Throwable) {
          call.respond(UnauthorizedResponse())
          throwable.printStackTrace()
        }
      }
    }
  }
}