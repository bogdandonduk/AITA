package kz.aita.server.jwt


import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

@kotlinx.serialization.Serializable
data class JwtCfg(
  val issuer: String,
  val audience: String,
  val realm: String,
  val secret: String,
  val access_ttl_sec: Long,
  val refresh_ttl_days: Int
)

fun Application.jwtCfg(): JwtCfg {
  val c = environment.config.config("ktor.security.jwt")
  return JwtCfg(
    issuer = c.property("issuer").getString(),
    audience = c.property("audience").getString(),
    realm = c.property("realm").getString(),
    secret = c.property("secret").getString(),
    access_ttl_sec = c.property("access_ttl_sec").getString().toLong(),
    refresh_ttl_days = c.property("refresh_ttl_days").getString().toInt()
  )
}

fun Application.configureJwtAuth() {
  val cfg = jwtCfg()
  authentication {
    jwt("auth-jwt") {                                 // Named auth provider
      realm = cfg.realm
      verifier(                                       // Defines how to verify incoming JWTs
        JWT.require(Algorithm.HMAC256(cfg.secret))    // HS256 with our secret
          .withIssuer(cfg.issuer)                     // Must match issuer
          .withAudience(cfg.audience)                 // Must match audience
          .build()
      )
      validate { cred ->                              // If validation passes, build a Principal
        val uid = cred.payload.subject                // "sub" claim → user id
        if (uid.isNullOrBlank()) null else JWTPrincipal(cred.payload)
      }
      challenge { _, _ ->                             // What to reply on 401
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "unauthorized"))
      }
    }
  }
}
