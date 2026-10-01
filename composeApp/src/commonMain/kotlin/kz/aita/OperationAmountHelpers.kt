package kz.aita

import kotlin.math.roundToLong

/** Prices and payment inputs use integer hundredths, avoiding repeated floating-point truncation. */
internal fun moneyMinorUnits(value: Double): Long =
    if (!value.isFinite() || value <= 0.0) 0 else (value.coerceAtMost(1_000_000_000_000.0) * 100.0).roundToLong()

internal fun percentQuickFillAmount(base: Double, percent: Double, capAtBase: Boolean): Double {
    if (!base.isFinite() || !percent.isFinite() || base <= 0.0 || percent < 0.0) return 0.0
    val result = moneyMinorUnits(base * percent / 100.0)
    return (if (capAtBase) result.coerceAtMost(moneyMinorUnits(base)) else result) / 100.0
}

internal data class MixedPaymentInputs(val cash: String, val card: String, val debt: String)

internal fun mixedPaymentQuickFillTarget(total: Double, cash: Double, field: String): Double =
    (moneyMinorUnits(total) - if (field == "cash") 0L else moneyMinorUnits(cash))
        .coerceAtLeast(0L) / 100.0

/** Editing an earlier field assigns its remaining balance to the next field. Editing debt
 * preserves cash and adjusts card, so every split stays within the transaction total. */
internal fun fillMixedPaymentRemainder(total: Double, cash: String, card: String, debt: String,
    field: String, input: String): MixedPaymentInputs {
    val all = moneyMinorUnits(total)
    fun amount(text: String) = moneyMinorUnits(text.replace(',', '.').toDoubleOrNull() ?: 0.0)
    fun text(minor: Long) = moneyInputFromDouble(minor / 100.0)
    val c = amount(cash).coerceAtMost(all)
    val entered = amount(input)
    fun enteredText(max: Long) = if (entered > max) text(max) else input
    return when (field) {
        "cash" -> MixedPaymentInputs(enteredText(all), text(all - entered.coerceAtMost(all)), "")
        "card" -> MixedPaymentInputs(cash, enteredText(all - c), text(all - c - entered.coerceAtMost(all - c)))
        "debt" -> MixedPaymentInputs(cash, text(all - c - entered.coerceAtMost(all - c)), enteredText(all - c))
        else -> MixedPaymentInputs(cash, card, debt)
    }
}
