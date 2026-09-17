package kz.aita

import kotlin.test.*

class DiagnosticRequestPolicyTest {
    @Test fun onlyTheExactAnonymousEndpointIsPublic() {
        assertFalse(cloudEndpointRequiresAuthentication("diagnostics/events/anonymous"))
        assertTrue(cloudEndpointRequiresAuthentication("diagnostics/events"))
        assertTrue(cloudEndpointRequiresAuthentication("diagnostics/admin/events"))
        assertTrue(cloudEndpointRequiresAuthentication("diagnostics/events/anonymous/other"))
    }
}
