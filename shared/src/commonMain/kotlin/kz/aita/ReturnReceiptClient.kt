package kz.aita

import io.ktor.http.HttpMethod

/** A focused lookup never replaces the transaction history or writes another cart. */
suspend fun searchReturnReceipts(
    query: String,
    exact: Boolean,
    storeId: String
): ResponseDataModel<List<TransactionDataModel>> {
    val owner = inventoryOwners.current
    if (owner.storeId != storeId || !inventoryOwnerIsCurrent(owner)) return cloudSessionExpiredResponse()
    val clean = query.trim()
    if (!validReturnReceiptQuery(clean, exact)) return ResponseDataModel(null, emptyList(), false)
    val result = networkRequest<List<TransactionDataModel>, Unit>(
        HttpMethod.Get,
        endpointUrl = "transactions/search",
        query = mapOf("query" to clean, "exact" to exact.toString()),
        headers = mapOf("store_id" to storeId),
        expectedSessionGeneration = owner.sessionGeneration
    )
    return if (inventoryOwnerIsCurrent(owner)) result else cloudSessionExpiredResponse()
}

fun validReturnReceiptQuery(query: String, exact: Boolean): Boolean =
    query.length in (if (exact) 1 else 3)..160 &&
        query.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' || it == ':' || it == '.' }
