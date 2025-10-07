package kz.aita.server.jwt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kz.aita.model.wrapper.TokenPair
import kz.aita.server.db.RefreshSessions
import kz.aita.server.encrypt.Refresh
import kz.aita.server.util.getException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

class TokenService(private val cfg: JwtConfig) {

  fun signAccess(userId: UUID): String {
    val now = Instant.now()
    val exp = now.plusMillis(cfg.accessTTL)
    return JWT.create()
      .withIssuer(cfg.issuer)
      .withAudience(cfg.audience)
      .withSubject(userId.toString())
      .withIssuedAt(Date.from(now))
      .withExpiresAt(Date.from(exp))
      .sign(Algorithm.HMAC256(cfg.secret))
  }

  fun newPair(userId: UUID, metaParam: Map<String, String>?): TokenPair = transaction {
    val refreshPlain = Refresh.newPlainToken()
    val refreshHash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val expires = now.plus(cfg.refreshTTL, ChronoUnit.MILLIS)

    RefreshSessions.insert {
      it[id] = UUID.randomUUID()
      it[RefreshSessions.userId] = userId
      it[tokenHash] = refreshHash
      it[createdAt] = now
      it[expiresAt] = expires
      it[meta] = metaParam
    }

    val access = signAccess(userId)
    TokenPair(access, cfg.accessTTL, refreshPlain, cfg.refreshTTL)
  }

  fun rotate(refreshPlain: String, metaParam: Map<String, String>?): TokenPair = transaction {
    val hash = Refresh.hash(refreshPlain)

    val cond = (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull()

    val session = RefreshSessions.selectAll().where { cond }.singleOrNull() ?: throw Unauthorized("invalid_refresh")

    if (session[RefreshSessions.expiresAt].isBefore(Instant.now()))
      throw Unauthorized(getException(3)?.message ?: "Refresh token expired")

    // Revoke old session (so it cannot be used again)
    RefreshSessions.update({ RefreshSessions.id eq session[RefreshSessions.id] }) {
      it[revokedAt] = Instant.now()
    }

    // Create a fresh session (rotation)
    val newPlain = Refresh.newPlainToken()
    val newHash = Refresh.hash(newPlain)
    val now = Instant.now()
    val nowMillis = now.toEpochMilli()
    val expires = now.plus(cfg.refreshTTL, ChronoUnit.MILLIS)

    RefreshSessions.insert {
      it[id] = UUID.randomUUID()
      it[userId] = session[RefreshSessions.userId]
      it[tokenHash] = newHash
      it[createdAt] = now
      it[expiresAt] = expires
      it[rotatedFrom] = session[RefreshSessions.id]
      it[meta] = metaParam
    }

    val access = signAccess(session[RefreshSessions.userId])

    TokenPair(
      access,
      nowMillis + cfg.accessTTL,
      newPlain,
      nowMillis + cfg.refreshTTL
    )
  }

  fun revoke(refreshPlain: String) = transaction {
    val hash = Refresh.hash(refreshPlain)
    RefreshSessions.update({ (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull() }) {
      it[revokedAt] = Instant.now()
    }
  }
}

class Unauthorized(msg: String): RuntimeException(msg)
