package kz.aita

import kotlin.test.*
import kotlin.time.Instant
import kotlinx.datetime.*

class ReplenishmentPlanningTest {
    private val now = Instant.parse("2026-11-06T10:00:00Z").toEpochMilliseconds()
    private val pc =
        QuantityDataModel("0", listOf(LocalizedStringDataModel("en", "pcs")), 1.0, 1.0, true)
    private val kg = QuantityDataModel("1", emptyList(), 1.0, 1.0, false)
    private val price = PriceDataModel("100", "KZT", "")

    private fun item(
        id: String = "tea",
        store: String = "store",
        unit: String = "0",
        created: Long = 0,
    ) =
        GoodsItemDataModel(
            id = id,
            storeId = store,
            barcodes = listOf("000123"),
            name = listOf(LocalizedStringDataModel("en", "Tea")),
            measurementUnitId = unit,
            supplyPrices = listOf(price),
            createdAtMillis = created,
        )

    private fun tx(
        type: String = "purchase",
        qty: Double = 28.0,
        id: String = "tx",
        unit: QuantityDataModel? = pc,
        store: String = "store",
        at: Long = now - 86400000,
        itemId: String? = "tea",
    ) =
        TransactionDataModel(
            id,
            1,
            type,
            store,
            listOf(
                GoodsItemInTransactionDataModel(
                    "000123",
                    qty,
                    10.0,
                    goodsItemId = itemId,
                    quantityUnit = unit,
                )
            ),
            0.0,
            0.0,
            0,
            timeMillis = at,
        )

    private fun batch(
        qty: Double = 4.0,
        expires: Long? = null,
        status: StockBatchStatusDataModel = StockBatchStatusDataModel.Delivered,
        kind: StockBatchKindDataModel = StockBatchKindDataModel.NORMAL,
    ) =
        GoodsBatchDataModel(
            id = "batch",
            goodsItemId = "tea",
            storeId = "store",
            quantity = pc.copy(total = qty),
            supplyPrice = price,
            status = status,
            kind = kind,
            expirationDateMillis = expires,
        )

    private fun snapshot(
        items: List<GoodsItemDataModel> = listOf(item()),
        batches: List<GoodsBatchDataModel> = listOf(batch()),
        txs: List<TransactionDataModel> = listOf(tx()),
        orders: List<SupplierOrderDataModel> = emptyList(),
        lines: List<SupplierOrderLineDataModel> = emptyList(),
    ) =
        buildReplenishmentSnapshot(
            "store",
            now,
            28,
            "Asia/Almaty",
            items,
            batches,
            txs,
            orders,
            lines,
            listOf(pc, kg, gramsQuantityUnit()),
        )

    @Test
    fun regularPurchaseAccountsForLeadCoverageAndReserve() {
        val row =
            snapshot()
                .suggest(ReplenishmentSettings(leadDays = 7, coverDays = 14, bufferPercent = 10))
                .single()
        assertEquals(1.0, row.dailyQuantity)
        assertEquals(23.1, row.targetQuantity, 1e-8)
        assertEquals(20.0, row.suggestedQuantity)
        assertEquals(LocalDate(2026, 11, 6), row.orderBy)
    }

    @Test
    fun usesCompleteLocalDaysAndIgnoresForeignFutureAndSupplyTransactions() {
        val snap =
            snapshot(
                txs =
                    listOf(
                        tx(),
                        tx(id = "today", at = now),
                        tx(id = "foreign", store = "elsewhere"),
                        tx(id = "future", at = now + 100000),
                        tx(id = "supply", type = "accept"),
                        tx(id = "old", at = now - 35 * 86400000L),
                    )
            )
        assertEquals(28.0, snap.items.single().sold)
        assertEquals(28, snap.items.single().observationDays)
    }

    @Test
    fun returnsReduceDemandAndCannotCreateNegativePurchase() {
        val snap =
            snapshot(txs = listOf(tx(qty = 28.0), tx(type = "return", qty = 7.0, id = "return")))
        assertEquals(.75, snap.suggest(ReplenishmentSettings()).single().dailyQuantity)
        val excess =
            snapshot(txs = listOf(tx(qty = 1.0), tx(type = "return", qty = 5.0, id = "return")))
                .suggest(ReplenishmentSettings())
                .single()
        assertEquals(0.0, excess.dailyQuantity)
        assertEquals(0.0, excess.suggestedQuantity)
    }

    @Test
    fun deduplicatesTransactionRetryIdentityAndPreservesBarcodeUniqueness() {
        val first = tx().copy(clientOperationId = "same-op")
        assertEquals(
            28.0,
            snapshot(txs = listOf(first, first.copy(id = "ack"))).items.single().sold,
        )
        val ambiguous =
            snapshot(items = listOf(item(), item("other")), txs = listOf(tx(itemId = null)))
        assertEquals(1, ambiguous.unmatchedTransactionLines)
        assertTrue(ambiguous.items.all { it.sold == 0.0 })
        assertEquals(
            28.0,
            snapshot(items = listOf(item(), item("other")), txs = listOf(tx()))
                .items
                .single { it.goodsItem.id == "tea" }
                .sold,
        )
    }

    @Test
    fun uncertainUnitDoesNotProduceConfidentAdviceAndKilogramsConvertToGrams() {
        assertNull(
            snapshot(txs = listOf(tx(unit = pc.copy(id = "liter"))))
                .suggest(ReplenishmentSettings())
                .single()
                .suggestedQuantity
        )
        val grams =
            snapshot(
                items = listOf(item(unit = "g")),
                batches = emptyList(),
                txs = listOf(tx(qty = 1.4, unit = kg)),
            )
        assertEquals(1400.0, grams.items.single().sold)
        assertEquals(50.0, grams.suggest(ReplenishmentSettings()).single().dailyQuantity)
    }

    @Test
    fun onShelfIsUsableButReservedWrittenOffAndExpiredAreNot() {
        val rows =
            snapshot(
                batches =
                    listOf(
                        batch(status = StockBatchStatusDataModel.OnShelf),
                        batch(qty = 9.0, status = StockBatchStatusDataModel.Reserved),
                        batch(qty = 9.0, status = StockBatchStatusDataModel.WrittenOff),
                        batch(qty = 3.0, expires = now + 86400000),
                    )
            )
        val s = rows.suggest(ReplenishmentSettings()).single()
        assertEquals(4.0, s.usableStock)
        assertEquals(3.0, s.expiringStock)
    }

    @Test
    fun unlimitedDoesNotCreateReplenishmentOrAcceptManualOrder() {
        val snap =
            snapshot(batches = listOf(batch(qty = 0.0, kind = StockBatchKindDataModel.UNLIMITED)))
        assertNull(snap.suggest(ReplenishmentSettings()).single().suggestedQuantity)
        assertFailsWith<IllegalArgumentException> {
            replenishmentOrder(
                snap,
                ReplenishmentSettings(),
                mapOf("tea" to 2.0),
                "supplier",
                "account",
                "order",
            )
        }
    }

    @Test
    fun newItemsHaveNoInventedHistoryAndAllowExplicitManualQuantity() {
        val snap = snapshot(items = listOf(item(created = now)), txs = emptyList())
        val row = snap.suggest(ReplenishmentSettings()).single()
        assertNull(row.suggestedQuantity)
        assertTrue(row.lowHistory)
        val order =
            replenishmentOrder(
                snap,
                ReplenishmentSettings(),
                mapOf("tea" to 5.0),
                "supplier",
                "account",
                "stable",
            )
        assertEquals(5.0, order.lines.single().requestedQuantity.total)
        assertEquals("stable", order.order.id)
        assertEquals(SupplierOrderStatusDataModel.Sent, order.order.status)
    }

    @Test
    fun incomingOnlyCountsConfirmedRemainingDatedAndUnexpiredAmounts() {
        val orders =
            listOf(
                SupplierOrderDataModel(
                    id = "confirmed",
                    storeId = "store",
                    supplierId = "s",
                    status = SupplierOrderStatusDataModel.PartiallyDelivered,
                    confirmedDeliveryTimeMillis = now + 86400000,
                ),
                SupplierOrderDataModel(
                    id = "undated",
                    storeId = "store",
                    supplierId = "s",
                    status = SupplierOrderStatusDataModel.Confirmed,
                ),
                SupplierOrderDataModel(
                    id = "draft",
                    storeId = "store",
                    supplierId = "s",
                    status = SupplierOrderStatusDataModel.Draft,
                    confirmedDeliveryTimeMillis = now + 86400000,
                ),
                SupplierOrderDataModel(
                    id = "late",
                    storeId = "store",
                    supplierId = "s",
                    status = SupplierOrderStatusDataModel.InDelivery,
                    confirmedDeliveryTimeMillis = now - 86400000,
                ),
            )
        val lines = orders.map {
            SupplierOrderLineDataModel(
                id = it.id,
                orderId = it.id,
                goodsItemId = "tea",
                requestedQuantity = pc.copy(total = 10.0),
                receivedQuantityTotal = 4.0,
            )
        }
        val s = snapshot(orders = orders, lines = lines).suggest(ReplenishmentSettings()).single()
        assertEquals(6.0, s.confirmedIncoming)
        assertEquals(12.0, s.undatedIncoming)
    }

    @Test
    fun legacyReceivedBatchesAreNotOrderedAgainAfterBeingSold() {
        val order =
            SupplierOrderDataModel(
                id = "order",
                storeId = "store",
                supplierId = "s",
                status = SupplierOrderStatusDataModel.PartiallyDelivered,
                confirmedDeliveryTimeMillis = now + 86400000,
            )
        val line =
            SupplierOrderLineDataModel(
                orderId = "order",
                goodsItemId = "tea",
                requestedQuantity = pc.copy(total = 10.0),
                deliveredBatchIds = listOf("already-received"),
            )
        assertEquals(
            0.0,
            snapshot(orders = listOf(order), lines = listOf(line))
                .suggest(ReplenishmentSettings())
                .single()
                .confirmedIncoming,
        )
    }

    @Test
    fun seasonalUpliftIsExplicitAndAppliesToCoverageOnly() {
        val settings =
            ReplenishmentSettings(
                event = "custom",
                arrivalDate = "2026-11-13",
                coverDays = 10,
                bufferPercent = 0,
                upliftPercent = 50,
            )
        val row = snapshot(batches = emptyList()).suggest(settings).single()
        assertEquals(22.0, row.targetQuantity)
        assertEquals(22.0, row.suggestedQuantity)
    }

    @Test
    fun cachedHistoryKeepsItsPeriodButPlansFromTodayAndRejectsPastEvents() {
        val later = now + 10 * 86400000L
        val promised =
            SupplierOrderDataModel(
                id = "promised",
                storeId = "store",
                supplierId = "s",
                status = SupplierOrderStatusDataModel.Confirmed,
                confirmedDeliveryTimeMillis = now + 86400000,
            )
        val snap =
            snapshot(
                orders = listOf(promised),
                lines =
                    listOf(
                        SupplierOrderLineDataModel(
                            orderId = "promised",
                            goodsItemId = "tea",
                            requestedQuantity = pc.copy(total = 9.0),
                        )
                    ),
            )
        val row = snap.suggest(ReplenishmentSettings(), later).single()
        assertEquals(LocalDate(2026, 11, 16), row.orderBy)
        assertEquals(0.0, row.confirmedIncoming)
        assertEquals(9.0, row.undatedIncoming)
        assertEquals(28.0, row.item.sold)
        assertEquals(28, row.item.observationDays)
        val order =
            replenishmentOrder(
                snap,
                ReplenishmentSettings(),
                mapOf("tea" to 5.0),
                "supplier",
                "account",
                "order",
                later,
            )
        val promisedDate =
            Instant.fromEpochMilliseconds(order.order.desiredDeliveryTimeMillis!!)
                .toLocalDateTime(TimeZone.of("Asia/Almaty"))
                .date
        assertEquals(LocalDate(2026, 11, 23), promisedDate)
        assertFailsWith<IllegalArgumentException> {
            snap.suggest(ReplenishmentSettings(event = "custom", arrivalDate = "2026-11-10"), later)
        }
    }

    @Test
    fun eventDatesRollToNextYearAndLunarHolidayNeedsUserDate() {
        val today = LocalDate(2026, 11, 6)
        assertEquals(LocalDate(2027, 1, 1), replenishmentEventDate("new_year", today))
        assertEquals(LocalDate(2027, 3, 21), replenishmentEventDate("nauryz", today))
        assertEquals(LocalDate(2027, 9, 1), replenishmentEventDate("school", today))
        assertNull(replenishmentEventDate("kurban", today))
        assertFalse(ReplenishmentSettings(event = "kurban").valid(today))
        assertFalse(ReplenishmentSettings(coverDays = 0).valid(today))
        assertFalse(
            ReplenishmentSettings(event = "custom", arrivalDate = "2026-10-01").valid(today)
        )
    }

    @Test
    fun orderRequiresAllSelectedRowsValidDoesNotMixCurrenciesOrInventPrices() {
        val snap =
            snapshot(
                items =
                    listOf(
                        item(),
                        item("coffee")
                            .copy(supplyPrices = listOf(price, price.copy(currency = "USD"))),
                    )
            )
        val order =
            replenishmentOrder(
                snap,
                ReplenishmentSettings(),
                mapOf("tea" to 5.0, "coffee" to 3.0),
                "supplier",
                "account",
                "order",
            )
        assertNull(order.order.amount)
        assertNull(order.lines.first { it.goodsItemId == "coffee" }.expectedSupplyPrice)
        assertEquals(
            "100",
            order.lines.first { it.goodsItemId == "tea" }.expectedSupplyPrice?.price,
        )
        assertTrue(order.lines.all { supplierRequestIdentityValid(it.id) })
        assertEquals(order.lines.size, order.lines.map { it.id }.distinct().size)
        assertFailsWith<IllegalArgumentException> {
            replenishmentOrder(
                snap,
                ReplenishmentSettings(),
                mapOf("tea" to 1.5),
                "supplier",
                "account",
                "order",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            replenishmentOrder(
                snap,
                ReplenishmentSettings(),
                mapOf("foreign" to 1.0),
                "supplier",
                "account",
                "order",
            )
        }
    }
}
