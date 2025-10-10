package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kz.aita.core.jsonBase
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.wrapper.TokenPair
import kz.aita.server.db.Stores
import kz.aita.server.db.Users
import kz.aita.server.encrypt.Pw
import kz.aita.server.jwt.TokenService
import kz.aita.server.util.getException
import kz.aita.server.util.metaFrom
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PSQLException
import java.time.Instant
import java.util.*

fun Application.authRoutes(tokenService: TokenService) {

  routing {
    route("/auth") {
      post("/signUp") {
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

          val userWithPhoneNumberAndEmailExists = userWithPhoneNumberExists && userWithEmailExists

          if (userWithPhoneNumberAndEmailExists)
            return@newSuspendedTransaction 1
          else if (userWithPhoneNumberExists)
            return@newSuspendedTransaction 2
          else if (userWithEmailExists)
            return@newSuspendedTransaction 3

          return@newSuspendedTransaction 0
        }

        when (conflictResult) {
          0 -> {}
          1 -> return@post call.respond(HttpStatusCode.Conflict, getException(2) ?: "User with this phone number and email address is already registered")
          2 -> return@post call.respond(HttpStatusCode.Conflict, getException(0) ?: "User with this phone number is already registered")
          3 -> return@post call.respond(HttpStatusCode.Conflict, getException(1) ?: "User with this email is already registered")
          else -> return@post call.respond(HttpStatusCode.InternalServerError)
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
                it[Users.workerAccountIds] = null
                it[Users.supplierAccountIds] = null
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

          call.respond(
            HttpStatusCode.Created,
            tokenPair
          )
        } ?: call.respond(HttpStatusCode.InternalServerError)

      }

      post("/logIn") {
        val body = call.receive<UserAuthLogInDataModel>()

        val login = body.login.trim().lowercase()

        val cond = (Users.phoneNumber eq login) or (Users.email eq login)

        val badCredentialsMessage = getException(4)?.message ?: "Login and/or password incorrect"

        val user = newSuspendedTransaction(Dispatchers.IO) {
          Users.selectAll().where { cond }.singleOrNull()
        } ?: return@post call.respond(HttpStatusCode.Unauthorized, badCredentialsMessage)

        val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

        if (!ok)
          return@post call.respond(HttpStatusCode.Unauthorized, badCredentialsMessage)

        val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call))

        call.respond(HttpStatusCode.OK, tokenPair)
      }

      delete("/logOut") {
        val body = call.receive<String>()
        try {
          call.respond(HttpStatusCode.OK, tokenService.revoke(body))
        } catch (throwable: Throwable) {
          call.respond(HttpStatusCode.OK)
          throwable.printStackTrace()
        }
      }

      post("/refresh") {
        println("refresh is called")
        val body = call.receive<String>()
        println("refresh is called2 $body")
        try {
          val newTokens = tokenService.rotate(body, metaFrom(call))
          call.respond(HttpStatusCode.OK, newTokens)
          println("refresh is called3 $newTokens")
        } catch (throwable: Throwable) {
          println("refresh is called4 $throwable")
          call.respond(UnauthorizedResponse())
          throwable.printStackTrace()
        }
      }
    }
  }
}