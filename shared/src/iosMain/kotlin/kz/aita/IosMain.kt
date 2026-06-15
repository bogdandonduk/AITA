// THIS IS IosMain.kt - place in shared/src/iosMain/kotlin/kz/aita/IosMain.kt
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.darwin.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

actual fun getCurrentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private const val secureStoragePrefix = "kz.aita.secure"

private fun storageKey(account: String): String = "$secureStoragePrefix.$account"

private fun iosStoredString(account: String): String? =
    NSUserDefaults.standardUserDefaults.stringForKey(storageKey(account))

private fun iosSetStoredString(account: String, value: String?) {
    val defaults = NSUserDefaults.standardUserDefaults
    val key = storageKey(account)
    if (value == null) {
        defaults.removeObjectForKey(key)
    } else {
        defaults.setObject(value, forKey = key)
    }
    defaults.synchronize()
}

private fun iosCacheDirectoryPath(): String {
    val aitaPath = NSTemporaryDirectory().trimEnd('/') + "/AITA"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = aitaPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    return aitaPath
}

private fun iosInstallationId(): String {
    iosStoredString("installation_id")?.takeIf { it.isNotBlank() }?.let { return it }
    val fresh = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: NSUUID().UUIDString
    iosSetStoredString("installation_id", fresh)
    return fresh
}

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = {
    runCatching {
        iosStoredString("auth_tokens")?.let { jsonBase.decodeFromString<TokenPair>(it) }
    }.getOrNull()
}

actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = { tokens ->
    runCatching {
        iosSetStoredString("auth_tokens", tokens?.let { jsonBase.encodeToString(it) })
    }
}

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = {
    runCatching {
        iosStoredString("user_account")?.let { jsonBase.decodeFromString<UserAccountDataModel>(it) }
    }.getOrNull()
}

actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = { user ->
    runCatching {
        iosSetStoredString("user_account", user?.let { jsonBase.encodeToString(it) })
    }
}

actual var getPersistentUiDraftValue: (suspend (String) -> String?)? = { key ->
    iosStoredString("ui_draft_" + key.hashCode().toString())
}

actual var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)? = { key, value ->
    iosSetStoredString("ui_draft_" + key.hashCode().toString(), value)
}

actual var cacheDirPath: String = iosCacheDirectoryPath()

actual val Dispatchers.ourIo: CoroutineDispatcher
    get() = Dispatchers.Default

actual var getHttpClientEngine: () -> HttpClientEngine = {
    Darwin.create()
}

actual var getSystemLocaleLanguage: () -> String = {
    "en"
}

actual var getPlatformName: () -> String = { "ios" }

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
    null
}

actual object LocalAitaLanTransport {
    actual fun start(
        deviceId: String,
        tcpPort: Int,
        discoveryPort: Int,
        onMessage: suspend (message: String, senderHost: String) -> String
    ): Boolean = false

    actual fun stop() = Unit
    actual fun broadcast(message: String, discoveryPort: Int) = Unit
    actual suspend fun send(host: String, port: Int, message: String, timeoutMillis: Int): String? = null
    actual fun localHostAddress(): String = "127.0.0.1"
}

fun installIosCommonPlatformBridges() {
    getClientDeviceInfo = {
        val device = UIDevice.currentDevice
        ClientDeviceInfoDataModel(
            installationId = iosInstallationId(),
            deviceName = device.name,
            platformName = "iOS",
            osName = "${device.systemName} ${device.systemVersion}",
            appName = "AITA",
            appVersion = "",
            localeLanguage = getSystemLocaleLanguage()
        )
    }
}
