package kz.aita

actual fun renderAitaPdfDocument(document: AitaPdfDocument): ByteArray =
    error("PDF export is not configured for this browser. Use the Android or desktop app.")
