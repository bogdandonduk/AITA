package kz.aita

import androidx.compose.runtime.*

internal enum class StockWorkspaceTool {
    IMPORT,
    PURCHASE_PLAN,
}

private val LocalStockWorkspaceTool = staticCompositionLocalOf<(StockWorkspaceTool) -> Unit> { {} }

/** Keep modal state above the wide/narrow warehouse branches, so resize does not abandon edits. */
@Composable
internal fun AppConfiguration.StockWorkspaceTools(content: @Composable () -> Unit) {
    var tool by remember { mutableStateOf<StockWorkspaceTool?>(null) }
    CompositionLocalProvider(LocalStockWorkspaceTool provides { tool = it }) { content() }
    when (tool) {
        StockWorkspaceTool.IMPORT -> {
            val recorder = remember { createStockScreenRecorder() }
            DisposableEffect(recorder) { onDispose { recorder.close() } }
            StockScreenImportSheet(recorder) { tool = null }
        }
        StockWorkspaceTool.PURCHASE_PLAN -> ReplenishmentSheet { tool = null }
        null -> Unit
    }
}

@Composable
internal fun stockWorkspaceToolLauncher(): (StockWorkspaceTool) -> Unit =
    LocalStockWorkspaceTool.current
