package kz.aita.server

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.UUID

/** Follow rotations, including a session-list entry that became stale before the user clicked.
 * UNION also terminates corrupt cycles. Both UUID values are typed, not request SQL fragments. */
internal fun securitySessionLineageInsideTransaction(userId: UUID, sessionId: UUID): Set<UUID> {
    val result = linkedSetOf<UUID>()
    TransactionManager.current().exec("""
        WITH RECURSIVE lineage(id) AS (
          SELECT id FROM refresh_sessions WHERE id = '$sessionId' AND user_id = '$userId'
          UNION
          SELECT child.id FROM refresh_sessions child JOIN lineage parent ON child.rotated_from = parent.id
          WHERE child.user_id = '$userId'
        ) SELECT id FROM lineage
    """.trimIndent(), explicitStatementType = org.jetbrains.exposed.sql.statements.StatementType.SELECT) { rows -> while (rows.next()) result += UUID.fromString(rows.getString(1)) }
    return result
}

internal fun refreshSessionWasSecurityRevokedInsideTransaction(refreshToken: String): Boolean {
    val root = RefreshSessions.selectAll().where { RefreshSessions.tokenHash eq Refresh.hash(refreshToken) }.singleOrNull()
        ?: return false
    val ids = securitySessionLineageInsideTransaction(root[RefreshSessions.userId], root[RefreshSessions.id])
    return RefreshSessions.select(RefreshSessions.id).where {
        (RefreshSessions.id inList ids.toList()) and (RefreshSessions.securityInvalidated eq true)
    }.limit(1).any()
}
