package kz.aita

import android.content.Context
import android.os.Build
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private val androidDiagnosticsInstalled = AtomicBoolean(false)
internal fun installAndroidRuntimeDiagnostics(context: Context) {
    if (!androidDiagnosticsInstalled.compareAndSet(false, true)) return
    val preferences = context.applicationContext.getSharedPreferences("aita_runtime_diagnostics", Context.MODE_PRIVATE)
    val lock = Any()
    RuntimeDiagnostics.configure(object : DiagnosticLocalStorage {
        override fun read(): String? = synchronized(lock) {
            preferences.getString("journal", null)?.also { require(it.length <= 2_000_000) }
        }
        override fun write(value: String) = synchronized(lock) {
            if (shouldWriteDiagnosticJournal(read(), value)) check(preferences.edit().putString("journal", value).commit())
            Unit
        }
        override fun reset(value: String) = synchronized(lock) {
            diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), value).validated()
            check(preferences.edit().putString("journal", value).commit()); Unit
        }
    }, diagnosticBuildContext(DiagnosticDevice("android", "Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}",
        "${Build.MANUFACTURER} ${Build.MODEL}", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())), newId = { UUID.randomUUID().toString() },
        frameExtractor = { error -> diagnosticStackFrames(error.stackTrace.take(48).joinToString("\n") { "at $it" }) })
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        try { RuntimeDiagnostics.capture(error, "android.uncaught", fatal = true) }
        finally {
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
}
