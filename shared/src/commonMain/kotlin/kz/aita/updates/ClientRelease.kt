package kz.aita.updates

import io.ktor.http.URLProtocol
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val CLIENT_RELEASE_MAX_BYTES = 262_144
const val CLIENT_INSTALLER_MAX_BYTES = 2_147_483_648L
val clientReleaseJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
enum class ReleaseChannel { RELEASE, TEST }
@Serializable
enum class ClientOs { ANDROID, WINDOWS, MACOS, LINUX, IOS, WEB }
@Serializable
enum class ClientArch { ARM64, X64, UNIVERSAL }
@Serializable
enum class InstallerKind { APK, MSI, EXE, PKG, DMG, DEB, RPM, PLAY_STORE, APP_STORE, TESTFLIGHT, WEB_RELOAD }

@Serializable
data class ClientArtifact(
    val os: ClientOs,
    val arch: ClientArch = ClientArch.UNIVERSAL,
    val kind: InstallerKind,
    val url: String,
    val bytes: Long = 0L,
    val sha256: String = "",
    val minimumOsMajor: Int = 0
) {
    val isFile: Boolean get() = kind in setOf(InstallerKind.APK, InstallerKind.MSI, InstallerKind.EXE,
        InstallerKind.PKG, InstallerKind.DMG, InstallerKind.DEB, InstallerKind.RPM)
    val extension: String get() = kind.name.lowercase()
}

@Serializable
data class ClientRelease(
    val schema: Int = 1,
    val channel: ReleaseChannel,
    val sequence: Long,
    val id: String,
    val version: String,
    val build: Long,
    val publishedAtMillis: Long,
    val expiresAtMillis: Long,
    val notes: Map<String, String> = emptyMap(),
    val artifacts: List<ClientArtifact> = emptyList()
) {
    val identity: String get() = "${channel.name}:$build:$id"
    fun notesFor(language: String): String = notes[language.substringBefore('-').substringBefore('_')]
        ?: notes["en"] ?: notes["ru"] ?: notes.values.firstOrNull().orEmpty()
}

/** Signature is RSA/PKCS#1 v1.5 + SHA-256 over the exact base64-decoded payload bytes. */
@Serializable
data class SignedClientRelease(val payload: String, val signature: String, val algorithm: String = "RS256")

@Serializable
data class ClientBuildIdentity(
    val version: String,
    val build: Long,
    val channel: ReleaseChannel,
    val revision: String,
    val builtAt: String,
    val distribution: String,
    val kotlinVersion: String,
    val composeVersion: String
)

data class ClientPlatform(val os: ClientOs, val arch: ClientArch, val osMajor: Int = 0,
    val description: String = "", val storeManaged: Boolean = false,
    val linuxPackage: InstallerKind? = null)

/** Never use fuzzy OS/architecture matching, filename guessing, or a server-supplied shell command. */
fun selectClientArtifact(release: ClientRelease, platform: ClientPlatform): ClientArtifact? {
    val eligible = release.artifacts.filter { artifact ->
        artifact.os == platform.os && (artifact.arch == platform.arch || artifact.arch == ClientArch.UNIVERSAL) &&
            artifact.minimumOsMajor <= platform.osMajor && when {
                platform.os == ClientOs.ANDROID && platform.storeManaged -> artifact.kind == InstallerKind.PLAY_STORE
                platform.os == ClientOs.ANDROID -> artifact.kind == InstallerKind.APK
                platform.os == ClientOs.IOS && release.channel == ReleaseChannel.TEST -> artifact.kind == InstallerKind.TESTFLIGHT
                platform.os == ClientOs.IOS -> artifact.kind == InstallerKind.APP_STORE
                platform.os == ClientOs.MACOS && platform.storeManaged -> artifact.kind == InstallerKind.APP_STORE
                platform.os == ClientOs.WEB -> artifact.kind == InstallerKind.WEB_RELOAD
                platform.os == ClientOs.LINUX -> artifact.kind == platform.linuxPackage
                else -> artifact.isFile
            }
    }
    return eligible.sortedWith(compareBy<ClientArtifact> { if (it.arch == platform.arch) 0 else 1 }
        .thenBy { when (it.kind) { InstallerKind.PKG, InstallerKind.MSI, InstallerKind.DEB -> 0; else -> 1 } }).firstOrNull()
}

fun clientReleaseIsNewer(release: ClientRelease, installed: ClientBuildIdentity): Boolean =
    release.channel == installed.channel && release.build > installed.build &&
        compareClientVersions(release.version, installed.version) >= 0

fun compareClientVersions(first: String, second: String): Int {
    val a = first.split('.').mapNotNull(String::toIntOrNull)
    val b = second.split('.').mapNotNull(String::toIntOrNull)
    if (a.size != 3 || b.size != 3) return -1
    for (i in 0..2) if (a[i] != b[i]) return a[i].compareTo(b[i])
    return 0
}

fun isPublicClientReleaseUrl(raw: String): Boolean = runCatching {
    if (raw.length !in 10..2048 || raw != raw.trim() || raw.any { it.isWhitespace() || it.code < 32 || it.code == 127 } || '\\' in raw) return false
    val url = Url(raw)
    val host = url.host.lowercase().trimEnd('.')
    url.protocol == URLProtocol.HTTPS && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() &&
        url.fragment.isEmpty() && url.port == 443 && host.contains('.') && host != "localhost" &&
        !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal") &&
        !host.contains(':') && host.any(Char::isLetter) && !host.startsWith("0x")
}.getOrDefault(false)

fun clientReleaseProblem(release: ClientRelease, now: Long, expectedChannel: ReleaseChannel): String? {
    if (release.schema != 1 || release.channel != expectedChannel) return "channel"
    if (release.sequence !in 1..9_007_199_254_740_991L || release.build !in 1..2_100_000_000L) return "version"
    if (!Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,79}").matches(release.id) ||
        !Regex("[0-9]{1,5}\\.[0-9]{1,5}\\.[0-9]{1,5}").matches(release.version)) return "version"
    if (release.publishedAtMillis <= 0 || release.publishedAtMillis > now + 600_000L ||
        release.expiresAtMillis <= now || release.expiresAtMillis <= release.publishedAtMillis ||
        release.expiresAtMillis - release.publishedAtMillis > 366L * 86_400_000L) return "expired"
    if (release.notes.size > 12 || release.notes.any { (k, v) -> !Regex("[a-z]{2,3}").matches(k) || v.length > 24_000 }) return "notes"
    if (release.artifacts.size !in 1..32 || release.artifacts.distinctBy { Triple(it.os, it.arch, it.kind) }.size != release.artifacts.size) return "artifacts"
    for (a in release.artifacts) {
        if (!isPublicClientReleaseUrl(a.url) || a.minimumOsMajor !in 0..10000) return "url"
        val uri = Url(a.url)
        val validKind = when(a.os) {
            ClientOs.ANDROID -> a.kind in setOf(InstallerKind.APK, InstallerKind.PLAY_STORE)
            ClientOs.WINDOWS -> a.kind in setOf(InstallerKind.MSI, InstallerKind.EXE)
            ClientOs.MACOS -> a.kind in setOf(InstallerKind.PKG, InstallerKind.DMG, InstallerKind.APP_STORE)
            ClientOs.LINUX -> a.kind in setOf(InstallerKind.DEB, InstallerKind.RPM)
            ClientOs.IOS -> a.kind in setOf(InstallerKind.APP_STORE, InstallerKind.TESTFLIGHT)
            ClientOs.WEB -> a.kind == InstallerKind.WEB_RELOAD
        }
        if (!validKind) return "platform"
        if (a.isFile && (a.bytes !in 1..CLIENT_INSTALLER_MAX_BYTES || !Regex("[0-9a-f]{64}").matches(a.sha256) ||
                !uri.encodedPath.endsWith(".${a.extension}", ignoreCase = true) || uri.parameters.names().isNotEmpty())) return "artifact"
        if (!a.isFile && (a.bytes != 0L || a.sha256.isNotEmpty())) return "artifact"
        if (a.kind == InstallerKind.PLAY_STORE && (uri.host != "play.google.com" || uri.encodedPath != "/store/apps/details" || uri.parameters["id"] != "kz.aita")) return "store"
        if (a.kind == InstallerKind.APP_STORE && (uri.host != "apps.apple.com" || !uri.encodedPath.contains(Regex("/id[0-9]+")))) return "store"
        if (a.kind == InstallerKind.TESTFLIGHT && (uri.host != "testflight.apple.com" || !Regex("/join/[A-Za-z0-9]+").matches(uri.encodedPath))) return "store"
    }
    return null
}

/** A newer installed build—not opening an installer—is the completion acknowledgement. */
fun clientUpdateInstalled(targetBuild: Long, targetChannel: ReleaseChannel, installed: ClientBuildIdentity): Boolean =
    targetChannel == installed.channel && targetBuild > 0 && installed.build >= targetBuild
