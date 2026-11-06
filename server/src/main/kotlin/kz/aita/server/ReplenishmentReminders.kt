@file:OptIn(kotlin.time.ExperimentalTime::class)

package kz.aita.server

import io.ktor.server.application.Application
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.encodeToString
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

internal object ReplenishmentReminders : Table("replenishment_reminders") {
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val storeId = uuid("store_id").references(Stores.id, onDelete = ReferenceOption.CASCADE)
    val revision = long("revision")
    val enabled = bool("enabled").default(false)
    val settings = jsonb("settings", jsonBase, ReplenishmentSettings.serializer())
    val timeZoneId = text("time_zone_id")
    val daysBeforeOrder = integer("days_before_order")
    val dueAtMillis = long("due_at_millis").nullable()
    val firedAtMillis = long("fired_at_millis").nullable()
    val updatedAtMillis = long("updated_at_millis")
    val requestId = uuid("request_id")
    val requestFingerprint = text("request_fingerprint")
    override val primaryKey = PrimaryKey(userId, storeId)
}

internal class ReplenishmentReminderFailure(val kind: String) : IllegalArgumentException(kind)

internal fun canReadPurchasingInsideTransaction(user: UUID, store: UUID): Boolean =
    Users.select(Users.isActive).where { Users.id eq user }.singleOrNull()?.get(Users.isActive) ==
        true &&
        listOf(
                STORE_PERMISSION_ANALYTICS_VIEW,
                STORE_PERMISSION_STOCK_READ,
                STORE_PERMISSION_SUPPLIER_ORDERS_VIEW,
            )
            .all {
                userCanUseStoreActionInsideTransaction(user, store, it, requireWorkshift = false)
            }

// Account deletion holds the same user row lock before removing personal data. Take it first
// for both saves and delivery, so a concurrent reminder cannot recreate data after deletion.
private fun lockReminderUser(user: UUID): Boolean =
    Users.select(Users.isActive)
        .where { Users.id eq user }
        .forUpdate()
        .singleOrNull()
        ?.get(Users.isActive) == true

private fun reminderRow(user: UUID, store: UUID) =
    ReplenishmentReminders.selectAll()
        .where {
            (ReplenishmentReminders.userId eq user) and (ReplenishmentReminders.storeId eq store)
        }
        .singleOrNull()

private fun ResultRow.toReminder() =
    ReplenishmentReminder(
        this[ReplenishmentReminders.storeId].toString(),
        this[ReplenishmentReminders.revision],
        this[ReplenishmentReminders.enabled],
        this[ReplenishmentReminders.settings],
        this[ReplenishmentReminders.timeZoneId],
        this[ReplenishmentReminders.daysBeforeOrder],
        this[ReplenishmentReminders.dueAtMillis],
        this[ReplenishmentReminders.firedAtMillis],
        this[ReplenishmentReminders.updatedAtMillis],
    )

private fun reminderLock(user: UUID, store: UUID) {
    // The same lock is used by edits and delivery; concurrent jobs cannot deliver twice.
    TransactionManager.current()
        .exec(
            "SELECT pg_advisory_xact_lock(hashtextextended('purchasing-reminder:$user:$store', 0))"
        )
}

internal fun readReminderInsideTransaction(user: UUID, store: UUID): ReplenishmentReminder {
    if (!canReadPurchasingInsideTransaction(user, store))
        throw ReplenishmentReminderFailure("access")
    return reminderRow(user, store)?.toReminder() ?: ReplenishmentReminder(store.toString())
}

internal fun saveReminderInsideTransaction(
    user: UUID,
    store: UUID,
    body: ReplenishmentReminderUpdate,
    now: Long,
): ReplenishmentReminder {
    val request =
        runCatching { UUID.fromString(body.requestId) }
            .getOrNull()
            ?.takeIf { it.toString().equals(body.requestId, true) }
            ?: throw ReplenishmentReminderFailure("reminder_invalid")
    if (body.expectedRevision < 0) throw ReplenishmentReminderFailure("reminder_invalid")
    if (!lockReminderUser(user)) throw ReplenishmentReminderFailure("access")
    TransactionManager.current()
        .exec(
            "SELECT pg_advisory_xact_lock(hashtextextended('purchasing-reminder-owner:$user', 0))"
        )
    reminderLock(user, store)
    val previous = reminderRow(user, store)
    if (!canReadPurchasingInsideTransaction(user, store))
        throw ReplenishmentReminderFailure("access")
    val digest =
        MessageDigest.getInstance("SHA-256")
            .digest(jsonBase.encodeToString(body).toByteArray())
            .joinToString("") { "%02x".format(it) }
    if (previous?.get(ReplenishmentReminders.requestId) == request) {
        if (previous[ReplenishmentReminders.requestFingerprint] != digest)
            throw ReplenishmentReminderFailure("reminder_conflict")
        return previous.toReminder()
    }
    val oldRevision = previous?.get(ReplenishmentReminders.revision) ?: 0
    if (oldRevision != body.expectedRevision || oldRevision == Long.MAX_VALUE)
        throw ReplenishmentReminderFailure("reminder_conflict")
    val due =
        if (body.enabled)
            try {
                require(body.settings.categoryId.length <= 120)
                replenishmentReminderDue(body.settings, body.timeZoneId, body.daysBeforeOrder, now)
            } catch (_: IllegalArgumentException) {
                throw ReplenishmentReminderFailure("reminder_invalid")
            }
        else null
    if (
        body.enabled &&
            previous?.get(ReplenishmentReminders.enabled) != true &&
            ReplenishmentReminders.selectAll()
                .where {
                    (ReplenishmentReminders.userId eq user) and
                        (ReplenishmentReminders.enabled eq true)
                }
                .count() >= 100
    )
        throw ReplenishmentReminderFailure("reminder_limit")
    // Disabling keeps the previous valid configuration instead of copying unrelated draft fields.
    val settings =
        if (body.enabled) body.settings
        else previous?.get(ReplenishmentReminders.settings) ?: ReplenishmentSettings()
    val zone =
        if (body.enabled) body.timeZoneId
        else previous?.get(ReplenishmentReminders.timeZoneId) ?: "Asia/Almaty"
    val before =
        if (body.enabled) body.daysBeforeOrder
        else previous?.get(ReplenishmentReminders.daysBeforeOrder) ?: 3
    fun write(row: org.jetbrains.exposed.sql.statements.UpdateBuilder<*>) {
        row[ReplenishmentReminders.revision] = oldRevision + 1
        row[ReplenishmentReminders.enabled] = body.enabled
        row[ReplenishmentReminders.settings] = settings
        row[ReplenishmentReminders.timeZoneId] = zone
        row[ReplenishmentReminders.daysBeforeOrder] = before
        row[ReplenishmentReminders.dueAtMillis] = due
        row[ReplenishmentReminders.firedAtMillis] = null
        row[ReplenishmentReminders.updatedAtMillis] = now
        row[ReplenishmentReminders.requestId] = request
        row[ReplenishmentReminders.requestFingerprint] = digest
    }
    if (previous == null)
        ReplenishmentReminders.insert {
            it[userId] = user
            it[storeId] = store
            write(it)
        }
    else
        ReplenishmentReminders.update({
            (ReplenishmentReminders.userId eq user) and (ReplenishmentReminders.storeId eq store)
        }) {
            write(it)
        }
    return requireNotNull(reminderRow(user, store)).toReminder()
}

/** Returns recipients only after a durable notification and the one-shot marker are committed. */
internal fun deliverPurchasingRemindersInsideTransaction(now: Long): Set<UUID> {
    val candidates =
        ReplenishmentReminders.selectAll()
            .where {
                (ReplenishmentReminders.enabled eq true) and
                    ReplenishmentReminders.firedAtMillis.isNull() and
                    (ReplenishmentReminders.dueAtMillis lessEq now)
            }
            .orderBy(ReplenishmentReminders.dueAtMillis)
            .limit(20)
            .map { it[ReplenishmentReminders.userId] to it[ReplenishmentReminders.storeId] }
    val recipients = mutableSetOf<UUID>()
    for ((user, store) in candidates.sortedBy { "${it.first}:${it.second}" }) {
        val activeUser = lockReminderUser(user)
        reminderLock(user, store)
        val row = reminderRow(user, store) ?: continue
        val state = row.toReminder()
        val due = state.dueAtMillis
        if (!state.enabled || state.firedAtMillis != null || due == null || due > now) continue
        val today =
            Instant.fromEpochMilliseconds(now).toLocalDateTime(TimeZone.of(state.timeZoneId)).date
        if (
            !activeUser ||
                !canReadPurchasingInsideTransaction(user, store) ||
                !state.settings.valid(today)
        ) {
            ReplenishmentReminders.update({
                (ReplenishmentReminders.userId eq user) and
                    (ReplenishmentReminders.storeId eq store)
            }) {
                it[enabled] = false
                it[updatedAtMillis] = now
            }
            continue
        }
        val storeName =
            Stores.select(Stores.name)
                .where { Stores.id eq store }
                .single()[Stores.name]
                .extractLocalizedString(
                    Users.select(Users.appLanguage)
                        .where { Users.id eq user }
                        .single()[Users.appLanguage]
                )
                .orEmpty()
        val notificationId = "purchasing_${user}_${store}_${state.revision}"
        if (
            Notifications.select(Notifications.id)
                .where { Notifications.id eq notificationId }
                .empty()
        ) {
            insertServerNotificationInsideTransaction(
                user,
                store,
                EventMessageReference("purchasing.reminder.title"),
                EventMessageReference(
                    "purchasing.reminder.body",
                    arguments = mapOf("store" to storeName, "date" to state.settings.arrivalDate),
                ),
                category = "purchasing",
                metadata =
                    mapOf(
                        "notificationId" to notificationId,
                        "storeId" to store.toString(),
                        "event" to state.settings.event,
                    ),
                nowMillis = now,
            )
        }
        ReplenishmentReminders.update({
            (ReplenishmentReminders.userId eq user) and (ReplenishmentReminders.storeId eq store)
        }) {
            it[firedAtMillis] = now
            it[updatedAtMillis] = now
        }
        recipients += user
    }
    return recipients
}

internal fun Application.startPurchasingReminders(scope: CoroutineScope) {
    scope.launch {
        while (isActive) {
            try {
                val recipients =
                    newSuspendedTransaction(aitaServerIoContext) {
                        maxAttempts = 1
                        exec("SET LOCAL statement_timeout = '15000ms'")
                        deliverPurchasingRemindersInsideTransaction(System.currentTimeMillis())
                    }
                recipients.forEach {
                    RealtimeServerBus.publish(
                        entity = "notifications",
                        userId = it.toString(),
                        reason = "purchasing_reminder",
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (problem: Exception) {
                environment.log.error("Purchasing reminder delivery failed", problem)
            }
            delay(60_000)
        }
    }
}
