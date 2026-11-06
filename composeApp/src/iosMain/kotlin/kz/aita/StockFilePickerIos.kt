@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package kz.aita

import androidx.compose.runtime.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

/** System Files picker: copies only the explicitly selected table, without broad library access. */
@Composable
internal actual fun rememberStockFilePicker(
    title: String,
    onResult: (StockImportFile) -> Unit,
): StockFilePicker {
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    var visible by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<UIDocumentPickerViewController?>(null) }
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                visible = false
                picker = null
            }

            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                picker = null
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url == null) {
                    visible = false
                    return
                }
                scope.launch {
                    try {
                        val selection =
                            withContext(Dispatchers.Default) {
                                val access = url.startAccessingSecurityScopedResource()
                                try {
                                    val path =
                                        url.path
                                            ?: return@withContext StockImportFile(
                                                error = "file_format"
                                            )
                                    val attributes =
                                        NSFileManager.defaultManager.attributesOfItemAtPath(
                                            path,
                                            null,
                                        )
                                    val size =
                                        (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue
                                            ?: 0L
                                    if (size !in 1..STOCK_FILE_MAX_BYTES.toLong())
                                        return@withContext StockImportFile(error = "file_size")
                                    val data =
                                        NSData.dataWithContentsOfURL(url)
                                            ?: return@withContext StockImportFile(
                                                error = "file_format"
                                            )
                                    if (data.length !in 1uL..STOCK_FILE_MAX_BYTES.toULong())
                                        return@withContext StockImportFile(error = "file_size")
                                    val bytes = ByteArray(data.length.toInt())
                                    bytes.usePinned {
                                        memcpy(it.addressOf(0), data.bytes, data.length)
                                    }
                                    StockImportFile(url.lastPathComponent.orEmpty(), bytes)
                                } finally {
                                    if (access) url.stopAccessingSecurityScopedResource()
                                }
                            }
                        result(selection)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (_: Exception) {
                        result(StockImportFile(error = "file_error"))
                    } finally {
                        visible = false
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            picker?.delegate = null
            picker?.dismissViewControllerAnimated(false, null)
            picker = null
        }
    }
    return StockFilePicker(visible) {
        if (!visible) {
            val windows =
                UIApplication.sharedApplication.connectedScenes
                    .filterIsInstance<UIWindowScene>()
                    .flatMap { it.windows.filterIsInstance<UIWindow>() }
            var presenter =
                (windows.firstOrNull { it.isKeyWindow() }
                        ?: UIApplication.sharedApplication.keyWindow)
                    ?.rootViewController
            while (presenter?.presentedViewController != null) presenter =
                presenter?.presentedViewController
            if (presenter == null) result(StockImportFile(error = "file_error"))
            else {
                val selected =
                    UIDocumentPickerViewController(
                        documentTypes =
                            listOf(
                                "public.comma-separated-values-text",
                                "public.tab-separated-values-text",
                                "public.plain-text",
                                "org.openxmlformats.spreadsheetml.sheet",
                            ),
                        inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                    )
                selected.allowsMultipleSelection = false
                selected.delegate = delegate
                selected.title = title
                picker = selected
                visible = true
                presenter.presentViewController(selected, true, null)
            }
        }
    }
}
