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
        val part = appDatabase.app_databaseQueries.selectKvSlice(sliceOffset = offset, sliceLength = 32_768L, cacheKey = key).awaitAsOneOrNull()?.text_slice ?: return null
        result.append(part)
        // UTF-16 may contain two chars per SQL character. Bound both to avoid malformed cache allocations.
        if (result.length.toLong() > maxCharacters.toLong() * 2L) return null
        offset += 32_768L
    }
    return result.toString()
}

/** Browser snapshots can atomically insert all chunks without exporting SQLite for every row. */
internal var writeCacheRowsPlatformAction: (suspend (List<Pair<String, String>>) -> Unit)? = null

// The browser installs its batch writer before init() first accesses this lazy cache.
// Native platforms retain streaming chunk writes and their bounded memory usage.
private val jsonTextCache by lazy { ChunkedTextCache(
    read = ::readLocalKvBounded,
    write = { key, value -> putLocalKv(key, value) },
    remove = ::deleteLocalKv,
    writeBatch = writeCacheRowsPlatformAction,
    removePrefixExcept = { prefix, keep ->
        // Preserve the manifest as well as the new generation. Exact prefix matching avoids SQL LIKE wildcards.
        appDatabase.app_databaseQueries.deleteKvPrefixExcept(prefix = prefix, manifestKey = prefix + "manifest", keepPrefix = keep)
    }
) }

internal suspend fun readJsonCacheText(key: String): String? = jsonTextCache.get(key)
internal suspend fun writeJsonCacheText(key: String, text: String) = jsonTextCache.put(key, text)
internal suspend fun deleteJsonCacheText(key: String) = jsonTextCache.delete(key)
