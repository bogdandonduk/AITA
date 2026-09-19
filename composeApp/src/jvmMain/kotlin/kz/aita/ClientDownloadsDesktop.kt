package kz.aita

import androidx.compose.runtime.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.aita.updates.ClientDownloadFile

internal actual fun clientDownloadsCanChooseFolder() = true
private fun downloadsDirectory(folder: String?): File {
    val directory = folder?.let(::File) ?: File(System.getProperty("user.home"), "Downloads/AITA/Releases")
    if (!directory.isDirectory && !directory.mkdirs()) throw ClientUpdateFailure("storage")
    return directory.canonicalFile
}
internal actual suspend fun clientDownloadsFolderLabel(folder: String?): String? = withContext(Dispatchers.IO) { downloadsDirectory(folder).path }
@Composable internal actual fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit {
    val result by rememberUpdatedState(onChosen)
    var showing by remember { mutableStateOf(false) }
    return {
        if (!showing) {
            showing = true
            SwingUtilities.invokeLater {
                try {
                    val picker = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; isMultiSelectionEnabled = false }
                    if (picker.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) result(picker.selectedFile.canonicalPath)
                } catch (_: Exception) { DownloadsWorkspace.reportProblem("unavailable") }
                finally { showing = false }
            }
        }
    }
}
internal actual suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?, installRequest: ClientDownloadInstallRequest?, progress: (Long, Long) -> Unit): ClientDownloadResult = withContext(Dispatchers.IO) {
    require(Regex("[A-Za-z0-9._-]{1,160}").matches(fileName))
    val directory = downloadsDirectory(folder)
    val temporary = fetchVerifiedClientDownload(directory, file, progress)
    try {
        val prepared = installRequest?.let {
            val release = it.release(System.currentTimeMillis(), clientUpdatePlatform(), installedClientBuild())
            desktopDownloadInstallers().importVerified(release, release.artifacts.single(), temporary)
        }
        // Never overwrite a user's existing file, even if it has our suggested name.
        var attempt = 0
        while (true) {
            val name = if (attempt == 0) fileName else fileName.substringBeforeLast('.') + "-$attempt." + fileName.substringAfterLast('.')
            val destination = File(directory, name)
            try {
                Files.move(temporary.toPath(), destination.toPath())
                return@withContext ClientDownloadResult(destination.canonicalPath, prepared = prepared)
            } catch (_: java.nio.file.FileAlreadyExistsException) { attempt++; if (attempt > 9999) throw ClientUpdateFailure("storage") }
        }
        @Suppress("UNREACHABLE_CODE") ClientDownloadResult()
    } finally { temporary.delete() }
}
