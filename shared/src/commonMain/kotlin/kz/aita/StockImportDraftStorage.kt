package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsList

/**
 * Local-only review drafts; chunked storage survives application updates and never syncs
 * screenshots.
 */
private fun stockImportDraftKey(accountId: String, storeId: String): String {
    require(
        accountId.isNotBlank() &&
            accountId.length <= 200 &&
            storeId.isNotBlank() &&
            storeId.length <= 200
    )
    return "stock-screen-import-v1:${accountId.length}:$accountId:${storeId.length}:$storeId"
}

suspend fun readStockImportDraft(accountId: String, storeId: String): String? =
    readRequiredJsonJournal(stockImportDraftKey(accountId, storeId))?.also {
        require(it.length <= 8_000_000)
    }

suspend fun writeStockImportDraft(accountId: String, storeId: String, text: String) {
    require(text.length <= 8_000_000)
    writeJsonCacheText(stockImportDraftKey(accountId, storeId), text)
}

suspend fun deleteStockImportDraft(accountId: String, storeId: String) =
    deleteJsonCacheText(stockImportDraftKey(accountId, storeId)).also {
        clearStockImportProgress(accountId, storeId)
    }

data class StockImportProgress(val started: Boolean, val itemSaved: Boolean, val complete: Boolean)

private fun stockImportProgressPrefix(accountId: String, storeId: String) =
    stockImportDraftKey(accountId, storeId) + ":progress:"

/** Tiny per-row checkpoints avoid rewriting a multi-megabyte review after every imported item. */
suspend fun writeStockImportProgress(
    accountId: String,
    storeId: String,
    rowId: String,
    progress: StockImportProgress,
) {
    require(rowId.isNotBlank() && rowId.length <= 200)
    require(progress.started && (!progress.complete || progress.itemSaved))
    putLocalKv(
        stockImportProgressPrefix(accountId, storeId) + rowId,
        "1|${if (progress.itemSaved) 1 else 0}|${if (progress.complete) 1 else 0}",
    )
}

suspend fun readStockImportProgress(
    accountId: String,
    storeId: String,
    rowIds: List<String>,
): Map<String, StockImportProgress> {
    require(rowIds.size <= 3000 && rowIds.all { it.isNotBlank() && it.length <= 200 })
    val prefix = stockImportProgressPrefix(accountId, storeId)
    return buildMap {
        // Stay below SQLite's historical parameter limit and avoid thousands of worker round trips
        // for a large draft, especially once completed checkpoints have already been cleared.
        for (group in rowIds.distinct().chunked(128)) {
            val saved =
                appDatabase.app_databaseQueries
                    .selectStockImportCheckpoints(group.map { prefix + it })
                    .awaitAsList()
            for (row in saved) {
                val value = row.progress
                check(value != null && value in setOf("1|0|0", "1|1|0", "1|1|1")) {
                    "Import progress is unreadable"
                }
                put(
                    row.key.removePrefix(prefix),
                    StockImportProgress(true, value[2] == '1', value[4] == '1'),
                )
            }
        }
    }
}

/** Call only after the full review snapshot was durably saved, or after explicit draft discard. */
suspend fun clearStockImportProgress(accountId: String, storeId: String) {
    val prefix = stockImportProgressPrefix(accountId, storeId)
    appDatabase.app_databaseQueries.deleteKvPrefixExcept(
        prefix = prefix,
        manifestKey = prefix,
        keepPrefix = "",
    )
}

suspend fun clearStockImportRowProgress(accountId: String, storeId: String, rowId: String) {
    require(rowId.isNotBlank() && rowId.length <= 200)
    deleteLocalKv(stockImportProgressPrefix(accountId, storeId) + rowId)
}
