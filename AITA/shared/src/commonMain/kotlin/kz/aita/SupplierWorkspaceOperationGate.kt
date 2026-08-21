package kz.aita

/**
 * Identifies one asynchronous operation inside a Supplier workspace.
 *
 * Supplier identity alone is not enough to reject stale results: a user can switch from identity A
 * to B and back to A before the first A request finishes. The monotonically advancing generation
 * distinguishes those two A workspaces, while [operationKey] prevents an Orders result from owning
 * a Dispatch operation that happened to start in the same generation.
 */
data class SupplierWorkspaceOperationTicket(
    val generation: Long,
    val supplierIdentityId: String?,
    val operationKey: String,
)

/**
 * Lightweight UI/repository ownership gate for Supplier-mode requests and mutations.
 *
 * This class intentionally has no coroutine or platform dependency. The owner is expected to keep
 * one gate per visible workspace and call [invalidate] when the account, app mode, or Supplier focus
 * changes. It is designed for single-owner state (for example a Compose screen or state holder), not
 * as a replacement for server-side authorization, locking, or idempotency.
 */
class SupplierWorkspaceOperationGate(
    initialGeneration: Long = 0L,
) {
    private var generation: Long = normalizeInitialGeneration(initialGeneration)
    private var currentTicket: SupplierWorkspaceOperationTicket? = null

    fun begin(
        supplierIdentityId: String?,
        operationKey: String,
    ): SupplierWorkspaceOperationTicket {
        val cleanOperationKey = operationKey.trim()
        require(cleanOperationKey.isNotEmpty()) { "Supplier workspace operation key must not be blank" }

        generation = nextSupplierWorkspaceOperationGeneration(generation)
        return SupplierWorkspaceOperationTicket(
            generation = generation,
            supplierIdentityId = normalizeSupplierWorkspaceIdentity(supplierIdentityId),
            operationKey = cleanOperationKey,
        ).also { ticket ->
            currentTicket = ticket
        }
    }

    fun isCurrent(ticket: SupplierWorkspaceOperationTicket): Boolean = currentTicket == ticket

    fun invalidate() {
        generation = nextSupplierWorkspaceOperationGeneration(generation)
        currentTicket = null
    }

    fun currentGeneration(): Long = generation
}

fun normalizeSupplierWorkspaceIdentity(supplierIdentityId: String?): String? =
    supplierIdentityId?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()

fun nextSupplierWorkspaceOperationGeneration(current: Long): Long =
    if (current <= 0L || current == Long.MAX_VALUE) 1L else current + 1L

private fun normalizeInitialGeneration(initialGeneration: Long): Long =
    initialGeneration.takeIf { it in 1 until Long.MAX_VALUE } ?: 0L
