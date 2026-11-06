package kz.aita

import androidx.compose.runtime.*
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlinx.coroutines.*

@Composable
internal actual fun rememberStockFilePicker(
    title: String,
    onResult: (StockImportFile) -> Unit,
): StockFilePicker {
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    var active by remember { mutableStateOf(false) }
    val dialog = remember { arrayOfNulls<FileDialog>(1) }
    DisposableEffect(Unit) {
        onDispose {
            SwingUtilities.invokeLater {
                dialog[0]?.dispose()
                dialog[0] = null
            }
        }
    }
    return StockFilePicker(active) {
        if (!active) {
            active = true
            SwingUtilities.invokeLater {
                try {
                    // Attach to AITA's current sheet/window so the native chooser cannot hide
                    // behind it, particularly when another application is being recorded.
                    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                    val picker =
                        when (owner) {
                            is java.awt.Dialog -> FileDialog(owner, title, FileDialog.LOAD)
                            is Frame -> FileDialog(owner, title, FileDialog.LOAD)
                            else ->
                                FileDialog(
                                    Frame.getFrames().firstOrNull { it.isVisible },
                                    title,
                                    FileDialog.LOAD,
                                )
                        }
                    dialog[0] = picker
                    picker.isMultipleMode = false
                    picker.setFilenameFilter { _, name ->
                        name.substringAfterLast('.').lowercase() in
                            setOf("csv", "tsv", "txt", "xlsx")
                    }
                    picker.isVisible = true
                    val selected = picker.files.firstOrNull()?.toPath()
                    picker.dispose()
                    dialog[0] = null
                    scope.launch {
                        try {
                            if (selected != null) {
                                val chosen =
                                    withContext(Dispatchers.IO) {
                                        if (
                                            !Files.isRegularFile(selected) ||
                                                Files.size(selected) > STOCK_FILE_MAX_BYTES
                                        )
                                            StockImportFile(error = "file_size")
                                        else
                                            Files.newInputStream(selected).use { input ->
                                                val output = ByteArrayOutputStream()
                                                val buf = ByteArray(16384)
                                                while (true) {
                                                    currentCoroutineContext().ensureActive()
                                                    val n = input.read(buf)
                                                    if (n < 0) break
                                                    if (output.size() + n > STOCK_FILE_MAX_BYTES)
                                                        return@withContext StockImportFile(
                                                            error = "file_size"
                                                        )
                                                    output.write(buf, 0, n)
                                                }
                                                StockImportFile(
                                                    selected.fileName.toString(),
                                                    output.toByteArray(),
                                                )
                                            }
                                    }
                                result(chosen)
                            }
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            result(StockImportFile(error = "file_format"))
                        } finally {
                            active = false
                        }
                    }
                } catch (_: Exception) {
                    scope.launch {
                        active = false
                        result(StockImportFile(error = "file_error"))
                    }
                }
            }
        }
    }
}
