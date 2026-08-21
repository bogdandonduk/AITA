package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentOperationsModelsTest {
    @Test
    fun topUpRequiresPositiveKztMinorUnitsAndDurableIdempotencyKey() {
        assertEquals(
            "amount_must_be_positive",
            validateOperationsBalanceTopUpRequest(
                BalanceTopUpCreateRequest("store", 0, "KZT", "1234567890123456"),
            ),
        )
        assertEquals(
            "unsupported_currency",
            validateOperationsBalanceTopUpRequest(
                BalanceTopUpCreateRequest("store", 100, "USD", "1234567890123456"),
            ),
        )
        assertNull(
            validateOperationsBalanceTopUpRequest(
                BalanceTopUpCreateRequest("store", 100, "kzt", "1234567890123456"),
            ),
        )
    }

    @Test
    fun unsafeReturnUrlIsRejected() {
        assertEquals(
            "invalid_return_url",
            validateOperationsBalanceTopUpRequest(
                BalanceTopUpCreateRequest(
                    storeId = "store",
                    amountMinor = 100,
                    idempotencyKey = "1234567890123456",
                    returnUrl = "javascript:alert(1)",
                ),
            ),
        )
    }
}
