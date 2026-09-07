package kz.aita

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MutableDataStateFlowTest {
    @Test
    fun nullableStateWritesRemainImmediateAndOrderedAfterOwnerScopeCancellation() {
        val scope = CoroutineScope(SupervisorJob())
        val state = MutableDataStateFlow<Int>(scope)
        scope.cancel()

        state.emit(DataState.Success(1))
        state.emit(DataState.Success(2))

        assertEquals(2, state.payloadValue)
        assertEquals(2, assertIs<DataState.Success<Int>>(state.value.value).payload)

        state.emit(DataState.Empty())

        assertNull(state.payloadValue)
        assertTrue(state.value.value is DataState.Empty<*>)
    }

    @Test
    fun concurrentWritesKeepNullableEnvelopeAndPayloadOnTheSameAtomicSnapshot() = runTest {
        val state = MutableDataStateFlow<Int>(this)

        coroutineScope {
            (0 until 24).map { writer ->
                async(Dispatchers.Default) {
                    repeat(150) { step ->
                        state.emit(DataState.Success(writer * 1_000 + step))
                    }
                }
            }.awaitAll()
        }

        val envelope = assertIs<DataState.Success<Int>>(state.value.value)
        assertEquals(envelope.payload, state.payloadValue)
    }

    @Test
    fun nonNullStateKeepsLastPayloadWhenOnlyTheEnvelopeBecomesEmpty() {
        val scope = CoroutineScope(SupervisorJob())
        val state = MutableDataStateFlowNonNull(scope, initial = "first")
        scope.cancel()

        state.emit(DataState.Success("second"))
        assertEquals("second", state.payloadValue)
        assertEquals("second", assertIs<DataState.Success<String>>(state.value.value).payload)

        state.emit(DataState.Empty())

        assertEquals("second", state.payloadValue)
        assertTrue(state.value.value is DataState.Empty<*>)
    }
}
