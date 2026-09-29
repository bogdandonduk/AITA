package kz.aita

/** Persisted unit IDs: existing kilograms retain their identity and price basis. */
const val GRAMS_UNIT_ID = "g"
fun String.isWeightMeasurementUnitId(): Boolean = this == "1" || this == GRAMS_UNIT_ID
fun gramsQuantityUnit() = QuantityDataModel(GRAMS_UNIT_ID, listOf(
    LocalizedStringDataModel("main", "g"), LocalizedStringDataModel("en", "g"),
    LocalizedStringDataModel("ru", "г"), LocalizedStringDataModel("kk", "г"),
    LocalizedStringDataModel("ky", "г"), LocalizedStringDataModel("tg", "г"),
    LocalizedStringDataModel("uz", "g")), total = 1.0, pricedAmount = 1.0, roundTotal = false)

fun QuantityDataModel.isGramQuantityUnit(): Boolean = id == GRAMS_UNIT_ID || immutableUnitName.any {
    it.value.trim().lowercase().trimEnd('.') in setOf("g", "г", "gram", "grams", "грамм", "граммы")
}
fun List<QuantityDataModel>.withGramUnit(): List<QuantityDataModel> =
    if (any { it.isGramQuantityUnit() }) this else this + gramsQuantityUnit()

fun QuantityDataModel.quantityFromScale(barcode: EmbeddedWeightBarcodeDataModel): QuantityDataModel? {
    if (!isWeightQuantityUnit() || barcode.weightGrams <= 0) return null
    return copy(roundTotal = false).withTotalValue(if (isGramQuantityUnit()) barcode.weightGrams.toDouble() else barcode.weightKilograms)
}

fun weightUnitChangeMessage() = listOf(
    LocalizedStringDataModel("en", "This item already has batches. Keep its unit or create a new item with another unit."),
    LocalizedStringDataModel("ru", "У товара уже есть партии. Сохраните единицу измерения или создайте новый товар с другой единицей."),
    LocalizedStringDataModel("kk", "Тауардың партиялары бар. Өлшем бірлігін сақтаңыз немесе жаңа тауар жасаңыз."),
    LocalizedStringDataModel("ky", "Товардын партиялары бар. Өлчөм бирдигин сактаңыз же жаңы товар түзүңүз."),
    LocalizedStringDataModel("tg", "Мол партияҳо дорад. Воҳидро нигоҳ доред ё моли нав созед."),
    LocalizedStringDataModel("uz", "Mahsulot partiyalari mavjud. Birlikni saqlang yoki yangi mahsulot yarating."))
