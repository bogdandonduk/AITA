package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReceiptIdentityTest {
    private fun transaction(id: String = "", operation: String = "operation-a") = TransactionDataModel(
        id = id, workshiftId = 1L, type = "purchase", storeId = "store-a",
        goodsInTransaction = emptyList(), paidCash = 10.0, paidCard = 0.0,
        cardPaymentOptionId = 0, timeMillis = 1_700_000_000_000L, clientOperationId = operation
    )
    private fun snapshot(id: String = "", operation: String = "operation-a") = TransactionReceiptSnapshotDataModel(
        transaction = transaction(id, operation), store = null, lines = emptyList(),
        paymentDraft = TransactionPaymentDraftDataModel(0, 0, "cash", 10.0, 0.0, 0),
        currencyCode = "KZT", currencySymbol = "₸"
    )
    private val labels = ReceiptTextLabelsDataModel()

    @Test fun draftHasNeitherPlaceholderNorTransactionIdentityLine() {
        val receipt = snapshot()
        assertNull(receipt.transaction.serverReceiptIdOrNull())
        assertEquals("", receipt.receiptNumberText(labels))
        val text = receipt.buildReceiptPlainText("en", labels)
        assertFalse(text.contains("${labels.receipt}:"))
        assertFalse(text.contains("${labels.transactionId}:"))
        assertFalse(text.contains("DRAFT", ignoreCase = true))
    }

    @Test fun offlineAcceptanceDoesNotInventAServerIdentity() {
        val receipt = snapshot("local_operation-a")
        assertTrue(receipt.transaction.id.isNotBlank()) // accepted; Complete must not become available again
        assertNull(receipt.transaction.serverReceiptIdOrNull())
        assertFalse(receipt.buildReceiptPlainText("en", labels).contains("local_"))
        assertFalse(receipt.buildReceiptPlainText("en", labels).contains("${labels.transactionId}:"))
        assertTrue(receipt.receiptPdfFileName().startsWith("receipt_pending_"))
    }

    @Test fun historicalDraftPlaceholderAlsoStaysHidden() {
        assertNull(transaction(" Draft ").serverReceiptIdOrNull())
        assertNull(transaction("LOCAL_pending").serverReceiptIdOrNull())
    }

    @Test fun realServerIdentityAppearsInBothReceiptAndTransactionLines() {
        val receipt = snapshot("9a765abc-1234-4567-8901-123456789abc")
        val text = receipt.buildReceiptPlainText("en", labels)
        assertEquals("9A765ABC", receipt.receiptNumberText(labels))
        assertTrue(text.contains("${labels.receipt}: 9A765ABC"))
        assertTrue(text.contains("${labels.transactionId}: 9a765abc-1234-4567-8901-123456789abc"))
    }

    @Test fun cloudSyncPromotesOnlyTheMatchingOfflineReceipt() {
        val local = snapshot("local_operation-a")
        val server = transaction("server-id")
        val promoted = local.withServerReceiptIdentity(server)
        assertEquals("server-id", promoted.transaction.id)
        assertSame(local.lines, promoted.lines)
        assertSame(local.paymentDraft, promoted.paymentDraft)
    }

    @Test fun differentStoreTypeOrOperationCannotReplaceThePreview() {
        val local = snapshot("local_operation-a")
        val server = transaction("server-id")
        assertSame(local, local.withServerReceiptIdentity(server.copy(storeId = "store-b")))
        assertSame(local, local.withServerReceiptIdentity(server.copy(type = "return")))
        assertSame(local, local.withServerReceiptIdentity(server.copy(clientOperationId = "operation-b")))
    }

    @Test fun emptyOperationIdsAreNotACorrelationKey() {
        val local = snapshot("local_unknown", "")
        assertSame(local, local.withServerReceiptIdentity(transaction("server-id", "")))
    }

    @Test fun localToLocalOrDuplicateCloudResponsesDoNotReplaceTheSnapshot() {
        val local = snapshot("local_operation-a")
        assertSame(local, local.withServerReceiptIdentity(transaction("local_other")))
        val cloud = snapshot("server-id")
        assertSame(cloud, cloud.withServerReceiptIdentity(transaction("other-server-id")))
    }
}
