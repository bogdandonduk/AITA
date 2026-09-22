package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.delay

internal fun <T> uniqueExactBarcode(items: List<T>, query: String, codes: (T) -> Iterable<String>): T? =
    query.trim().takeIf { it.isNotEmpty() }?.let { value ->
        items.filter { item -> codes(item).any { it.trim().equals(value, true) } }.singleOrNull()
    }

/** Wait for typing to settle and for a complete result before applying a template. */
@Composable internal fun <T> AutomaticBarcodeChoice(query: String, items: List<T>, ready: Boolean,
    owner: String?, codes: (T) -> Iterable<String>, onChoose: (T) -> Unit) {
    val choose by rememberUpdatedState(onChoose)
    var consumed by remember(owner) { mutableStateOf<String?>(null) }
    val match = uniqueExactBarcode(items, query, codes)
    LaunchedEffect(query, match, ready, owner) {
        if (query.isBlank()) { consumed = null; return@LaunchedEffect }
        if (!ready || match == null || query == consumed) return@LaunchedEffect
        delay(500)
        choose(match)
        consumed = query
    }
}
