package kz.aita

/** Manual prefixes remain editable. A suffix-less HID scanner is a rapid append-only burst. */
internal class TransactionSearchInputBurst {
    private var previousMillis = Long.MIN_VALUE
    private var fastCharacters = 0
    fun reset() { previousMillis = Long.MIN_VALUE; fastCharacters = 0 }
    fun edited(previous: String, next: String, now: Long): Boolean {
        if (next.isEmpty() || !next.startsWith(previous) || next.length <= previous.length) { reset(); return false }
        val added = next.length - previous.length
        if (added > 1) {
            reset()
            return added >= 4 && next.looksLikeCompleteRetailBarcodeInput()
        }
        fastCharacters = if (previousMillis != Long.MIN_VALUE && now >= previousMillis && now - previousMillis <= 50)
            fastCharacters + 1 else 1
        previousMillis = now
        return fastCharacters >= 4 && next.length in 4..64 && next.all(Char::isDigit)
    }
}

internal data class PendingTransactionSearchScan(
    val text: String,
    val handler: (String) -> Boolean,
    val owner: ReceiptActionOwner,
    val inventoryOwner: String?
)
