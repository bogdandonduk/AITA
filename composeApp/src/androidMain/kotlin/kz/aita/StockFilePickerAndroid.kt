package kz.aita

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*

@Composable
internal actual fun rememberStockFilePicker(
    title: String,
    onResult: (StockImportFile) -> Unit,
): StockFilePicker {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    var picking by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) picking = false
            else
                scope.launch {
                    try {
                        val choice =
                            withContext(Dispatchers.IO) {
                                try {
                                    val name =
                                        context.contentResolver
                                            .query(
                                                uri,
                                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                                null,
                                                null,
                                                null,
                                            )
                                            ?.use { c ->
                                                if (c.moveToFirst()) c.getString(0) else null
                                            }
                                            .orEmpty()
                                    val bytes =
                                        context.contentResolver.openInputStream(uri)?.use { input ->
                                            val output = ByteArrayOutputStream()
                                            val buffer = ByteArray(16 * 1024)
                                            while (true) {
                                                currentCoroutineContext().ensureActive()
                                                val n = input.read(buffer)
                                                if (n < 0) break
                                                if (output.size() + n > STOCK_FILE_MAX_BYTES)
                                                    return@withContext StockImportFile(
                                                        error = "file_size"
                                                    )
                                                output.write(buffer, 0, n)
                                            }
                                            output.toByteArray()
                                        }
                                    if (bytes == null || bytes.isEmpty())
                                        StockImportFile(error = "file_format")
                                    else StockImportFile(name, bytes)
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (_: Exception) {
                                    StockImportFile(error = "file_format")
                                }
                            }
                        result(choice)
                    } finally {
                        picking = false
                    }
                }
        }
    return StockFilePicker(picking) {
        if (!picking) {
            picking = true
            try {
                launcher.launch(
                    arrayOf(
                        "text/*",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "application/octet-stream",
                    )
                )
            } catch (_: Exception) {
                picking = false
                result(StockImportFile(error = "file_error"))
            }
        }
    }
}
