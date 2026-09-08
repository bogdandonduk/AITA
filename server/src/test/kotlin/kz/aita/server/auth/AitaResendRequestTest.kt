package kz.aita.server.auth
import java.util.UUID
import kotlin.test.*
class AitaResendRequestTest {
    @Test fun requestIsHttpsJsonWithStableIdentity() {
        val id=UUID.fromString("12345678-1234-1234-1234-123456789012")
        val a=buildResendAuthRequest("re_unit_test_not_secret",id,"{}")
        val b=buildResendAuthRequest("re_unit_test_not_secret",id,"{}")
        assertEquals("https://api.resend.com/emails",a.uri().toString()); assertEquals("POST",a.method())
        assertEquals("AITA-Authentication/1.0",a.headers().firstValue("User-Agent").get())
        assertEquals("Bearer re_unit_test_not_secret",a.headers().firstValue("Authorization").get())
        assertEquals(a.headers().firstValue("Idempotency-Key"),b.headers().firstValue("Idempotency-Key"))
        assertEquals(20L,a.timeout().get().seconds)
    }
    @Test fun anotherChallengeDoesNotReuseDeliveryIdentity() {
        val a=buildResendAuthRequest("re_test",UUID.randomUUID(),"{}")
        val b=buildResendAuthRequest("re_test",UUID.randomUUID(),"{}")
        assertNotEquals(a.headers().firstValue("Idempotency-Key"),b.headers().firstValue("Idempotency-Key"))
    }
}
