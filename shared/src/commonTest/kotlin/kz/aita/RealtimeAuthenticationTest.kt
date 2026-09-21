package kz.aita
import kotlin.test.*
class RealtimeAuthenticationTest {
    private val token = "header0123.payload0123.signature0123"
    @Test fun browserProtocolsCarryOneTokenWithoutPuttingItInTheUrl() {
        assertEquals(token, realtimeProtocolAccessToken(listOf(AITA_REALTIME_PROTOCOL, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + token)))
        assertEquals(token, realtimeProtocolAccessToken(listOf("$AITA_REALTIME_PROTOCOL, $AITA_REALTIME_AUTH_PROTOCOL_PREFIX$token")))
    }
    @Test fun unrelatedMalformedOrAmbiguousProtocolCredentialsAreRejected() {
        assertNull(realtimeProtocolAccessToken(listOf(AITA_REALTIME_AUTH_PROTOCOL_PREFIX + token)))
        assertNull(realtimeProtocolAccessToken(listOf(AITA_REALTIME_PROTOCOL)))
        assertNull(realtimeProtocolAccessToken(listOf(AITA_REALTIME_PROTOCOL, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + token, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + token)))
        assertNull(realtimeProtocolAccessToken(listOf(AITA_REALTIME_PROTOCOL, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + "bad token")))
        assertNull(realtimeProtocolAccessToken(listOf(AITA_REALTIME_PROTOCOL, AITA_REALTIME_AUTH_PROTOCOL_PREFIX + "a".repeat(5000))))
    }
}
