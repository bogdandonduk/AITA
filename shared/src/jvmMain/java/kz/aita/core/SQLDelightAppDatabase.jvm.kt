package kz.aita.core

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kz.aita.AppDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


actual fun getSqlDelightDriver(): SqlDriver {
  // Use ONE explicit, stable path
  val dir: Path = Paths.get(System.getProperty("user.home"), ".aita")
  Files.createDirectories(dir)
  val dbPath = dir.resolve("app.db").toAbsolutePath()

  val url = "jdbc:sqlite:$dbPath"
  val firstRun = !Files.exists(dbPath)
  println("SQLDelight (JVM): opening $url (firstRun=$firstRun)")

  val driver: SqlDriver = JdbcSqliteDriver(url)

  val schema = AppDatabase.Schema.synchronous()
  if (firstRun) {
    schema.create(driver)
  } else {
    val cursor = driver
      .executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        parameters = 0,
        mapper = { cursor: SqlCursor ->
          QueryResult.Value(
            if (cursor.next().value)
              cursor.getLong(0)?.toInt() ?: 0
            else
              0
          )
        }
      )
    val currentVersion = cursor.value
    val targetVersion = AppDatabase.Schema.version.toInt()
    if (currentVersion < targetVersion) {
      schema.migrate(driver, currentVersion.toLong(), schema.version)
    }
  }

  return driver
}

actual fun getAppDatabase(): AppDatabase {
  // Reuse the SAME driver created above
  val driver = getSqlDelightDriver()
  return AppDatabase(driver)
}