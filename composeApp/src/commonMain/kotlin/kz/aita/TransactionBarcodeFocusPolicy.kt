package kz.aita

/** One focus decision for the checkout, instead of competing search/hidden-input timers. */
internal enum class TransactionBarcodeFocusTarget { None, Search, Hid }

internal fun transactionBarcodeFocusTarget(
    captureEnabled: Boolean,
    windowFocused: Boolean,
    modalOpen: Boolean,
    otherEditorFocused: Boolean,
    preferSearch: Boolean,
    searchAttached: Boolean
): TransactionBarcodeFocusTarget = when {
    !captureEnabled || !windowFocused || modalOpen || otherEditorFocused -> TransactionBarcodeFocusTarget.None
    preferSearch && searchAttached -> TransactionBarcodeFocusTarget.Search
    preferSearch -> TransactionBarcodeFocusTarget.None // Wait for the visible field to attach.
    else -> TransactionBarcodeFocusTarget.Hid
}

/** BasicTextField gives us the WHOLE edited value, not just the newest scanner character. */
internal fun transactionHidBuffer(raw: String, maxLength: Int = 32): String = raw
    .filterNot { it == '\r' || it == '\n' || it == '\t' }
    .takeLast(maxLength.coerceIn(32, 64))

internal fun transactionPrefersVisibleSearch(platformName: String, isNarrowScreen: Boolean): Boolean {
    val platform = platformName.lowercase()
    return !isNarrowScreen && listOf("desktop", "jvm", "wasm", "web", "browser").any { it in platform }
}
