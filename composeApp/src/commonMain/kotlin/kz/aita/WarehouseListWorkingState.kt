package kz.aita

import androidx.compose.runtime.*

internal class WarehouseListWorkingState {
    val selectedIds = mutableStateOf(emptyList<String>())
    val sortOrderIds = mutableStateOf(emptyList<String>())
}

/** Recomputable catalogue-sized presentation state must never enter an Activity Bundle.
 * Normal background/foreground keeps it; a new owner or reconstructed screen starts fresh.
 * Carts, drafts and pending transaction commands have their own durable storage.
 */
@Composable
internal fun rememberWarehouseListWorkingState(owner: Any?, query: String?, transaction: Int?): WarehouseListWorkingState =
    remember(owner, query, transaction) { WarehouseListWorkingState() }
