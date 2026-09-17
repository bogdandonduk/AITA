@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import platform.UIKit.UIDevice
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException
import kotlinx.coroutines.flow.MutableStateFlow

private val iosDiagnosticsInstalled = MutableStateFlow(false)
internal fun installIosRuntimeDiagnostics() {
    if (!iosDiagnosticsInstalled.compareAndSet(false, true)) return
    val defaults = NSUserDefaults.standardUserDefaults
    val lock = NSRecursiveLock()
    RuntimeDiagnostics.configure(object : DiagnosticLocalStorage {
        override fun read(): String? {
            lock.lock()
            try { return defaults.stringForKey("aita.runtime-diagnostics")?.also { require(it.length <= 2_000_000) } }
            finally { lock.unlock() }
        }
        override fun write(value: String) {
            lock.lock()
            try { if (shouldWriteDiagnosticJournal(read(), value)) {
                defaults.setObject(value, "aita.runtime-diagnostics"); check(defaults.synchronize())
            } } finally { lock.unlock() }
        }
        override fun reset(value: String) {
            lock.lock()
            try {
                diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), value).validated()
                defaults.setObject(value, "aita.runtime-diagnostics"); check(defaults.synchronize())
            } finally { lock.unlock() }
        }
    }, diagnosticBuildContext(DiagnosticDevice("ios", "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
        UIDevice.currentDevice.model, "native")), newId = { NSUUID().UUIDString.lowercase() },
        frameExtractor = { error ->
            val symbol = Regex("kfun:([A-Za-z_$][A-Za-z0-9_.$]*)")
            error.getStackTrace().take(48).mapNotNull { symbol.find(it)?.groupValues?.get(1)?.let { name -> "symbol $name" } }
        })
    var previous: ((Throwable) -> Unit)? = null
    previous = setUnhandledExceptionHook { error ->
        try { RuntimeDiagnostics.capture(error, "ios.kotlin.uncaught", fatal = true) }
        finally { val old = previous; if (old != null) old(error) else terminateWithUnhandledException(error) }
    }
}
