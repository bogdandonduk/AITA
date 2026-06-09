// THIS IS IosMain.kt - place in shared/src/iosMain/kotlin/kz/aita/IosMain.kt
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.UIKit.UIDevice

actual fun getCurrentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private const val keychainService = "kz.aita.secure"

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    val bytes = if (isEmpty()) null else pinned.addressOf(0)
    NSData.create(bytes = bytes, length = size.convert())
}

private fun NSData.toByteArray(): ByteArray = bytes?.readBytes(length.toInt()) ?: ByteArray(0)

private fun keychainBaseQuery(account: String): Map<Any?, Any?> = mapOf(
    kSecClass to kSecClassGenericPassword,
    kSecAttrService to keychainService,
    kSecAttrAccount to account
)

private fun keychainGetString(account: String): String? = memScoped {
    val query = keychainBaseQuery(account) + mapOf(
        kSecReturnData to kCFBooleanTrue,
        kSecMatchLimit to kSecMatchLimitOne
    )
    val result = alloc<CFTypeRefVar>()
    val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
    if (status != errSecSuccess) return@memScoped null

    val data = result.value as? NSData ?: return@memScoped null
    data.toByteArray().decodeToString()
}

private fun keychainSetString(account: String, value: String?) {
    val baseQuery = keychainBaseQuery(account)
    SecItemDelete(baseQuery as CFDictionaryRef)

    if (value == null) return

    val attributes = baseQuery + mapOf(
        kSecValueData to value.encodeToByteArray().toNSData()
    )
    SecItemAdd(attributes as CFDictionaryRef, null)
}

private fun iosCacheDirectoryPath(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
    val cachePath = paths.firstOrNull() as? String
    val aitaPath = (cachePath ?: platform.Foundation.NSTemporaryDirectory()).trimEnd('/') + "/AITA"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = aitaPath,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )
    return aitaPath
}

private fun keychainInstallationId(): String {
    keychainGetString("installation_id")?.takeIf { it.isNotBlank() }?.let { return it }
    val fresh = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: platform.Foundation.NSUUID().UUIDString
    keychainSetString("installation_id", fresh)
    return fresh
}

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = {
    runCatching {
        keychainGetString("auth_tokens")?.let { jsonBase.decodeFromString<TokenPair>(it) }
    }.getOrNull()
}

actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = { tokens ->
    runCatching {
        keychainSetString("auth_tokens", tokens?.let { jsonBase.encodeToString(it) })
    }
}

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = {
    runCatching {
        keychainGetString("user_account")?.let { jsonBase.decodeFromString<UserAccountDataModel>(it) }
    }.getOrNull()
}

actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = { user ->
    runCatching {
        keychainSetString("user_account", user?.let { jsonBase.encodeToString(it) })
    }
}

actual var getPersistentUiDraftValue: (suspend (String) -> String?)? = { key ->
    keychainGetString("ui_draft_" + key.hashCode().toString())
}

actual var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)? = { key, value ->
    keychainSetString("ui_draft_" + key.hashCode().toString(), value)
}

actual var cacheDirPath: String = iosCacheDirectoryPath()

actual val Dispatchers.ourIo: CoroutineDispatcher
    get() = Dispatchers.Default

actual var getHttpClientEngine: () -> HttpClientEngine = {
    Darwin.create()
}

actual var getSystemLocaleLanguage: () -> String = {
    val preferred = (NSLocale.preferredLanguages.firstOrNull() as? String).orEmpty()
    preferred.substringBefore('-').substringBefore('_').ifBlank { "en" }
}

actual var getPlatformName: () -> String = { "ios" }

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
    NativeSqliteDriver(AppDatabase.Schema, "aita_app.db")
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
        val info = NSBundle.mainBundle.infoDictionary
        val version = (info?.get("CFBundleShortVersionString") as? String).orEmpty()
        val device = UIDevice.currentDevice
        ClientDeviceInfoDataModel(
            installationId = keychainInstallationId(),
            deviceName = device.name,
            platformName = "iOS",
            osName = "${device.systemName} ${device.systemVersion}",
            appName = "AITA",
            appVersion = version,
            localeLanguage = getSystemLocaleLanguage()
        )
    }
}
