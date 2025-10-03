package kz.aita.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.request.userAgent
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kz.aita.model.dataModel.UserAccountDataModel
import kz.aita.model.dataModel.UserAuthSignUpDataModel
import kz.aita.server.dataModel.request.RefreshTokenRequestBody
import kz.aita.server.dataModel.request.UserAuthLogInRequestBody
import kz.aita.server.db.Users
import kz.aita.server.encrypt.Pw
import kz.aita.server.jwt.TokenService
import kz.aita.server.jwt.Unauthorized
import kz.aita.server.jwt.jwtCfg
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID

fun Application.routes() {
  val cfg = jwtCfg()
  val tokens = TokenService(cfg)

  routing {
    route("/auth") {

      post("/signUp") {
        val body = call.receive<UserAuthSignUpDataModel>()
        val phoneNumber = body.phoneNumber.trim().lowercase()
        val email = body.email.trim().lowercase()

        val exists = transaction {
          val cond = Users.email eq email
          Users.selectAll().where { cond }.count()
        } > 0
        if (exists) return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "email_taken"))

        // Hash the password and save new user
        val userId = UUID.randomUUID()
        val hash = Pw.hash(body.password.toCharArray())
        println("creating $userId ${body.phoneNumber} $email ${body.firstName} ${body.lastName} ${body.countryLocale} $hash")
        transaction {
          Users.insert {
            it[id] = userId
            it[Users.phoneNumber] = body.phoneNumber
            it[Users.email] = email
            it[Users.firstName] = body.firstName
            it[Users.lastName] = body.lastName
            it[Users.countryLocale] = body.countryLocale
            it[passwordHash] = hash
            // createdAt defaults to now; isActive defaults to true
          }
        }
        call.respond(
          HttpStatusCode.Created,
          UserAccountDataModel(
            phoneNumber = body.phoneNumber,
            email = body.email,
            firstName = body.firstName,
            lastName = body.lastName,
            countryLocale = body.countryLocale
          )
        )
      }

      post("/logIn") {
        val body = call.receive<UserAuthLogInRequestBody>()
        val login = body.login.trim().lowercase()

        val cond = (Users.phoneNumber eq login) or (Users.email eq login)
        // Load user row
        val user = transaction {
          Users.selectAll().where { cond }.singleOrNull()
        } ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "bad_credentials"))

        // Verify password hash
        val ok = Pw.verify(body.password.toCharArray(), user[Users.passwordHash])
        if (!ok || !user[Users.isActive]) {
          return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "bad_credentials"))
        }

        // Issue tokens (+ create refresh session)
        val pair = tokens.newRefreshPair(user[Users.id], metaFrom(call))
        call.respond(pair)
      }

      post("/refresh") {
        val body = call.receive<RefreshTokenRequestBody>()
        try {
          val pair = tokens.rotate(body.refresh_token, metaFrom(call))
          call.respond(pair)
        } catch (e: Unauthorized) {
          call.respond(HttpStatusCode.Unauthorized, mapOf("error" to e.message))
        }
      }

      post("/logout") {
        val body = call.receive<RefreshTokenRequestBody>()
        tokens.revoke(body.refresh_token)
        call.respond(HttpStatusCode.NoContent)
      }
    }

    // Protected routes require a valid "stamp" (access token)
    authenticate("auth-jwt") {
      get("/me") {
        val principal = call.principal<JWTPrincipal>()!!          // Provided by the JWT plugin
        val userId = principal.subject!!                           // The "sub" claim we set
        call.respond(mapOf("user_id" to userId))
      }

      // ...add more protected endpoints here...
    }

    staticFiles("config/app/global", File("AITA/server/config/app/global.json"))

    staticFiles("res/string", File("AITA/server/assets/values/strings.json"))
    staticFiles("res/dimension", File("AITA/server/assets/values/dimensions.json"))
    staticFiles("res/color", File("AITA/server/assets/values/colors.json"))
    staticFiles("res/drawableConfig", File("AITA/server/assets/drawable/drawables.json"))
    staticFiles("res/drawable", File("AITA/server/assets/drawable"))
  }
}

private fun metaFrom(call: ApplicationCall): Map<String, String> = mapOf(
  "ip" to (call.request.header("X-Forwarded-For") ?: call.request.origin.remoteHost),
  "ua" to (call.request.userAgent() ?: "unknown")
)