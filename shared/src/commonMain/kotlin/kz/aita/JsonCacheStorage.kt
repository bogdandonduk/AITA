package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

/** SELECT substr instead of fetching an oversized CursorWindow row on Android. SQLite offsets count code points. */
internal suspend fun readLocalKvBounded(key: String, maxCharacters: Int): String? {
    val length = appDatabase.app_databaseQueries.selectKvLength(key).awaitAsOneOrNull()?.text_length ?: return null
    if (length < 0 || length > maxCharacters.toLong()) return null
    if (length == 0L) return ""
    val result = StringBuilder()
    var offset = 1L
    while (offset <= length) {
        val part = appDatabase.app_databaseQueries.selectKvSlice(offset, 32_768L, key).awaitAsOneOrNull()?.text_slice ?: return null
        result.append(part)
        // UTF-16 may contain two chars per SQL character. Bound both to avoid malformed cache allocations.
        if (result.length.toLong() > maxCharacters.toLong() * 2L) return null
        offset += 32_768L
    }
    return result.toString()
}

private val jsonTextCache = ChunkedTextCache(
    read = ::readLocalKvBounded,
    write = { key, value -> putLocalKv(key, value) },
    remove = ::deleteLocalKv,
    removePrefixExcept = { prefix, keep ->
        // Preserve the manifest as well as the new generation. Exact prefix matching avoids SQL LIKE wildcards.
        appDatabase.app_databaseQueries.deleteKvPrefixExcept(prefix, prefix, prefix + "manifest", keep, keep, keep)
    }
)

internal suspend fun readJsonCacheText(key: String): String? = jsonTextCache.get(key)
internal suspend fun writeJsonCacheText(key: String, text: String) = jsonTextCache.put(key, text)
internal suspend fun deleteJsonCacheText(key: String) = jsonTextCache.delete(key)
