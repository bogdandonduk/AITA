package kz.aita

// Apple release packaging is deferred. Plain CSV/TSV still works through Files on this target.
internal actual suspend fun inflateStockFile(bytes: ByteArray, expectedSize: Int): ByteArray =
    throw StockFileProblem("file_xlsx_unavailable")
