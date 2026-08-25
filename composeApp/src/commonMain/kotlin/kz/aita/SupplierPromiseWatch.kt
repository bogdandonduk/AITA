package kz.aita

import kotlinx.datetime.Instant

internal const val SUPPLIER_PROMISE_WATCH_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1_000L

internal enum class SupplierPromiseUrgency {
    OVERDUE,
    DUE_SOON,
    LATER,
    UNSCHEDULED,
}

internal data class SupplierPromiseWatchEntry<T>(
    val value: T,
    val stableKey: String,
    val promisedAtEpochMillis: Long?,
    val urgency: SupplierPromiseUrgency,
    val terminal: Boolean,
)

internal data class SupplierPromiseWatch<T>(
    val prioritized: List<T>,
    val attention: List<T>,
    val overdueCount: Int,
    val dueSoonCount: Int,
    val nextPromiseAtEpochMillis: Long?,
) {
    val hasAttention: Boolean
        get() = overdueCount > 0 || dueSoonCount > 0
}

/**
 * Builds a stable delivery-promise view without changing authoritative domain objects.
 *
 * Entries with a real stable key are deduplicated so a newer aggregate cannot appear twice. When
 * the caller cannot supply a key, each list position is deliberately treated as distinct rather
 * than relying on hashCode, whose collisions could silently hide legitimate work.
 */
internal fun <T> buildSupplierPromiseWatch(
    values: List<T>,
    nowEpochMillis: Long,
    promisedAt: (T) -> Any?,
    isTerminal: (T) -> Boolean,
    stableKey: (T) -> Any?,
    dueSoonWindowMillis: Long = SUPPLIER_PROMISE_WATCH_WINDOW_MILLIS,
): SupplierPromiseWatch<T> {
    val safeWindow = dueSoonWindowMillis.coerceAtLeast(0L)
    val seenKeys = mutableSetOf<String>()
    val entries = values.mapIndexedNotNull { index, value ->
        val suppliedKey = stableKey(value)?.toString()?.trim().orEmpty()
        val resolvedKey = suppliedKey.ifBlank { "position:$index" }
        if (suppliedKey.isNotBlank() && !seenKeys.add(suppliedKey)) return@mapIndexedNotNull null

        val terminal = isTerminal(value)
        val promisedAtEpochMillis = supplierPromiseEpochMillis(promisedAt(value))
        val urgency = when {
            terminal -> SupplierPromiseUrgency.LATER
            promisedAtEpochMillis == null -> SupplierPromiseUrgency.UNSCHEDULED
            promisedAtEpochMillis < nowEpochMillis -> SupplierPromiseUrgency.OVERDUE
            promisedAtEpochMillis <= nowEpochMillis + safeWindow -> SupplierPromiseUrgency.DUE_SOON
            else -> SupplierPromiseUrgency.LATER
        }
        SupplierPromiseWatchEntry(
            value = value,
            stableKey = resolvedKey,
            promisedAtEpochMillis = promisedAtEpochMillis,
            urgency = urgency,
            terminal = terminal,
        )
    }

    val attentionEntries = entries
        .filter {
            it.urgency == SupplierPromiseUrgency.OVERDUE ||
                it.urgency == SupplierPromiseUrgency.DUE_SOON
        }
        .sortedWith(
            compareBy<SupplierPromiseWatchEntry<T>> { it.urgency.ordinal }
                .thenBy { it.promisedAtEpochMillis ?: Long.MAX_VALUE },
        )
    val attentionKeys = attentionEntries.mapTo(mutableSetOf()) { it.stableKey }
    val remainder = entries.filterNot { it.stableKey in attentionKeys }

    return SupplierPromiseWatch(
        prioritized = (attentionEntries + remainder).map { it.value },
        attention = attentionEntries.map { it.value },
        overdueCount = attentionEntries.count {
            it.urgency == SupplierPromiseUrgency.OVERDUE
        },
        dueSoonCount = attentionEntries.count {
            it.urgency == SupplierPromiseUrgency.DUE_SOON
        },
        nextPromiseAtEpochMillis = entries
            .asSequence()
            .filterNot { it.terminal }
            .mapNotNull { it.promisedAtEpochMillis }
            .filter { it >= nowEpochMillis }
            .minOrNull(),
    )
}

internal fun supplierPromiseEpochMillis(value: Any?): Long? = when (value) {
    null -> null
    is Number -> value.toLong()
    is Instant -> value.toEpochMilliseconds()
    is String -> value.trim().takeIf { it.isNotEmpty() }?.let { raw ->
        raw.toLongOrNull()
            ?: runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()
    }
    else -> value.toString().trim().toLongOrNull()
}

internal fun isTerminalSupplierOrderStatus(value: Any?): Boolean {
    val normalized = value?.toString()
        ?.substringAfterLast('.')
        ?.trim()
        ?.uppercase()
        .orEmpty()
    return normalized in setOf(
        "CANCELLED",
        "CANCELED",
        "COMPLETED",
        "DELIVERED",
        "DECLINED",
        "REJECTED",
        "CLOSED",
        "VOIDED",
        "REFUNDED",
    )
}
