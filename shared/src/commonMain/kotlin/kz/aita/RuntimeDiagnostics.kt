package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex

object RuntimeDiagnostics {
    private val mutableState = MutableStateFlow(DiagnosticJournal())
    val state = mutableState.asStateFlow()
    val waiting = MutableStateFlow(false)
    val storageError = MutableStateFlow(false)
    val damaged = MutableStateFlow(false)
    val storageWaiting = MutableStateFlow(false)
    fun waitForPlatformStorage() { storageWaiting.value = true }
    fun platformStorageUnavailable() { storageWaiting.value = false; storageError.value = true }
    private val configured = MutableStateFlow(false)
    private val started = MutableStateFlow(false)
    private val recorder = MutableStateFlow<DiagnosticRecorder?>(null)
    private val context = MutableStateFlow(DiagnosticContext())
    private val frames = MutableStateFlow<(Throwable) -> List<String>>({ diagnosticStackFrames(it.stackTraceToString()) })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.ourIo)
    private val uploadMutex = Mutex()
    private val activeUpload = MutableStateFlow<Job?>(null)
    fun configure(storage: DiagnosticLocalStorage, buildContext: DiagnosticContext, newId: () -> String,
        frameExtractor: (Throwable) -> List<String> = { diagnosticStackFrames(it.stackTraceToString()) }) {
        if (!configured.compareAndSet(false, true)) return
        storageWaiting.value = false
        context.value = buildContext.copy(accountId = null, storeId = null).sanitized()
        frames.value = frameExtractor
        try { recorder.value = DiagnosticRecorder(storage, newId, ::getCurrentTimeMillis) { next -> mutableState.update { old -> if (next.installationId != old.installationId || next.revision >= old.revision) next else old } }; refreshStatus() }
        catch (_: Exception) { storageError.value = true; damaged.value = true }
    }
    private fun refreshStatus() {
        recorder.value?.let { storageError.value = it.storageFailed.value; damaged.value = it.corrupt.value }
    }
    fun updateContext(workspace: String, screen: String, language: String, storeId: String?) {
        context.update { it.copy(workspace = diagnosticCode(workspace, 40), screen = diagnosticCode(screen, 80),
            language = language, storeId = storeId?.takeIf(::validDiagnosticId)).sanitized() }
    }
    fun eventContext(): DiagnosticContext {
        val generation = currentAuthenticatedSessionGeneration()
        val account = userAccountState.payloadValue?.id?.takeIf(::validDiagnosticId)
        val owner = inventoryOwners.current
        val stableAccount = account?.takeIf { getStoredUserAuthTokens?.invoke() != null && authenticatedSessionGenerationIsCurrent(generation) }
        return context.value.copy(accountId = stableAccount,
            storeId = owner.storeId?.takeIf { owner.accountId == stableAccount && owner.sessionGeneration == generation && stableAccount != null }).sanitized()
    }
    fun capture(error: Throwable, category: String, fatal: Boolean = false, eventContext: DiagnosticContext? = null) {
        if (error is CancellationException || !state.value.enabled) return
        try { recorder.value?.capture(error, category, eventContext ?: this.eventContext(), fatal, frames.value(error)) }
        catch (_: Throwable) { /* Error reporting cannot replace a fatal handler or recursively report itself. */ }
        refreshStatus()
    }
    fun captureBrowser(errorType: String, category: String, stack: String) {
        if (!state.value.enabled || errorType.endsWith("CancellationException") || errorType == "AbortError") return
        try { recorder.value?.capture(IllegalStateException(), category, eventContext(), false,
            diagnosticStackFrames(stack), explicitType = diagnosticCode(errorType, 120).ifBlank { "BrowserError" }) }
        catch (_: Throwable) { }
        refreshStatus()
    }
    fun setEnabled(enabled: Boolean) {
        if (!enabled) activeUpload.value?.cancel()
        recorder.value?.setEnabled(enabled); refreshStatus()
        if (enabled) sendNow()
    }
    fun setApproximateLocation(enabled: Boolean) {
        if (!enabled) activeUpload.value?.cancel()
        recorder.value?.setLocation(enabled); refreshStatus()
    }
    fun clearPending(accountId: String?) {
        activeUpload.value?.cancel()
        recorder.value?.clear(accountId); refreshStatus()
    }
    fun resetDamagedStorage() { recorder.value?.resetDamagedJournal(); refreshStatus() }
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { while (isActive) { supervisorScope { launch { uploadOnce() }.join() }; delay(30_000) } }
    }
    fun sendNow() { scope.launch { uploadOnce() } }
    private suspend fun uploadOnce() {
        val reporter = recorder.value ?: return
        if (!uploadMutex.tryLock()) return
        val job = currentCoroutineContext()[Job]
        activeUpload.value = job
        try {
            val generation = currentAuthenticatedSessionGeneration()
            val account = userAccountState.payloadValue?.id?.takeIf { getStoredUserAuthTokens?.invoke() != null }
            val batch = reporter.batch(account) ?: return
            val owner = batch.events.first().context.accountId
            if (owner != null && (owner != account || !authenticatedSessionGenerationIsCurrent(generation))) return
            if (!reporter.state.value.enabled) return
            waiting.value = true
            val response = withTimeoutOrNull(20_000) {
                networkRequest<DiagnosticAck, DiagnosticBatch>(HttpMethod.Post,
                    endpointUrl = if (owner == null) "diagnostics/events/anonymous" else "diagnostics/events",
                    body = batch.validated(), expectedSessionGeneration = if (owner == null) null else generation)
            }
            if (response == null || response.negative || response.payload == null || !reporter.acknowledge(batch, response.payload))
                reporter.failedAttempt()
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { reporter.failedAttempt() }
        finally {
            waiting.value = false; activeUpload.compareAndSet(job, null); refreshStatus(); uploadMutex.unlock()
        }
    }
}
