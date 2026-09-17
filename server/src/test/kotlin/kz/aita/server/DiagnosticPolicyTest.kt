package kz.aita.server

import io.ktor.server.config.MapApplicationConfig
import kz.aita.*
import java.util.UUID
import kotlin.test.*

class DiagnosticPolicyTest {
    private val a = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val b = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private fun batch(owner: String?) = DiagnosticBatch("33333333-3333-4333-8333-333333333333", listOf(
        DiagnosticEvent("44444444-4444-4444-8444-444444444444", "55555555-5555-4555-8555-555555555555", 1_800_000_000_000,
            "runtime", "IllegalStateException", emptyList(), false,
            DiagnosticContext("1.0.0", 1, "RELEASE", "abc1234", DiagnosticDevice("desktop"), owner))))
    @Test fun ownershipMustMatchTheVerifiedPrincipalNotTheClaimedAccount() {
        assertTrue(diagnosticOwnerMatches(a, batch(a.toString())))
        assertFalse(diagnosticOwnerMatches(a, batch(b.toString())))
        assertFalse(diagnosticOwnerMatches(null, batch(a.toString())))
        assertFalse(diagnosticOwnerMatches(a, batch(null)))
        assertTrue(diagnosticOwnerMatches(null, batch(null)))
    }
    @Test fun adminAccessRequiresAnExplicitValidAllowlist() {
        assertFalse(DiagnosticServerSettings().canReview(a))
        val valid = DiagnosticServerSettings.read(MapApplicationConfig("diagnostics.adminUserIds" to a.toString()))
        assertTrue(valid.canReview(a)); assertFalse(valid.canReview(b))
        val malformed = DiagnosticServerSettings.read(MapApplicationConfig("diagnostics.adminUserIds" to "$a,not-an-id"))
        assertFalse(malformed.canReview(a))
    }
    @Test fun countryHeadersAreUntrustedWithoutTheConfiguredGatewayCredential() {
        val key = "unit-test-only-key-".repeat(3)
        assertNull(trustedDiagnosticLocation(null, key, "KZ", "AST"))
        assertNull(trustedDiagnosticLocation(key, "forged-key-value-".repeat(3), "KZ", "AST"))
        assertNull(trustedDiagnosticLocation("short", "short", "KZ", "AST"))
        assertEquals(TrustedDiagnosticLocation("KZ", "AST"), trustedDiagnosticLocation(key, key, "KZ", "AST"))
        for (country in listOf("XX", "T1", "KZZ", "kz", "<x>")) assertNull(trustedDiagnosticLocation(key, key, country, "AST"))
        assertEquals(TrustedDiagnosticLocation("KZ", null), trustedDiagnosticLocation(key, key, "KZ", "street=private"))
    }
    @Test fun reportingDoesNotRequireAStoreSubscriptionButAdminAndOwnedEndpointsRequireAuth() {
        for (path in listOf("diagnostics/events", "diagnostics/events/anonymous", "diagnostics/admin/events", "diagnostics/admin/changes"))
            assertFalse(storeSubscriptionRequiredForEndpoint(path))
    }
}
