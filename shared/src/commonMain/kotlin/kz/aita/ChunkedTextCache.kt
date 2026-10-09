package kz.aita

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Snapshot files expressed as small KV rows. The manifest is the sole commit point. */
internal class ChunkedTextCache(
    private val read: suspend (String, Int) -> String?,
    private val write: suspend (String, String) -> Unit,
    private val remove: suspend (String) -> Unit,
    private val removePrefixExcept: suspend (prefix: String, keepPrefix: String) -> Unit,
    private val newGeneration: () -> String = { Random.nextLong().toULong().toString(16) + Random.nextLong().toULong().toString(16) },
    private val chunkSize: Int = 64 * 1024,
    private val maxLength: Int = 128 * 1024 * 1024,
    private val writeBatch: (suspend (List<Pair<String, String>>) -> Unit)? = null
) {
    private val registryMutex = Mutex()
    private val keyMutexes = mutableMapOf<String, Mutex>()
    private suspend fun mutexFor(key: String): Mutex = registryMutex.withLock { keyMutexes.getOrPut(key) { Mutex() } }
    init { require(chunkSize in 2..(128 * 1024) && maxLength >= chunkSize) }
    private fun prefix(key: String) = "aita-cache-chunks-v1:${key.length}:$key:"
    private fun manifest(key: String) = prefix(key) + "manifest"

    suspend fun get(key: String): String? = mutexFor(key).withLock {
        val header = read(manifest(key), 256)
        if (header == null) return@withLock read(key, maxLength) // bounded compatibility read for old one-row JSON
        val parts = header.split('|')
        if (parts.size != 5 || parts[0] != "1" || parts[1].isEmpty() || parts[1].length > 64 ||
            parts[1].any { it !in 'a'..'f' && it !in '0'..'9' }) return@withLock null
        val length = parts[2].toIntOrNull()?.takeIf { it in 0..maxLength } ?: return@withLock null
        val count = parts[3].toIntOrNull()?.takeIf { it in 1..(maxLength / 2 + 1) } ?: return@withLock null
        // Prevent corrupt metadata from asking for millions of disk reads or oversized allocation.
        if (count > (length / (chunkSize - 1) + 2) || (length == 0 && count != 1)) return@withLock null
        val text = StringBuilder(length)
        repeat(count) { index ->
            val chunk = read(prefix(key) + parts[1] + ":$index", chunkSize * 2) ?: return@withLock null
            if (chunk.length > chunkSize || (index < count - 1 && chunk.length < chunkSize - 1)) return@withLock null
            text.append(chunk)
            if (text.length > length) return@withLock null
        }
        text.toString().takeIf { it.length == length && cacheTextChecksum(it) == parts[4] }
    }

    suspend fun put(key: String, text: String) = mutexFor(key).withLock {
        require(text.length <= maxLength) { "Cache snapshot is too large" }
        val generation = newGeneration()
        require(generation.isNotEmpty() && generation.length <= 64 && generation.all { it in 'a'..'f' || it in '0'..'9' })
        val generationPrefix = prefix(key) + generation + ":"
        val rows = if (writeBatch != null) mutableListOf<Pair<String, String>>() else null
        var offset = 0
        var index = 0
        do {
            var end = (offset + chunkSize).coerceAtMost(text.length)
            // A SQLite TEXT value must not end with half a surrogate pair.
            if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            val row = generationPrefix + index to text.substring(offset, end)
            if (rows != null) rows += row else write(row.first, row.second)
            offset = end
            index++
        } while (offset < text.length)
        val header = manifest(key) to "1|$generation|${text.length}|$index|${cacheTextChecksum(text)}"
        if (rows != null) {
            rows += header
            writeBatch!!.invoke(rows)
        } else write(header.first, header.second)
        // Interrupted cleanup is harmless: only the committed generation is ever read.
        try {
            remove(key)
            removePrefixExcept(prefix(key), generationPrefix)
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { /* Manifest already committed. Cleanup failure cannot turn a saved operation into a false failure. */ }
    }

    suspend fun delete(key: String) = mutexFor(key).withLock {
        remove(key)
        remove(manifest(key))
        removePrefixExcept(prefix(key), "")
    }
}

/** A corruption check, not an authentication primitive. */
internal fun cacheTextChecksum(value: String): String {
    var hash = 0x811c9dc5u
    for (char in value) { hash = (hash xor char.code.toUInt()) * 0x01000193u }
    return hash.toString(16)
}
