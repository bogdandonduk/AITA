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
    val promisedAtEpochMillis: Long?,
    val urgency: SupplierPromiseUrgency,
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
 * Builds a stable, duplicate-free delivery-promise view without changing the authoritative
 * order objects. Near-term commitments are moved to the front; terminal orders stay out of
 * the attention slice but retain their relative position in the full list.
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
    val entries = values.mapNotNull { value ->
        val stableKeyValue = stableKey(value)?.toString()?.trim().orEmpty()
        val fallbackKey = value.hashCode().toString()
        val key = stableKeyValue.ifEmpty { fallbackKey }
        if (!seenKeys.add(key)) return@mapNotNull null

        val promisedAtEpochMillis = supplierPromiseEpochMillis(promisedAt(value))
        val urgency = when {
            isTerminal(value) -> SupplierPromiseUrgency.LATER
            promisedAtEpochMillis == null -> SupplierPromiseUrgency.UNSCHEDULED
            promisedAtEpochMillis < nowEpochMillis -> SupplierPromiseUrgency.OVERDUE
            promisedAtEpochMillis <= nowEpochMillis + safeWindow ->
                SupplierPromiseUrgency.DUE_SOON
            else -> SupplierPromiseUrgency.LATER
        }
        SupplierPromiseWatchEntry(value, promisedAtEpochMillis, urgency)
    }

    val attentionEntries = entries
        .filter { it.urgency == SupplierPromiseUrgency.OVERDUE ||
            it.urgency == SupplierPromiseUrgency.DUE_SOON }
        .sortedWith(
            compareBy<SupplierPromiseWatchEntry<T>> { it.urgency.ordinal }
                .thenBy { it.promisedAtEpochMillis ?: Long.MAX_VALUE },
        )
    val attentionKeys = attentionEntries
        .map { stableKey(it.value)?.toString()?.trim().orEmpty().ifEmpty {
            it.value.hashCode().toString()
        } }
        .toHashSet()
    val remainder = entries.filterNot {
        val key = stableKey(it.value)?.toString()?.trim().orEmpty().ifEmpty {
            it.value.hashCode().toString()
        }
        key in attentionKeys
    }

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
            .filter { it.urgency != SupplierPromiseUrgency.OVERDUE }
            .mapNotNull { it.promisedAtEpochMillis }
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
