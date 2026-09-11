package kz.aita

internal data class PaymentAmountSlot(val field: String, val keypad: Boolean = false) {
    // A single stable keypad key makes it move instead of creating competing input owners.
    val key: String get() = if (keypad) "payment-amount-keypad" else "payment-amount-${this.field}"
}

/** Amount editor order; only the selected field is immediately followed by the keypad. */
internal fun paymentAmountSlots(modeId: String, selectedField: String): List<PaymentAmountSlot> {
    val fields = when (modeId) {
        "0" -> listOf("cash")
        "2" -> listOf("cash", "card", "debt")
        else -> return emptyList() // Cashless-only mode uses the full total and provider selection.
    }
    val active = selectedField.takeIf { it in fields } ?: fields.first()
    return buildList {
        fields.forEach { field ->
            add(PaymentAmountSlot(field))
            if (field == active) add(PaymentAmountSlot(field, keypad = true))
        }
    }
}
