package kz.aita

fun operationEntityMessageReference(entityType: String): EventMessageReference {
    val key = "log.entity.$entityType"
    return EventMessageReference(if (key in EventMessages.templates) key else "log.entity.operation")
}

fun operationTitleMessageReference(action: String, entityType: String): EventMessageReference = when (action) {
    "created", "updated", "deleted", "completed" -> EventMessageReference(
        "log.action.$action", children = mapOf("entity" to listOf(operationEntityMessageReference(entityType)))
    )
    "extracted" -> EventMessageReference("message.cash_extracted_2")
    "started" -> EventMessageReference("message.workshift_started")
    "ended" -> EventMessageReference("message.workshift_ended")
    "accepted" -> EventMessageReference("message.request_accepted")
    "declined" -> EventMessageReference("message.request_declined")
    "invited" -> EventMessageReference("message.worker_invited")
    "moved" -> EventMessageReference("message.stock_moved")
    else -> EventMessageReference("message.operation_completed")
}

fun operationDetailsMessageReference(action: String, entityType: String): EventMessageReference =
    EventMessageReference("log.subject_action", children = mapOf(
        "entity" to listOf(operationEntityMessageReference(entityType)),
        "action" to listOf(operationTitleMessageReference(action, entityType))
    ))

fun transactionTypeMessageReference(type: String): EventMessageReference = when (type.trim().lowercase()) {
    "purchase", "sale" -> EventMessageReference("log.transaction.sale")
    "return" -> EventMessageReference("log.transaction.return")
    "supply" -> EventMessageReference("log.transaction.supply")
    "" -> EventMessageReference("log.entity.transaction")
    else -> eventFact(type)
}

fun operationChangedFieldsReference(fields: List<String>): EventMessageReference = EventMessageReference(
    "log.fields_changed", children = mapOf("fields" to fields.map { field ->
        val key = "log.field.${field.replace(' ', '_')}"
        if (key in EventMessages.templates) EventMessageReference(key) else eventFact(field)
    }.ifEmpty { listOf(operationEntityMessageReference("stock_item")) })
)

fun batchStatusMessageReference(status: String): EventMessageReference =
    EventMessageReference("log.batch_status.$status").takeIf { EventMessages.isRecognized(it) } ?: eventFact(status)

/** Snapshot values are already formatted original facts (including their original currency/unit). */
fun batchDetailsMessageReference(
    goodsName: String,
    barcode: String,
    quantity: String,
    supplier: String?,
    supply: String?,
    sale: String?,
    returns: String?,
    wholesale: String?,
    status: String
): EventMessageReference = EventMessageReference("event.join", children = mapOf("parts" to buildList {
    add(eventFact(goodsName))
    if (barcode.isNotBlank()) add(eventMessageReference("log.detail.barcode", "value" to barcode))
    add(eventMessageReference("log.detail.quantity", "value" to quantity))
    listOf("supplier" to supplier, "supply" to supply, "sale" to sale, "return" to returns, "wholesale" to wholesale)
        .forEach { (label, value) -> value?.takeIf { it.isNotBlank() }?.let {
            add(eventMessageReference("log.detail.$label", "value" to it))
        } }
    add(EventMessageReference("log.detail.status", children = mapOf("value" to listOf(batchStatusMessageReference(status)))))
}))

fun batchDecisionDetailsMessageReference(quantity: String, note: String?): EventMessageReference =
    EventMessageReference("event.middle_dot", children = mapOf("parts" to buildList {
        add(eventMessageReference("log.decision.quantity", "value" to quantity))
        note?.let { add(eventMessageReference("log.decision.note", "value" to it)) }
    }))
