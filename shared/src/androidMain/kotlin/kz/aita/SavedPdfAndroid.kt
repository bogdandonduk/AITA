package kz.aita

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Android 10+: owned Downloads entry. Older devices/fallback: app-owned Documents, no broad storage permission. */
fun saveAitaPdfOnAndroid(context: Context, fileName: String, bytes: ByteArray): ReceiptPlatformActionResult {
    val app = context.applicationContext
    val safeName = safeReceiptPdfFileName(fileName)
    fun result(uri: Uri, name: String, folder: String): ReceiptPlatformActionResult = ReceiptPlatformActionResult(
        success = true,
        message = "Saved to $folder/$name",
        savedFile = SavedPdfFile(name, folder) { isCurrent ->
            withContext(Dispatchers.ourIo) {
                val readable = runCatching { app.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true }.getOrDefault(false)
                if (!readable) ReceiptPlatformActionResult(false, "The saved PDF was moved, deleted, or is no longer accessible")
                else withContext(Dispatchers.Main) {
                    if (!isCurrent()) ReceiptPlatformActionResult(false, "This file action belongs to an earlier sign-in")
                    else try {
                        app.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            clipData = ClipData.newRawUri("PDF", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        ReceiptPlatformActionResult(true, "Opening saved PDF")
                    } catch (_: Exception) { ReceiptPlatformActionResult(false, "No available PDF viewer could open this file") }
                }
            }
        }
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        var pending: Uri? = null
        try {
            val relativeFolder = "${Environment.DIRECTORY_DOWNLOADS}/AITA/Receipts"
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "$relativeFolder/")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create PDF")
            pending = uri
            app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write PDF")
            check(app.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) > 0)
            // Providers may disambiguate duplicate names. Show the name actually assigned, and keep its exact URI.
            val assignedName = runCatching {
                app.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: safeName
            return result(uri, assignedName, relativeFolder)
        } catch (_: Exception) {
            pending?.let { runCatching { app.contentResolver.delete(it, null, null) } }
        }
    }
    return try {
        val root = app.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: app.filesDir
        val directory = File(root, "receipts").apply { check(isDirectory || mkdirs()) }
        val file = File.createTempFile((safeName.removeSuffix(".pdf") + "_").padEnd(3, '_'), ".pdf", directory)
        try {
            file.outputStream().use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(app, app.packageName + ".fileprovider", file)
            result(uri, file.name, file.parentFile!!.absolutePath)
        } catch (failure: Exception) { file.delete(); throw failure }
    } catch (_: Exception) { ReceiptPlatformActionResult(false, "Could not save the PDF to Downloads or app Documents") }
}
