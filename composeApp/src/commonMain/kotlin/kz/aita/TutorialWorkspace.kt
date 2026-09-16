@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class, kotlin.time.ExperimentalTime::class)
package kz.aita

import aita.composeapp.generated.resources.Res
import io.ktor.http.HttpMethod
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.help.*
import kotlin.time.Clock

internal data class TutorialBookState(val catalogue: HelpCatalogue?=null, val refreshing: Boolean=false,
    val remoteUnavailable: Boolean=false)
/** Shared process-lifetime cache. Searches, decoding and disk access never run on the UI thread. */
internal object TutorialWorkspace {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private val bundledLock=Mutex()
    private var bundled: HelpCatalogue?=null
    private val states=HelpMode.entries.associateWith { MutableStateFlow(TutorialBookState()) }
    private val checked=HelpMode.entries.associateWith { MutableStateFlow<Long?>(null) }
    fun state(mode: HelpMode): StateFlow<TutorialBookState> = states.getValue(mode).asStateFlow()
    private suspend fun baseline()=bundledLock.withLock {
        bundled ?: helpJson.decodeFromString<HelpCatalogue>(Res.readBytes("files/assets/help/tutorials.json").decodeToString())
            .also { require(validHelpCatalogue(it)); bundled=it }
    }
    private val started=MutableStateFlow(false)
    private val queues=HelpMode.entries.associateWith {mode -> TutorialRefreshQueue(scope,
        read={force->readBook(mode,force)},onFailure={states.getValue(mode).update {it.copy(refreshing=false,remoteUnavailable=true)}})}
    fun start() {
        if(!started.compareAndSet(false,true))return
        scope.launch {appModeState.collect {load(HelpMode.fromId(it))}}
        scope.launch {PublicContentSignals.revision.drop(1).collect {refreshObserved()}}
        scope.launch {while(isActive){delay(300_000L);refreshObserved()}} // Missed sockets/old servers recover automatically.
    }
    fun refreshObserved() {
        val current=HelpMode.fromId(appModeState.value)
        states.forEach { (mode,state) -> if(mode==current || state.value.catalogue!=null)load(mode,force=true) }
    }
    fun load(mode:HelpMode,force:Boolean=false) {queues.getValue(mode).request(force)}
    private suspend fun readBook(mode:HelpMode,force:Boolean) {
        val state=states.getValue(mode)
        try {
            if(state.value.catalogue==null) {
                val packaged=baseline().forMode(mode)
                val saved=runCatching {readHelpCatalogueCache(mode)}.getOrNull()
                    ?.takeIf {validHelpCatalogue(it,mode) && it.revision>=packaged.revision}
                state.value=TutorialBookState(saved ?: packaged)
            }
            val now=Clock.System.now().toEpochMilliseconds()
            val last=checked.getValue(mode).value
            if(!force && last!=null && now-last in 0..300_000)return
            state.update {it.copy(refreshing=true)}
            val response=withTimeoutOrNull(30_000L) {networkRequest<HelpCatalogue,Unit>(HttpMethod.Get,endpointUrl="help/tutorials/${mode.slug}")}
            if(response==null) {
                checked.getValue(mode).value=now
                state.update {it.copy(remoteUnavailable=true)}
                return // A request timeout must not cancel the process-lifetime refresh worker.
            }
            val book=response.payload?.takeIf {!response.negative && validHelpCatalogue(it,mode) &&
                it.revision>=(state.value.catalogue?.revision ?: 0)}
            checked.getValue(mode).value=now
            if(book!=null) {
                state.update {it.copy(catalogue=book,remoteUnavailable=false)}
                runCatching {writeHelpCatalogueCache(mode,book)}
            } else state.update {it.copy(remoteUnavailable=true)}
        } catch(cancel:CancellationException){throw cancel}
        catch(_:Exception){state.update {it.copy(remoteUnavailable=true)}}
        finally {state.update {it.copy(refreshing=false)}}
    }
}
