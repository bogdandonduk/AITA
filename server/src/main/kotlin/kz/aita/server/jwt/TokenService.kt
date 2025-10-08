package kz.aita.server.jwt

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kz.aita.core.io
import kz.aita.model.wrapper.TokenPair
import kz.aita.server.db.RefreshSessions
import kz.aita.server.encrypt.Refresh
import kz.aita.server.util.getException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class TokenService(private val cfg: JwtConfig) {

  private val algorithm = Algorithm
    .HMAC256(cfg.secret)

  fun signAccess(userId: UUID, instant: Instant): String {
    val exp = instant.plusMillis(cfg.accessTTL)
    return JWT.create()
      .withIssuer(cfg.issuer)
      .withAudience(cfg.audience)
      .withSubject(userId.toString())
      .withIssuedAt(Date.from(instant))
      .withExpiresAt(Date.from(exp))
      .sign(algorithm)
  }

  suspend fun newPair(userId: UUID, metaParam: Map<String, String>?): TokenPair = coroutineScope {
    val refreshPlain = Refresh.newPlainToken()
    val refreshHash = Refresh.hash(refreshPlain)
    val now = Instant.now()
    val expires = now.plus(cfg.refreshTTL, ChronoUnit.MILLIS)

    val signAccessAsync = async(Dispatchers.Default) {
      signAccess(userId, now)
    }

    newSuspendedTransaction(Dispatchers.io) {
      RefreshSessions.insert {
        it[id] = UUID.randomUUID()
        it[RefreshSessions.userId] = userId
        it[tokenHash] = refreshHash
        it[createdAt] = now
        it[expiresAt] = expires
        it[meta] = metaParam
      }
    }

    TokenPair(signAccessAsync.await(), cfg.accessTTL, refreshPlain, cfg.refreshTTL)
  }

  suspend fun rotate(refreshPlain: String, metaParam: Map<String, String>?): TokenPair = coroutineScope {
    val hash = Refresh.hash(refreshPlain)

    val cond = RefreshSessions.tokenHash eq hash and RefreshSessions.revokedAt.isNull()

    val oldSession = transaction {
      RefreshSessions.selectAll().where { cond }.forUpdate().singleOrNull() ?: throw IllegalAccessException("No legitimate previous refresh token")
    }

    val now = Instant.now()

    if (oldSession[RefreshSessions.expiresAt].isBefore(now))
      throw IllegalAccessException(getException(3)?.message ?: "Refresh token expired")

    // Revoke old session (so it cannot be used again)

    // Create a fresh session (rotation)
    val newPlain = Refresh.newPlainToken()
    val newHash = Refresh.hash(newPlain)
    val nowMillis = now.toEpochMilli()
    val expires = now.plus(cfg.refreshTTL, ChronoUnit.MILLIS)

    val signAccessAsync = async(Dispatchers.Default) {
      signAccess(oldSession[RefreshSessions.userId], now)
    }

    newSuspendedTransaction(Dispatchers.io) {
      RefreshSessions.update({ RefreshSessions.id eq oldSession[RefreshSessions.id] }) {
        it[revokedAt] = now
      }
    }

    newSuspendedTransaction(Dispatchers.io) {
      RefreshSessions.insert {
        it[id] = UUID.randomUUID()
        it[userId] = oldSession[RefreshSessions.userId]
        it[tokenHash] = newHash
        it[createdAt] = now
        it[expiresAt] = expires
        it[rotatedFrom] = oldSession[RefreshSessions.id]
        it[meta] = metaParam
      }
    }

    TokenPair(
      signAccessAsync.await(),
      nowMillis + cfg.accessTTL,
      newPlain,
      nowMillis + cfg.refreshTTL
    )
  }

  suspend fun revoke(refreshPlain: String) = newSuspendedTransaction(Dispatchers.io) {
    val hash = Refresh.hash(refreshPlain)
    RefreshSessions.update({ (RefreshSessions.tokenHash eq hash) and RefreshSessions.revokedAt.isNull() }) {
      it[revokedAt] = Instant.now()
    }
  }
}