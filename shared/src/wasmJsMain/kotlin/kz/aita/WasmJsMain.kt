// THIS IS WasmMain.kt - place in shared/src/wasmJsMain/kotlin/kz/aita/WasmMain.kt
package kz.aita

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import org.w3c.dom.Worker

actual fun getCurrentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

private const val browserStoragePrefix = "aita."

private fun browserGet(key: String): String? = runCatching { localStorage.getItem(browserStoragePrefix + key) }.getOrNull()
private fun browserSet(key: String, value: String?) {
    runCatching {
        if (value == null) localStorage.removeItem(browserStoragePrefix + key)
        else localStorage.setItem(browserStoragePrefix + key, value)
    }
}

actual var getStoredUserAuthTokens: (() -> TokenPair?)? = {
    runCatching {
        browserGet("auth_tokens")?.let { jsonBase.decodeFromString<TokenPair>(it) }
    }.getOrNull()
}

actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = { tokens ->
    browserSet("auth_tokens", tokens?.let { jsonBase.encodeToString(it) })
}

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = {
    runCatching {
        browserGet("user_account")?.let { jsonBase.decodeFromString<UserAccountDataModel>(it) }
    }.getOrNull()
}

actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = { user ->
    browserSet("user_account", user?.let { jsonBase.encodeToString(it) })
}

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

private val webWorkerDriver: SqlDriver by lazy {
    WebWorkerDriver(
        Worker(js("new URL('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js', import.meta.url).toString()").unsafeCast<String>())
    )
}

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
    webWorkerDriver
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
