package kz.aita.android

import androidx.core.content.FileProvider

/** App-owned provider with narrowly scoped receipt paths; no public filesystem access. */
class AitaReceiptFileProvider : FileProvider()
