package kz.aita

import kotlin.time.Instant
import kotlinx.datetime.*
import kotlinx.serialization.Serializable

/** One personal seasonal reminder per store. Updates use a revision and an immutable request ID. */
@Serializable
data class ReplenishmentReminder(
    val storeId: String,
    val revision: Long = 0,
    val enabled: Boolean = false,
    val settings: ReplenishmentSettings = ReplenishmentSettings(),
    val timeZoneId: String = "Asia/Almaty",
    val daysBeforeOrder: Int = 3,
    val dueAtMillis: Long? = null,
    val firedAtMillis: Long? = null,
    val updatedAtMillis: Long = 0,
)

@Serializable
data class ReplenishmentReminderUpdate(
    val requestId: String,
    val expectedRevision: Long,
    val enabled: Boolean,
    val settings: ReplenishmentSettings,
    val timeZoneId: String,
    val daysBeforeOrder: Int = 3,
)

/** A reminder created on another device keeps its own named time zone in its status label. */
fun ReplenishmentReminder.localDueTime(): String? = dueAtMillis?.let { millis ->
    runCatching {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.of(timeZoneId))
        "${local.date} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }
        .getOrNull()
}

/**
 * Local 09:00 avoids midnight notifications. A reminder already due is sent on the next job tick.
 */
fun replenishmentReminderDue(
    settings: ReplenishmentSettings,
    zoneId: String,
    daysBeforeOrder: Int,
    now: Long,
): Long {
    require(zoneId.length in 1..100 && daysBeforeOrder in 0..30)
    val zone = TimeZone.of(zoneId)
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    require(settings.event != "regular" && settings.valid(today))
    val date = settings.arrival(today).minus(settings.leadDays + daysBeforeOrder, DateTimeUnit.DAY)
    return date.atTime(9, 0).toInstant(zone).toEpochMilliseconds().coerceAtLeast(now)
}

data class ReplenishmentReminderResult(
    val state: ReplenishmentReminder? = null,
    val message: List<LocalizedStringDataModel>? = null,
    val conflict: Boolean = false,
    val denied: Boolean = false,
    val rejected: Boolean = false,
)

suspend fun requestReplenishmentReminder(
    update: ReplenishmentReminderUpdate? = null
): ReplenishmentReminderResult =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.ourIo) {
        val owner = inventoryOwners.current
        val store =
            owner.storeId
                ?: return@withContext ReplenishmentReminderResult(
                    message = replenishmentMessage("access"),
                    denied = true,
                )
        fun current() = inventoryOwnerIsCurrent(owner) && canUseReplenishment(store)
        if (!current())
            return@withContext ReplenishmentReminderResult(
                message = replenishmentMessage("access"),
                denied = true,
            )
        try {
            val response =
                kotlinx.coroutines.withTimeoutOrNull(20_000) {
                    networkRequest<ReplenishmentReminder, ReplenishmentReminderUpdate>(
                        method =
                            if (update == null) io.ktor.http.HttpMethod.Get
                            else io.ktor.http.HttpMethod.Put,
                        endpointUrl = "analytics/replenishment/reminder",
                        headers = mapOf("store_id" to store),
                        body = update,
                        expectedSessionGeneration = owner.sessionGeneration,
                    )
                }
            if (!current()) return@withContext ReplenishmentReminderResult()
            val state =
                response?.payload?.takeIf {
                    !response.negative &&
                        it.storeId == store &&
                        it.revision >= 0 &&
                        it.daysBeforeOrder in 0..30 &&
                        it.timeZoneId.length in 1..100 &&
                        (!it.enabled || it.dueAtMillis != null)
                }
            ReplenishmentReminderResult(
                state,
                response?.message
                    ?: if (state == null) replenishmentMessage("reminder_unavailable") else null,
                conflict = response?.httpStatusCode == 409,
                denied = response?.httpStatusCode == 403,
                rejected = response?.httpStatusCode in setOf(400, 422),
            )
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (_: Exception) {
            ReplenishmentReminderResult(message = replenishmentMessage("reminder_unavailable"))
        }
    }
