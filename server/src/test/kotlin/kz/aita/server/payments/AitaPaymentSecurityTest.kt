package kz.aita.server.payments

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kz.aita.payments.AitaBalanceInvoiceStatus

class AitaPaymentSecurityTest {
    @Test
    fun secretsAreBoundToStoreAndProviderContext() {
        val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
        val cipher = IntegrationSecretCipher.fromEnvironment(
            mapOf(
                "AITA_INTEGRATION_MASTER_KEY_VERSION" to "3",
                "AITA_INTEGRATION_MASTER_KEY_B64" to key,
            ),
        )
        val aad = IntegrationSecretCipher.associatedData(
            storeId = "store-a",
            provider = "KASPI_PAY",
            environment = "SANDBOX",
            credentialId = "credential-a",
        )
        val plaintext = """{"token":"very-secret"}""".encodeToByteArray()
        val envelope = cipher.encrypt(plaintext, aad)
        assertContentEquals(plaintext, cipher.decrypt(envelope, aad))
    }

    @Test
    fun reconciliationCreditsBalanceExactlyOnPaidTransition() {
        val paid = decideInvoiceReconciliation(
            current = AitaBalanceInvoiceStatus.AWAITING_PAYMENT,
            observation = ProviderInvoiceObservation(
                status = AitaBalanceInvoiceStatus.PAID,
                externalInvoiceId = "invoice",
                observedAtEpochMillis = 1L,
            ),
        )
        assertTrue(paid.apply)
        assertTrue(paid.creditBalance)

        val duplicate = decideInvoiceReconciliation(
            current = AitaBalanceInvoiceStatus.PAID,
            observation = ProviderInvoiceObservation(
                status = AitaBalanceInvoiceStatus.PAID,
                externalInvoiceId = "invoice",
                observedAtEpochMillis = 2L,
            ),
        )
        assertFalse(duplicate.apply)
        assertFalse(duplicate.creditBalance)
    }
}
