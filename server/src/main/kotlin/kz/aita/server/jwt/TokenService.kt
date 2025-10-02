package kz.aita.server.jwt

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kz.aita.server.db.RefreshSessions
import kz.aita.server.encrypt.Refresh
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

@Serializable
data class TokenPair(
  val access_token: String,
  val access_expires_in: Long,     // seconds
  val refresh_token: String
)

class TokenService(private val cfg: JwtCfg) {

  fun signAccess(userId: UUID, deviceId: String?): String {
    val now = Instant.now()
    val exp = now.plusSeconds(cfg.access_ttl_sec)
    return JWT.create()
      .withIssuer(cfg.issuer)
      .withAudience(cfg.audience)
      .withSubject(userId.toString())
      .withClaim("device_id", deviceId)
      .withIssuedAt(Date.from(now))
      .withExpiresAt(Date.from(exp))
      .sign(Algorithm.HMAC256(cfg.secret))
  }

  fun newRefreshPair(userId: UUID, deviceIdParam: String?, metaParam: Map<String, String>?): TokenPair = transaction {
    val refreshPlain = Refresh.newPlainToken()
    val refreshHash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val expires = now.plus(cfg.refresh_ttl_days.toLong(), ChronoUnit.DAYS)

    RefreshSessions.insert {
      it[id] = UUID.randomUUID()
      it[RefreshSessions.userId] = userId
      it[tokenHash] = refreshHash
      it[deviceId] = deviceIdParam
      it[createdAt] = now
      it[expiresAt] = expires
      it[meta] = metaParam
    }

    val access = signAccess(userId, deviceIdParam)
    TokenPair(access, cfg.access_ttl_sec, refreshPlain)
  }

  fun rotate(refreshPlain: String, deviceId: String?, metaParam: Map<String, String>?): TokenPair = transaction {
    val hash = Refresh.hash(refreshPlain)

    val cond = (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull()

    val session = RefreshSessions.selectAll().where { cond }.singleOrNull() ?: throw Unauthorized("invalid_refresh")

    if (session[RefreshSessions.expiresAt].isBefore(Instant.now()))
      throw Unauthorized("refresh_expired")

    // Revoke old session (so it cannot be used again)
    RefreshSessions.update({ RefreshSessions.id eq session[RefreshSessions.id] }) {
      it[revokedAt] = Instant.now()
    }

    // Create a fresh session (rotation)
    val newPlain = Refresh.newPlainToken()
    val newHash = Refresh.hash(newPlain)
    val now = Instant.now()
    val expires = now.plus(cfg.refresh_ttl_days.toLong(), ChronoUnit.DAYS)

    RefreshSessions.insert {
      it[id] = UUID.randomUUID()
      it[userId] = session[RefreshSessions.userId]
      it[tokenHash] = newHash
      it[RefreshSessions.deviceId] = deviceId
      it[createdAt] = now
      it[expiresAt] = expires
      it[rotatedFrom] = session[RefreshSessions.id]
      it[meta] = metaParam
    }

    val access = signAccess(session[RefreshSessions.userId], deviceId)
    TokenPair(access, cfg.access_ttl_sec, newPlain)
  }

  fun revoke(refreshPlain: String) = transaction {
    val hash = Refresh.hash(refreshPlain)
    RefreshSessions.update({ (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull() }) {
      it[revokedAt] = Instant.now()
    }
  }
}

class Unauthorized(msg: String): RuntimeException(msg)
