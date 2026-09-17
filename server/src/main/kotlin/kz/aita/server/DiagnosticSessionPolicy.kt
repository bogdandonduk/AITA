package kz.aita.server

import io.ktor.server.routing.RoutingCall
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

internal suspend fun RoutingCall.diagnosticSessionIsLive(userId: UUID): Boolean {
    val sessionId = currentJwtSessionId() ?: return false
    return newSuspendedTransaction(Dispatchers.IO) {
        exec("SET LOCAL statement_timeout = '3000ms'")
        !RefreshSessions.select(RefreshSessions.id).where {
            (RefreshSessions.id eq sessionId) and (RefreshSessions.userId eq userId) and
                RefreshSessions.revokedAt.isNull() and (RefreshSessions.securityInvalidated eq false) and
                (RefreshSessions.expiresAt greater Instant.now())
        }.empty()
    }
}
