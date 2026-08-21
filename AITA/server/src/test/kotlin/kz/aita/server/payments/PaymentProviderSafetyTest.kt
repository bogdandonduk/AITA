package kz.aita.server.payments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentProviderSafetyTest {
    @Test
    fun productionProviderRejectsPrivateAndNonHttpsEndpoints() {
        assertFalse(PaymentProviderUrlPolicy.validate("http://provider.example/api", production = true).accepted)
        assertFalse(PaymentProviderUrlPolicy.validate("https://127.0.0.1/api", production = true).accepted)
        assertFalse(PaymentProviderUrlPolicy.validate("https://service.local/api", production = true).accepted)
        assertFalse(PaymentProviderUrlPolicy.validate("https://provider.example/api", production = true).accepted)
        assertTrue(PaymentProviderUrlPolicy.validate(
            "https://provider.example/api",
            production = true,
            allowedHosts = setOf("provider.example"),
        ).accepted)
    }

    @Test
    fun redactorRemovesBearerAndJsonSecrets() {
        val redacted = PaymentSecretRedactor.redactText(
            "Authorization: Bearer abc.def and {\"token\":\"top-secret\"}",
        )
        assertFalse("abc.def" in redacted)
        assertFalse("top-secret" in redacted)
        assertTrue("<redacted>" in redacted)
    }
}
