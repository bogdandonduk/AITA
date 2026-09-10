// THIS IS WasmMainCompose.kt - place in composeApp/src/wasmJsMain/kotlin/kz/aita/WasmMainCompose.kt
package kz.aita

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun installWasmComposePlatformBridges() {
    setClipboardText = { text ->
        runCatching { window.prompt("Copy", text) }
    }

    openExternalUrlPlatformAction = { rawUrl ->
        runCatching {
            val cleanUrl = rawUrl.trim()
            val allowed = cleanUrl.startsWith("https://", ignoreCase = true) ||
                cleanUrl.startsWith("http://", ignoreCase = true) ||
                cleanUrl.startsWith("geo:", ignoreCase = true)
            if (!allowed) {
                ReceiptPlatformActionResult(false, "Unsupported link")
            } else {
                val opened = window.open(cleanUrl, "_blank")
                if (opened != null) {
                    ReceiptPlatformActionResult(true, "Opened map")
                } else {
                    ReceiptPlatformActionResult(false, "The browser blocked the new tab")
                }
            }
        }.getOrElse { throwable ->
            ReceiptPlatformActionResult(false, throwable.message ?: "Could not open the link")
        }
    }

    openPlatformAppSettings = { _ ->
        ReceiptPlatformActionResult(false, "Browser app permissions are controlled by the browser and operating system")
    }

    openSystemDevicesSettings = {
        ReceiptPlatformActionResult(false, "Browser device settings are controlled by the browser and operating system")
    }

    saveReceiptPdfFile = { _, _ ->
        withContext(Dispatchers.ourIo) {
            ReceiptPlatformActionResult(false, "Browser PDF saving bridge is not enabled yet")
        }
    }

    shareReceiptPdfFile = { _, _, _ ->
        withContext(Dispatchers.ourIo) {
            ReceiptPlatformActionResult(false, "Browser sharing bridge is not enabled yet")
        }
    }

    receiptPrintUsesCurrentPage = true
    printReceiptPlatformAction = { _, _, _ ->
        runCatching { window.print() }
        ReceiptPlatformActionResult(true, "Browser print dialog opened")
    }

    getCameraScannerPermissionState = { PlatformPermissionState.Granted }

    requestCameraScannerPermission = { _, onGranted, _ ->
        onGranted()
    }

    barcodeCameraScannerContent = { modifier, _, onClose ->
        BarcodeCameraScannerFallbackPane(
            modifier = modifier,
            onClose = onClose
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    installWasmCommonPlatformBridges()
    installWasmComposePlatformBridges()

    ComposeViewport(document.body!!) {
        AppConfiguration({ MainScreen() })
    }
}
