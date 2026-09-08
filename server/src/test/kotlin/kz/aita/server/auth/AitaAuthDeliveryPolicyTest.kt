package kz.aita.server.auth
import kotlin.test.*
class AitaAuthDeliveryPolicyTest {
    @Test fun loopbackWorkerUsesActualClient() {
        assertEquals("203.0.113.7", aitaAuthClientIp("127.0.0.1", "203.0.113.7", "cloudflare-workers-vpc"))
        assertNotEquals("::1", aitaAuthClientIp("::1", "2001:db8::7", "cloudflare-workers-vpc"))
    }
    @Test fun directClientsCannotChooseQuotaBucket() {
        assertEquals("192.168.0.3", aitaAuthClientIp("192.168.0.3", "203.0.113.7", "cloudflare-workers-vpc"))
        assertEquals("127.0.0.1", aitaAuthClientIp("127.0.0.1", "203.0.113.7", null))
    }
    @Test fun malformedForwardingNeverDoesDnsLookup() {
        for (v in listOf("example.com", "1.2.3.4, 5.6.7.8", "1.2.3.999", "", "evil%eth0", "1.2.3")) {
            assertEquals("127.0.0.1", aitaAuthClientIp("127.0.0.1", v, "cloudflare-workers-vpc"))
        }
    }
    @Test fun codeIsSingleUseAndBounded() {
        assertTrue(authCodeCanBeVerified(1, 2, null, null, 0, 5))
        assertFalse(authCodeCanBeVerified(2, 2, null, null, 0, 5))
        assertFalse(authCodeCanBeVerified(1, 2, 1, null, 0, 5))
        assertFalse(authCodeCanBeVerified(1, 2, null, 1, 0, 5))
        assertFalse(authCodeCanBeVerified(1, 2, null, null, 5, 5))
    }
    @Test fun staleMessagesAreNotDelivered() {
        assertTrue(authEmailCanBeDelivered(1, 2, null, null, true))
        assertFalse(authEmailCanBeDelivered(2, 2, null, null, true))
        assertFalse(authEmailCanBeDelivered(1, 2, null, 1, true))
        assertFalse(authEmailCanBeDelivered(1, 2, 1, null, true))
        assertFalse(authEmailCanBeDelivered(1, 2, null, null, false))
    }
    @Test fun acceptanceNeedsProviderId() {
        assertTrue(classifyResendDelivery(200, "abc-123", null).success)
        for (id in listOf(null, "", "<html>error</html>", "bad\nvalue")) {
            val result=classifyResendDelivery(200, id, null)
            assertFalse(result.success); assertTrue(result.retry)
        }
    }
    @Test fun onlyConcurrentIdempotencyConflictRetries() {
        assertTrue(classifyResendDelivery(409, null, "concurrent_idempotent_requests").retry)
        assertFalse(classifyResendDelivery(409, null, "invalid_idempotent_request").retry)
        assertFalse(classifyResendDelivery(409, null, null).retry)
    }
    @Test fun quotasDoNotRetryExpiredCodes() {
        for (code in listOf("daily_quota_exceeded", "monthly_quota_exceeded")) {
            val result=classifyResendDelivery(429, null, code)
            assertFalse(result.retry); assertTrue(result.serviceWideFailure)
        }
        assertTrue(classifyResendDelivery(429, null, "rate_limit_exceeded").retry)
        assertTrue(classifyResendDelivery(503, null, null).retry)
        assertFalse(classifyResendDelivery(422, null, "validation_error").retry)
    }
    @Test fun retryAfterSecondsDatesAndBounds() {
        assertEquals(6000L,resendRetryAfterMillis("6",0))
        assertEquals(10000L,resendRetryAfterMillis("Thu, 01 Jan 1970 00:00:10 GMT",0))
        assertEquals(86400000L,resendRetryAfterMillis("999999999",0))
        assertEquals(0L,resendRetryAfterMillis("-1",0))
        assertNull(resendRetryAfterMillis("not a date",0))
    }
    @Test fun diagnosticsNeverReflectProviderMessage() {
        val result=classifyResendDelivery(403,null,"private.person@example.com")
        assertEquals("RESEND_HTTP_403",result.errorCode)
        assertTrue(result.serviceWideFailure)
    }
}
