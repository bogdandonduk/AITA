package kz.aita

import kotlin.test.*

class TransactionReceiptBarcodeTest {
    private val id = "9a765abc-1234-4567-8901-123456789abc"
    private fun snapshot(receiptId: String) = TransactionReceiptSnapshotDataModel(
        TransactionDataModel(receiptId, 1L, "purchase", "store-a", emptyList(), 10.0, 0.0, 0,
            timeMillis = 1_700_000_000_000L, clientOperationId = "accepted-operation"),
        null, emptyList(), TransactionPaymentDraftDataModel(0, 0, "cash", 10.0, 0.0, 0), "KZT", "₸"
    )

    @Test fun fullUnsignedUuidIncludingLeadingZerosRoundTripsWithoutPlatformNumbers() {
        for (uuid in listOf(id, "00000000-0000-0000-0000-000000000000", "00000000-0000-0000-0000-000000000001",
            "ffffffff-ffff-ffff-ffff-ffffffffffff", "80000000-0000-0000-0000-000000000000")) {
            val payload = assertNotNull(transactionReceiptBarcodePayload(uuid.uppercase()))
            assertEquals(44, payload.length)
            assertTrue(payload.all { it in '0'..'9' })
            assertEquals(uuid, parseTransactionReceiptBarcode(payload))
        }
        assertEquals("99101340282366920938463463374607431768211455", transactionReceiptBarcodePayload("ffffffff-ffff-ffff-ffff-ffffffffffff"))
    }

    @Test fun hardwareTerminatorsAndAimPrefixAreAcceptedWithoutAcceptingArbitraryWrappers() {
        val payload = assertNotNull(transactionReceiptBarcodePayload(id))
        assertEquals(id, parseTransactionReceiptBarcode("\t]C0$payload\r\n"))
        for (input in listOf("https://example.test/$payload", "receipt:$payload", "]Q3$payload", "$payload\u0000")) {
            assertNull(parseTransactionReceiptBarcode(input))
        }
    }

    @Test fun productCodesPartialInputAndOverflowCannotBecomeReceiptIds() {
        val payload = assertNotNull(transactionReceiptBarcodePayload(id))
        for (input in listOf("4871234567890", "99101", payload.dropLast(1), payload + "0", "99101" + "9".repeat(39),
            "99101340282366920938463463374607431768211456", payload.replaceFirst('9', '8'), id)) {
            assertNull(parseTransactionReceiptBarcode(input), input)
        }
        assertNull(parseTransactionReceiptBarcode(" ".repeat(100_000)))
    }

    @Test fun noMadeUpBarcodeForDraftOrLocalOperationButOfflineTextPrintingIsPreserved() {
        for (receiptId in listOf("", "draft", "local_accepted-operation", "malformed-server-id")) {
            val receipt = snapshot(receiptId)
            assertNull(receipt.transaction.receiptBarcodePayload())
            val document = receipt.buildReceiptPdfDocument("en", ReceiptTextLabelsDataModel())
            assertTrue(document.blocks.none { it.barcodePayload != null })
            assertTrue(receipt.buildReceiptPlainText("en", ReceiptTextLabelsDataModel()).contains("Thank"))
        }
    }

    @Test fun acceptedOfflineOperationCodeStillResolvesAfterServerAcknowledgement() {
        val operation = "txn-$id"
        val queued = snapshot("local_$operation").transaction.copy(clientOperationId = operation)
        val printed = assertNotNull(queued.receiptBarcodePayload())
        assertTrue(printed.startsWith("99102"))
        assertEquals(operation, parseTransactionReceiptBarcodeIdentity("]C0$printed\r\n")?.clientOperationId)
        assertNull(parseTransactionReceiptBarcode(printed))
        val synced = queued.copy(id = "0c22f80d-ace5-4b17-bff9-ad69048f45ec")
        assertEquals(synced.clientOperationId, parseTransactionReceiptBarcodeIdentity(printed)?.clientOperationId)
        assertEquals(synced.id, parseTransactionReceiptBarcode(assertNotNull(synced.receiptBarcodePayload())))
        assertNull(queued.copy(id = "").receiptBarcodePayload()) // operation allocated before Complete is still a draft
        assertNull(transactionOperationReceiptBarcodePayload("txn-device-user-installation-1726700000000-abc123"))
    }

    @Test fun acknowledgedReceiptHasExactlyOneBarcodeAsTheLastBlock() {
        val receipt = snapshot(id)
        val labels = ReceiptTextLabelsDataModel()
        val blocks = receipt.buildReceiptPdfDocument("en", labels).blocks
        assertEquals(1, blocks.count { it.barcodePayload != null })
        assertEquals(transactionReceiptBarcodePayload(id), blocks.last().barcodePayload)
        assertEquals(labels.thankYou, blocks[blocks.lastIndex - 1].text)
        assertTrue(receipt.buildReceiptPlainText("en", labels).endsWith(labels.thankYou + "\n"))
    }

    @Test fun footerIsNeverSplitAcrossPdfPagesOrOutsideItsMargins() {
        val payload = assertNotNull(transactionReceiptBarcodePayload(id))
        val document = AitaPdfDocument(List(70) { AitaPdfBlock("Receipt row $it") } + AitaPdfBlock("", barcodePayload = payload))
        val pages = layoutAitaPdfDocument(document, { text, style -> text.length * style.size / 2f }, { -it.size to it.size / 4f })
        val footer = pages.last().lines.last()
        val barcode = assertNotNull(footer.barcode)
        assertEquals(1, pages.flatMap { it.lines }.count { it.barcode != null })
        assertTrue(footer.x >= document.margin)
        assertTrue(footer.x + barcode.width <= document.width - document.margin)
        assertTrue(footer.baseline + barcode.height <= pages.last().height - document.margin + .001f)
        assertTrue(barcode.bars.all { it.x >= 0 && it.y >= 0 && it.x + it.width <= barcode.width && it.y + it.height <= barcode.height })
    }
}
