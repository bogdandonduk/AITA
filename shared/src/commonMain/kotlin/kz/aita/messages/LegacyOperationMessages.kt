package kz.aita

/** Frozen old labels are recognition data, not current presentation wording. */
private val legacyOperationEntities = mapOf(
    "store" to listOf("Store", "Магазин", "Дүкен"),
    "worker" to listOf("Worker", "Сотрудник", "Қызметкер"),
    "workshift" to listOf("Workshift", "Смена", "Ауысым"),
    "stock_item" to listOf("Stock item", "Товар", "Тауар"),
    "stock_batch" to listOf("Stock batch", "Партия", "Партия"),
    "transaction" to listOf("Transaction", "Транзакция", "Транзакция"),
    "cash_register" to listOf("Cash register", "Касса", "Касса"),
    "supplier" to listOf("Supplier", "Поставщик", "Жеткізуші"),
    "subscription" to listOf("Subscription", "Подписка", "Жазылым"),
    "finance" to listOf("Finance", "Финансы", "Қаржы")
)
private fun legacyLanguageIndex(language: String): Int? = when (language.lowercase()) {
    "main", "en" -> 0
    "ru" -> 1
    "kk" -> 2
    else -> null
}
private fun List<LocalizedStringDataModel>.matchesLegacyOperationText(expected: List<String>): Boolean =
    isNotEmpty() && all { value -> legacyLanguageIndex(value.language)?.let { value.value == expected[it] } == true }

private val frozenSimpleOperationTitles by lazy { legacyEventMessagePatterns().associateBy { it.key } }

private fun legacyOperationTitle(action: String, entityType: String): List<String> {
    val entity = legacyOperationEntities[entityType] ?: listOf("Operation", "Операция", "Операция")
    val suffix = when (action) {
        "created" -> listOf("saved", "сохранён", "сақталды")
        "updated" -> listOf("updated", "обновлён", "жаңартылды")
        "deleted" -> listOf("deleted", "удалён", "жойылды")
        "completed" -> listOf("completed", "завершена", "аяқталды")
        else -> null
    }
    if (suffix != null) return entity.indices.map { "${entity[it]} ${suffix[it]}" }
    val reference = operationTitleMessageReference(action, entityType)
    val frozen = frozenSimpleOperationTitles[reference.key]
    return frozen?.let { listOf(it.en, it.ru, it.kk) }.orEmpty()
}

fun OperationLogDataModel.resolvedTitleMessageReference(): EventMessageReference? {
    titleTemplate?.let { return it }
    title.eventMessageReferenceOrNull()?.let { return it }
    val oldTitle = legacyOperationTitle(action, entityType)
    if (oldTitle.size == 3 && title.matchesLegacyOperationText(oldTitle)) return operationTitleMessageReference(action, entityType)
    if (entityType == "transaction" && action == "completed") {
        val type = metadata["type"] ?: return null
        val names = legacyTransactionNames(type) ?: return null
        val expected = listOf("${names[0]} completed", "${names[1]} завершена", "${names[2]} аяқталды")
        if (title.matchesLegacyOperationText(expected)) return EventMessageReference("log.transaction.completed",
            children = mapOf("type" to listOf(transactionTypeMessageReference(type))))
    }
    return null
}

fun OperationLogDataModel.resolvedDetailsMessageReference(): EventMessageReference? {
    detailsTemplate?.let { return it }
    details.explicitEventMessageReference()?.let { return it }
    val oldTitle = legacyOperationTitle(action, entityType)
    val entity = legacyOperationEntities[entityType] ?: listOf("Operation", "Операция", "Операция")
    if (oldTitle.size == 3 && details.matchesLegacyOperationText(entity.indices.map { "${entity[it]}: ${oldTitle[it]}" })) {
        return operationDetailsMessageReference(action, entityType)
    }
    if (entityType == "transaction" && action == "completed") {
        val type = metadata["type"]
        val names = type?.let(::legacyTransactionNames)
        val total = metadata["total"]
        if (names != null && total != null && details.matchesLegacyOperationText(names.map { "$it • $total" })) {
            return EventMessageReference("log.transaction.summary", arguments = mapOf("total" to total),
                children = mapOf("type" to listOf(transactionTypeMessageReference(type))))
        }
    }
    legacyBatchDetailsMessageReference()?.let { return it }
    val legacy = details.eventMessageReferenceOrNull()
    if (legacy?.key == "message.changed") {
        return operationChangedFieldsReference(legacy.arguments["value"].orEmpty().split(", "))
    }
    return legacy
}

private fun legacyTransactionNames(type: String): List<String>? = when (type.lowercase()) {
    "purchase", "sale" -> listOf("Sale", "Продажа", "Сату")
    "return" -> listOf("Return", "Возврат", "Қайтару")
    "supply" -> listOf("Supply", "Поставка", "Жеткізу")
    else -> null
}

private fun OperationLogDataModel.legacyBatchDetailsMessageReference(): EventMessageReference? {
    if (entityType != "stock_batch") return null
    val name = metadata["goods_name"] ?: return null
    val rawQuantity = metadata["quantity"]?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
    val quantityNumber = if (rawQuantity == rawQuantity.toLong().toDouble()) rawQuantity.toLong().toString()
        else rawQuantity.toString().trimEnd('0').trimEnd('.')
    val quantity = listOf(quantityNumber, metadata["quantity_unit"].orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
    val barcode = metadata["barcode"].orEmpty()
    val status = metadata["status"] ?: return null
    val supplier = metadata["supplier_name"]?.takeIf { it.isNotBlank() }
    val supply = metadata["supply_price"]?.takeIf { it.isNotBlank() }
    val sale = metadata["sale_price"]?.takeIf { it.isNotBlank() }
    val returns = metadata["return_price"]?.takeIf { it.isNotBlank() }
    val wholesale = metadata["wholesale_price"]?.takeIf { it.isNotBlank() }
    val labels = listOf(
        listOf("Qty", "Supplier", "Barcode", "Supply", "Sale", "Return", "Wholesale", "Status"),
        listOf("Кол-во", "Поставщик", "Штрихкод", "Закупка", "Продажа", "Возврат", "Опт", "Статус"),
        listOf("Саны", "Жеткізуші", "Штрихкод", "Жеткізу", "Сату", "Қайтару", "Көтерме", "Күйі")
    )
    val expected = labels.map { label -> listOfNotNull(
        name, barcode.takeIf { it.isNotBlank() }?.let { "${label[2]}: $it" }, "${label[0]}: $quantity",
        supplier?.let { "${label[1]}: $it" }, supply?.let { "${label[3]}: $it" },
        sale?.let { "${label[4]}: $it" }, returns?.let { "${label[5]}: $it" },
        wholesale?.let { "${label[6]}: $it" }, "${label[7]}: $status"
    ).joinToString(" • ") }
    if (!details.matchesLegacyOperationText(expected)) return null
    return batchDetailsMessageReference(name, barcode, quantity, supplier, supply, sale, returns, wholesale, status)
}
