package kz.aita

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import io.ktor.client.engine.*
import io.ktor.client.engine.js.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

actual fun getCurrentTimeMillis(): Long {
  return jsDateNow().toLong()
}

//actual var getStoredUserAuthTokens: (() -> TokenPair?)? = null
actual var setStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null

actual var getStoredUserAccountDataModel: (() -> UserAccountDataModel?)? = null
actual var setStoredUserAccountDataModel: ((UserAccountDataModel?) -> Unit)? = null

//actual var cacheDirPath: String = ""
actual val Dispatchers.ourIo: CoroutineDispatcher
  get() = Dispatchers.Default

actual var getHttpClientEngine: () -> HttpClientEngine = {
  Js.create()
}

actual var getSystemLocaleLanguage: () -> String = {
  "ru"
}

actual var getPlatformName: () -> String = {
  "wasmJs"
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(path) => new Worker(new URL(path, import.meta.url))")
external fun createWorker(path: String): Worker

actual var getSqlDelightDriver: (() -> SqlDriver?)? = {
  WebWorkerDriver(
    createWorker("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js")
  )
}
