package kz.aita

/** Uses the same display/storage conversion as the main quantity field (including grams). */
internal fun subtractBatchQuantityText(balance: String, removal: String, unit: QuantityDataModel): String? {
    val format = if (unit.allowsFractionalStockQuantityInput()) Regex("[0-9]+([.,][0-9]{1,3})?") else Regex("[0-9]+")
    if (!format.matches(balance.trim()) || !format.matches(removal.trim())) return null
    val available = parseStockQuantityInputText(balance, unit) ?: return null
    val amount = parseStockQuantityInputText(removal, unit) ?: return null
    if (!available.isFinite() || !amount.isFinite() || amount <= 0.0 || available < amount) return null
    return stockQuantityInputTextFromAmount((available - amount).coerceAtLeast(0.0), unit)
}
