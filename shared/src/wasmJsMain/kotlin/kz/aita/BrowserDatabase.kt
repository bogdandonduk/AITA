package kz.aita

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import kotlinx.coroutines.withTimeout
import org.w3c.dom.Worker

/** Open local browser storage before any shared service or Compose state reads the database. */
suspend fun initializeBrowserDatabase() {
    val driver = WebWorkerDriver(Worker("/aita-db-worker.js"))
    try {
        withTimeout(30_000) {
            val version = driver.executeQuery(null, "PRAGMA user_version", { cursor ->
                QueryResult.AsyncValue { if (cursor.next().await()) cursor.getLong(0) ?: 0L else 0L }
            }, 0).await()
            require(version <= AppDatabase.Schema.version) { "A newer AITA version is required to open this browser's data" }
            if (version < AppDatabase.Schema.version) {
                AppDatabase(driver).transaction {
                    if (version == 0L) AppDatabase.Schema.create(driver).await()
                    else AppDatabase.Schema.migrate(driver, version, AppDatabase.Schema.version).await()
                    driver.execute(null, "PRAGMA user_version = ${AppDatabase.Schema.version}", 0).await()
                }
            }
        }
        // One SQL statement is atomic, and the worker acknowledges it only after IndexedDB
        // commits. No transaction may stay open across unrelated coroutine requests.
        writeCacheRowsPlatformAction = { rows ->
            require(rows.isNotEmpty() && rows.size <= 4096)
            val sql = "INSERT OR REPLACE INTO key_value(key, value) VALUES " + rows.joinToString(",") { "(?, ?)" }
            driver.execute(null, sql, rows.size * 2) {
                rows.forEachIndexed { index, (key, value) ->
                    bindString(index * 2, key)
                    bindString(index * 2 + 1, value)
                }
            }.await()
        }
        getSqlDelightDriver = { driver }
    } catch (failure: Throwable) {
        driver.close()
        throw failure
    }
}
