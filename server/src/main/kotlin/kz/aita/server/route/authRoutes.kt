package kz.aita.server.route

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kz.aita.model.dataModel.UserAuthLogInDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.model.wrapper.TokenPair
import kz.aita.server.db.Users
import kz.aita.server.encrypt.Pw
import kz.aita.server.jwt.TokenService
import kz.aita.server.util.getException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

fun Application.authRoutes(tokenService: TokenService) {

  routing {
    route("/auth") {
      post("/signUp") {
        val body = call.receive<UserAuthSignUpDataModel>()
        val phoneNumber = body.phoneNumber.trim().lowercase()
        val email = body.email.trim().lowercase()

        val phoneNumberExists = transaction {
          val cond = Users.phoneNumber eq phoneNumber
          Users.selectAll().where { cond }.count()
        } > 0

        val emailExists = transaction {
          val cond = Users.email eq email
          Users.selectAll().where { cond }.count()
        } > 0

        if (phoneNumberExists && emailExists)
          return@post call.respond(
            HttpStatusCode.Conflict,
            getException(2)?.message ?: "User with this phone number and email address is already registered"
          )
        else if (phoneNumberExists)
          return@post call.respond(
            HttpStatusCode.Conflict,
            getException(0)?.message ?: "User with this phone number is already registered"
          )
        else if (emailExists)
          return@post call.respond(
            HttpStatusCode.Conflict,
            getException(1)?.message ?: "User with this email address is already registered"
          )

        val firstName = body.firstName.trim()
        val lastName = body.lastName.trim()
        val countryLocale = body.countryLocale.trim().lowercase()

        val userId = UUID.randomUUID()
        val hash = Pw.hash(body.password.toCharArray())
        val instant = Instant.now()

        transaction {
          Users.insert {
            it[Users. id] = userId
            it[Users.phoneNumber] = phoneNumber
            it[Users.email] = email
            it[Users.firstName] = firstName
            it[Users.lastName] = lastName
            it[Users.countryLocale] = countryLocale
            it[Users.storeWorkerAccountId] = null
            it[Users.storeSupplierAccountId] = null
            it[Users.passwordHash] = hash
            it[Users.createdAt] = instant
            it[Users.isActive] = true
          }
        }

        val tokenPair: TokenPair = tokenService.newPair(userId, metaFrom(call))

        call.respond(
          HttpStatusCode.Created,
          tokenPair
        )
      }

      post("/logIn") {
        val body = call.receive<UserAuthLogInDataModel>()

        println("received1 $body")
        val login = body.login.trim().lowercase()

        val cond = (Users.phoneNumber eq login) or (Users.email eq login)
        println("received3")

        val badCredentialsMessage = getException(4)?.message ?: "Login or password incorrect"

        println("received4")

        val user = transaction {
          Users.selectAll().where { cond }.singleOrNull()
        } ?: return@post call.respond(HttpStatusCode.Unauthorized, badCredentialsMessage)

        println("received5")

        // Verify password hash
        val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])

        println("received6")

        if (!ok)
          return@post call.respond(HttpStatusCode.Unauthorized, badCredentialsMessage)


        println("received7")

        val tokenPair: TokenPair = tokenService.newPair(user[Users.id], metaFrom(call))

        println("received8 $tokenPair")

        call.respond(HttpStatusCode.OK,tokenPair)
      }

      post("/logOut") {
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