package kz.aita.payments

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AitaPaymentsModelsTest {
    @Test
    fun paidInvoiceCanOnlyMoveToRefunded() {
        assertTrue(
            canTransitionAitaBalanceInvoice(
                AitaBalanceInvoiceStatus.PAID,
                AitaBalanceInvoiceStatus.REFUNDED,
            ),
        )
        assertFalse(
            canTransitionAitaBalanceInvoice(
                AitaBalanceInvoiceStatus.PAID,
                AitaBalanceInvoiceStatus.CANCELLED,
            ),
        )
    }

    @Test
    fun fiscalReceiptRequiresExactLineAndPaymentTotals() {
        val valid = AitaFiscalReceiptCommand(
            storeId = "store",
            transactionId = "transaction",
            type = AitaFiscalReceiptType.SALE,
            idempotencyKey = "sale:transaction",
            lines = listOf(
                AitaFiscalLine(
                    lineId = "line",
                    name = "Goods",
                    quantityMilli = 1_000L,
                    unitPriceMinor = 1_500L,
                    totalMinor = 1_500L,
                ),
            ),
            payments = listOf(
                AitaFiscalPayment(
                    typeCode = 0,
                    amountMinor = 1_500L,
                ),
            ),
            totalMinor = 1_500L,
        )
        requireValidAitaFiscalReceipt(valid)

        assertFailsWith<IllegalArgumentException> {
            requireValidAitaFiscalReceipt(
                valid.copy(totalMinor = 1_499L),
            )
        }
    }
}
