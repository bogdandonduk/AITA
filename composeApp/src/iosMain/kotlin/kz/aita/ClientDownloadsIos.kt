package kz.aita

import androidx.compose.runtime.Composable
import kz.aita.updates.ClientDownloadFile

internal actual fun clientDownloadsCanChooseFolder() = false
internal actual suspend fun clientDownloadsFolderLabel(folder: String?): String? = null
@Composable internal actual fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit = {}
internal actual suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?, progress: (Long, Long) -> Unit): ClientDownloadResult =
    throw ClientUpdateFailure("unsupported")
