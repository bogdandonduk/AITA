package kz.aita.server.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
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
import java.util.UUID

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
        println("received2")

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

        call.respond(tokenPair)
      }

      post("/logOut") {
        val body = call.receive<String>()
        tokenService.revoke(body)
        call.respond(HttpStatusCode.OK)
      }

      post("/refresh") {
        val body = call.receive<TokenPair>()
        try {
          call.respond(tokenService.rotate(body.refreshToken, metaFrom(call)))
        } catch (throwable: Throwable) {
          call.respond(HttpStatusCode.Unauthorized,getException(5) ?: "Please log in first")
          throwable.printStackTrace()
        }
      }
    }

    authenticate("auth-jwt") {
      get("/me") {
        val principal = call.principal<JWTPrincipal>()!!          // Provided by the JWT plugin
        val userId = principal.subject!!                           // The "sub" claim we set
        call.respond(mapOf("user_id" to userId))
      }
    }
  }
}