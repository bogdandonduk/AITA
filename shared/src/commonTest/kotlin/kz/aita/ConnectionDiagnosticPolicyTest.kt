package kz.aita

import kotlin.test.*

class ConnectionDiagnosticPolicyTest {
    @Test fun aitaHealthyResponseIsNotAnError() { assertEquals(ConnectionFailureKind.None,classifyConnectionFailure(200,true)) }
    @Test fun htmlSuccessIsNotOurServer() { assertEquals(ConnectionFailureKind.UnexpectedResponse,classifyConnectionFailure(200,false)) }
    @Test fun workerOriginFailureIsDistinctFromARejectedLogin() {
        assertEquals(ConnectionFailureKind.Origin,classifyConnectionFailure(502,false,true))
        assertEquals(ConnectionFailureKind.ServerRejected,classifyConnectionFailure(403,true))
    }
    @Test fun gatewayFailureDoesNotClaimDnsFailure() { assertEquals(ConnectionFailureKind.Gateway,classifyConnectionFailure(503,false)) }
    @Test fun dnsTlsAndTimeoutFailuresAreSeparated() {
        assertEquals(ConnectionFailureKind.Dns,classifyConnectionFailure(null,false,errorSummary="UnknownHostException"))
        assertEquals(ConnectionFailureKind.Tls,classifyConnectionFailure(null,false,errorSummary="SSLHandshakeException"))
        assertEquals(ConnectionFailureKind.Timeout,classifyConnectionFailure(null,false,errorSummary="HttpRequestTimeoutException"))
    }
    @Test fun unknownFailureIsNotInventedAsACloudflareOutage() { assertEquals(ConnectionFailureKind.Unknown,classifyConnectionFailure(null,false,errorSummary="failed")) }
    @Test fun healthyEdgeDoesNotProveHealthyOrigin() { assertEquals("connection.edge_only",connectionDiagnosticMessage(true,true,200,502,false,false)) }
    @Test fun missingBindingHasActionableDiagnosis() { assertEquals("connection.binding_missing",connectionDiagnosticMessage(true,false,200,502,false,false)) }
    @Test fun unrelatedReadyJsonDoesNotPassServerIdentityCheck() { assertEquals("connection.layers_failed",connectionDiagnosticMessage(false,null,404,200,false,true)) }
    @Test fun completeAndDirectReadinessAreDistinct() {
        assertEquals("connection.layers_ready",connectionDiagnosticMessage(true,true,200,200,true,true))
        assertEquals("connection.origin_ready",connectionDiagnosticMessage(false,null,404,200,true,true))
    }
    @Test fun noResponsesMeansUnverifiedNotCloudflareConfirmed() { assertEquals("connection.no_response",connectionDiagnosticMessage(false,null,null,null,false,false)) }
}
