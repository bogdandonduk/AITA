package kz.aita

/** Parent catalogue lookup uses the same full barcode rules on the device and server. */
fun GoodsItemDataModel.matchesParentCatalogueQuery(rawQuery: String?): Boolean {
    val query = rawQuery?.trim().orEmpty()
    if (query.isEmpty()) return true
    val barcode = query.normalizedBarcodeToken()
    if (barcode.isNotEmpty() && (allBarcodeValues().any { it.normalizedBarcodeToken().startsWith(barcode) } ||
        query.parseEmbeddedWeightBarcodeFormats().any { matchesEmbeddedWeightBarcode(it) })) return true
    // Full scanned numeric codes are identifiers, not substrings of prices, dates or UUIDs.
    if (barcode.length >= 8 && barcode.all(Char::isDigit)) return false
    val text = (name.map { it.value } + description.map { it.value } + noteLocalized.map { it.value } +
        listOfNotNull(note) + conditions).joinToString(" ").lowercase()
    return query.lowercase().split(Regex("\\s+")).all { it in text }
}

/** Older releases shortened 2-prefixed non-weight codes. Show these ONLY as explicit,
 * manually confirmed suggestions. Never auto-add or derive a quantity from these candidates. */
fun GoodsItemDataModel.isLegacyParentBarcodeCandidate(rawQuery: String): Boolean {
    if (measurementUnitId.isWeightMeasurementUnitId() || matchesParentCatalogueQuery(rawQuery)) return false
    val query = rawQuery.normalizedBarcodeToken()
    if (query.length != 13 || query.firstOrNull() != '2' || !query.all(Char::isDigit)) return false
    return allBarcodeValues().map { it.normalizedBarcodeToken() }.any {
        it.length in 6..8 && it.all(Char::isDigit) && query.startsWith(it)
    }
}

fun List<GoodsItemDataModel>.searchParentCatalogue(rawQuery: String?): List<GoodsItemDataModel> {
    val matches = filter { it.matchesParentCatalogueQuery(rawQuery) }
    return if (matches.isNotEmpty() || rawQuery.isNullOrBlank()) matches
        else filter { it.isLegacyParentBarcodeCandidate(rawQuery) }
}

/** Only call after the user explicitly chooses a labelled legacy suggestion. */
fun GoodsItemDataModel.withConfirmedParentBarcode(rawQuery: String): GoodsItemDataModel {
    if (!isLegacyParentBarcodeCandidate(rawQuery)) return this
    val code = rawQuery.normalizedBarcodeToken()
    val models = effectiveBarcodeModels()
    val source = models.firstOrNull { code.startsWith(it.value.normalizedBarcodeToken()) }
    val added = GoodsItemBarcodeDataModel(code, source?.type ?: GOODS_ITEM_BARCODE_TYPE_STANDARD, source?.storeId)
    return copy(barcodes = (barcodes + code).distinct(), barcodeModels = models + added)
}

/** Recover an already stored full label when an old barcode-model row kept only its prefix.
 * No missing suffix is guessed. Deliberately excluded for measured goods. */
fun List<GoodsItemBarcodeDataModel>.restoreFullLegacyBarcodeModels(legacy: List<String>, weightEncoded: Boolean): List<GoodsItemBarcodeDataModel> {
    if (weightEncoded || isEmpty()) return this
    val recovered = legacy.mapNotNull { value ->
        val full = value.normalizedBarcodeToken()
        if (full.length != 13 || !full.startsWith("2") || !full.all(Char::isDigit) || any { it.value.normalizedBarcodeToken() == full }) return@mapNotNull null
        firstOrNull { model -> model.value.normalizedBarcodeToken().let { it.length in 6..8 && it.all(Char::isDigit) && full.startsWith(it) } }
            ?.copy(value = full)
    }
    return this + recovered
}
