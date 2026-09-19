@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import kz.aita.updates.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.UIKit.*

internal actual fun clientUpdatePlatform(): ClientPlatform = ClientPlatform(ClientOs.IOS, ClientArch.ARM64,
    UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: 0,
    "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}", true)
internal actual fun installedClientBuild(): ClientBuildIdentity {
    // Only a new executable's embedded build identity acknowledges installation, never a store-link tap.
    return GeneratedClientBuild.identity
}
internal actual suspend fun verifyClientReleaseSignature(payload: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean = withContext(Dispatchers.Default) {
    val key = clientRsaPkcs1PublicKey(publicKey) ?: return@withContext false
    if (payload.isEmpty() || signature.isEmpty()) return@withContext false
    fun cfData(bytes: ByteArray): CFDataRef? = bytes.usePinned { CFDataCreate(null,it.addressOf(0).reinterpret(),bytes.size.toLong()) }
    val keyData = cfData(key) ?: return@withContext false
    val data = cfData(payload) ?: run { CFRelease(keyData); return@withContext false }
    val sig = cfData(signature) ?: run { CFRelease(data); CFRelease(keyData); return@withContext false }
    // Keys and values here are process-lifetime Security constants; the dictionary owns no transient objects.
    val attributes = CFDictionaryCreateMutable(null,0,null,null)
    if (attributes == null) { CFRelease(sig); CFRelease(data); CFRelease(keyData); return@withContext false }
    try {
        CFDictionarySetValue(attributes,kSecAttrKeyType,kSecAttrKeyTypeRSA)
        CFDictionarySetValue(attributes,kSecAttrKeyClass,kSecAttrKeyClassPublic)
        val secKey = SecKeyCreateWithData(keyData,attributes,null) ?: return@withContext false
        try { SecKeyVerifySignature(secKey,kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,data,sig,null) }
        finally { CFRelease(secKey) }
    } finally { CFRelease(attributes); CFRelease(sig); CFRelease(data); CFRelease(keyData) }
}
internal actual suspend fun readClientUpdatePreference(key: String): String? = NSUserDefaults.standardUserDefaults.stringForKey("aita.client-updates.$key")
internal actual suspend fun writeClientUpdatePreference(key: String,value: String?) {
    val defaults = NSUserDefaults.standardUserDefaults
    if (value == null) defaults.removeObjectForKey("aita.client-updates.$key") else defaults.setObject(value,"aita.client-updates.$key")
}
internal actual suspend fun prepareClientInstaller(release: ClientRelease,artifact: ClientArtifact,progress: (Long,Long)->Unit): PreparedClientInstaller = throw ClientUpdateFailure("unsupported")
internal actual suspend fun restoreClientInstaller(release: ClientRelease,artifact: ClientArtifact): PreparedClientInstaller? = null
internal actual suspend fun cleanCompletedClientInstallers(installed: ClientBuildIdentity) = Unit // App Store/TestFlight owns installer storage.
internal actual suspend fun handoffClientUpdate(release: ClientRelease,artifact: ClientArtifact,prepared: PreparedClientInstaller?): UpdateHandoff = withContext(Dispatchers.Main) {
    if (artifact != selectClientArtifact(release,clientUpdatePlatform()) || !clientReleaseIsNewer(release,installedClientBuild()) ||
        artifact.kind !in setOf(InstallerKind.APP_STORE,InstallerKind.TESTFLIGHT)) throw ClientUpdateFailure("integrity")
    val url = NSURL.URLWithString(artifact.url) ?: throw ClientUpdateFailure("url")
    if (!UIApplication.sharedApplication.openURL(url)) throw ClientUpdateFailure("unavailable")
    UpdateHandoff.STORE_OPENED
}

internal actual fun clientInstallerPermissionGranted() = false
internal actual suspend fun handoffClientDownload(request: ClientDownloadInstallRequest, prepared: PreparedClientInstaller): UpdateHandoff =
    throw ClientUpdateFailure("unsupported")
