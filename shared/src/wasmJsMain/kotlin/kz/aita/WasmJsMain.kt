// THIS IS WasmMain.kt - place in shared/src/wasmJsMain/kotlin/kz/aita/WasmMain.kt
package kz.aita

import app.cash.sqldelight.db.SqlDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual fun getCurrentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

private const val browserStoragePrefix = "aita."

private fun browserGet(key: String): String? = localStorage.getItem(browserStoragePrefix + key)
private fun browserSet(key: String, value: String?) {
    if (value == null) localStorage.removeItem(browserStoragePrefix + key)
    else localStorage.setItem(browserStoragePrefix + key, value)
}

private val browserTokens = DecodedStoredValue<TokenPair>(
    read = { browserGet("auth_tokens") }, write = { browserSet("auth_tokens", it) },
    decode = { jsonBase.decodeFromString<TokenPair>(it) }, encode = { jsonBase.encodeToString(it) }
)
actual var getStoredUserAuthTokens: (() -> TokenPair?)? = browserTokens::get
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = browserTokens::set

private val browserAccount = DecodedStoredValue<UserAccountDataModel>(
    read = { browserGet("user_account") }, write = { browserSet("user_account", it) },
    decode = { jsonBase.decodeFromString<UserAccountDataModel>(it) }, encode = { jsonBase.encodeToString(it) }
)
actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = browserAccount::get
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = browserAccount::set

actual var getPersistentUiDraftValue: (suspend (String) -> String?)? = { key ->
    browserGet("ui_draft_" + key.hashCode().toString())
}

actual var setPersistentUiDraftValue: (suspend (String, String?) -> Unit)? = { key, value ->
    browserSet("ui_draft_" + key.hashCode().toString(), value)
}

actual var cacheDirPath: String = "browser"

actual val Dispatchers.ourIo: CoroutineDispatcher
    get() = Dispatchers.Default

actual var getHttpClientEngine: () -> HttpClientEngine = {
    Js.create()
}

actual var getSystemLocaleLanguage: () -> String = {
    window.navigator.language.substringBefore('-').substringBefore('_').ifBlank { "en" }
}

actual var getPlatformName: () -> String = { "wasmJs" }

// initializeBrowserDatabase installs the durable worker before the app starts.
actual var getSqlDelightDriver: (() -> SqlDriver?)? = null

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
    actual fun localHostAddress(): String = window.location.hostname.ifBlank { "browser" }
}

private fun browserInstallationId(): String {
    browserGet("installation_id")?.takeIf { it.isNotBlank() }?.let { return it }
    val fresh = "web-${getCurrentTimeMillis()}-${kotlin.random.Random.nextLong().toString(16)}"
    browserSet("installation_id", fresh)
    return fresh
}

fun installWasmCommonPlatformBridges() {
    getClientDeviceInfo = {
        ClientDeviceInfoDataModel(
            installationId = browserInstallationId(),
            deviceName = window.navigator.userAgent.take(64).ifBlank { "Browser" },
            platformName = "Web/Wasm",
            osName = window.navigator.platform,
            appName = "AITA",
            appVersion = "web",
            localeLanguage = getSystemLocaleLanguage()
        )
    }
}
