package kz.aita

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** One reader per audience. A burst needs at most one trailing read, never a queue of HTTP jobs. */
internal class TutorialRefreshQueue(scope:CoroutineScope,read:suspend (force:Boolean)->Unit,
    onFailure:(Exception)->Unit) {
    private val wakeup=Channel<Unit>(Channel.CONFLATED)
    private val forceRequested=MutableStateFlow(false)
    init {
        scope.launch {
            try {
                for(ignored in wakeup) {
                    val force=forceRequested.compareAndSet(true,false)
                    try {read(force)}
                    catch(cancel:CancellationException){throw cancel}
                    catch(error:Exception){onFailure(error)}
                }
            } finally {wakeup.close()}
        }
    }
    fun request(force:Boolean=false) {
        if(force)forceRequested.value=true
        wakeup.trySend(Unit)
    }
}
