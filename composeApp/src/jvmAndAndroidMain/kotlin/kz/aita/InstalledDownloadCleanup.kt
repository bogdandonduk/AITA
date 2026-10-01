package kz.aita

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kz.aita.updates.*

@Serializable
internal data class OwnedInstallerDownload(
    val location: String, val build: Long, val channel: ReleaseChannel,
    val bytes: Long, val sha256: String, val modifiedAt: Long? = null
)

/** Exact downloads created by AITA only; never scan a user's Downloads folder. */
internal class InstalledDownloadCleanup(private val storage: ManagedClientInstaller) {
    companion object { private val registryLock = Any() }
    private val key = "owned-downloads-v1"
    private fun records(): List<OwnedInstallerDownload> = runCatching {
        storage.readPreference(key)?.let { clientReleaseJson.decodeFromString<List<OwnedInstallerDownload>>(it) }.orEmpty()
    }.getOrDefault(emptyList())
    private fun save(records: List<OwnedInstallerDownload>) = storage.writePreference(key,
        if (records.isEmpty()) null else clientReleaseJson.encodeToString(records))
    fun record(record: OwnedInstallerDownload) = synchronized(registryLock) {
        require(record.bytes > 0 && Regex("[0-9a-f]{64}").matches(record.sha256))
        save((records().filterNot { it.location == record.location } + record).takeLast(128))
    }
    fun clean(installed: ClientBuildIdentity, remove: (OwnedInstallerDownload) -> Boolean) = synchronized(registryLock) {
        save(records().filterNot { record ->
            clientUpdateInstalled(record.build, record.channel, installed) && runCatching { remove(record) }.getOrDefault(false)
        })
    }
}

internal fun OwnedInstallerDownload.matches(input: InputStream): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    var size = 0L
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        size += count
        if (size > bytes) return false
        digest.update(buffer, 0, count)
    }
    return size == bytes && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == sha256
}

internal fun removeOwnedInstallerFile(record: OwnedInstallerDownload): Boolean {
    val file = File(record.location)
    if (!file.isAbsolute || managedInstallerFileIdentity(file) != file.absoluteFile) return true // replaced/aliased: preserve it
    if (!file.exists()) return true
    if (!file.isFile || file.length() != record.bytes || record.modifiedAt != file.lastModified()) return true
    if (!file.inputStream().use { record.matches(it) }) return true // user's replacement is no longer ours
    return file.delete() // sharing violation: retain the record and retry next launch
}
