package kz.aita.server.jwt

import io.ktor.server.application.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.Application
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UnauthorizedResponse
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import kz.aita.server.util.getException

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

      validate { cred ->                              // If validation passes, build a Principal
        // Basic checks
        if (
          cred.payload.issuer != cfg.issuer
          || !cred.payload.audience.contains(cfg.audience)
          || cred.subject == null
        ) return@validate null

        JWTPrincipal(cred.payload)
      }

      challenge { _, _ ->
        call.respond(UnauthorizedResponse())
      }
    }
  }
}
