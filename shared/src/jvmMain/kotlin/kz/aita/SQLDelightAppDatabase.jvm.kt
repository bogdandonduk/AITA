package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlin.io.path.Path

actual var sqlDelightDriver: SqlDriver? = Unit.run {
  val dir = Path(cacheDirPath)
  val dbPath = dir.resolve("app_database.db").toAbsolutePath()

  val url = "jdbc:sqlite:$dbPath"
  val firstRun = !Files.exists(dbPath)

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

  driver
}
