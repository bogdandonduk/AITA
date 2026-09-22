package kz.aita

/** Manual prefixes remain editable. A suffix-less HID scanner is a rapid append-only burst. */
internal class TransactionSearchInputBurst {
    private var previousMillis = Long.MIN_VALUE
    private var fastCharacters = 0
    fun reset() { previousMillis = Long.MIN_VALUE; fastCharacters = 0 }
    fun edited(previous: String, next: String, now: Long): Boolean {
        if (next.isEmpty() || !next.startsWith(previous) || next.length <= previous.length) { reset(); return false }
        val added = next.length - previous.length
        val rapidAppend = previousMillis != Long.MIN_VALUE && now >= previousMillis && now - previousMillis <= 50
        fastCharacters = if (rapidAppend) fastCharacters + added else added
        previousMillis = now
        // Compose may report several HID key events together in one editor update.
        // Count their characters, while keeping a lone pasted short prefix editable.
        return next.length in 4..64 && next.all(Char::isDigit) &&
            ((added >= 4 && next.looksLikeCompleteRetailBarcodeInput()) || (rapidAppend && fastCharacters >= 4))
    }
}

internal data class PendingTransactionSearchScan(
    val text: String,
    val handler: (String) -> Boolean,
    val owner: ReceiptActionOwner,
    val inventoryOwner: String?
)

/** A hidden HID input and the camera must reset the same visible search as its own editor. */
internal class TransactionSearchCompletion {
    private var active: (() -> Unit)? = null
    fun attach(reset: () -> Unit): () -> Unit {
        active = reset
        return { if (active === reset) active = null }
    }
    private var captureActive: (() -> (() -> Unit))? = null
    fun attachSnapshot(capture: () -> (() -> Unit)): () -> Unit {
        captureActive = capture
        return { if (captureActive === capture) captureActive = null }
    }
    fun capture(): () -> Unit {
        val source = captureActive
        val completion = source?.invoke() ?: active ?: {}
        return { if (source === captureActive) completion() }
    }
    fun handled() { capture().invoke() }
}

internal val transactionSearchCompletion = TransactionSearchCompletion()

/** A delayed disk read must not resurrect a query after typing or a completed scan. */
internal class TextDraftRestoreGuard {
    var revision = 0L
        private set
    fun edited() { revision++ }
    fun accepts(startedAt: Long) = revision == startedAt
}
