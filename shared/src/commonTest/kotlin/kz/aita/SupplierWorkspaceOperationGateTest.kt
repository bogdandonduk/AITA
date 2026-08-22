package kz.aita

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupplierWorkspaceOperationGateTest {
    @Test
    fun newerOperationInvalidatesOlderOperationForSameSupplier() {
        val gate = SupplierWorkspaceOperationGate()
        val first = gate.begin(" supplier-a ", "orders.refresh")
        val second = gate.begin("SUPPLIER-A", "orders.refresh")

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun supplierIdentityIsPartOfOperationOwnership() {
        val gate = SupplierWorkspaceOperationGate()
        val supplierA = gate.begin("supplier-a", "orders.refresh")
        val supplierB = gate.begin("supplier-b", "orders.refresh")

        assertFalse(gate.isCurrent(supplierA))
        assertTrue(gate.isCurrent(supplierB))
    }

    @Test
    fun operationKeyIsPartOfOwnership() {
        val gate = SupplierWorkspaceOperationGate()
        val orders = gate.begin("supplier-a", "orders.refresh")
        val dispatch = gate.begin("supplier-a", "dispatch.refresh")

        assertFalse(gate.isCurrent(orders))
        assertTrue(gate.isCurrent(dispatch))
    }

    @Test
    fun explicitInvalidationRejectsLastTicket() {
        val gate = SupplierWorkspaceOperationGate()
        val ticket = gate.begin(null, "combined.refresh")

        gate.invalidate()

        assertFalse(gate.isCurrent(ticket))
        assertNull(normalizeSupplierWorkspaceIdentity("   "))
    }

    @Test
    fun generationRolloverNeverUsesZero() {
        val gate = SupplierWorkspaceOperationGate(Long.MAX_VALUE)
        val ticket = gate.begin("supplier-a", "orders.refresh")

        assertTrue(ticket.generation > 0L)
        assertTrue(gate.isCurrent(ticket))
    }
}
