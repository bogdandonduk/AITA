package kz.aita

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClientBackgroundWorkTest {
    @Test fun nativeDefaultDoesNotDelayWork() = runTest {
        val gate = ClientBackgroundWorkGate()
        gate.awaitActive()
        assertTrue(gate.isActive)
    }

    @Test fun hiddenTimerWaitsWithoutRepeatingAndResumesOnce() = runTest {
        val gate = ClientBackgroundWorkGate(false)
        var reads = 0
        val job = launch { gate.awaitActive(); reads++ }
        runCurrent()
        assertEquals(0, reads)
        assertFalse(gate.setActive(false))
        assertTrue(gate.setActive(true))
        assertFalse(gate.setActive(true))
        runCurrent()
        assertEquals(1, reads)
        job.join()
    }

    @Test fun disposedScreenDoesNotResumeAnObsoleteRead() = runTest {
        val gate = ClientBackgroundWorkGate(false)
        var reads = 0
        val job = launch { gate.awaitActive(); reads++ }
        runCurrent()
        job.cancel()
        gate.setActive(true)
        runCurrent()
        assertEquals(0, reads)
    }
}
