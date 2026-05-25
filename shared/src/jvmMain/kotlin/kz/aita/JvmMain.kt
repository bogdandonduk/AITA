package kz.aita

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.io.path.Path

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()
actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
  get() = Dispatchers.IO

actual var getHttpClientEngine: () -> HttpClientEngine = {
  OkHttp.create {
    preconfigured = OkHttpClient.Builder()
      .cache(
        Cache(
          File(cacheDirPath, "http"),
          cacheSize
        )
      ).build()
  }
}

actual var getSystemLocaleLanguage: () -> String = {
  Locale.getDefault()?.language ?: "ru"
}

actual var getPlatformName: () -> String = {
  "jvm"
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
  Unit.run {
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
}

object ReceiptPlatformJvmBridge {
  /**
   * Optional desktop ESC/POS writer. Configure it for a USB serial, COM port, or network printer.
   */
  var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null
}

fun installReceiptPlatformJvm() {
  saveReceiptPdfFile = { fileName, pdfBytes ->
    withContext(Dispatchers.IO) {
      runCatching {
        val downloads = File(System.getProperty("user.home"), "Downloads").takeIf { it.exists() }
          ?: File(System.getProperty("user.home"))
        val file = File(downloads, fileName)
        file.writeBytes(pdfBytes)
        ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
      }.getOrElse {
        ReceiptPlatformActionResult(false, it.message ?: "Could not save PDF")
      }
    }
  }

  shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(System.getProperty("java.io.tmpdir"), fileName)
        file.writeBytes(pdfBytes)
        if (Desktop.isDesktopSupported()) {
          Desktop.getDesktop().open(file)
          ReceiptPlatformActionResult(true, if (whatsappOnly) "Opened PDF; send it through WhatsApp Desktop manually" else "Opened PDF")
        } else {
          ReceiptPlatformActionResult(true, "PDF created at ${file.absolutePath}")
        }
      }.getOrElse {
        ReceiptPlatformActionResult(false, it.message ?: "Could not share PDF")
      }
    }
  }

  printReceiptEscPosBytes = { printerBytes ->
    runCatching {
      val writer = ReceiptPlatformJvmBridge.writeEscPosBytes
        ?: return@runCatching ReceiptPlatformActionResult(false, "No desktop ESC/POS printer writer is configured")

      if (writer(printerBytes)) {
        ReceiptPlatformActionResult(true, "Sent to printer")
      } else {
        ReceiptPlatformActionResult(false, "Printer rejected the receipt")
      }
    }.getOrElse {
      ReceiptPlatformActionResult(false, it.message ?: "Could not print receipt")
    }
  }
}
