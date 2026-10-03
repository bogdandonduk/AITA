package kz.aita

/** Quick discounts reduce the unit price to currency precision, before quantity is applied. */
fun validQuickDiscount(percent: Double): Boolean = percent.isFinite() && percent in 0.0..100.0
fun discountedUnitPrice(gross: Double, percent: Double): Double {
    require(gross.isFinite() && gross >= 0.0 && validQuickDiscount(percent))
    return (gross * (1.0 - percent / 100.0)).roundMoney()
}
fun GoodsItemInTransactionDataModel.quickDiscountAmount(): Double =
    ((priceBeforeDiscount ?: pricePerUnit) - pricePerUnit).coerceAtLeast(0.0).times(quantity).roundMoney()
fun TransactionReceiptLineDataModel.quickDiscountAmount(): Double =
    ((priceBeforeDiscount ?: pricePerUnit) - pricePerUnit).coerceAtLeast(0.0).times(quantity.total).roundMoney()
fun quickDiscountLabel(language: String = appLanguageState.value): String = when (language) {
    "ru" -> "Скидка"; "kk" -> "Жеңілдік"; "ky" -> "Арзандатуу"; "tg" -> "Тахфиф"; "uz" -> "Chegirma"; else -> "Discount"
}

@kotlinx.serialization.Serializable
data class CartCheckoutAttempt(val transaction: TransactionDataModel, val receipt: TransactionReceiptSnapshotDataModel)

/** The server response is authoritative for prices, quantities, and any applied discount. */
fun TransactionReceiptSnapshotDataModel.completedWith(completed: TransactionDataModel): TransactionReceiptSnapshotDataModel = copy(
    transaction = completed,
    lines = lines.mapIndexed { index, line -> completed.goodsInTransaction.getOrNull(index)?.let { actual ->
        line.copy(pricePerUnit = actual.pricePerUnit, quantity = line.quantity.copy(total = actual.quantity),
            quickDiscountPercent = actual.quickDiscountPercent, priceBeforeDiscount = actual.priceBeforeDiscount)
    } ?: line }
)
