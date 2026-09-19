package kz.aita

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private fun browserPrintDocument(title: String, html: String, done: (Boolean) -> Unit): Unit = js("""{
    if (typeof window.aitaPrintDocument !== 'function') { done(false); return; }
    window.aitaPrintDocument(title, html).then(() => done(true), () => done(false));
}""")

internal fun installBrowserPrinting() {
    preferHtmlDocumentPrinting = true
    printHtmlDocumentPlatformAction = { title, html ->
        val opened = suspendCancellableCoroutine { continuation ->
            browserPrintDocument(title, html) { success -> if (continuation.isActive) continuation.resume(success) }
        }
        ReceiptPlatformActionResult(opened, deviceWorkflowText(if (opened) "print_opened" else "print_failed"))
    }
    fun systemPrinter() = PlatformReceiptPrinterDataModel("browser-system-print", deviceWorkflowText("system_print"),
        deviceWorkflowText("receipt_system_help"), configured = true)
    listPlatformReceiptPrinterDevicesAction = { listOf(systemPrinter()) }
    listPlatformLabelPrinterDevicesAction = { listOf(PlatformLabelPrinterDataModel("browser-system-print",
        deviceWorkflowText("system_print"), deviceWorkflowText("label_system_help"), configured = true,
        supportedProtocols = emptyList())) }
    configurePlatformReceiptPrinterDeviceAction = {
        ReceiptPlatformActionResult(true, deviceWorkflowText("system_print_help"), selectedDeviceId = "browser-system-print")
    }
    configurePlatformLabelPrinterDeviceAction = {
        ReceiptPlatformActionResult(true, deviceWorkflowText("system_print_help"), selectedDeviceId = "browser-system-print")
    }
    configuredReceiptPrinterDeviceIdState.value = "browser-system-print"
    configuredLabelPrinterDeviceIdState.value = "browser-system-print"
}
