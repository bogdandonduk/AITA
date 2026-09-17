package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Commands are enqueued synchronously, not launched on competing dispatchers before taking a lock. */
internal class OrderedCartWork(scope: CoroutineScope, capacity: Int = 1024) {
    private val commands = Channel<suspend () -> Unit>(capacity)
    init {
        scope.launch {
            for (command in commands) {
                try { command() }
                catch (cancel: CancellationException) { currentCoroutineContext().ensureActive() }
                catch (_: Exception) { /* Individual callers own their failure notification/completion. */ }
            }
        }
    }
    fun post(command: suspend () -> Unit): Boolean = commands.trySend(command).isSuccess
    suspend fun <T> run(command: suspend () -> T): T {
        val reply = CompletableDeferred<T>()
        commands.send {
            if (!reply.isCancelled) try { reply.complete(command()) }
            catch (failure: Throwable) { reply.completeExceptionally(failure) }
        }
        return try { reply.await() }
        catch (cancel: CancellationException) { reply.cancel(); throw cancel }
    }
    suspend fun drain() = run { Unit }
}
