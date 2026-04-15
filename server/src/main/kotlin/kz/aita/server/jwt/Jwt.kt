package kz.aita.server.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kz.aita.server.db.RefreshSessions
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

@kotlinx.serialization.Serializable
data class JwtConfig(
  val issuer: String,
  val audience: String,
  val realm: String,
  val secret: String,
  val accessTTL: Long,
  val refreshTTL: Long
)

fun Application.jwtConfig(): JwtConfig {
  val c = environment.config.config("ktor.security.jwt")
  return JwtConfig(
    issuer = c.property("issuer").getString(),
    audience = c.property("audience").getString(),
    realm = c.property("realm").getString(),
    secret = c.property("secret").getString(),
    accessTTL = c.property("accessTTL").getString().toLong(),
    refreshTTL = c.property("refreshTTL").getString().toLong()
  )
}

fun Application.configureJwtAuth() {
  val cfg = jwtConfig()

  install(Authentication) {
    jwt("auth-jwt") {                                 // Named auth provider
      realm = cfg.realm

      verifier(                                       // Defines how to verify incoming JWTs
        JWT.require(Algorithm.HMAC256(cfg.secret))    // HS256 with our secret
          .withIssuer(cfg.issuer)                     // Must match issuer
          .withAudience(cfg.audience)                 // Must match audience
          .build()
      )

      validate { cred ->
        val sessionId = runCatching { UUID.fromString(cred.payload.getClaim("sessionId").asString()) }.getOrNull()
          ?: return@validate null

        val ok = newSuspendedTransaction(Dispatchers.IO) {
          val row = RefreshSessions
            .selectAll()
            .where { RefreshSessions.id eq sessionId }
            .limit(1)
            .singleOrNull()

          row != null && row[RefreshSessions.revokedAt] == null && row[RefreshSessions.expiresAt].isAfter(Instant.now()) && cred.payload.issuer == cfg.issuer && cred.payload.audience.contains(
            cfg.audience
          ) && cred.subject != null
        }

        if (ok) JWTPrincipal(cred.payload) else null
      }

      challenge { _, _ ->
        call.respond(UnauthorizedResponse())
      }
    }
  }
}
