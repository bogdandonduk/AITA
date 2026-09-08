package kz.aita

/** Local outbox IDs identify accepted offline work, not a server-issued receipt number. */
fun TransactionDataModel.serverReceiptIdOrNull(): String? = id.trim().takeIf {
    it.isNotEmpty() && !it.equals("draft", ignoreCase = true) &&
        !it.startsWith("local_", ignoreCase = true)
}

/** Promote only the same accepted operation; never attach another cart/store's response. */
internal fun TransactionReceiptSnapshotDataModel.withServerReceiptIdentity(
    completed: TransactionDataModel
): TransactionReceiptSnapshotDataModel {
    val operationId = transaction.clientOperationId.trim()
    if (operationId.isEmpty() || operationId != completed.clientOperationId.trim() ||
        transaction.storeId != completed.storeId || transaction.type != completed.type ||
        completed.serverReceiptIdOrNull() == null || transaction.serverReceiptIdOrNull() != null
    ) return this
    // The immutable receipt lines/payments are the cashier's accepted snapshot, not live stock.
    return copy(transaction = completed)
}

internal fun reconcileLatestReceiptIdentity(completed: TransactionDataModel) {
    while (true) {
        val previous = latestTransactionReceiptSnapshotState.value ?: return
        val next = previous.withServerReceiptIdentity(completed)
        if (next === previous || latestTransactionReceiptSnapshotState.compareAndSet(previous, next)) return
    }
}
