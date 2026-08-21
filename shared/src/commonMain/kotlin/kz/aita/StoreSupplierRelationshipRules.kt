package kz.aita

import kotlinx.serialization.Serializable

/**
 * A supplier business identity and a Store's relationship with that identity are deliberately
 * different concepts. This model is the compatibility boundary used while legacy Store-local
 * supplier entries are migrated into explicit relationships.
 */
@Serializable
enum class StoreSupplierRelationshipStatus {
    PENDING_STORE,
    PENDING_SUPPLIER,
    ACTIVE,
    DECLINED,
    UNLINKED,
}

@Serializable
enum class StoreSupplierRelationshipKind {
    /** A Store is linked to a Supplier business profile owned by a Supplier-side account. */
    BUSINESS_LINK,

    /** Historical Store-local directory data. It must never be treated as ownership of a business. */
    LEGACY_LOCAL_CONTACT,
}

@Serializable
enum class StoreSupplierRelationshipSide {
    STORE,
    SUPPLIER,
}

@Serializable
enum class StoreSupplierRelationshipAction {
    ACCEPT,
    DECLINE,
    CANCEL_REQUEST,
    UNLINK,
}

@Serializable
data class StoreSupplierRelationshipKey(
    val storeId: String,
    val supplierId: String,
) {
    fun normalizedOrNull(): StoreSupplierRelationshipKey? {
        val normalizedStore = storeId.trim()
        val normalizedSupplier = supplierId.trim()
        if (normalizedStore.isEmpty() || normalizedSupplier.isEmpty()) return null
        return StoreSupplierRelationshipKey(
            storeId = normalizedStore,
            supplierId = normalizedSupplier,
        )
    }
}

@Serializable
data class StoreSupplierRelationshipSnapshot(
    val id: String,
    val key: StoreSupplierRelationshipKey,
    val kind: StoreSupplierRelationshipKind,
    val status: StoreSupplierRelationshipStatus,
    val requestedBy: StoreSupplierRelationshipSide? = null,
    val revision: Long = 0L,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val sourceOrderIds: List<String> = emptyList(),
    val sourceContractIds: List<String> = emptyList(),
    val sourcePriceIds: List<String> = emptyList(),
)

data class StoreSupplierRelationshipDecision(
    val allowed: Boolean,
    val requiredSide: StoreSupplierRelationshipSide? = null,
    val targetStatus: StoreSupplierRelationshipStatus? = null,
    val reasonCode: String? = null,
)

fun StoreSupplierRelationshipSnapshot.requiredAcceptanceSide(): StoreSupplierRelationshipSide? =
    when (status) {
        StoreSupplierRelationshipStatus.PENDING_STORE -> StoreSupplierRelationshipSide.STORE
        StoreSupplierRelationshipStatus.PENDING_SUPPLIER -> StoreSupplierRelationshipSide.SUPPLIER
        else -> null
    }

fun StoreSupplierRelationshipSnapshot.canPerform(
    actorSide: StoreSupplierRelationshipSide,
    action: StoreSupplierRelationshipAction,
): StoreSupplierRelationshipDecision {
    if (kind == StoreSupplierRelationshipKind.LEGACY_LOCAL_CONTACT) {
        return StoreSupplierRelationshipDecision(
            allowed = false,
            reasonCode = "LEGACY_CONTACT_REQUIRES_LINK_MIGRATION",
        )
    }

    return when (action) {
        StoreSupplierRelationshipAction.ACCEPT -> {
            val required = requiredAcceptanceSide()
            when {
                required == null -> StoreSupplierRelationshipDecision(
                    allowed = false,
                    reasonCode = "RELATIONSHIP_NOT_PENDING",
                )
                required != actorSide -> StoreSupplierRelationshipDecision(
                    allowed = false,
                    requiredSide = required,
                    reasonCode = "RELATIONSHIP_WAITING_FOR_OTHER_SIDE",
                )
                else -> StoreSupplierRelationshipDecision(
                    allowed = true,
                    requiredSide = required,
                    targetStatus = StoreSupplierRelationshipStatus.ACTIVE,
                )
            }
        }

        StoreSupplierRelationshipAction.DECLINE -> {
            val required = requiredAcceptanceSide()
            when {
                required == null -> StoreSupplierRelationshipDecision(
                    allowed = false,
                    reasonCode = "RELATIONSHIP_NOT_PENDING",
                )
                required != actorSide -> StoreSupplierRelationshipDecision(
                    allowed = false,
                    requiredSide = required,
                    reasonCode = "RELATIONSHIP_WAITING_FOR_OTHER_SIDE",
                )
                else -> StoreSupplierRelationshipDecision(
                    allowed = true,
                    requiredSide = required,
                    targetStatus = StoreSupplierRelationshipStatus.DECLINED,
                )
            }
        }

        StoreSupplierRelationshipAction.CANCEL_REQUEST -> {
            val requester = requestedBy
            when {
                requiredAcceptanceSide() == null -> StoreSupplierRelationshipDecision(
                    allowed = false,
                    reasonCode = "RELATIONSHIP_NOT_PENDING",
                )
                requester == null || requester != actorSide -> StoreSupplierRelationshipDecision(
                    allowed = false,
                    reasonCode = "ONLY_REQUESTER_CAN_CANCEL",
                )
                else -> StoreSupplierRelationshipDecision(
                    allowed = true,
                    targetStatus = StoreSupplierRelationshipStatus.UNLINKED,
                )
            }
        }

        StoreSupplierRelationshipAction.UNLINK -> {
            if (status == StoreSupplierRelationshipStatus.ACTIVE) {
                StoreSupplierRelationshipDecision(
                    allowed = true,
                    targetStatus = StoreSupplierRelationshipStatus.UNLINKED,
                )
            } else {
                StoreSupplierRelationshipDecision(
                    allowed = false,
                    reasonCode = "ONLY_ACTIVE_RELATIONSHIP_CAN_UNLINK",
                )
            }
        }
    }
}

fun newStoreSupplierRelationshipRequest(
    id: String,
    key: StoreSupplierRelationshipKey,
    requester: StoreSupplierRelationshipSide,
    nowMillis: Long,
): StoreSupplierRelationshipSnapshot? {
    val normalizedKey = key.normalizedOrNull() ?: return null
    val normalizedId = id.trim()
    if (normalizedId.isEmpty()) return null
    return StoreSupplierRelationshipSnapshot(
        id = normalizedId,
        key = normalizedKey,
        kind = StoreSupplierRelationshipKind.BUSINESS_LINK,
        status = when (requester) {
            StoreSupplierRelationshipSide.STORE -> StoreSupplierRelationshipStatus.PENDING_SUPPLIER
            StoreSupplierRelationshipSide.SUPPLIER -> StoreSupplierRelationshipStatus.PENDING_STORE
        },
        requestedBy = requester,
        revision = 1L,
        createdAtMillis = nowMillis.coerceAtLeast(0L),
        updatedAtMillis = nowMillis.coerceAtLeast(0L),
    )
}

/**
 * Produces one deterministic relationship per immutable Store/Supplier pair.
 * Mutable display names are intentionally not part of the key.
 */
fun mergeStoreSupplierRelationships(
    values: Iterable<StoreSupplierRelationshipSnapshot>,
): List<StoreSupplierRelationshipSnapshot> {
    val grouped = linkedMapOf<StoreSupplierRelationshipKey, MutableList<StoreSupplierRelationshipSnapshot>>()
    values.forEach { value ->
        val key = value.key.normalizedOrNull() ?: return@forEach
        grouped.getOrPut(key) { mutableListOf() } += value.copy(key = key)
    }

    return grouped.values.mapNotNull { candidates ->
        candidates.maxWithOrNull(
            compareBy<StoreSupplierRelationshipSnapshot>(
                { relationshipStatusPriority(it.status) },
                { it.revision.coerceAtLeast(0L) },
                { it.updatedAtMillis.coerceAtLeast(0L) },
                { it.createdAtMillis.coerceAtLeast(0L) },
                { it.id.trim() },
            ),
        )?.let { canonical ->
            canonical.copy(
                sourceOrderIds = candidates.flatMap { it.sourceOrderIds }.normalizeRelationshipSourceIds(),
                sourceContractIds = candidates.flatMap { it.sourceContractIds }.normalizeRelationshipSourceIds(),
                sourcePriceIds = candidates.flatMap { it.sourcePriceIds }.normalizeRelationshipSourceIds(),
            )
        }
    }.sortedWith(
        compareBy<StoreSupplierRelationshipSnapshot>(
            { it.key.storeId },
            { it.key.supplierId },
        ),
    )
}

private fun relationshipStatusPriority(status: StoreSupplierRelationshipStatus): Int =
    when (status) {
        StoreSupplierRelationshipStatus.ACTIVE -> 5
        StoreSupplierRelationshipStatus.PENDING_STORE,
        StoreSupplierRelationshipStatus.PENDING_SUPPLIER,
        -> 4
        StoreSupplierRelationshipStatus.DECLINED -> 3
        StoreSupplierRelationshipStatus.UNLINKED -> 2
    }

private fun Iterable<String>.normalizeRelationshipSourceIds(): List<String> =
    map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

fun StoreSupplierRelationshipSnapshot.hasCommercialHistory(): Boolean =
    sourceOrderIds.any { it.isNotBlank() } ||
        sourceContractIds.any { it.isNotBlank() } ||
        sourcePriceIds.any { it.isNotBlank() }

fun StoreSupplierRelationshipSnapshot.isVisibleInOperationalDirectory(): Boolean =
    status == StoreSupplierRelationshipStatus.ACTIVE ||
        status == StoreSupplierRelationshipStatus.PENDING_STORE ||
        status == StoreSupplierRelationshipStatus.PENDING_SUPPLIER ||
        kind == StoreSupplierRelationshipKind.LEGACY_LOCAL_CONTACT
