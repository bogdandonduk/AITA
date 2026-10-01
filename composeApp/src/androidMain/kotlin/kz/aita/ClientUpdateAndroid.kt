package kz.aita

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kz.aita.android.MainActivity
import kz.aita.updates.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private fun updateActivity() = MainActivity.getOrNull() ?: throw ClientUpdateFailure("unavailable")
private fun updateContext() = kz.aita.android.AITA.get()
private fun androidUpdates() = ManagedClientInstaller(privateClientInstallerDirectory(updateContext().cacheDir)) { a,b -> android.system.Os.rename(a.path,b.path) }
internal actual fun installedClientBuild(): ClientBuildIdentity = runCatching {
    @Suppress("DEPRECATION") val info = updateContext().packageManager.getPackageInfo(updateContext().packageName, 0)
    GeneratedClientBuild.identity.copy(version = info.versionName ?: GeneratedClientBuild.identity.version,
        build = if(Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong())
}.getOrDefault(GeneratedClientBuild.identity)
internal actual fun clientUpdatePlatform(): ClientPlatform {
    val activity = updateContext()
    val store = GeneratedClientBuild.identity.distribution == "store" || runCatching {
        @Suppress("DEPRECATION") val installer = if (Build.VERSION.SDK_INT >= 30) activity?.packageManager?.getInstallSourceInfo(activity.packageName)?.installingPackageName
            else activity?.packageManager?.getInstallerPackageName(activity.packageName)
        installer == "com.android.vending"
    }.getOrDefault(false)
    val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
    return ClientPlatform(ClientOs.ANDROID, if(abi == "arm64-v8a") ClientArch.ARM64 else if(abi.startsWith("x86_64")) ClientArch.X64 else ClientArch.UNIVERSAL,
        Build.VERSION.SDK_INT, "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}", store)
}
internal actual suspend fun verifyClientReleaseSignature(payload: ByteArray, signature: ByteArray, publicKey: ByteArray) =
    withContext(Dispatchers.IO) { verifyRsaClientRelease(payload,signature,publicKey) }
internal actual suspend fun readClientUpdatePreference(key: String): String? = withContext(Dispatchers.IO) {
    updateContext().getSharedPreferences("aita-client-updates",0).getString(key,null)
}
internal actual suspend fun writeClientUpdatePreference(key: String,value: String?) = withContext(Dispatchers.IO) {
    val editor = updateContext().getSharedPreferences("aita-client-updates",0).edit()
    if (value == null) editor.remove(key) else editor.putString(key,value)
    if (!editor.commit()) throw ClientUpdateFailure("storage")
}
internal actual suspend fun prepareClientInstaller(release: ClientRelease, artifact: ClientArtifact, progress: (Long,Long)->Unit) = withContext(Dispatchers.IO) { androidUpdates().prepare(release,artifact,progress) }
internal actual suspend fun restoreClientInstaller(release: ClientRelease, artifact: ClientArtifact) = withContext(Dispatchers.IO) { androidUpdates().restore(release,artifact) }
internal actual suspend fun cleanCompletedClientInstallers(installed: ClientBuildIdentity) = withContext(Dispatchers.IO) { androidUpdates().clean(installed); androidDownloadInstallers().clean(installed); cleanAndroidDownloadCopies(installed) }
internal actual suspend fun handoffClientUpdate(release: ClientRelease, artifact: ClientArtifact, prepared: PreparedClientInstaller?): UpdateHandoff {
    if (artifact != selectClientArtifact(release,clientUpdatePlatform()) || !clientReleaseIsNewer(release,installedClientBuild())) throw ClientUpdateFailure("integrity")
    if (artifact.kind == InstallerKind.PLAY_STORE) return withContext(Dispatchers.Main) {
        updateActivity().startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(artifact.url))); UpdateHandoff.STORE_OPENED
    }
    return handoffAndroidInstaller(release, artifact, prepared, androidUpdates())
}
internal fun androidDownloadInstallers() = ManagedClientInstaller(java.io.File(privateClientInstallerDirectory(updateContext().cacheDir), "downloads")) { a, b -> android.system.Os.rename(a.path, b.path) }
internal actual fun clientInstallerPermissionGranted(): Boolean = Build.VERSION.SDK_INT < 26 || updateContext().packageManager.canRequestPackageInstalls()
internal actual suspend fun handoffClientDownload(request: ClientDownloadInstallRequest, prepared: PreparedClientInstaller): UpdateHandoff {
    val release = request.release(System.currentTimeMillis(), clientUpdatePlatform(), installedClientBuild())
    return handoffAndroidInstaller(release, release.artifacts.single(), prepared, androidDownloadInstallers())
}
private suspend fun handoffAndroidInstaller(release: ClientRelease, artifact: ClientArtifact,
    prepared: PreparedClientInstaller?, storage: ManagedClientInstaller): UpdateHandoff {
    if (clientUpdatePlatform().storeManaged || artifact.kind != InstallerKind.APK || prepared == null || prepared.build != release.build || prepared.releaseId != release.id || prepared.channel != release.channel) throw ClientUpdateFailure("integrity")
    val file = withContext(Dispatchers.IO) {
        val f = storage.verifiedFile(prepared,artifact)
        val pm = updateContext().packageManager
        @Suppress("DEPRECATION") val flags = if(Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        @Suppress("DEPRECATION") val target = pm.getPackageArchiveInfo(f.path,flags) ?: throw ClientUpdateFailure("package")
        @Suppress("DEPRECATION") val current = pm.getPackageInfo(updateContext().packageName,flags)
        @Suppress("DEPRECATION") val targetBuild = if(Build.VERSION.SDK_INT >= 28) target.longVersionCode else target.versionCode.toLong()
        if(target.packageName != updateContext().packageName || targetBuild != release.build || target.versionName != release.version) throw ClientUpdateFailure("package")
        fun fingerprint(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        @Suppress("DEPRECATION") val oldSigners = (if(Build.VERSION.SDK_INT >= 28) current.signingInfo?.apkContentsSigners else current.signatures).orEmpty().map { fingerprint(it.toByteArray()) }.toSet()
        @Suppress("DEPRECATION") val newHistory = (if(Build.VERSION.SDK_INT >= 28) target.signingInfo?.let { if(it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory } else target.signatures).orEmpty().map { fingerprint(it.toByteArray()) }.toSet()
        if(oldSigners.isEmpty() || !newHistory.containsAll(oldSigners)) throw ClientUpdateFailure("signing")
        f
    }
    return withContext(Dispatchers.Main) {
        val activity = updateActivity()
        if(Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${activity.packageName}")))
            UpdateHandoff.PERMISSION_REQUIRED
        } else {
            val uri = FileProvider.getUriForFile(activity,"${activity.packageName}.updatefiles",file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            UpdateHandoff.INSTALLER_OPENED
        }
    }
}
