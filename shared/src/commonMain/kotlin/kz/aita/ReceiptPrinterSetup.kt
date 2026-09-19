package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

val browserReceiptPrinterEnabledState = MutableStateFlow(true)
val browserReceiptPrinterNameState = MutableStateFlow<String?>(null)
private const val BROWSER_RECEIPT_NAME = "browser-receipt-confirmed-name"
fun normalizedBrowserPrinterName(value: String): String = value.filter { it >= ' ' && it != '\u007f' }.trim().take(80)
suspend fun loadBrowserReceiptPrinterName() {
    try {
        browserReceiptPrinterEnabledState.value = getLocalKv("browser-receipt-enabled") != "0"
        configuredReceiptPrinterDeviceIdState.value = if (browserReceiptPrinterEnabledState.value) "browser-system-print" else null
        browserReceiptPrinterNameState.value = getLocalKv(BROWSER_RECEIPT_NAME)?.let(::normalizedBrowserPrinterName)?.takeIf(String::isNotBlank) }
    catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { /* The system dialog remains usable when optional local settings fail. */ }
}
suspend fun saveBrowserReceiptPrinterName(name: String?) {
    val normalized = name?.let(::normalizedBrowserPrinterName)?.takeIf(String::isNotBlank)
    putLocalKv(BROWSER_RECEIPT_NAME, normalized)
    browserReceiptPrinterNameState.value = normalized
}

/** A short calibration slip, without a rotated full transaction barcode or an A4-sized page. */
fun receiptPrinterTestDocument(title: String, dateText: String, language: String = appLanguageState.value): AitaPdfDocument =
    AitaPdfDocument(listOf(
        AitaPdfBlock("aita", AitaPdfRole.Store),
        AitaPdfBlock("aita.kz", AitaPdfRole.Heading),
        AitaPdfBlock("", AitaPdfRole.Divider),
        AitaPdfBlock(title.take(60), AitaPdfRole.Title),
        AitaPdfBlock(dateText.take(40), AitaPdfRole.Heading),
        AitaPdfBlock("", AitaPdfRole.Divider),
        AitaPdfBlock("0123456789  ·  100.00 ₸", AitaPdfRole.Total),
        AitaPdfBlock("Аа Бб Әә Ғғ Ққ Өө Ұұ Үү"),
        AitaPdfBlock("", AitaPdfRole.Divider),
        AitaPdfBlock(deviceWorkflowText("test_only", language), AitaPdfRole.Heading)
    ), maxHeight = 396f, minHeight = 0f, bodySize = 10f)

suspend fun setBrowserReceiptPrinterEnabled(enabled: Boolean) {
    putLocalKv("browser-receipt-enabled", if (enabled) "1" else "0")
    browserReceiptPrinterEnabledState.value = enabled
    configuredReceiptPrinterDeviceIdState.value = if (enabled) "browser-system-print" else null
    if (!enabled) saveBrowserReceiptPrinterName(null)
}
