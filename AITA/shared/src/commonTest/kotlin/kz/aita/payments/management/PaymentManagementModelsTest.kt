package kz.aita.payments.management

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentManagementModelsTest {
    @Test
    fun secretCannotBeSetAndClearedTogether() {
        val request = PaymentIntegrationSecretPatchRequest(
            storeId = "store-1",
            secrets = mapOf("apiToken" to "secret"),
            clearSecretKeys = setOf("apiToken"),
        )
        assertEquals("SECRET_SET_AND_CLEARED", request.validationError())
    }

    @Test
    fun topUpRequiresPositiveKztMinorUnitsAndDurableKey() {
        assertEquals("INVALID_AMOUNT", PaymentCreateTopUpRequest("store", 0, idempotencyKey = "request-123").validationError())
        assertEquals("UNSUPPORTED_CURRENCY", PaymentCreateTopUpRequest("store", 100, "USD", "request-123").validationError())
        assertEquals(null, PaymentCreateTopUpRequest("store", 100, "KZT", "request-123").validationError())
    }
}
