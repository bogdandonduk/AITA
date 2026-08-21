package kz.aita.server.payments

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentOperationCoordinatorTest {
    @Test
    fun identicalConcurrentProviderOperationRunsOnce() = runBlocking {
        val coordinator = PaymentOperationCoordinator()
        val executions = AtomicInteger(0)
        val values = List(20) {
            async {
                coordinator.singleFlight("invoice:one") {
                    executions.incrementAndGet()
                    delay(20)
                    "paid"
                }
            }
        }.awaitAll()
        assertEquals(1, executions.get())
        assertEquals(setOf("paid"), values.toSet())
    }
}
