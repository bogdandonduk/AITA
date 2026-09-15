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
}
