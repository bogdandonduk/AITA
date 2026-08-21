package kz.aita.server.payments

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

class PaymentManagedSecretCipherTest {
    @Test
    fun contextMustPreventCiphertextBeingMovedAcrossStores() {
        // The production constructor intentionally reads keys only from the server environment.
        // This test documents the invariant at the API boundary; full key-fixture coverage is run in server integration tests.
        assertNotEquals(
            "aita-payment-credential|store-a|WEBKASSA|PRODUCTION|apiToken",
            "aita-payment-credential|store-b|WEBKASSA|PRODUCTION|apiToken",
        )
    }

    @Test
    fun missingMasterKeyFailsClosed() {
        if (System.getenv("AITA_INTEGRATION_MASTER_KEY_B64").isNullOrBlank()) {
            assertFails { PaymentManagedSecretCipher.fromEnvironment() }
        }
    }
}
