package kz.aita.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

internal object AccountAppStates : Table("account_app_states") {
    val owner = uuid("owner_user_id")
    val scope = varchar("scope", 64)
    val revision = long("revision")
    val enabled = bool("enabled")
    val document = text("document").nullable()
    val updated = long("updated_at_millis")
    override val primaryKey = PrimaryKey(owner, scope)
}

internal class AccountAppStateRepository(private val database: Database? = null) {
    private fun row(owner: UUID, scope: AppStateScope) = AccountAppStates.selectAll().where {
        (AccountAppStates.owner eq owner) and (AccountAppStates.scope eq scope.key)
    }.singleOrNull()?.let {
        AccountAppState(it[AccountAppStates.revision], it[AccountAppStates.enabled],
            it[AccountAppStates.document]?.let { text -> jsonBase.decodeFromString<AppStateDocument>(text) }, it[AccountAppStates.updated])
    } ?: AccountAppState()

    suspend fun read(owner: UUID, scope: AppStateScope) = newSuspendedTransaction(Dispatchers.IO, db = database) {
        require(scope.valid())
        row(owner, scope)
    }

    suspend fun write(owner: UUID, request: AppStateWrite): AppStateResult {
        require(request.scope.valid() && request.expectedRevision >= 0 && request.expectedRevision < Long.MAX_VALUE)
        require(request.document == null || request.enabled && request.document?.valid() == true)
        return newSuspendedTransaction(Dispatchers.IO, db = database) {
            exec("SET LOCAL lock_timeout = '3000ms'")
            // The account row also serializes first writes: two revision-zero requests cannot both win.
            Users.select(Users.id).where { Users.id eq owner }.forUpdate().single()
            val before = row(owner, request.scope)
            if (before.revision != request.expectedRevision) return@newSuspendedTransaction AppStateResult(before, conflict = true)
            if (before.revision == 0L) require(AccountAppStates.selectAll().where { AccountAppStates.owner eq owner }.count() < 256)
            val after = AccountAppState(before.revision + 1, request.enabled, request.document, System.currentTimeMillis())
            fun assign(it: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>) {
                it[AccountAppStates.revision] = after.revision; it[AccountAppStates.enabled] = after.enabled
                it[AccountAppStates.document] = after.document?.let { jsonBase.encodeToString(it) }
                it[AccountAppStates.updated] = after.updatedAtMillis
            }
            if (before.revision == 0L) AccountAppStates.insert {
                it[AccountAppStates.owner] = owner; it[AccountAppStates.scope] = request.scope.key; assign(it)
            } else AccountAppStates.update({ (AccountAppStates.owner eq owner) and (AccountAppStates.scope eq request.scope.key) }) { assign(it) }
            AppStateResult(after)
        }
    }
}
