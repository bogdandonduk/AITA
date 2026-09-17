package kz.aita

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class DynamicCartPersistenceTest {
    private val account = UserAccountDataModel(id = "cart-test-owner", publicId = "CART-TEST",
        phoneNumber = "", email = "", firstName = "Test", lastName = "Owner", countryLocale = "KZ",
        workerAccountIds = null, supplierAccountIds = null, activeStoreId = "cart-store-a",
        appLanguage = "en", appThemeId = 0L, appSizeModeId = DEFAULT_APP_SIZE_MODE_ID, createdAt = 1L, isActive = true)
    private fun quantity(total: Double) = QuantityDataModel("piece", listOf(LocalizedStringDataModel("en", "pcs")), total, 1.0, true)
    private fun fixture(block: suspend () -> Unit) = runBlocking<Unit> {
        val oldDriver = getSqlDelightDriver
        val oldTokens = getStoredUserAuthTokens
        val oldAccount = getStoredUserAccountDataModel
        val oldUserState = userAccountState.value.value
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver).await()
        setAppDatabaseForTests(AppDatabase(driver)); getSqlDelightDriver = { driver }
        getStoredUserAuthTokens = { TokenPair(accessToken = "isolated-access", refreshToken = "isolated-refresh") }
        getStoredUserAccountDataModel = { account }
        try {
            publishActiveInventoryStoreId(null)
            userAccountState.emit(DataState.Success(account))
            putLocalKv(KEY_ACTIVE_STORE_ID, "cart-store-a")
            withTimeout(10_000) { block() }
        } finally {
            DynamicCarts.flush(); publishActiveInventoryStoreId(null)
            userAccountState.emit(oldUserState)
            getStoredUserAuthTokens = oldTokens; getStoredUserAccountDataModel = oldAccount
            getSqlDelightDriver = oldDriver; setAppDatabaseForTests(null); driver.close()
        }
    }
    @Test fun legacyCartFiveAndDraftOnlyCartsSurviveImportWithoutDeletingOldRows() = fixture {
        appDatabase.app_databaseQueries.upsertCart("legacy", 0L, 4L, jsonBase.encodeToString(QuantityDataModel.serializer(), quantity(3.0)))
        putLocalKv("cache_json:transaction_supply_supplier_ids", "{\"2:4\":\"supplier-old\"}")
        DynamicCarts.prepareLegacyImport()
        publishActiveInventoryStoreId("cart-store-a")
        assertTrue(cartPersistenceHydratedState.value)
        assertEquals(5, DynamicCarts.counts.value[0]); assertEquals(5, DynamicCarts.counts.value[2])
        assertEquals(3.0, getCartState(0, 4).value.single().quantity.total)
        assertEquals("supplier-old", currentTransactionSupplySupplierId(2, 4))
        assertEquals(1L, appDatabase.app_databaseQueries.getAllCarts().executeAsList().size.toLong())
        publishActiveInventoryStoreId("cart-store-b")
        assertEquals(listOf(2, 2, 2), DynamicCarts.counts.value)
        assertTrue(getCartState(0, 4).value.isEmpty()); assertNull(currentTransactionSupplySupplierId(2, 4))
        publishActiveInventoryStoreId("cart-store-a")
        assertEquals("legacy", getCartState(0, 4).value.single().id)
        assertEquals("supplier-old", currentTransactionSupplySupplierId(2, 4))
    }
    @Test fun cartsSevenTwentyAndTwentyEightHaveIndependentDurableContents() = fixture {
        publishActiveInventoryStoreId("cart-store-a")
        for (slot in listOf(6, 19, 27)) {
            assertTrue(DynamicCarts.reveal(0, slot))
            upsertCart("item-$slot", 0, slot, quantity(slot + 1.0))
        }
        DynamicCarts.flush()
        assertEquals(28, DynamicCarts.counts.value[0])
        publishActiveInventoryStoreId("cart-store-b"); publishActiveInventoryStoreId("cart-store-a")
        for (slot in listOf(6, 19, 27)) assertEquals("item-$slot", getCartState(0, slot).value.single().id)
        assertTrue(DynamicCarts.delete(0, 19))
        assertTrue(getCartState(0, 19).value.isEmpty()); assertEquals("item-27", getCartState(0, 27).value.single().id)
        assertTrue(getCartState(0, 4).value.isEmpty())
    }
    @Test fun removedTabAndLateWritesStayRemovedAcrossRestart() = fixture {
        publishActiveInventoryStoreId("cart-store-a")
        val removed = assertNotNull(DynamicCarts.add(2))
        val retained = assertNotNull(DynamicCarts.add(2))
        upsertCart("removed", 2, removed, quantity(1.0))
        upsertCart("retained", 2, retained, quantity(5.0))
        setTransactionSupplySupplierId(2, removed, "supplier-removed")
        setTransactionSupplySupplierId(2, retained, "supplier-retained")
        DynamicCarts.flush()
        assertTrue(DynamicCarts.remove(2, removed))
        upsertCart("late", 2, removed, quantity(99.0))
        setTransactionSupplySupplierId(2, removed, "late-supplier")
        DynamicCarts.flush()
        assertTrue(getCartState(2, removed).value.isEmpty())
        assertNull(currentTransactionSupplySupplierId(2, removed))
        publishActiveInventoryStoreId("cart-store-b"); publishActiveInventoryStoreId("cart-store-a")
        assertEquals(listOf(0, 1, retained), DynamicCarts.state.value.book.activeSlots(2))
        assertFalse(DynamicCarts.reveal(2, removed))
        assertEquals(5.0, getCartState(2, retained).value.single().quantity.total)
        assertEquals("supplier-retained", currentTransactionSupplySupplierId(2, retained))
        clearTransactionSupplySupplierId(2, retained); DynamicCarts.flush()
        assertNull(currentTransactionSupplySupplierId(2, retained))
        assertEquals(5.0, getCartState(2, retained).value.single().quantity.total)
    }
    @Test fun queuedContentsAndSupplierWritesAreSavedBeforeSwitchingStores() = fixture {
        publishActiveInventoryStoreId("cart-store-a")
        upsertCart("ordered", 2, 0, quantity(1.0))
        setTransactionSupplySupplierId(2, 0, "first")
        setTransactionSupplySupplierId(2, 0, "second")
        publishActiveInventoryStoreId("cart-store-b")
        assertTrue(getCartState(2, 0).value.isEmpty())
        publishActiveInventoryStoreId("cart-store-a")
        assertEquals("ordered", getCartState(2, 0).value.single().id)
        assertEquals("second", currentTransactionSupplySupplierId(2, 0))
    }
}
