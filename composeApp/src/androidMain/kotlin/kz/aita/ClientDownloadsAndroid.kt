package kz.aita

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.aita.android.MainActivity
import kz.aita.updates.ClientDownloadFile

private fun downloadContext() = MainActivity.getOrNull()?.applicationContext ?: throw ClientUpdateFailure("unavailable")
internal actual fun clientDownloadsCanChooseFolder() = true
private fun treeDirectory(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
private fun documentName(uri: Uri): String? = downloadContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
    if (it.moveToFirst()) it.getString(0) else null
}
internal actual suspend fun clientDownloadsFolderLabel(folder: String?): String? = withContext(Dispatchers.IO) {
    if (folder != null) {
        val uri = Uri.parse(folder)
        if (!DocumentsContract.isTreeUri(uri)) throw ClientUpdateFailure("storage")
        documentName(treeDirectory(uri)) ?: throw ClientUpdateFailure("storage")
    } else if (Build.VERSION.SDK_INT >= 29) "Download/AITA/Releases"
    else File(downloadContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: downloadContext().filesDir, "AITA/Releases").path
}
@Composable internal actual fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val result by rememberUpdatedState(onChosen)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                result(uri.toString())
            } catch (_: Exception) { DownloadsWorkspace.reportProblem("storage") }
        }
    }
    return { try { picker.launch(null) } catch (_: Exception) { DownloadsWorkspace.reportProblem("unavailable") } }
}
internal actual suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?, progress: (Long, Long) -> Unit): ClientDownloadResult = withContext(Dispatchers.IO) {
    require(Regex("[A-Za-z0-9._-]{1,160}").matches(fileName))
    val context = downloadContext()
    val temporary = fetchVerifiedClientDownload(File(context.cacheDir, "release-downloads"), file, progress)
    try {
        val resolver = context.contentResolver
        if (folder != null || Build.VERSION.SDK_INT >= 29) {
            val uri = if (folder != null) DocumentsContract.createDocument(resolver, treeDirectory(Uri.parse(folder)), "application/octet-stream", fileName)
            else resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AITA/Releases")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            })
            if (uri == null) throw ClientUpdateFailure("storage")
            try {
                resolver.openOutputStream(uri, "w")?.use { output -> temporary.inputStream().use { it.copyTo(output) } }
                    ?: throw ClientUpdateFailure("storage")
                if (folder == null && Build.VERSION.SDK_INT >= 29) {
                    if (resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) != 1)
                        throw ClientUpdateFailure("storage")
                }
                val name = documentName(uri) ?: fileName
                ClientDownloadResult("${clientDownloadsFolderLabel(folder)}/$name")
            } catch (failure: Exception) {
                runCatching { if (folder != null) DocumentsContract.deleteDocument(resolver, uri) else resolver.delete(uri, null, null) }
                throw failure
            }
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "AITA/Releases")
            if (!directory.isDirectory && !directory.mkdirs()) throw ClientUpdateFailure("storage")
            // API 24–28 can save without a broad storage permission in their app-specific folder.
            var attempt = 0
            var destination: File
            do {
                val name = if (attempt == 0) fileName else fileName.substringBeforeLast('.') + "-$attempt." + fileName.substringAfterLast('.')
                destination = File(directory, name); attempt++
                if (attempt > 10000) throw ClientUpdateFailure("storage")
            } while (!destination.createNewFile())
            try {
                FileOutputStream(destination).use { output -> temporary.inputStream().use { it.copyTo(output) }; output.fd.sync() }
                ClientDownloadResult(destination.canonicalPath)
            } catch (failure: Exception) { destination.delete(); throw failure }
        }
    } finally { temporary.delete() }
}
