package kz.aita

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Uses the REAL generated query signatures. It cannot pass by substituting a hand-written DAO. */
class JsonCacheQueryBindingTest {
    private fun testDb(work: suspend (AppDatabase) -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try { AppDatabase.Schema.create(driver).await(); work(AppDatabase(driver)) }
        finally { driver.close() }
    }
    @Test fun namedSliceBindsNumericOffsetsAndStringKey() = testDb { database ->
        val queries = database.app_databaseQueries
        queries.insertKv("cache-key", "abcd")
        assertEquals("bc", queries.selectKvSlice(sliceOffset = 2L, sliceLength = 2L, cacheKey = "cache-key").awaitAsOneOrNull()?.text_slice)
    }
    @Test fun unicodeOffsetsCountSqlCharactersNotUtf16Units() = testDb { database ->
        val queries = database.app_databaseQueries
        queries.insertKv("unicode", "А😀Б🌿В")
        assertEquals("😀Б🌿", queries.selectKvSlice(sliceOffset = 2L, sliceLength = 3L, cacheKey = "unicode").awaitAsOneOrNull()?.text_slice)
    }
    @Test fun missingKeyDoesNotLookLikeAnEmptySavedCatalogue() = testDb { database ->
        val queries = database.app_databaseQueries
        assertNull(queries.selectKvSlice(sliceOffset = 1L, sliceLength = 32_768L, cacheKey = "missing").awaitAsOneOrNull())
        queries.insertKv("empty", "")
        assertEquals("", queries.selectKvSlice(sliceOffset = 1L, sliceLength = 32_768L, cacheKey = "empty").awaitAsOneOrNull()?.text_slice)
    }
    @Test fun repeatedSlicesReconstructAnEntireLegacyUnicodeCache() = testDb { database ->
        val queries = database.app_databaseQueries
        val expected = "А😀Б🌿В".repeat(8_000)
        queries.insertKv("large-legacy", expected)
        val length = queries.selectKvLength("large-legacy").awaitAsOneOrNull()!!.text_length!!
        val restored = buildString {
            var offset = 1L
            while (offset <= length) {
                append(queries.selectKvSlice(sliceOffset = offset, sliceLength = 32_768L,
                    cacheKey = "large-legacy").awaitAsOneOrNull()!!.text_slice!!)
                offset += 32_768L
            }
        }
        assertEquals(expected, restored)
    }
    @Test fun clearingGenerationsKeepsOnlyThisOwnerManifest() = testDb { database ->
        val queries = database.app_databaseQueries
        queries.insertKv("owner:manifest", "committed")
        queries.insertKv("owner:old:0", "old")
        queries.insertKv("owner:new:0", "new")
        queries.insertKv("another:old:0", "private")
        queries.deleteKvPrefixExcept(prefix = "owner:", manifestKey = "owner:manifest", keepPrefix = "")
        assertEquals("committed", queries.selectKvByKey("owner:manifest").awaitAsOneOrNull()!!.value_)
        assertNull(queries.selectKvByKey("owner:old:0").awaitAsOneOrNull())
        assertNull(queries.selectKvByKey("owner:new:0").awaitAsOneOrNull())
        assertEquals("private", queries.selectKvByKey("another:old:0").awaitAsOneOrNull()!!.value_)
    }
    @Test fun nullableStoredValueIsDifferentFromAnAbsentRow() = testDb { database ->
        val queries = database.app_databaseQueries
        queries.insertKv("null-value", null)
        val storedRow = assertNotNull(queries.selectKvByKey("null-value").awaitAsOneOrNull())
        assertNull(storedRow.value_)
        assertNull(queries.selectKvByKey("absent").awaitAsOneOrNull())
    }
    @Test fun cleanupUsesLiteralPrefixesAndKeepsCommittedManifest() = testDb { database ->
        val queries = database.app_databaseQueries
        val prefix = "cache%_x:"
        listOf(prefix + "manifest", prefix + "new:1", prefix + "old:1", "cacheABCx:other").forEach { queries.insertKv(it, "value") }
        queries.deleteKvPrefixExcept(prefix = prefix, manifestKey = prefix + "manifest", keepPrefix = prefix + "new:")
        assertNotNull(queries.selectKvByKey(prefix + "manifest").awaitAsOneOrNull())
        assertNotNull(queries.selectKvByKey(prefix + "new:1").awaitAsOneOrNull())
        assertNotNull(queries.selectKvByKey("cacheABCx:other").awaitAsOneOrNull())
        assertNull(queries.selectKvByKey(prefix + "old:1").awaitAsOneOrNull())
    }
    @Test fun prefixCleanupOnlyMatchesTheBeginningAndPreservesUnicodeOwner() = testDb { database ->
        val queries = database.app_databaseQueries
        val prefix = "😀%_[owner]:"
        val retained = listOf(prefix + "manifest", prefix + "new:0", "other:" + prefix + "old:0")
        (retained + (prefix + "old:0")).forEach { queries.insertKv(it, "value") }
        queries.deleteKvPrefixExcept(prefix = prefix, manifestKey = prefix + "manifest", keepPrefix = prefix + "new:")
        retained.forEach { assertNotNull(queries.selectKvByKey(it).awaitAsOneOrNull(), it) }
        assertNull(queries.selectKvByKey(prefix + "old:0").awaitAsOneOrNull())
    }
    @Test fun boundedSlicesDoNotReturnTheWholeValueAtZeroLengthOrPastEnd() = testDb { database ->
        val queries = database.app_databaseQueries
        queries.insertKv("bounded", "А😀Б")
        assertEquals("", queries.selectKvSlice(sliceOffset = 1L, sliceLength = 0L, cacheKey = "bounded").awaitAsOneOrNull()?.text_slice)
        assertEquals("", queries.selectKvSlice(sliceOffset = 4L, sliceLength = 32_768L, cacheKey = "bounded").awaitAsOneOrNull()?.text_slice)
    }
}
