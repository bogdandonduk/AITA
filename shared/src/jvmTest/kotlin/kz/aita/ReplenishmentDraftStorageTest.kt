package kz.aita

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.*
import kotlinx.coroutines.runBlocking

/** Real chunked SQLite persistence, without opening a user's installed app database. */
class ReplenishmentDraftStorageTest {
    private val account = "11111111-1111-4111-8111-111111111111"
    private val store = "22222222-2222-4222-8222-222222222222"
    private val supplier = "33333333-3333-4333-8333-333333333333"
    private val item = "44444444-4444-4444-8444-444444444444"
    private val order = "55555555-5555-4555-8555-555555555555"

    private fun fixture(work: suspend () -> Unit) = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver).await()
            setAppDatabaseForTests(AppDatabase(driver))
            work()
        } finally {
            setAppDatabaseForTests(null)
            driver.close()
        }
    }

    private fun pending() =
        SupplierOrderWithLinesDataModel(
            SupplierOrderDataModel(
                id = order,
                userId = account,
                storeId = store,
                supplierId = supplier,
                status = SupplierOrderStatusDataModel.Sent,
            ),
            listOf(
                SupplierOrderLineDataModel(
                    id = "66666666-6666-4666-8666-666666666666",
                    orderId = order,
                    goodsItemId = item,
                    requestedQuantity =
                        QuantityDataModel(
                            id = "0",
                            immutableUnitName = emptyList(),
                            total = 5.0,
                            roundTotal = true,
                        ),
                )
            ),
        )

    @Test
    fun savedEditsRemainSeparateForEveryAccountAndStore() = fixture {
        val draft =
            ReplenishmentDraft(
                accountId = account,
                storeId = store,
                supplierId = supplier,
                quantities = mapOf(item to "1,5"),
                settings =
                    ReplenishmentSettings(
                        event = "nauryz",
                        arrivalDate = "2027-03-21",
                        upliftPercent = 35,
                    ),
            )
        saveReplenishmentDraft(draft)
        assertEquals(draft, readReplenishmentDraft(account, store))
        assertTrue(readReplenishmentDraft(supplier, store).quantities.isEmpty())
        assertTrue(readReplenishmentDraft(account, supplier).quantities.isEmpty())
        saveReplenishmentDraft(draft.copy(quantities = mapOf(item to "")))
        assertEquals(
            "",
            readReplenishmentDraft(account, store).quantities[item],
            "Incomplete input is a valid saved edit, not a hard zero",
        )
    }

    @Test
    fun lostAcknowledgementPreservesEveryOrderIdentityAndValueAcrossReload() = fixture {
        val draft =
            ReplenishmentDraft(
                accountId = account,
                storeId = store,
                supplierId = supplier,
                quantities = mapOf(item to "5"),
                pendingOrder = pending(),
            )
        saveReplenishmentDraft(draft)
        assertEquals(draft.pendingOrder, readReplenishmentDraft(account, store).pendingOrder)
        saveReplenishmentDraft(
            draft.copy(pendingOrder = null, quantities = emptyMap(), completedOrderId = order)
        )
        assertNull(readReplenishmentDraft(account, store).pendingOrder)
        assertEquals(order, readReplenishmentDraft(account, store).completedOrderId)
    }

    @Test
    fun corruptedJournalNeverTurnsIntoAnEmptySuccess() = fixture {
        val key = "replenishment-draft-v1:$account:$store"
        writeJsonCacheText(key, "{broken")
        assertFails { readReplenishmentDraft(account, store) }
        assertEquals("{broken", readRequiredJsonJournal(key))
    }

    @Test
    fun pendingOrderCannotCrossAccountStoreSupplierOrItemBoundaries() = fixture {
        val good = pending()
        val invalid =
            listOf(
                good.copy(order = good.order.copy(userId = supplier)),
                good.copy(order = good.order.copy(storeId = supplier)),
                good.copy(order = good.order.copy(supplierId = item)),
                good.copy(lines = good.lines + good.lines),
                good.copy(lines = good.lines.map { it.copy(orderId = item) }),
                good.copy(
                    lines =
                        good.lines.map {
                            it.copy(requestedQuantity = it.requestedQuantity.copy(total = -1.0))
                        }
                ),
            )
        for (body in invalid) assertFailsWith<IllegalArgumentException> {
            saveReplenishmentDraft(
                ReplenishmentDraft(
                    accountId = account,
                    storeId = store,
                    supplierId = supplier,
                    pendingOrder = body,
                )
            )
        }
        assertNull(readReplenishmentDraft(account, store).pendingOrder)
    }
}
