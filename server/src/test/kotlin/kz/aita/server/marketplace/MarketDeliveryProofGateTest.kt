package kz.aita.server.marketplace

import kz.aita.MarketDeliveryAction
import kz.aita.MarketDeliveryCommand
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class MarketDeliveryProofGateTest {
    private fun command() = MarketDeliveryCommand(UUID.randomUUID().toString(),MarketDeliveryAction.DELIVER,
        UUID.randomUUID().toString(),handoverCode="123456")

    @Test fun repeatedProofsAndChangedCodesWithSameCommandConsumeTheRollingWindow() {
        val gate=MarketDeliveryProofGate();val user=UUID.randomUUID();val command=command()
        repeat(6) { assertTrue(gate.check(user,command.copy(handoverCode=it.toString().padStart(6,'0')),it.toLong()).allowed) }
        val blocked=gate.check(user,command,6)
        assertFalse(blocked.allowed);assertEquals(300L,blocked.retryAfterSeconds)
        assertTrue(gate.check(user,command,300_000).allowed)
        assertFalse(gate.check(user,command,300_000).allowed)
        assertTrue(gate.check(user,command,300_001).allowed)
    }
    @Test fun BuyerConfirmationAndOtherCommandsDoNotConsumeOrDependOnProofAllowance() {
        val gate=MarketDeliveryProofGate();val user=UUID.randomUUID();val command=command()
        repeat(6) { assertTrue(gate.check(user,command,it.toLong()).allowed) }
        assertTrue(gate.check(user,command.copy(handoverCode=null),10).allowed)
        assertTrue(gate.check(user,command.copy(handoverCode=""),10).allowed)
        assertTrue(gate.check(user,command.copy(action=MarketDeliveryAction.ARRIVE),10).allowed)
        assertFalse(gate.check(user,command,10).allowed)
    }
    @Test fun DifferentOrdersAndActorsHaveSeparateAllowances() {
        val gate=MarketDeliveryProofGate();val user=UUID.randomUUID();val command=command()
        repeat(6) { assertTrue(gate.check(user,command,it.toLong()).allowed) }
        assertTrue(gate.check(UUID.randomUUID(),command,10).allowed)
        assertTrue(gate.check(user,command.copy(orderId=UUID.randomUUID().toString()),10).allowed)
    }
    @Test fun boundedMemoryDoesNotEvictActiveQuotasToAllowBypass() {
        val gate=MarketDeliveryProofGate(maximumTrackedKeys=1);val user=UUID.randomUUID();val first=command();val second=command()
        assertTrue(gate.check(user,first,0).allowed)
        assertFalse(gate.check(user,second,1).allowed)
        assertTrue(gate.check(user,second,300_000).allowed)
    }
    @Test fun concurrentProofChecksCannotExceedThePerOrderAllowance() {
        val gate=MarketDeliveryProofGate();val user=UUID.randomUUID();val command=command()
        val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(16)
        try {
            val jobs=(1..16).map { pool.submit<Boolean> {start.await();gate.check(user,command,0).allowed} }
            start.countDown()
            assertEquals(6,jobs.count { it.get(5,TimeUnit.SECONDS) })
        } finally { pool.shutdownNow() }
    }
    @Test fun malformedIdsDoNotAllocateQuotaBuckets() {
        val gate=MarketDeliveryProofGate(maximumTrackedKeys=1);val user=UUID.randomUUID()
        assertFailsWith<MarketFailure>{gate.check(user,command().copy(orderId="not-an-id"),0)}
        assertFailsWith<MarketFailure>{gate.check(user,command().copy(commandId="bad"),0)}
        assertTrue(gate.check(user,command(),0).allowed)
    }
}
