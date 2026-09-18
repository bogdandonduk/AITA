package kz.aita.updates

import io.ktor.http.Url
import kotlinx.serialization.Serializable

@Serializable data class ClientDownloadFile(
    val os: String, val arch: String = "UNIVERSAL", val kind: String,
    val url: String, val bytes: Long, val sha256: String, val publisherSigned: Boolean? = null
)
@Serializable data class ClientDownloadVersion(
    val id: String, val version: String, val build: Long,
    val notes: Map<String, String> = emptyMap(), val files: List<ClientDownloadFile> = emptyList()
)

/** Downloading an older release is explicit; it never changes updater selection/high-water rules. */
fun verifiedDownloadVersions(release: ClientRelease): List<ClientDownloadVersion>? {
    val versions = release.downloads.ifEmpty {
        listOf(ClientDownloadVersion(release.id, release.version, release.build, release.notes,
            release.artifacts.filter { it.kind.name in setOf("APK", "MSI", "EXE") }.map {
                ClientDownloadFile(it.os.name, it.arch.name, it.kind.name, it.url, it.bytes, it.sha256)
            }))
    }.filter { it.files.isNotEmpty() }
    if (versions.size > 20 || versions.distinctBy { it.build }.size != versions.size) return null
    for (version in versions) {
        if (!Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}").matches(version.id) ||
            !Regex("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,5}").matches(version.version) ||
            version.build !in 1..release.build || compareClientVersions(version.version, release.version) > 0 ||
            version.notes.size > 12 || version.notes.any { (key, value) -> !Regex("[a-z]{2,3}").matches(key) || value.length > 24_000 } ||
            version.files.size !in 1..8 || version.files.distinctBy { Triple(it.os, it.arch, it.kind) }.size != version.files.size) return null
        for (file in version.files) {
            if (!(file.os == "ANDROID" && file.kind in setOf("APK", "AAB") || file.os == "WINDOWS" && file.kind in setOf("EXE", "MSI")) ||
                file.arch !in setOf("ARM64", "X64", "UNIVERSAL") || !isPublicClientReleaseUrl(file.url) ||
                file.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES || !Regex("[0-9a-f]{64}").matches(file.sha256)) return null
            val uri = Url(file.url)
            if (!uri.encodedPath.endsWith(".${file.kind.lowercase()}") || uri.parameters.names().isNotEmpty()) return null
        }
    }
    return versions.sortedByDescending { it.build }
}

fun clientDownloadFileName(version: ClientDownloadVersion, file: ClientDownloadFile): String =
    "AITA-${version.version}-${version.build}-${file.os.lowercase()}-${file.arch.lowercase()}.${file.kind.lowercase()}"
