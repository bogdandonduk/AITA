package kz.aita

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

actual fun getCurrentTimeMillis(): Long = System.currentTimeMillis()

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
  get() = Dispatchers.IO

actual var getHttpClientEngine: () -> HttpClientEngine = {
  OkHttp.create { preconfigured = OkHttpClient.Builder().cache(Cache(File(cacheDirPath, "http"), cacheSize)).build() }
}

actual var getSystemLocaleLanguage: () -> String = {
  "ru"
}

actual var getPlatformName: () -> String = {
  "android"
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
  null
}

object ReceiptPlatformAndroidBridge {
  /**
   * Set this from your Bluetooth receipt-printer manager.
   * It should write raw ESC/POS bytes to the already-selected printer socket/output stream.
   */
  var writeEscPosBytes: (suspend (ByteArray) -> Boolean)? = null
}

fun installReceiptPlatformAndroid(context: Context) {
  val appContext = context.applicationContext

  saveReceiptPdfFile = { fileName, pdfBytes ->
    withContext(Dispatchers.IO) {
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
          }

          val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching ReceiptPlatformActionResult(false, "Could not create PDF file")

          appContext.contentResolver.openOutputStream(uri)?.use { it.write(pdfBytes) }
            ?: return@runCatching ReceiptPlatformActionResult(false, "Could not open PDF output stream")

          ReceiptPlatformActionResult(true, "Saved to Downloads")
        } else {
          val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
          if (!dir.exists()) dir.mkdirs()
          val file = File(dir, fileName)
          file.writeBytes(pdfBytes)
          ReceiptPlatformActionResult(true, "Saved to ${file.absolutePath}")
        }
      }.getOrElse {
        ReceiptPlatformActionResult(false, it.message ?: "Could not save PDF")
      }
    }
  }

  shareReceiptPdfFile = { fileName, pdfBytes, whatsappOnly ->
    withContext(Dispatchers.IO) {
      runCatching {
        val dir = File(appContext.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeBytes(pdfBytes) }
        val uri: Uri = FileProvider.getUriForFile(
          appContext,
          appContext.packageName + ".fileprovider",
          file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
          type = "application/pdf"
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          if (whatsappOnly) setPackage("com.whatsapp")
        }

        val chooser = if (whatsappOnly) intent else Intent.createChooser(intent, "Share receipt").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        appContext.startActivity(chooser)
        ReceiptPlatformActionResult(true, if (whatsappOnly) "Opening WhatsApp" else "Opening share sheet")
      }.getOrElse {
        ReceiptPlatformActionResult(false, it.message ?: "Could not share PDF")
      }
    }
  }

  printReceiptEscPosBytes = { printerBytes ->
    runCatching {
      val writer = ReceiptPlatformAndroidBridge.writeEscPosBytes
        ?: return@runCatching ReceiptPlatformActionResult(false, "No Android ESC/POS printer writer is configured")

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
