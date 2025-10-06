package kz.aita.core

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import kz.aita.KeyValueDatabase
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(path) => new Worker(new URL(path, import.meta.url))")
private external fun createWorker(path: String): Worker

@OptIn(ExperimentalWasmJsInterop::class)
actual var sqlDelightDriver: SqlDriver? =
  WebWorkerDriver(
    createWorker("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js")
  )