package kz.aita

import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private val transactionBarcodeFocusRequests = MutableStateFlow(0L)
private val transactionBarcodeModals = mutableStateMapOf<Any, Unit>()
private val transactionBarcodeEditors = mutableStateMapOf<Any, Unit>()
private var transactionSearchFocusRequester by mutableStateOf<FocusRequester?>(null)

internal fun requestTransactionBarcodeFocus() {
    transactionBarcodeFocusRequests.update { it + 1L }
}

internal fun transactionBarcodeModalOpen(): Boolean = transactionBarcodeModals.isNotEmpty()

/** Nested dialogs have independent ownership: closing one cannot release another's guard. */
@Composable
internal fun TransactionBarcodeModalGuard() {
    val owner = remember { Any() }
    DisposableEffect(owner) {
        transactionBarcodeModals[owner] = Unit
        onDispose { transactionBarcodeModals.remove(owner); requestTransactionBarcodeFocus() }
    }
}

@Composable
internal fun rememberTransactionEditorFocusGuard(): (Boolean) -> Unit {
    val owner = remember { Any() }
    DisposableEffect(owner) {
        onDispose { transactionBarcodeEditors.remove(owner) }
    }
    return remember(owner) {
        { focused ->
            if (focused) transactionBarcodeEditors[owner] = Unit
            else transactionBarcodeEditors.remove(owner)
        }
    }
}

internal fun prefersVisibleTransactionSearch(isNarrowScreen: Boolean): Boolean =
    transactionPrefersVisibleSearch(getPlatformName(), isNarrowScreen)

@Composable
internal fun TransactionSearchFocusTarget(requester: FocusRequester, enabled: Boolean) {
    DisposableEffect(requester, enabled) {
        if (enabled) transactionSearchFocusRequester = requester
        onDispose {
            if (transactionSearchFocusRequester === requester) transactionSearchFocusRequester = null
        }
    }
}

@Composable
internal fun TransactionBarcodeFocusEffect(
    contextKey: String,
    captureEnabled: Boolean,
    preferSearch: Boolean,
    requestHidFocus: () -> Unit
) {
    val requestRevision by transactionBarcodeFocusRequests.collectAsState()
    val windowInfo = LocalWindowInfo.current
    val searchRequester = transactionSearchFocusRequester
    val decision = transactionBarcodeFocusTarget(
        captureEnabled = captureEnabled,
        windowFocused = windowInfo.isWindowFocused,
        modalOpen = transactionBarcodeModals.isNotEmpty(),
        otherEditorFocused = transactionBarcodeEditors.isNotEmpty(),
        preferSearch = preferSearch,
        searchAttached = searchRequester != null
    )
    val latestDecision by rememberUpdatedState(decision)
    val latestHidFocus by rememberUpdatedState(requestHidFocus)
    LaunchedEffect(contextKey, requestRevision, decision, searchRequester) {
        if (decision == TransactionBarcodeFocusTarget.None) return@LaunchedEffect
        // Let a just-clicked editor/dialog claim focus and let navigation finish attaching nodes.
        delay(120)
        repeat(3) {
            withFrameNanos { }
            if (latestDecision != decision) return@LaunchedEffect
            try {
                when (decision) {
                    TransactionBarcodeFocusTarget.Search -> {
                        if (transactionSearchFocusRequester !== searchRequester) return@LaunchedEffect
                        if (searchRequester?.requestFocus() == true) return@LaunchedEffect
                    }
                    TransactionBarcodeFocusTarget.Hid -> {
                        latestHidFocus()
                        return@LaunchedEffect
                    }
                    TransactionBarcodeFocusTarget.None -> return@LaunchedEffect
                }
            } catch (_: IllegalStateException) {
                // A resize/navigation may detach a focus node between layout and this request.
            }
            delay(80)
        }
    }
}
