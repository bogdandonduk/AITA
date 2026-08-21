package kz.aita

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierProfileRealtimeTest {
    @Test
    fun supplierProfileMutationEntitiesAreSeparatedFromDashboardInvalidation() {
        assertTrue(supplierRealtimeEntityChangesProfiles("suppliers"))
        assertTrue(supplierRealtimeEntityChangesProfiles("suppliers/add"))
        assertTrue(supplierRealtimeEntityChangesProfiles("SUPPLIERS/UPDATE"))
        assertTrue(supplierRealtimeEntityChangesProfiles(" suppliers/delete "))
        assertTrue(supplierRealtimeEntityChangesProfiles("suppliers/profiles/shared"))

        assertFalse(supplierRealtimeEntityChangesProfiles("suppliers/dashboard"))
        assertFalse(supplierRealtimeEntityChangesProfiles("supplierorders"))
        assertFalse(supplierRealtimeEntityChangesProfiles(null))
    }
}
