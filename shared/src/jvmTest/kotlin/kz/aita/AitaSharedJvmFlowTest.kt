package kz.aita

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


private const val AITA_FLOW_TEST_SERVER_URL = "http://aita.flow.test"
private const val AITA_FLOW_TEST_USER_ID = "flow-test-user"
private const val AITA_FLOW_SOURCE_STORE_ID = "store-main"
private const val AITA_FLOW_DESTINATION_STORE_ID = "store-branch"
private const val AITA_FLOW_WORKER_USER_ID = "flow-worker-user"
private const val AITA_FLOW_WORKER_PUBLIC_ID = "FLOW-WORKER-001"

private data class RecordedAitaRequest(
    val method: String,
    val path: String,
    val storeIdHeader: String?,
    val goodsItemIdHeader: String?,
    val supplierIdHeader: String? = null,
    val queryParameters: Map<String, List<String>> = emptyMap()
)

private class AitaFlowTestEnvironment {
    var storedTokens: TokenPair? = aitaTestTokenPair("initial")
    var storedAccount: UserAccountDataModel? = null
    var stores: List<StoreDataModel> = emptyList()
    var workerMemberships: List<StoreWorkerDataModel> = emptyList()
    var myWorkerMemberships: List<StoreWorkerDataModel> = emptyList()
    var incomingWorkerRequests: List<StoreWorkerRequestDataModel> = emptyList()
    var myWorkerRequests: List<StoreWorkerRequestDataModel> = emptyList()
    var activeWorkshift: WorkshiftDataModel? = null
    var stock: List<GoodsItemDataModel> = emptyList()
    var batches: List<GoodsBatchDataModel> = emptyList()
    var availability: StockItemBranchAvailabilityDataModel = aitaTestAvailability()
    var transactions: List<TransactionDataModel> = emptyList()
    var debtors: List<DebtorDataModel> = emptyList()
    var suppliers: List<SupplierDataModel> = emptyList()
    var supplierGoodsPrices: List<SupplierGoodsPriceDataModel> = emptyList()
    var supplierOrders: List<SupplierOrderWithLinesDataModel> = emptyList()
    var notifications: List<NotificationDataModel> = emptyList()
    var operationLogs: List<OperationLogDataModel> = emptyList()
    var nextStoreResponse: StoreDataModel? = null
    var nextDeletedStoreId: String? = null
    var nextWorkerRequestResponse: StoreWorkerRequestDataModel? = null
    var nextWorkerMembershipResponse: StoreWorkerDataModel? = null
    var nextRemovedWorkerResponse: StoreWorkerDataModel? = null
    var nextWorkshiftResponse: WorkshiftDataModel? = null
    var nextGoodsItemResponse: GoodsItemDataModel? = null
    var nextGoodsBatchResponse: List<GoodsBatchDataModel>? = null
    var nextDeletedGoodsItemId: String? = null
    var nextDeletedBatchIds: List<String>? = null
    var nextMoveResult: StockBatchMoveResultDataModel? = null
    var nextDecisionResult: StockBatchMoveResultDataModel? = null
    var nextCompletedTransaction: TransactionDataModel? = null
    var forceNextCompleteTransactionFailure: Boolean = false
    var nextDebtorResponse: DebtorDataModel? = null
    var nextDeletedDebtorId: String? = null
    var nextSupplierResponse: SupplierDataModel? = null
    var nextDeletedSupplierId: String? = null
    var nextSupplierGoodsPriceResponse: SupplierGoodsPriceDataModel? = null
    var nextSupplierOrderResponse: SupplierOrderWithLinesDataModel? = null
    var nextDeletedSupplierOrderId: String? = null
    var nextSavedNotificationResponse: NotificationDataModel? = null
    val requests: MutableList<RecordedAitaRequest> = mutableListOf()
}

class AitaSharedJvmFlowTest {
    private lateinit var environment: AitaFlowTestEnvironment
    private lateinit var originalHttpClient: HttpClient
    private var originalCacheDirPath: String = cacheDirPath
    private var testSqlDriver: SqlDriver? = null
    private var originalGetSqlDelightDriver: (() -> SqlDriver?)? = null
    private var originalGetStoredUserAuthTokens: (() -> TokenPair?)? = null
    private var originalSetStoredUserAuthTokens: ((TokenPair?) -> Unit)? = null
    private var originalGetStoredUserAccount: (() -> UserAccountDataModel?)? = null
    private var originalSetStoredUserAccount: ((UserAccountDataModel?) -> Unit)? = null

    @BeforeTest
    fun setUp() = runBlocking {
        originalCacheDirPath = cacheDirPath
        cacheDirPath = Files.createTempDirectory("aita-flow-jvm-test").toString()
        environment = AitaFlowTestEnvironment()

        originalGetSqlDelightDriver = getSqlDelightDriver
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it).await() }
        testSqlDriver = driver
        setAppDatabaseForTests(AppDatabase(driver))
        getSqlDelightDriver = { driver }

        originalHttpClient = httpClient
        originalGetStoredUserAuthTokens = getStoredUserAuthTokens
        originalSetStoredUserAuthTokens = setStoredUserAuthTokens
        originalGetStoredUserAccount = getStoredUserAccountDataModel
        originalSetStoredUserAccount = setStoredUserAccountDataModel

        getStoredUserAuthTokens = { environment.storedTokens }
        setStoredUserAuthTokens = { environment.storedTokens = it }
        getStoredUserAccountDataModel = { environment.storedAccount }
        setStoredUserAccountDataModel = { environment.storedAccount = it }
        httpClient = buildAitaFlowMockClient(environment)

        resetAitaFlowSharedState()
    }

    @AfterTest
    fun tearDown() {
        stopRealtimeUpdates()
        runCatching { httpClient.close() }
        httpClient = originalHttpClient
        getSqlDelightDriver = originalGetSqlDelightDriver
        getStoredUserAuthTokens = originalGetStoredUserAuthTokens
        setStoredUserAuthTokens = originalSetStoredUserAuthTokens
        getStoredUserAccountDataModel = originalGetStoredUserAccount
        setStoredUserAccountDataModel = originalSetStoredUserAccount
        setAppDatabaseForTests(null)
        cacheDirPath = originalCacheDirPath
        runCatching { testSqlDriver?.close() }
        testSqlDriver = null
        
    }

    @Test
    fun loginStoresTokensAndFetchesFreshUserAccount() = runBlocking {
        environment.storedTokens = null
        val expectedTokens = aitaTestTokenPair("login")

        logInUser(
            UserAuthLogInDataModel(
                login = "tester@aita.local",
                password = "StrongPassword123"
            ),
            serverUrlOverride = AITA_FLOW_TEST_SERVER_URL
        )

        waitUntilAitaFlowCondition { environment.storedTokens == expectedTokens }
        waitUntilAitaFlowCondition { environment.storedAccount?.id == AITA_FLOW_TEST_USER_ID }
        stopRealtimeUpdates()

        assertEquals(expectedTokens, environment.storedTokens)
        assertEquals(AITA_FLOW_TEST_USER_ID, environment.storedAccount?.id)
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "auth/logIn" })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "user/get" })
    }

    @Test
    fun signUpStoresTokensAndFetchesFreshUserAccount() = runBlocking {
        environment.storedTokens = null
        val expectedTokens = aitaTestTokenPair("signup")

        signUpUser(
            UserAuthSignUpDataModel(
                phoneNumber = "+77000000001",
                email = "signup@aita.local",
                firstName = "Aita",
                lastName = "Tester",
                countryLocale = "kz",
                password = "StrongPassword123"
            ),
            serverUrlOverride = AITA_FLOW_TEST_SERVER_URL
        )

        waitUntilAitaFlowCondition { environment.storedTokens == expectedTokens }
        waitUntilAitaFlowCondition { environment.storedAccount?.id == AITA_FLOW_TEST_USER_ID }
        stopRealtimeUpdates()

        assertEquals(expectedTokens, environment.storedTokens)
        assertEquals(AITA_FLOW_TEST_USER_ID, environment.storedAccount?.id)
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "auth/signUp" })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "user/get" })
    }

    @Test
    fun logoutClearsLocalAuthAndQueuesServerCleanup() = runBlocking {
        environment.storedTokens = aitaTestTokenPair("logout")
        environment.storedAccount = aitaTestUserAccount()

        logOutUser()

        waitUntilAitaFlowCondition { environment.storedTokens == null && environment.storedAccount == null }
        waitUntilAitaFlowCondition { environment.requests.any { it.method == "DELETE" && it.path == "auth/logOut" } }

        assertNull(environment.storedTokens)
        assertNull(environment.storedAccount)
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "auth/logOut" })
    }

    @Test
    fun loadsStockAndBatchesFromServerWithStoreHeaders() = runBlocking {
        val item = aitaTestGoodsItem(id = "load-item", name = "Loaded apple")
        val batch = aitaTestBatch(id = "load-batch", goodsItemId = item.id, quantityTotal = 9.0)
        environment.stock = listOf(item)
        environment.batches = listOf(batch)
        environment.requests.clear()
        stockState.emit(DataState.Empty())
        stockBatchesState.emit(DataState.Empty())

        getStock(AITA_FLOW_SOURCE_STORE_ID)
        getStockBatches(AITA_FLOW_SOURCE_STORE_ID)

        waitUntilAitaFlowCondition {
            stockState.payloadValue?.singleOrNull()?.id == item.id &&
                    stockBatchesState.payloadValue?.singleOrNull()?.id == batch.id
        }

        val loadedItem = assertNotNull(stockState.payloadValue?.singleOrNull())
        val loadedBatch = assertNotNull(stockBatchesState.payloadValue?.singleOrNull())
        assertEquals(item.id, loadedItem.id)
        assertEquals(item.name, loadedItem.name)
        assertEquals(item.storeId, loadedItem.storeId)
        assertEquals(batch.id, loadedBatch.id)
        assertEquals(batch.goodsItemId, loadedBatch.goodsItemId)
        assertEquals(batch.quantity.total, loadedBatch.quantity.total)
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "stock/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "stockBatches/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun addsUpdatesDeletesGoodsItemAndKeepsLocalStockStateInSync() = runBlocking {
        stockState.emit(DataState.Success(emptyList()))
        waitUntilAitaFlowCondition { stockState.payloadValue?.isEmpty() == true }

        val added = aitaTestGoodsItem(id = "goods-add", name = "Fresh pear", barcode = "4600000000011")
        environment.nextGoodsItemResponse = added
        val addResult = CompletableDeferred<DataState<GoodsItemDataModel>>()
        addGoodsItem(added) { addResult.complete(it) }

        assertEquals(added, requireAitaFlowSuccess(addResult).payload)
        waitUntilAitaFlowCondition { stockState.payloadValue?.any { it.id == added.id } == true }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "stock/add" })

        val updated = added.copy(
            name = aitaTestLocalized("Golden pear"),
            salePrices = listOf(aitaTestPrice("850")),
            updatedAtMillis = 2L
        )
        environment.nextGoodsItemResponse = updated
        val updateResult = CompletableDeferred<DataState<GoodsItemDataModel>>()
        updateGoodsItem(updated) { updateResult.complete(it) }

        assertEquals(updated, requireAitaFlowSuccess(updateResult).payload)
        waitUntilAitaFlowCondition { stockState.payloadValue?.firstOrNull { it.id == updated.id }?.name == updated.name }
        assertEquals(updated, stockState.payloadValue?.first { it.id == updated.id })
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "stock/update" })

        environment.nextDeletedGoodsItemId = updated.id
        val deleteDone = CompletableDeferred<Unit>()
        deleteGoodsItem(updated.id, AITA_FLOW_SOURCE_STORE_ID) { deleteDone.complete(Unit) }

        withTimeout(8_000L) { deleteDone.await() }
        waitUntilAitaFlowCondition { stockState.payloadValue?.none { it.id == updated.id } == true }
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "stock/delete" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun addsUpdatesDeletesStockBatchesAndKeepsLocalBatchStateInSync() = runBlocking {
        stockBatchesState.emit(DataState.Success(emptyList()))
        waitUntilAitaFlowCondition { stockBatchesState.payloadValue?.isEmpty() == true }

        val item = aitaTestGoodsItem(id = "batch-item", name = "Batch item")
        val firstBatch = aitaTestBatch(id = "batch-add-1", goodsItemId = item.id, quantityTotal = 12.0)
        val secondBatch = aitaTestBatch(id = "batch-add-2", goodsItemId = item.id, quantityTotal = 7.0, shelfPriority = 2)
        environment.nextGoodsBatchResponse = listOf(firstBatch, secondBatch)
        val addResult = CompletableDeferred<DataState<List<GoodsBatchDataModel>>>()
        addGoodsBatches(listOf(firstBatch, secondBatch)) { addResult.complete(it) }

        assertEquals(listOf(firstBatch, secondBatch), requireAitaFlowSuccess(addResult).payload)
        waitUntilAitaFlowCondition { stockBatchesState.payloadValue?.size == 2 }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "stockBatches/add" })

        val editedFirstBatch = firstBatch.copy(
            quantity = aitaTestQuantity(15.0),
            status = StockBatchStatusDataModel.OnShelf,
            shelfPriority = 1,
            updatedAtMillis = 3L
        )
        environment.nextGoodsBatchResponse = listOf(editedFirstBatch)
        val updateResult = CompletableDeferred<DataState<List<GoodsBatchDataModel>>>()
        updateGoodsBatches(listOf(editedFirstBatch)) { updateResult.complete(it) }

        assertEquals(listOf(editedFirstBatch), requireAitaFlowSuccess(updateResult).payload)
        waitUntilAitaFlowCondition { stockBatchesState.payloadValue?.firstOrNull { it.id == editedFirstBatch.id }?.quantity?.total == 15.0 }
        assertEquals(editedFirstBatch, stockBatchesState.payloadValue?.first { it.id == editedFirstBatch.id })
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "stockBatches/update" })

        environment.nextDeletedBatchIds = listOf(secondBatch.id)
        val deleteDone = CompletableDeferred<Unit>()
        deleteGoodsBatches(listOf(secondBatch.id), AITA_FLOW_SOURCE_STORE_ID) { deleteDone.complete(Unit) }

        withTimeout(8_000L) { deleteDone.await() }
        waitUntilAitaFlowCondition { stockBatchesState.payloadValue?.none { it.id == secondBatch.id } == true }
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "stockBatches/delete" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun loadsBranchAvailabilityMovesBatchAndAcceptsMovementDecision() = runBlocking {
        activeStoreIdState.emit(AITA_FLOW_SOURCE_STORE_ID)
        val sourceItem = aitaTestGoodsItem(id = "move-source-item", name = "Move source")
        val destinationItem = aitaTestGoodsItem(id = "move-destination-item", storeId = AITA_FLOW_DESTINATION_STORE_ID, name = "Move destination")
        val sourceBatch = aitaTestBatch(id = "move-source-batch", goodsItemId = sourceItem.id, quantityTotal = 6.0)
        val destinationBatch = aitaTestBatch(id = "move-destination-batch", goodsItemId = destinationItem.id, storeId = AITA_FLOW_DESTINATION_STORE_ID, quantityTotal = 4.0)
        val availability = aitaTestAvailability(sourceItemId = sourceItem.id, sourceBatch = sourceBatch, destinationBatch = destinationBatch)
        environment.availability = availability

        val availabilityCallback = CompletableDeferred<DataState<StockItemBranchAvailabilityDataModel>>()
        getStockItemBranchAvailability(AITA_FLOW_SOURCE_STORE_ID, sourceItem.id) { availabilityCallback.complete(it) }

        assertEquals(availability, requireAitaFlowSuccess(availabilityCallback).payload)
        waitUntilAitaFlowCondition { stockItemBranchAvailabilityState.payloadValue?.sourceGoodsItemId == sourceItem.id }
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "stockBatches/branchAvailability" && it.goodsItemIdHeader == sourceItem.id })

        val moveResult = aitaTestMoveResult(
            sourceItem = sourceItem,
            destinationItem = destinationItem,
            sourceBatch = sourceBatch.copy(quantity = aitaTestQuantity(6.0)),
            destinationBatch = destinationBatch.copy(quantity = aitaTestQuantity(4.0)),
            status = StockBatchMovementStatusDataModel.PendingAcceptance,
            movementId = "movement-pending"
        )
        environment.nextMoveResult = moveResult
        val moveCallback = CompletableDeferred<DataState<StockBatchMoveResultDataModel>>()
        moveStockBatchBetweenStores(
            StockBatchMoveRequestDataModel(
                sourceStoreId = AITA_FLOW_SOURCE_STORE_ID,
                destinationStoreId = AITA_FLOW_DESTINATION_STORE_ID,
                sourceGoodsItemId = sourceItem.id,
                sourceBatchId = sourceBatch.id,
                quantity = aitaTestQuantity(4.0),
                note = "Send to branch"
            )
        ) { moveCallback.complete(it) }

        assertEquals(moveResult, requireAitaFlowSuccess(moveCallback).payload)
        waitUntilAitaFlowCondition { stockBatchMoveResultState.payloadValue?.movement?.id == "movement-pending" }
        assertEquals(moveResult.availability, stockItemBranchAvailabilityState.payloadValue)
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "stockBatches/move" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val decisionResult = aitaTestMoveResult(
            sourceItem = sourceItem,
            destinationItem = destinationItem,
            sourceBatch = sourceBatch.copy(quantity = aitaTestQuantity(5.0)),
            destinationBatch = destinationBatch.copy(quantity = aitaTestQuantity(5.0)),
            status = StockBatchMovementStatusDataModel.Accepted,
            movementId = "movement-decision"
        )
        environment.nextDecisionResult = decisionResult
        val decisionCallback = CompletableDeferred<DataState<StockBatchMoveResultDataModel>>()
        decideStockBatchMove(
            StockBatchMoveDecisionRequestDataModel(
                movementId = "movement-pending",
                accept = true,
                note = "Accepted by branch"
            )
        ) { decisionCallback.complete(it) }

        assertEquals(decisionResult, requireAitaFlowSuccess(decisionCallback).payload)
        waitUntilAitaFlowCondition { stockBatchMoveResultState.payloadValue?.movement?.id == "movement-decision" }
        assertEquals(decisionResult.availability, stockItemBranchAvailabilityState.payloadValue)
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "stockBatches/decideMove" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun persistsStockBatchAndCartSnapshotsLocally() = runBlocking {
        val item = aitaTestGoodsItem(id = "cached-item", name = "Cached oats")
        val batch = aitaTestBatch(id = "cached-batch", goodsItemId = item.id, quantityTotal = 5.0)
        val stockCacheKey = "cache_json:stock:$AITA_FLOW_SOURCE_STORE_ID"
        val batchesCacheKey = "cache_json:stock_batches:$AITA_FLOW_SOURCE_STORE_ID"

        putLocalKv(KEY_ACTIVE_STORE_ID, AITA_FLOW_SOURCE_STORE_ID)
        putLocalKv(stockCacheKey, jsonBase.encodeToString(listOf(item)))
        putLocalKv(batchesCacheKey, jsonBase.encodeToString(listOf(batch)))

        assertEquals(AITA_FLOW_SOURCE_STORE_ID, getLocalKv(KEY_ACTIVE_STORE_ID))
        val restoredStock = jsonBase.decodeFromString<List<GoodsItemDataModel>>(assertNotNull(getLocalKv(stockCacheKey)))
        val restoredBatches = jsonBase.decodeFromString<List<GoodsBatchDataModel>>(assertNotNull(getLocalKv(batchesCacheKey)))

        assertEquals(listOf(item), restoredStock)
        assertEquals(listOf(batch), restoredBatches)

        addGoodsItemToTransactionCart(
            goodsItem = item,
            transactionTypeIndex = 0,
            clientId = 0,
            configuration = globalAppConfigurationState.payloadValue,
            currentCart = emptyList(),
            quantityToAdd = aitaTestQuantity(2.0)
        )

        waitUntilAitaFlowCondition { currentAitaFlowCart(0, 0).singleOrNull()?.id == item.id }
        assertEquals(2.0, currentAitaFlowCart(0, 0).single().quantity.total)
    }

    @Test
    fun loadsTransactionHistoryFromServer() = runBlocking {
        val sale = aitaTestTransaction(id = "history-sale", type = transactionServerType(0))
        val returned = aitaTestTransaction(id = "history-return", type = transactionServerType(1), paidCash = 200.0)
        environment.transactions = listOf(sale, returned)

        getTransactions(AITA_FLOW_SOURCE_STORE_ID)

        waitUntilAitaFlowCondition { transactionsState.payloadValue?.map { it.id } == listOf("history-sale", "history-return") }
        assertEquals(listOf(sale, returned), transactionsState.payloadValue)
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "transactions/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun completesSaleTransactionWithDebtorAndClearsCartAndPaymentDraft() = runBlocking {
        activeStoreIdState.emit(null)
        val item = aitaTestGoodsItem(id = "sale-cart-item", name = "Cart cookie", activeShelfBatchId = "sale-cart-batch")
        val debtor = aitaTestDebtor(id = "debtor-from-sale", debtAmount = 400.0)
        val completed = aitaTestTransaction(
            id = "completed-sale-with-debtor",
            type = transactionServerType(0),
            goodsItem = item,
            debtor = debtor,
            paidCash = 600.0,
            paidCard = 0.0,
            quantity = 2.0,
            pricePerUnit = 500.0
        )
        environment.nextCompletedTransaction = completed

        addGoodsItemToTransactionCart(item, transactionTypeIndex = 0, clientId = 0, configuration = globalAppConfigurationState.payloadValue, currentCart = emptyList())
        waitUntilAitaFlowCondition { currentAitaFlowCart(0, 0).isNotEmpty() }
        val draft = aitaTestPaymentDraft(transactionTypeIndex = 0, clientId = 0, paidCash = 600.0, debtor = debtor)
        setTransactionPaymentDraft(draft)
        waitUntilAitaFlowCondition { getTransactionPaymentDraft(0, 0)?.debtor?.id == debtor.id }

        val callback = CompletableDeferred<Unit>()
        completeTransaction(
            transaction = completed.copy(id = "draft-sale-with-debtor"),
            transactionTypeIndex = 0,
            clientId = 0,
            receiptSnapshot = aitaTestReceiptSnapshot(completed, draft),
            onCompleted = { callback.complete(Unit) }
        )

        withTimeout(8_000L) { callback.await() }
        waitUntilAitaFlowCondition { transactionsState.payloadValue?.any { it.id == completed.id } == true }
        waitUntilAitaFlowCondition { debtorsState.payloadValue?.any { it.id == debtor.id } == true }
        waitUntilAitaFlowCondition { currentAitaFlowCart(0, 0).isEmpty() && getTransactionPaymentDraft(0, 0) == null }

        assertEquals(completed.id, latestTransactionReceiptSnapshotState.value?.transaction?.id)
        assertEquals(debtor, debtorsState.payloadValue?.first { it.id == debtor.id })
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "transactions/complete" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun completesReturnAndSupplyTransactionTypesToHistory() = runBlocking {
        activeStoreIdState.emit(null)
        val returnTransaction = aitaTestTransaction(id = "completed-return", type = transactionServerType(1), paidCash = 250.0)
        val supplyTransaction = aitaTestTransaction(id = "completed-supply", type = transactionServerType(2), paidCash = 0.0, paidCard = 1500.0)

        for ((transactionTypeIndex, completed) in listOf(1 to returnTransaction, 2 to supplyTransaction)) {
            environment.nextCompletedTransaction = completed
            val draft = aitaTestPaymentDraft(transactionTypeIndex = transactionTypeIndex, clientId = 0, paidCash = completed.paidCash, paidCard = completed.paidCard)
            val callback = CompletableDeferred<Unit>()
            completeTransaction(
                transaction = completed.copy(id = "draft-${completed.id}"),
                transactionTypeIndex = transactionTypeIndex,
                clientId = 0,
                receiptSnapshot = aitaTestReceiptSnapshot(completed, draft),
                onCompleted = { callback.complete(Unit) }
            )
            withTimeout(8_000L) { callback.await() }
            waitUntilAitaFlowCondition { transactionsState.payloadValue?.any { it.id == completed.id && it.type == completed.type } == true }
        }

        val ids = transactionsState.payloadValue.orEmpty().map { it.id }.toSet()
        assertTrue("completed-return" in ids)
        assertTrue("completed-supply" in ids)
        assertTrue(environment.requests.count { it.method == "POST" && it.path == "transactions/complete" } >= 2)
    }

    @Test
    fun queuesTransactionLocallyWhenCloudCompletionFails() = runBlocking {
        activeStoreIdState.emit(null)
        val item = aitaTestGoodsItem(id = "offline-item", name = "Offline tea", activeShelfBatchId = "offline-batch")
        val batch = aitaTestBatch(id = "offline-batch", goodsItemId = item.id, quantityTotal = 10.0)
        val transaction = aitaTestTransaction(id = "", type = transactionServerType(0), goodsItem = item, quantity = 3.0, pricePerUnit = 100.0, paidCash = 300.0)
        stockState.emit(DataState.Success(listOf(item)))
        stockBatchesState.emit(DataState.Success(listOf(batch)))
        environment.forceNextCompleteTransactionFailure = true

        val callback = CompletableDeferred<Unit>()
        completeTransaction(
            transaction = transaction,
            transactionTypeIndex = 0,
            clientId = 1,
            receiptSnapshot = aitaTestReceiptSnapshot(transaction, aitaTestPaymentDraft(0, 1, paidCash = 300.0)),
            onCompleted = { callback.complete(Unit) }
        )

        withTimeout(8_000L) { callback.await() }
        waitUntilAitaFlowCondition { localNetworkQueuedOperationsState.value.any { it.operationType == LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE } }
        waitUntilAitaFlowCondition { transactionsState.payloadValue?.any { it.clientOperationId.isNotBlank() && it.id.startsWith("local_") } == true }
        waitUntilAitaFlowCondition { stockBatchesState.payloadValue?.firstOrNull { it.id == batch.id }?.quantity?.total == 7.0 }

        assertEquals(1, localNetworkQueuedOperationsState.value.count { it.operationType == LOCAL_NETWORK_OPERATION_TRANSACTION_COMPLETE })
        assertEquals(7.0, stockBatchesState.payloadValue?.first { it.id == batch.id }?.quantity?.total)
    }

    @Test
    fun debtorCrudAndDebtPaymentStayInSyncWithServerResponses() = runBlocking {
        val added = aitaTestDebtor(id = "debtor-add", debtAmount = 1200.0, firstName = "Dana")
        environment.nextDebtorResponse = added
        val addCallback = CompletableDeferred<DataState<DebtorDataModel>>()
        addDebtor(AITA_FLOW_SOURCE_STORE_ID, added) { addCallback.complete(it) }
        assertEquals(added, requireAitaFlowSuccess(addCallback).payload)
        waitUntilAitaFlowCondition { debtorsState.payloadValue?.singleOrNull()?.id == added.id }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "debtors/add" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val updated = added.copy(lastName = "Updated", debtAmount = 1500.0)
        environment.nextDebtorResponse = updated
        val updateCallback = CompletableDeferred<DataState<DebtorDataModel>>()
        updateDebtor(AITA_FLOW_SOURCE_STORE_ID, updated) { updateCallback.complete(it) }
        assertEquals(updated, requireAitaFlowSuccess(updateCallback).payload)
        waitUntilAitaFlowCondition { debtorsState.payloadValue?.firstOrNull { it.id == updated.id }?.debtAmount == 1500.0 }
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "debtors/update" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val paid = updated.copy(debtAmount = 700.0)
        environment.nextDebtorResponse = paid
        val payCallback = CompletableDeferred<DataState<DebtorDataModel>>()
        payDebtorDebt(
            DebtPaymentRequestDataModel(
                debtorId = paid.id,
                storeId = AITA_FLOW_SOURCE_STORE_ID,
                amount = 800.0,
                currency = "KZT",
                paymentKind = "partial",
                note = "Test payment"
            )
        ) { payCallback.complete(it) }
        assertEquals(paid, requireAitaFlowSuccess(payCallback).payload)
        waitUntilAitaFlowCondition { debtorsState.payloadValue?.firstOrNull { it.id == paid.id }?.debtAmount == 700.0 }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "debtors/pay" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        environment.nextDeletedDebtorId = paid.id
        val deleteCallback = CompletableDeferred<DataState<String>>()
        deleteDebtor(AITA_FLOW_SOURCE_STORE_ID, paid.id) { deleteCallback.complete(it) }
        assertEquals(paid.id, requireAitaFlowSuccess(deleteCallback).payload)
        waitUntilAitaFlowCondition { debtorsState.payloadValue?.none { it.id == paid.id } == true }
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "debtors/delete" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun loadsStoresBranchesWorkerMembershipsAndEmploymentRequestsFromServer() = runBlocking {
        val branch = aitaTestStore(
            id = AITA_FLOW_DESTINATION_STORE_ID,
            publicId = "BRANCH-001",
            parentStoreId = AITA_FLOW_SOURCE_STORE_ID,
            name = "Downtown demo branch"
        )
        val rootStore = aitaTestStore(
            id = AITA_FLOW_SOURCE_STORE_ID,
            publicId = "STORE-001",
            name = "Aita launch market",
            branches = listOf(branch)
        )
        val storeWorker = aitaTestWorkerMembership(id = "worker-store-list", storeId = AITA_FLOW_SOURCE_STORE_ID)
        val myWorker = aitaTestWorkerMembership(
            id = "worker-my-list",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            userId = AITA_FLOW_TEST_USER_ID,
            userPublicId = "AITA-TEST-USER",
            firstName = "Aita",
            lastName = "Tester"
        )
        val incomingRequest = aitaTestWorkerRequest(
            id = "incoming-worker-request",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            direction = WORKER_REQUEST_DIRECTION_USER_TO_STORE,
            status = WORKER_REQUEST_STATUS_PENDING
        )
        val myInvite = aitaTestWorkerRequest(
            id = "my-worker-invite",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            direction = WORKER_REQUEST_DIRECTION_STORE_TO_USER,
            status = WORKER_REQUEST_STATUS_INVITED,
            requesterUserId = AITA_FLOW_TEST_USER_ID,
            requesterPublicId = "AITA-TEST-USER"
        )
        environment.stores = listOf(rootStore)
        environment.workerMemberships = listOf(storeWorker)
        environment.myWorkerMemberships = listOf(myWorker)
        environment.incomingWorkerRequests = listOf(incomingRequest)
        environment.myWorkerRequests = listOf(myInvite)
        environment.requests.clear()

        getStores()
        getStoreWorkers(AITA_FLOW_SOURCE_STORE_ID)
        getMyWorkerMemberships()
        getIncomingWorkerRequests(AITA_FLOW_SOURCE_STORE_ID)
        getMyWorkerRequests()

        waitUntilAitaFlowCondition {
            storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(AITA_FLOW_DESTINATION_STORE_ID)?.parentStoreId == AITA_FLOW_SOURCE_STORE_ID &&
                    storeWorkerMembershipsState.payloadValue?.singleOrNull()?.id == storeWorker.id &&
                    myWorkerMembershipsState.payloadValue?.singleOrNull()?.id == myWorker.id &&
                    incomingWorkerRequestsState.payloadValue?.singleOrNull()?.id == incomingRequest.id &&
                    myWorkerRequestsState.payloadValue?.singleOrNull()?.id == myInvite.id
        }

        assertEquals(rootStore.id, storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(AITA_FLOW_SOURCE_STORE_ID)?.id)
        assertEquals(branch.id, storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(AITA_FLOW_DESTINATION_STORE_ID)?.id)
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "stores/get" })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "workers/store/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "workers/my/get" })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "workers/requests/incoming" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "workers/requests/my" })
    }

    @Test
    fun addsSwitchesUpdatesAndDeletesStoresAndBranchesCleanly() = runBlocking {
        val rootStore = aitaTestStore(id = "store-add-flow", publicId = "STORE-ADD", name = "Sunrise vegan market")
        environment.nextStoreResponse = rootStore
        environment.requests.clear()
        val addRootCallback = CompletableDeferred<DataState<StoreDataModel>>()

        addStore(rootStore) { addRootCallback.complete(it) }

        assertEquals(rootStore.id, requireAitaFlowSuccess(addRootCallback).payload.id)
        waitUntilAitaFlowCondition { storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(rootStore.id)?.publicId == rootStore.publicId }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "stores/add" })

        setActiveStoreId(rootStore.id)
        waitUntilAitaFlowCondition { activeStoreIdState.value == rootStore.id && getLocalKv(KEY_ACTIVE_STORE_ID) == rootStore.id }
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "stores/active" })

        val branch = aitaTestStore(
            id = "branch-add-flow",
            publicId = "BRANCH-ADD",
            parentStoreId = rootStore.id,
            name = "Airport branch"
        )
        environment.nextStoreResponse = branch
        val addBranchCallback = CompletableDeferred<DataState<StoreDataModel>>()
        addStore(branch) { addBranchCallback.complete(it) }

        assertEquals(branch.id, requireAitaFlowSuccess(addBranchCallback).payload.id)
        waitUntilAitaFlowCondition { storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(branch.id)?.parentStoreId == rootStore.id }

        val updatedRoot = rootStore.copy(
            name = aitaTestLocalized("Sunrise vegan market updated"),
            branches = listOf(branch)
        )
        environment.nextStoreResponse = updatedRoot
        val updateCallback = CompletableDeferred<DataState<StoreDataModel>>()
        updateStore(updatedRoot) { updateCallback.complete(it) }

        assertEquals(updatedRoot.id, requireAitaFlowSuccess(updateCallback).payload.id)
        waitUntilAitaFlowCondition { storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(rootStore.id)?.name == updatedRoot.name }
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "stores/update" })

        environment.nextDeletedStoreId = branch.id
        val deleteBranchCallback = CompletableDeferred<DataState<Unit>>()
        deleteStore(branch) { deleteBranchCallback.complete(it) }

        requireAitaFlowSuccess(deleteBranchCallback)
        waitUntilAitaFlowCondition { storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(branch.id) == null }
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "stores/delete" })

        environment.nextDeletedStoreId = rootStore.id
        val deleteRootCallback = CompletableDeferred<DataState<Unit>>()
        deleteStore(updatedRoot.copy(branches = emptyList())) { deleteRootCallback.complete(it) }

        requireAitaFlowSuccess(deleteRootCallback)
        waitUntilAitaFlowCondition { storesState.payloadValue.orEmpty().findAitaTestStoreOrBranch(rootStore.id) == null && activeStoreIdState.value == null }
    }

    @Test
    fun workerEmploymentInvitesPermissionsRemovalAndWorkshiftsStayInSync() = runBlocking {
        val rootStore = aitaTestStore(id = AITA_FLOW_SOURCE_STORE_ID, publicId = "STORE-WORK", name = "Worker test market")
        environment.stores = listOf(rootStore)
        storesState.emit(DataState.Success(listOf(rootStore)))
        userAccountState.emit(DataState.Success(aitaTestUserAccount()))
        activeStoreIdState.emit(AITA_FLOW_SOURCE_STORE_ID)
        environment.requests.clear()

        val employmentRequest = aitaTestWorkerRequest(
            id = "employment-request-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            direction = WORKER_REQUEST_DIRECTION_USER_TO_STORE,
            status = WORKER_REQUEST_STATUS_PENDING
        )
        environment.nextWorkerRequestResponse = employmentRequest
        val requestCallback = CompletableDeferred<DataState<StoreWorkerRequestDataModel>>()
        requestStoreEmployment(AITA_FLOW_SOURCE_STORE_ID, "I can help with launch testing") { requestCallback.complete(it) }

        assertEquals(employmentRequest.id, requireAitaFlowSuccess(requestCallback).payload.id)
        waitUntilAitaFlowCondition { myWorkerRequestsState.payloadValue?.any { it.id == employmentRequest.id } == true }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/request" })

        val acceptedWorker = aitaTestWorkerMembership(
            id = "accepted-worker-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            userId = AITA_FLOW_WORKER_USER_ID,
            userPublicId = AITA_FLOW_WORKER_PUBLIC_ID,
            firstName = "Dauren",
            lastName = "Worker"
        )
        environment.incomingWorkerRequests = listOf(employmentRequest)
        incomingWorkerRequestsState.emit(DataState.Success(listOf(employmentRequest)))
        environment.nextWorkerMembershipResponse = acceptedWorker
        val acceptEmploymentCallback = CompletableDeferred<DataState<StoreWorkerDataModel>>()
        acceptStoreEmploymentRequest(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            requestId = employmentRequest.id,
            roleId = WORKER_ROLE_STANDARD,
            permissions = STANDARD_STORE_PERMISSION_IDS,
            workerPassword = "WorkerPass123!"
        ) { acceptEmploymentCallback.complete(it) }

        assertEquals(acceptedWorker.id, requireAitaFlowSuccess(acceptEmploymentCallback).payload.id)
        waitUntilAitaFlowCondition { storeWorkerMembershipsState.payloadValue?.any { it.id == acceptedWorker.id } == true }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/accept" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val declinedEmployment = aitaTestWorkerRequest(
            id = "declined-employment-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            direction = WORKER_REQUEST_DIRECTION_USER_TO_STORE,
            status = WORKER_REQUEST_STATUS_DECLINED,
            requesterUserId = "declined-user",
            requesterPublicId = "DECLINED-USER"
        )
        environment.incomingWorkerRequests = environment.incomingWorkerRequests.upsertAitaTestWorkerRequest(declinedEmployment.copy(status = WORKER_REQUEST_STATUS_PENDING))
        environment.nextWorkerRequestResponse = declinedEmployment
        val declineEmploymentCallback = CompletableDeferred<DataState<StoreWorkerRequestDataModel>>()
        declineStoreEmploymentRequest(AITA_FLOW_SOURCE_STORE_ID, declinedEmployment.id, "Not now") { declineEmploymentCallback.complete(it) }

        assertEquals(WORKER_REQUEST_STATUS_DECLINED, requireAitaFlowSuccess(declineEmploymentCallback).payload.status)
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/decline" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val invite = aitaTestWorkerRequest(
            id = "store-invite-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            direction = WORKER_REQUEST_DIRECTION_STORE_TO_USER,
            status = WORKER_REQUEST_STATUS_INVITED,
            requesterUserId = AITA_FLOW_WORKER_USER_ID,
            requesterPublicId = AITA_FLOW_WORKER_PUBLIC_ID
        )
        environment.nextWorkerRequestResponse = invite
        val inviteCallback = CompletableDeferred<DataState<StoreWorkerRequestDataModel>>()
        inviteStoreWorker(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            userId = AITA_FLOW_WORKER_PUBLIC_ID,
            roleId = WORKER_ROLE_STANDARD,
            permissions = STANDARD_STORE_PERMISSION_IDS + STORE_PERMISSION_STOCK_WRITE,
            note = "Launch helper",
            workerPassword = "WorkerPass123!"
        ) { inviteCallback.complete(it) }

        assertEquals(invite.id, requireAitaFlowSuccess(inviteCallback).payload.id)
        waitUntilAitaFlowCondition { incomingWorkerRequestsState.payloadValue?.any { it.id == invite.id } == true }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/invite" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val invitedMembership = aitaTestWorkerMembership(
            id = "my-invited-membership-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            userId = AITA_FLOW_TEST_USER_ID,
            userPublicId = "AITA-TEST-USER",
            firstName = "Aita",
            lastName = "Tester",
            hasWorkshiftPassword = false
        )
        environment.myWorkerRequests = listOf(invite)
        environment.nextWorkerMembershipResponse = invitedMembership
        val acceptInviteCallback = CompletableDeferred<DataState<StoreWorkerDataModel>>()
        acceptMyStoreWorkerInvitation(invite.id, "Accepted for testing") { acceptInviteCallback.complete(it) }

        assertEquals(invitedMembership.id, requireAitaFlowSuccess(acceptInviteCallback).payload.id)
        waitUntilAitaFlowCondition { myWorkerMembershipsState.payloadValue?.any { it.id == invitedMembership.id } == true }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/invitations/accept" })

        val declinedInvite = aitaTestWorkerRequest(
            id = "declined-invite-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            direction = WORKER_REQUEST_DIRECTION_STORE_TO_USER,
            status = WORKER_REQUEST_STATUS_DECLINED,
            requesterUserId = AITA_FLOW_TEST_USER_ID,
            requesterPublicId = "AITA-TEST-USER"
        )
        environment.nextWorkerRequestResponse = declinedInvite
        val declineInviteCallback = CompletableDeferred<DataState<StoreWorkerRequestDataModel>>()
        declineMyStoreWorkerInvitation(declinedInvite.id, "Busy") { declineInviteCallback.complete(it) }

        assertEquals(WORKER_REQUEST_STATUS_DECLINED, requireAitaFlowSuccess(declineInviteCallback).payload.status)
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/invitations/decline" })

        val updatedWorker = acceptedWorker.copy(
            roleId = WORKER_ROLE_ADMIN,
            permissions = ALL_STORE_PERMISSION_IDS,
            hasWorkshiftPassword = true
        )
        environment.nextWorkerMembershipResponse = updatedWorker
        val permissionsCallback = CompletableDeferred<DataState<StoreWorkerDataModel>>()
        updateStoreWorkerPermissions(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            workerId = acceptedWorker.id,
            roleId = WORKER_ROLE_ADMIN,
            permissions = ALL_STORE_PERMISSION_IDS,
            workerPassword = "WorkerPass123!"
        ) { permissionsCallback.complete(it) }

        assertEquals(WORKER_ROLE_ADMIN, requireAitaFlowSuccess(permissionsCallback).payload.roleId)
        waitUntilAitaFlowCondition { storeWorkerMembershipsState.payloadValue?.firstOrNull { it.id == acceptedWorker.id }?.permissions == ALL_STORE_PERMISSION_IDS }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/updatePermissions" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val passwordWorker = invitedMembership.copy(hasWorkshiftPassword = true)
        environment.nextWorkerMembershipResponse = passwordWorker
        val passwordCallback = CompletableDeferred<DataState<StoreWorkerDataModel>>()
        updateMyWorkerPassword(passwordWorker.id, "WorkerPass123!", "AccountPass123!") { passwordCallback.complete(it) }

        assertEquals(true, requireAitaFlowSuccess(passwordCallback).payload.hasWorkshiftPassword)
        waitUntilAitaFlowCondition { myWorkerMembershipsState.payloadValue?.firstOrNull { it.id == passwordWorker.id }?.hasWorkshiftPassword == true }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/my/password" })

        val startedShift = aitaTestWorkshift(
            id = "workshift-started-flow",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            workerMembershipId = passwordWorker.id,
            workerUserId = passwordWorker.userId,
            workerPublicId = passwordWorker.userPublicId,
            isActive = true
        )
        environment.nextWorkshiftResponse = startedShift
        val startShiftCallback = CompletableDeferred<DataState<WorkshiftDataModel>>()
        startWorkshift(AITA_FLOW_SOURCE_STORE_ID, passwordWorker.userPublicId, "WorkerPass123!") { startShiftCallback.complete(it) }

        assertEquals(startedShift.id, requireAitaFlowSuccess(startShiftCallback).payload.id)
        waitUntilAitaFlowCondition { activeWorkshiftState.payloadValue?.id == startedShift.id }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workshifts/start" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val endedShift = startedShift.copy(endedAtMillis = 1_720_000_000_000L, endedByUserId = AITA_FLOW_TEST_USER_ID, isActive = false)
        environment.nextWorkshiftResponse = endedShift
        val endShiftCallback = CompletableDeferred<DataState<WorkshiftDataModel>>()
        endCurrentWorkshift(AITA_FLOW_SOURCE_STORE_ID) { endShiftCallback.complete(it) }

        assertEquals(endedShift.id, requireAitaFlowSuccess(endShiftCallback).payload.id)
        waitUntilAitaFlowCondition { activeWorkshiftState.payloadValue == null }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workshifts/end" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        environment.nextRemovedWorkerResponse = updatedWorker.copy(isActive = false)
        val removeWorkerCallback = CompletableDeferred<DataState<StoreWorkerDataModel>>()
        removeStoreWorker(AITA_FLOW_SOURCE_STORE_ID, updatedWorker.id, "Cleanup after test") { removeWorkerCallback.complete(it) }

        assertEquals(updatedWorker.id, requireAitaFlowSuccess(removeWorkerCallback).payload.id)
        waitUntilAitaFlowCondition { storeWorkerMembershipsState.payloadValue.orEmpty().none { it.id == updatedWorker.id } }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "workers/remove" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })
    }

    @Test
    fun menuAnalyticsCalculatesTotalsBucketsAndFiltersFromCurrentData() = runBlocking {
        val fixture = aitaAnalyticsFixture()

        val dashboard = buildStoreAnalyticsDashboard(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            startMillis = fixture.startMillis,
            endMillisExclusive = fixture.endMillis,
            transactions = fixture.transactions,
            stock = fixture.stock,
            batches = fixture.batches,
            fallbackCurrencyCode = "KZT"
        )

        assertEquals(AITA_FLOW_SOURCE_STORE_ID, dashboard.storeId)
        assertEquals("KZT", dashboard.currencyCode)
        assertEquals(2, dashboard.saleCount)
        assertEquals(1, dashboard.returnCount)
        assertEquals(1, dashboard.supplyCount)
        assertEquals(4, dashboard.transactionCount)
        assertAitaDoubleEquals(4_400.0, dashboard.grossSales)
        assertAitaDoubleEquals(1_000.0, dashboard.returnsAmount)
        assertAitaDoubleEquals(3_000.0, dashboard.supplyCost)
        assertAitaDoubleEquals(3_400.0, dashboard.netRevenue)
        assertAitaDoubleEquals(2_100.0, dashboard.estimatedSalesCost)
        assertAitaDoubleEquals(1_300.0, dashboard.estimatedGrossProfit)
        assertAitaDoubleEquals(38.235, dashboard.estimatedMarginPercent, tolerance = 0.002)
        assertAitaDoubleEquals(2_200.0, dashboard.averageSale)
        assertAitaDoubleEquals(2.5, dashboard.averageItemsPerSale)
        assertAitaDoubleEquals(1_000.0, dashboard.cashTotal)
        assertAitaDoubleEquals(2_900.0, dashboard.cashlessTotal)
        assertAitaDoubleEquals(500.0, dashboard.debtTotal)
        assertAitaDoubleEquals(22.727, dashboard.cashSharePercent, tolerance = 0.002)
        assertAitaDoubleEquals(65.909, dashboard.cashlessSharePercent, tolerance = 0.002)
        assertAitaDoubleEquals(11.364, dashboard.debtSharePercent, tolerance = 0.002)
        assertAitaDoubleEquals(5.0, dashboard.soldQuantity)
        assertAitaDoubleEquals(1.0, dashboard.returnedQuantity)
        assertAitaDoubleEquals(5.0, dashboard.suppliedQuantity)
        assertAitaDoubleEquals(6_600.0, dashboard.stockValueAtSupplyPrice)
        assertAitaDoubleEquals(11_600.0, dashboard.stockValueAtSalePrice)
        assertAitaDoubleEquals(12.0, dashboard.activeStockQuantity)
        assertEquals(1, dashboard.lowStockItemCount)
        assertEquals(0, dashboard.outOfStockItemCount)
        assertEquals(0, dashboard.expiredBatchCount)
        assertEquals(0, dashboard.expiringSoonBatchCount)
        assertAitaDoubleEquals(29.412, dashboard.sellThroughPercentEstimate, tolerance = 0.002)
        assertEquals(fixture.banana.id, dashboard.topItemsByRevenue.first().id)
        assertAitaDoubleEquals(2_400.0, dashboard.topItemsByRevenue.first().amount)
        assertEquals(fixture.banana.id, dashboard.topItemsByQuantity.first().id)
        assertAitaDoubleEquals(3.0, dashboard.topItemsByQuantity.first().quantity)
        assertEquals(1, dashboard.salesByDay.size)
        assertAitaDoubleEquals(4_400.0, dashboard.salesByDay.single().amount)
        assertAitaDoubleEquals(500.0, dashboard.salesByDay.single().debt)
        assertTrue(dashboard.salesByHour.isNotEmpty())

        val appleDashboard = buildStoreAnalyticsDashboard(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            startMillis = fixture.startMillis,
            endMillisExclusive = fixture.endMillis,
            transactions = fixture.transactions,
            stock = fixture.stock,
            batches = fixture.batches,
            fallbackCurrencyCode = "KZT",
            goodsItemIdFilter = fixture.apple.id
        )
        assertEquals(fixture.apple.id, appleDashboard.goodsItemIdFilter)
        assertEquals(1, appleDashboard.saleCount)
        assertEquals(1, appleDashboard.returnCount)
        assertEquals(1, appleDashboard.supplyCount)
        assertAitaDoubleEquals(2_000.0, appleDashboard.grossSales)
        assertAitaDoubleEquals(1_000.0, appleDashboard.returnsAmount)
        assertAitaDoubleEquals(3_000.0, appleDashboard.supplyCost)
        assertAitaDoubleEquals(10.0, appleDashboard.activeStockQuantity)
        assertEquals(fixture.apple.id, appleDashboard.topItemsByRevenue.single().id)

        val supplierDashboard = buildStoreAnalyticsDashboard(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            startMillis = fixture.startMillis,
            endMillisExclusive = fixture.endMillis,
            transactions = fixture.transactions,
            stock = fixture.stock,
            batches = fixture.batches,
            fallbackCurrencyCode = "KZT",
            supplierIdFilter = fixture.supplierAlphaId
        )
        assertEquals(fixture.supplierAlphaId, supplierDashboard.supplierIdFilter)
        assertAitaDoubleEquals(2_000.0, supplierDashboard.grossSales)
        assertAitaDoubleEquals(10.0, supplierDashboard.activeStockQuantity)
        assertEquals(fixture.apple.id, supplierDashboard.topItemsByQuantity.single().id)

        val categoryDashboard = buildStoreAnalyticsDashboard(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            startMillis = fixture.startMillis,
            endMillisExclusive = fixture.endMillis,
            transactions = fixture.transactions,
            stock = fixture.stock,
            batches = fixture.batches,
            fallbackCurrencyCode = "KZT",
            categoryIdFilter = fixture.categoryFruitId
        )
        assertEquals(fixture.categoryFruitId, categoryDashboard.categoryIdFilter)
        assertAitaDoubleEquals(2_000.0, categoryDashboard.grossSales)
        assertEquals(fixture.apple.id, categoryDashboard.topItemsByRevenue.single().id)

        val shortWindowDashboard = buildStoreAnalyticsDashboard(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            startMillis = fixture.startMillis,
            endMillisExclusive = fixture.startMillis + 2_000L,
            transactions = fixture.transactions,
            stock = fixture.stock,
            batches = fixture.batches,
            fallbackCurrencyCode = "KZT"
        )
        assertEquals(1, shortWindowDashboard.saleCount)
        assertEquals(1, shortWindowDashboard.transactionCount)
        assertAitaDoubleEquals(2_000.0, shortWindowDashboard.grossSales)
        assertEquals(fixture.apple.id, shortWindowDashboard.topItemsByRevenue.single().id)

        environment.stock = fixture.stock
        environment.batches = fixture.batches
        environment.transactions = fixture.transactions
        environment.requests.clear()
        val callback = CompletableDeferred<DataState<StoreAnalyticsDashboardDataModel>>()
        getStoreAnalytics(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            startMillis = fixture.startMillis,
            endMillisExclusive = fixture.endMillis,
            supplierIdFilter = fixture.supplierAlphaId
        ) { callback.complete(it) }

        val serverLikeDashboard = requireAitaFlowSuccess(callback).payload
        waitUntilAitaFlowCondition { storeAnalyticsDashboardState.payloadValue?.supplierIdFilter == fixture.supplierAlphaId }
        assertAitaDoubleEquals(supplierDashboard.grossSales, serverLikeDashboard.grossSales)
        assertAitaDoubleEquals(supplierDashboard.activeStockQuantity, serverLikeDashboard.activeStockQuantity)
        assertTrue(environment.requests.any {
            it.method == "GET" &&
                    it.path == "analytics/store/get" &&
                    it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID &&
                    it.queryParameters["supplierId"] == listOf(fixture.supplierAlphaId)
        })
    }

    @Test
    fun menuOperationLogsLoadByScopeAndRecordSupplierMenuActions() = runBlocking {
        val branch = aitaTestStore(
            id = AITA_FLOW_DESTINATION_STORE_ID,
            publicId = "BRANCH-LOG",
            parentStoreId = AITA_FLOW_SOURCE_STORE_ID,
            name = "Logs branch"
        )
        val root = aitaTestStore(
            id = AITA_FLOW_SOURCE_STORE_ID,
            publicId = "ROOT-LOG",
            name = "Logs root",
            branches = listOf(branch)
        )
        val rootLog = aitaTestOperationLog(
            id = "log-root-stock",
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            action = "stock_add",
            entityType = "goods_item",
            entityId = "logged-goods",
            title = "Stock item added",
            createdAtMillis = 1_710_000_010_000L
        )
        val branchLog = aitaTestOperationLog(
            id = "log-branch-sale",
            storeId = AITA_FLOW_DESTINATION_STORE_ID,
            rootStoreId = AITA_FLOW_SOURCE_STORE_ID,
            action = "transaction_complete",
            entityType = "transaction",
            entityId = "logged-transaction",
            title = "Sale completed",
            createdAtMillis = 1_710_000_020_000L
        )
        environment.stores = listOf(root)
        environment.operationLogs = listOf(rootLog, branchLog)
        storesState.emit(DataState.Success(listOf(root)))
        operationLogsState.emit(DataState.Empty())
        environment.requests.clear()

        val currentCallback = CompletableDeferred<DataState<List<OperationLogDataModel>>>()
        getOperationLogs(AITA_FLOW_SOURCE_STORE_ID, OPERATION_LOG_SCOPE_CURRENT) { currentCallback.complete(it) }

        val currentLogs = requireAitaFlowSuccess(currentCallback).payload
        assertEquals(listOf(rootLog.id), currentLogs.map { it.id })
        assertEquals(rootLog.id, operationLogsState.payloadValue?.singleOrNull()?.id)
        assertTrue(environment.requests.any {
            it.method == "GET" &&
                    it.path == "logs/get" &&
                    it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID &&
                    it.queryParameters["scope"] == listOf(OPERATION_LOG_SCOPE_CURRENT)
        })

        val rootScopeCallback = CompletableDeferred<DataState<List<OperationLogDataModel>>>()
        getOperationLogs(AITA_FLOW_DESTINATION_STORE_ID, OPERATION_LOG_SCOPE_ROOT) { rootScopeCallback.complete(it) }

        val rootScopeLogs = requireAitaFlowSuccess(rootScopeCallback).payload
        assertEquals(listOf(branchLog.id, rootLog.id), rootScopeLogs.map { it.id })
        assertTrue(environment.requests.any {
            it.method == "GET" &&
                    it.path == "logs/get" &&
                    it.storeIdHeader == AITA_FLOW_DESTINATION_STORE_ID &&
                    it.queryParameters["scope"] == listOf(OPERATION_LOG_SCOPE_ROOT)
        })

        val supplier = aitaTestSupplier(id = "logged-supplier", name = "Logged supplier")
        environment.nextSupplierResponse = supplier
        val addSupplierCallback = CompletableDeferred<DataState<SupplierDataModel>>()
        addSupplier(supplier) { addSupplierCallback.complete(it) }

        assertEquals(supplier.id, requireAitaFlowSuccess(addSupplierCallback).payload.id)
        waitUntilAitaFlowCondition { environment.operationLogs.any { it.action == "supplier_add" && it.entityId == supplier.id } }

        val afterActionCallback = CompletableDeferred<DataState<List<OperationLogDataModel>>>()
        getOperationLogs(AITA_FLOW_SOURCE_STORE_ID, OPERATION_LOG_SCOPE_ROOT) { afterActionCallback.complete(it) }
        val afterActionLogs = requireAitaFlowSuccess(afterActionCallback).payload

        assertTrue(afterActionLogs.any { it.action == "supplier_add" && it.entityType == "supplier" && it.entityId == supplier.id })
        assertTrue(afterActionLogs.first().createdAtMillis >= rootLog.createdAtMillis)
    }

    @Test
    fun menuNotificationsPersistDedupeMergePopupsAndMarkReadCorrectly() = runBlocking {
        environment.storedTokens = aitaTestTokenPair("notifications")
        userAccountState.emit(DataState.Success(aitaTestUserAccount()))
        activeStoreIdState.emit(AITA_FLOW_SOURCE_STORE_ID)
        notificationsState.emit(DataState.Success(emptyList()))
        activeInAppNotificationsState.emit(emptyList())
        latestInAppNotificationState.emit(null)
        environment.notifications = emptyList()
        environment.requests.clear()

        environment.nextSavedNotificationResponse = aitaTestNotification(
            id = "server-saved-positive",
            message = "Stock item saved",
            type = NotificationType.Positive,
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            category = "positive",
            isSavedOnServer = true
        )
        postInAppNotification("Stock item saved", NotificationType.Positive)

        waitUntilAitaFlowCondition { latestInAppNotificationState.value?.message == "Stock item saved" }
        waitUntilAitaFlowCondition { notificationsState.payloadValue.orEmpty().any { it.id == "server-saved-positive" && it.isSavedOnServer } }
        assertTrue(activeInAppNotificationsState.value.any { it.message == "Stock item saved" && it.type == NotificationType.Positive })
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "notifications/add" })

        val savedCountBeforeDuplicate = notificationsState.payloadValue.orEmpty().count { it.message == "Stock item saved" }
        postInAppNotification("Stock item saved", NotificationType.Positive)
        delay(150L)
        val savedCountAfterDuplicate = notificationsState.payloadValue.orEmpty().count { it.message == "Stock item saved" }
        assertTrue(savedCountAfterDuplicate <= savedCountBeforeDuplicate)

        val serverUnread = aitaTestNotification(
            id = "server-worker-unread",
            message = "Worker invite received",
            title = "Worker invite",
            type = NotificationType.Neutral,
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            category = "worker",
            createdAtMillis = getCurrentTimeMillis(),
            isSavedOnServer = true
        )
        environment.notifications = environment.notifications.upsertAitaTestNotification(serverUnread)
        getNotifications()

        waitUntilAitaFlowCondition { notificationsState.payloadValue.orEmpty().any { it.id == serverUnread.id } }
        waitUntilAitaFlowCondition { activeInAppNotificationsState.value.any { it.id == serverUnread.id || it.message == serverUnread.message } }
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "notifications/get" })

        markNotificationRead(serverUnread.id)
        waitUntilAitaFlowCondition { notificationsState.payloadValue.orEmpty().firstOrNull { it.id == serverUnread.id }?.readAtMillis != null }
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "notifications/read" })

        markAllNotificationsRead()
        waitUntilAitaFlowCondition { notificationsState.payloadValue.orEmpty().filter { it.isSavedOnServer }.all { it.readAtMillis != null } }

        clearInAppNotification()
        waitUntilAitaFlowCondition { activeInAppNotificationsState.value.isEmpty() && latestInAppNotificationState.value == null }
    }

    @Test
    fun menuSupplierCrudStoresAndFiltersSuppliersCorrectly() = runBlocking {
        val generic = aitaTestSupplier(
            id = "supplier-generic",
            name = "Generic greenhouse",
            userIds = emptyList(),
            categoryIds = listOf("vegetables")
        )
        val mine = aitaTestSupplier(
            id = "supplier-mine",
            name = "My local bakery",
            userIds = listOf(AITA_FLOW_TEST_USER_ID),
            categoryIds = listOf("bakery")
        )
        environment.suppliers = listOf(generic)
        suppliersState.emit(DataState.Empty())
        environment.requests.clear()

        getSuppliers()
        waitUntilAitaFlowCondition { suppliersState.payloadValue?.singleOrNull()?.id == generic.id }
        assertTrue(suppliersState.payloadValue.orEmpty().single { it.id == generic.id }.isGenericSupplier())
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "suppliers/get" })

        environment.nextSupplierResponse = mine
        val addCallback = CompletableDeferred<DataState<SupplierDataModel>>()
        addSupplier(mine) { addCallback.complete(it) }

        assertEquals(mine.id, requireAitaFlowSuccess(addCallback).payload.id)
        waitUntilAitaFlowCondition { suppliersState.payloadValue.orEmpty().any { it.id == mine.id } }
        assertTrue(suppliersState.payloadValue.orEmpty().single { it.id == mine.id }.isMineForUser(AITA_FLOW_TEST_USER_ID))
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "suppliers/add" })

        val updated = mine.copy(
            name = aitaTestLocalized("My local bakery updated"),
            phoneNumbers = listOf("+77005550101"),
            emails = listOf("bakery-updated@aita.local"),
            categoryIds = listOf("bakery", "launch")
        )
        environment.nextSupplierResponse = updated
        val updateCallback = CompletableDeferred<DataState<SupplierDataModel>>()
        updateSupplier(updated) { updateCallback.complete(it) }

        assertEquals(updated.name, requireAitaFlowSuccess(updateCallback).payload.name)
        waitUntilAitaFlowCondition { suppliersState.payloadValue.orEmpty().single { it.id == updated.id }.categoryIds.contains("launch") }
        assertEquals(listOf("+77005550101"), suppliersState.payloadValue.orEmpty().single { it.id == updated.id }.phoneNumbers)
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "suppliers/update" })

        environment.nextDeletedSupplierId = generic.id
        val deleteCallback = CompletableDeferred<DataState<String>>()
        deleteSupplier(generic.id) { deleteCallback.complete(it) }

        assertEquals(generic.id, requireAitaFlowSuccess(deleteCallback).payload)
        waitUntilAitaFlowCondition { suppliersState.payloadValue.orEmpty().none { it.id == generic.id } }
        assertTrue(suppliersState.payloadValue.orEmpty().any { it.id == updated.id })
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "suppliers/delete" })
    }

    @Test
    fun menuSupplierPricesOrdersReceivingAndBatchReferencesStayInSync() = runBlocking {
        val supplier = aitaTestSupplier(id = "supplier-order-flow", name = "North farm")
        val item = aitaTestGoodsItem(id = "supplier-order-item", name = "North farm tofu")
        val existingBatch = aitaTestBatch(
            id = "supplier-order-existing-batch",
            goodsItemId = item.id,
            quantityTotal = 4.0,
            supplierId = supplier.id,
            supplyPriceValue = "500",
            salePriceValue = "900"
        )
        val price = aitaTestSupplierGoodsPrice(
            id = "supplier-price-flow",
            supplierId = supplier.id,
            goodsItemId = item.id,
            supplyPriceValue = "500"
        )
        val order = aitaTestSupplierOrderWithLines(
            id = "supplier-order-flow",
            supplierId = supplier.id,
            goodsItemId = item.id,
            requestedQuantity = 8.0,
            expectedSupplyPriceValue = "500",
            status = SupplierOrderStatusDataModel.Sent
        )
        environment.suppliers = listOf(supplier)
        environment.stock = listOf(item)
        environment.batches = listOf(existingBatch)
        environment.supplierGoodsPrices = listOf(price)
        environment.supplierOrders = listOf(order)
        supplierGoodsPricesState.emit(DataState.Empty())
        supplierOrdersState.emit(DataState.Empty())
        supplierOrderLinesState.emit(DataState.Empty())
        environment.requests.clear()

        val pricesCallback = CompletableDeferred<DataState<List<SupplierGoodsPriceDataModel>>>()
        getSupplierGoodsPrices(AITA_FLOW_SOURCE_STORE_ID) { pricesCallback.complete(it) }
        val loadedPrices = requireAitaFlowSuccess(pricesCallback).payload
        assertEquals(listOf(price.id), loadedPrices.map { it.id })
        waitUntilAitaFlowCondition { supplierGoodsPricesState.payloadValue?.singleOrNull()?.supplierId == supplier.id }
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "supplierGoodsPrices/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val updatedPrice = price.copy(
            supplyPrice = aitaTestPrice("550", supplierId = supplier.id),
            minOrderQuantity = aitaTestQuantity(5.0),
            packageQuantity = aitaTestQuantity(10.0),
            supplierGoodsName = "Tofu block 400g updated",
            updatedAtMillis = 2_000L
        )
        environment.nextSupplierGoodsPriceResponse = updatedPrice
        val upsertPriceCallback = CompletableDeferred<DataState<SupplierGoodsPriceDataModel>>()
        upsertSupplierGoodsPrice(updatedPrice) { upsertPriceCallback.complete(it) }

        assertEquals(updatedPrice, requireAitaFlowSuccess(upsertPriceCallback).payload)
        waitUntilAitaFlowCondition { supplierGoodsPricesState.payloadValue.orEmpty().single { it.id == updatedPrice.id }.supplyPrice.price == "550" }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "supplierGoodsPrices/upsert" })

        val ordersCallback = CompletableDeferred<DataState<List<SupplierOrderWithLinesDataModel>>>()
        getSupplierOrders(AITA_FLOW_SOURCE_STORE_ID) { ordersCallback.complete(it) }
        val loadedOrders = requireAitaFlowSuccess(ordersCallback).payload
        assertEquals(listOf(order.order.id), loadedOrders.map { it.order.id })
        waitUntilAitaFlowCondition { supplierOrdersState.payloadValue?.singleOrNull()?.supplierId == supplier.id }
        waitUntilAitaFlowCondition { supplierOrderLinesState.payloadValue?.singleOrNull()?.goodsItemId == item.id }
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "supplierOrders/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID })

        val supplierSideCallback = CompletableDeferred<DataState<List<SupplierOrderWithLinesDataModel>>>()
        getSupplierOrdersForSupplier(supplier.id) { supplierSideCallback.complete(it) }
        assertEquals(listOf(order.order.id), requireAitaFlowSuccess(supplierSideCallback).payload.map { it.order.id })
        assertTrue(environment.requests.any { it.method == "GET" && it.path == "supplierOrders/get" && it.supplierIdHeader == supplier.id })

        val mySupplierSideCallback = CompletableDeferred<DataState<List<SupplierOrderWithLinesDataModel>>>()
        getMySupplierSideOrders { mySupplierSideCallback.complete(it) }
        assertTrue(requireAitaFlowSuccess(mySupplierSideCallback).payload.any { it.order.supplierId == supplier.id })

        val addedOrder = aitaTestSupplierOrderWithLines(
            id = "supplier-order-added",
            supplierId = supplier.id,
            goodsItemId = item.id,
            requestedQuantity = 12.0,
            expectedSupplyPriceValue = "530",
            status = SupplierOrderStatusDataModel.Sent
        )
        environment.nextSupplierOrderResponse = addedOrder
        val addOrderCallback = CompletableDeferred<DataState<SupplierOrderWithLinesDataModel>>()
        addSupplierOrder(addedOrder) { addOrderCallback.complete(it) }

        assertEquals(addedOrder.order.id, requireAitaFlowSuccess(addOrderCallback).payload.order.id)
        waitUntilAitaFlowCondition { supplierOrdersState.payloadValue.orEmpty().any { it.id == addedOrder.order.id } }
        waitUntilAitaFlowCondition { supplierOrderLinesState.payloadValue.orEmpty().any { it.orderId == addedOrder.order.id } }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "supplierOrders/add" })

        val confirmedOrder = addedOrder.copy(
            order = addedOrder.order.copy(
                status = SupplierOrderStatusDataModel.Confirmed,
                confirmedDeliveryTimeMillis = 1_720_000_000_000L,
                amount = aitaTestPrice("6360", supplierId = supplier.id)
            ),
            lines = addedOrder.lines.map {
                it.copy(
                    supplierAcceptedQuantity = aitaTestQuantity(12.0),
                    supplierOfferedSupplyPrice = aitaTestPrice("530", supplierId = supplier.id),
                    supplierComment = "Confirmed for tomorrow"
                )
            }
        )
        environment.nextSupplierOrderResponse = confirmedOrder
        val updateOrderCallback = CompletableDeferred<DataState<SupplierOrderWithLinesDataModel>>()
        updateSupplierOrder(confirmedOrder) { updateOrderCallback.complete(it) }

        assertEquals(SupplierOrderStatusDataModel.Confirmed, requireAitaFlowSuccess(updateOrderCallback).payload.order.status)
        waitUntilAitaFlowCondition { supplierOrdersState.payloadValue.orEmpty().first { it.id == confirmedOrder.order.id }.status == SupplierOrderStatusDataModel.Confirmed }
        assertEquals("Confirmed for tomorrow", supplierOrderLinesState.payloadValue.orEmpty().first { it.orderId == confirmedOrder.order.id }.supplierComment)
        assertTrue(environment.requests.any { it.method == "PUT" && it.path == "supplierOrders/update" })

        val deliveredBatch = aitaTestBatch(
            id = "supplier-order-delivered-batch",
            goodsItemId = item.id,
            quantityTotal = 12.0,
            supplierId = supplier.id,
            supplyPriceValue = "530",
            salePriceValue = "950"
        )
        val receivedOrder = confirmedOrder.copy(
            order = confirmedOrder.order.copy(
                status = SupplierOrderStatusDataModel.Delivered,
                deliveredAtMillis = 1_720_000_100_000L
            ),
            lines = confirmedOrder.lines.map { it.copy(deliveredBatchIds = listOf(deliveredBatch.id)) }
        )
        environment.batches = environment.batches.upsertAitaTestBatch(deliveredBatch)
        environment.nextSupplierOrderResponse = receivedOrder
        val receiveCallback = CompletableDeferred<DataState<SupplierOrderWithLinesDataModel>>()
        receiveSupplierOrder(
            ReceiveSupplierOrderRequestDataModel(
                orderId = receivedOrder.order.id,
                receivedLines = receivedOrder.lines.map { line ->
                    ReceiveSupplierOrderLineDataModel(
                        orderLineId = line.id,
                        goodsItemId = line.goodsItemId,
                        receivedQuantity = line.requestedQuantity,
                        actualSupplyPrice = line.supplierOfferedSupplyPrice ?: updatedPrice.supplyPrice,
                        expirationDateMillis = 1_800_000_000_000L
                    )
                }
            )
        ) { receiveCallback.complete(it) }

        assertEquals(SupplierOrderStatusDataModel.Delivered, requireAitaFlowSuccess(receiveCallback).payload.order.status)
        waitUntilAitaFlowCondition { supplierOrderLinesState.payloadValue.orEmpty().any { deliveredBatch.id in it.deliveredBatchIds } }
        waitUntilAitaFlowCondition { environment.requests.any { it.method == "GET" && it.path == "stockBatches/get" && it.storeIdHeader == AITA_FLOW_SOURCE_STORE_ID } }
        assertTrue(environment.requests.any { it.method == "POST" && it.path == "supplierOrders/receive" })
        assertTrue(stockBatchesState.payloadValue.orEmpty().any { it.id == deliveredBatch.id && it.supplierId == supplier.id })

        environment.nextDeletedSupplierOrderId = receivedOrder.order.id
        val deleteOrderCallback = CompletableDeferred<DataState<String>>()
        deleteSupplierOrder(receivedOrder.order.id) { deleteOrderCallback.complete(it) }

        assertEquals(receivedOrder.order.id, requireAitaFlowSuccess(deleteOrderCallback).payload)
        waitUntilAitaFlowCondition { supplierOrdersState.payloadValue.orEmpty().none { it.id == receivedOrder.order.id } }
        waitUntilAitaFlowCondition { supplierOrderLinesState.payloadValue.orEmpty().none { it.orderId == receivedOrder.order.id } }
        assertTrue(environment.requests.any { it.method == "DELETE" && it.path == "supplierOrders/delete" })
    }

}

private suspend fun resetAitaFlowSharedState() {
    stopRealtimeUpdates()
    activeStoreIdState.emit(null)
    userAccountState.emit(DataState.Empty())
    storesState.emit(DataState.Empty())
    storeWorkerMembershipsState.emit(DataState.Empty())
    myWorkerMembershipsState.emit(DataState.Empty())
    incomingWorkerRequestsState.emit(DataState.Empty())
    myWorkerRequestsState.emit(DataState.Empty())
    suppliersState.emit(DataState.Empty())
    supplierGoodsPricesState.emit(DataState.Empty())
    supplierOrdersState.emit(DataState.Empty())
    supplierOrderLinesState.emit(DataState.Empty())
    operationLogsState.emit(DataState.Empty())
    storeAnalyticsDashboardState.emit(DataState.Empty())
    stockState.emit(DataState.Empty())
    stockBatchesState.emit(DataState.Empty())
    stockItemBranchAvailabilityState.emit(DataState.Empty())
    stockBatchMoveResultState.emit(DataState.Empty())
    transactionsState.emit(DataState.Empty())
    debtorsState.emit(DataState.Empty())
    cashRegisterState.emit(DataState.Empty())
    cashRegisterAmountState.emit(0.0)
    activeWorkshiftState.emit(DataState.Empty())
    workshiftLoginInProgressState.emit(false)
    notificationsState.emit(DataState.Empty())
    supportTicketsState.emit(DataState.Empty())
    securitySessionsState.emit(DataState.Empty())
    securitySessionHistoryState.emit(DataState.Empty())
    localNetworkState.emit(LocalNetworkStateDataModel())
    localNetworkQueuedOperationsState.emit(emptyList())
    latestTransactionReceiptSnapshotState.emit(null)
    completeTransactionInProgressState.emit(false)
    cloudTransportStatusState.emit(CLOUD_TRANSPORT_STATUS_UNKNOWN)
    realtimeUpdatesConnectedState.emit(false)
    latestInAppNotificationState.emit(null)
    activeInAppNotificationsState.emit(emptyList())
    for (transactionTypeIndex in 0..2) {
        deleteCart(transactionTypeIndex, 0)
        deleteCart(transactionTypeIndex, 1)
    }
}

private suspend fun waitUntilAitaFlowCondition(condition: suspend () -> Boolean) {
    withTimeout(8_000L) {
        while (!condition()) {
            delay(25L)
        }
    }
}

private suspend fun <T> requireAitaFlowSuccess(deferred: CompletableDeferred<DataState<T>>): DataState.Success<T> {
    val state = withTimeout(8_000L) { deferred.await() }
    assertTrue(state is DataState.Success<*>, "Expected DataState.Success but got $state")
    @Suppress("UNCHECKED_CAST")
    return state as DataState.Success<T>
}

private suspend fun currentAitaFlowCart(transactionTypeIndex: Int, clientId: Int): List<GoodsItemInCartDataModel> =
    observeCart(transactionTypeIndex, clientId).first().orEmpty()

private fun buildAitaFlowMockClient(environment: AitaFlowTestEnvironment): HttpClient = HttpClient(
    MockEngine { request ->
        val path = request.url.encodedPath.trimStart('/')
        val queryParameters = request.url.parameters.names().associateWith { name ->
            request.url.parameters.getAll(name).orEmpty()
        }
        environment.requests += RecordedAitaRequest(
            method = request.method.value,
            path = path,
            storeIdHeader = request.headers["store_id"],
            goodsItemIdHeader = request.headers["goods_item_id"],
            supplierIdHeader = request.headers["supplier_id"],
            queryParameters = queryParameters
        )

        if (path == "transactions/complete" && environment.forceNextCompleteTransactionFailure) {
            environment.forceNextCompleteTransactionFailure = false
            return@MockEngine respond(
                content = aitaTestNegativeEnvelope("Temporary server failure"),
                status = HttpStatusCode.ServiceUnavailable,
                headers = aitaFlowResponseHeaders()
            )
        }

        val body: String = when (path) {
            "auth/logIn" -> aitaTestSuccessEnvelope(aitaTestTokenPair("login"))
            "auth/signUp" -> aitaTestSuccessEnvelope(aitaTestTokenPair("signup"))
            "auth/logOut" -> aitaTestSuccessNullEnvelope()
            "auth/refresh" -> aitaTestSuccessEnvelope(aitaTestTokenPair("refresh"))
            "user/get" -> aitaTestSuccessEnvelope(aitaTestUserAccount())
            "config/global" -> aitaTestSuccessEnvelope(globalAppConfigurationState.payloadValue.copy(serverUrl = AITA_FLOW_TEST_SERVER_URL to "test"))
            "stores/get" -> aitaTestSuccessEnvelope(environment.stores)
            "stores/add" -> {
                val store = environment.nextStoreResponse ?: aitaTestStore(id = "server-added-store")
                environment.stores = environment.stores.upsertAitaTestStore(store)
                aitaTestSuccessEnvelope(store)
            }
            "stores/update" -> {
                val store = environment.nextStoreResponse ?: environment.stores.firstOrNull() ?: aitaTestStore(id = "server-updated-store")
                environment.stores = environment.stores.upsertAitaTestStore(store)
                aitaTestSuccessEnvelope(store)
            }
            "stores/delete" -> {
                val id = environment.nextDeletedStoreId.orEmpty()
                environment.stores = environment.stores.removeAitaTestStore(id)
                aitaTestSuccessNullEnvelope()
            }
            "stores/active" -> aitaTestSuccessNullEnvelope()
            "suppliers/get" -> aitaTestSuccessEnvelope(environment.suppliers)
            "suppliers/add" -> {
                val supplier = environment.nextSupplierResponse ?: aitaTestSupplier(id = "server-added-supplier")
                environment.suppliers = environment.suppliers.upsertAitaTestSupplier(supplier)
                environment.recordAitaTestOperationLog(
                    action = "supplier_add",
                    entityType = "supplier",
                    entityId = supplier.id,
                    title = "Supplier added",
                    details = supplier.name.extractLocalizedString(DEFAULT_APP_LANGUAGE).orEmpty()
                )
                aitaTestSuccessEnvelope(supplier)
            }
            "suppliers/update" -> {
                val supplier = environment.nextSupplierResponse ?: environment.suppliers.firstOrNull() ?: aitaTestSupplier(id = "server-updated-supplier")
                environment.suppliers = environment.suppliers.upsertAitaTestSupplier(supplier)
                environment.recordAitaTestOperationLog(
                    action = "supplier_update",
                    entityType = "supplier",
                    entityId = supplier.id,
                    title = "Supplier updated",
                    details = supplier.name.extractLocalizedString(DEFAULT_APP_LANGUAGE).orEmpty()
                )
                aitaTestSuccessEnvelope(supplier)
            }
            "suppliers/delete" -> {
                val id = environment.nextDeletedSupplierId.orEmpty()
                environment.suppliers = environment.suppliers.filterNot { it.id == id }
                environment.recordAitaTestOperationLog(
                    action = "supplier_delete",
                    entityType = "supplier",
                    entityId = id,
                    title = "Supplier deleted"
                )
                aitaTestSuccessEnvelope(id)
            }
            "supplierGoodsPrices/get" -> {
                val storeId = request.headers["store_id"].orEmpty()
                aitaTestSuccessEnvelope(environment.supplierGoodsPrices.filter { storeId.isBlank() || it.storeId == storeId })
            }
            "supplierGoodsPrices/upsert" -> {
                val price = environment.nextSupplierGoodsPriceResponse ?: environment.supplierGoodsPrices.firstOrNull() ?: aitaTestSupplierGoodsPrice(id = "server-supplier-price")
                environment.supplierGoodsPrices = environment.supplierGoodsPrices.upsertAitaTestSupplierGoodsPrice(price)
                environment.recordAitaTestOperationLog(
                    action = "supplier_goods_price_upsert",
                    entityType = "supplier_goods_price",
                    entityId = price.id,
                    title = "Supplier goods price saved",
                    details = "${price.supplierId} • ${price.goodsItemId}"
                )
                aitaTestSuccessEnvelope(price)
            }
            "supplierOrders/get" -> {
                val storeId = request.headers["store_id"].orEmpty()
                val supplierId = request.headers["supplier_id"].orEmpty()
                aitaTestSuccessEnvelope(
                    environment.supplierOrders.filter { orderWithLines ->
                        (storeId.isBlank() || orderWithLines.order.storeId == storeId) &&
                                (supplierId.isBlank() || orderWithLines.order.supplierId == supplierId)
                    }
                )
            }
            "supplierOrders/add" -> {
                val orderWithLines = environment.nextSupplierOrderResponse ?: aitaTestSupplierOrderWithLines(id = "server-added-supplier-order")
                environment.supplierOrders = environment.supplierOrders.upsertAitaTestSupplierOrder(orderWithLines)
                environment.recordAitaTestOperationLog(
                    action = "supplier_order_add",
                    entityType = "supplier_order",
                    entityId = orderWithLines.order.id,
                    title = "Supplier order added",
                    details = orderWithLines.order.supplierId
                )
                aitaTestSuccessEnvelope(orderWithLines)
            }
            "supplierOrders/update" -> {
                val orderWithLines = environment.nextSupplierOrderResponse ?: environment.supplierOrders.firstOrNull() ?: aitaTestSupplierOrderWithLines(id = "server-updated-supplier-order")
                environment.supplierOrders = environment.supplierOrders.upsertAitaTestSupplierOrder(orderWithLines)
                environment.recordAitaTestOperationLog(
                    action = "supplier_order_update",
                    entityType = "supplier_order",
                    entityId = orderWithLines.order.id,
                    title = "Supplier order updated",
                    details = orderWithLines.order.status.name
                )
                aitaTestSuccessEnvelope(orderWithLines)
            }
            "supplierOrders/delete" -> {
                val id = environment.nextDeletedSupplierOrderId.orEmpty()
                environment.supplierOrders = environment.supplierOrders.filterNot { it.order.id == id }
                environment.recordAitaTestOperationLog(
                    action = "supplier_order_delete",
                    entityType = "supplier_order",
                    entityId = id,
                    title = "Supplier order deleted"
                )
                aitaTestSuccessEnvelope(id)
            }
            "supplierOrders/receive" -> {
                val orderWithLines = environment.nextSupplierOrderResponse ?: environment.supplierOrders.firstOrNull()?.copy(
                    order = environment.supplierOrders.first().order.copy(status = SupplierOrderStatusDataModel.Delivered)
                ) ?: aitaTestSupplierOrderWithLines(id = "server-received-supplier-order", status = SupplierOrderStatusDataModel.Delivered)
                environment.supplierOrders = environment.supplierOrders.upsertAitaTestSupplierOrder(orderWithLines)
                environment.recordAitaTestOperationLog(
                    action = "supplier_order_receive",
                    entityType = "supplier_order",
                    entityId = orderWithLines.order.id,
                    title = "Supplier order received",
                    details = orderWithLines.lines.sumOf { it.requestedQuantity.total }.toString()
                )
                aitaTestSuccessEnvelope(orderWithLines)
            }
            "generic/goodsCategories/get" -> aitaTestSuccessEnvelope(emptyList<GenericGoodsCategoryDataModel>())
            "notifications/get" -> aitaTestSuccessEnvelope(environment.notifications)
            "notifications/add" -> {
                val notification = environment.nextSavedNotificationResponse ?: aitaTestNotification(
                    id = "server-saved-notification-${environment.notifications.size + 1}",
                    message = "Saved notification",
                    type = NotificationType.Positive,
                    isSavedOnServer = true
                )
                val saved = notification.copy(isSavedOnServer = true, readAtMillis = notification.readAtMillis)
                environment.notifications = environment.notifications.upsertAitaTestNotification(saved)
                aitaTestSuccessEnvelope(saved)
            }
            "notifications/read" -> {
                val now = getCurrentTimeMillis()
                environment.notifications = environment.notifications.map { it.copy(readAtMillis = it.readAtMillis ?: now) }
                aitaTestSuccessEnvelope(environment.notifications)
            }
            "logs/get" -> {
                val storeId = request.headers["store_id"].orEmpty()
                val scope = request.url.parameters["scope"] ?: OPERATION_LOG_SCOPE_CURRENT
                val rootStoreId = environment.stores.findAitaTestStoreOrBranch(storeId)?.parentStoreId ?: storeId
                val logs = environment.operationLogs.filter { log ->
                    when (scope) {
                        OPERATION_LOG_SCOPE_ROOT -> log.rootStoreId == rootStoreId || log.storeId == storeId
                        else -> log.storeId == storeId
                    }
                }.sortedByDescending { it.createdAtMillis }
                aitaTestSuccessEnvelope(logs)
            }
            "analytics/store/get" -> {
                val storeId = request.headers["store_id"].orEmpty()
                val startMillis = request.url.parameters["startMillis"]?.toLongOrNull() ?: 0L
                val endMillisExclusive = request.url.parameters["endMillisExclusive"]?.toLongOrNull() ?: Long.MAX_VALUE
                val goodsItemIdFilter = request.url.parameters["goodsItemId"]
                val supplierIdFilter = request.url.parameters["supplierId"]
                val categoryIdFilter = request.url.parameters["categoryId"]
                aitaTestSuccessEnvelope(
                    buildStoreAnalyticsDashboard(
                        storeId = storeId,
                        startMillis = startMillis,
                        endMillisExclusive = endMillisExclusive,
                        transactions = environment.transactions,
                        stock = environment.stock,
                        batches = environment.batches,
                        fallbackCurrencyCode = "KZT",
                        goodsItemIdFilter = goodsItemIdFilter,
                        supplierIdFilter = supplierIdFilter,
                        categoryIdFilter = categoryIdFilter
                    )
                )
            }
            "support/tickets/get" -> aitaTestSuccessEnvelope(emptyList<SupportTicketDataModel>())
            "security/sessions/get" -> aitaTestSuccessEnvelope(emptyList<SecuritySessionDataModel>())
            "security/sessions/history" -> aitaTestSuccessEnvelope(emptyList<SecuritySessionHistoryDataModel>())
            "workers/store/get" -> {
                val storeId = request.headers["store_id"].orEmpty()
                aitaTestSuccessEnvelope(environment.workerMemberships.filter { storeId.isBlank() || it.storeId == storeId })
            }
            "workers/my/get" -> aitaTestSuccessEnvelope(environment.myWorkerMemberships)
            "workers/requests/incoming" -> {
                val storeId = request.headers["store_id"].orEmpty()
                aitaTestSuccessEnvelope(environment.incomingWorkerRequests.filter { storeId.isBlank() || it.storeId == storeId })
            }
            "workers/requests/my" -> aitaTestSuccessEnvelope(environment.myWorkerRequests)
            "workers/request" -> {
                val workerRequest = environment.nextWorkerRequestResponse ?: aitaTestWorkerRequest(id = "server-employment-request")
                environment.myWorkerRequests = environment.myWorkerRequests.upsertAitaTestWorkerRequest(workerRequest)
                aitaTestSuccessEnvelope(workerRequest)
            }
            "workers/invite" -> {
                val workerRequest = environment.nextWorkerRequestResponse ?: aitaTestWorkerRequest(
                    id = "server-worker-invite",
                    direction = WORKER_REQUEST_DIRECTION_STORE_TO_USER,
                    status = WORKER_REQUEST_STATUS_INVITED
                )
                environment.incomingWorkerRequests = environment.incomingWorkerRequests.upsertAitaTestWorkerRequest(workerRequest)
                aitaTestSuccessEnvelope(workerRequest)
            }
            "workers/accept" -> {
                val worker = environment.nextWorkerMembershipResponse ?: aitaTestWorkerMembership(id = "server-accepted-worker")
                environment.workerMemberships = environment.workerMemberships.upsertAitaTestWorker(worker)
                aitaTestSuccessEnvelope(worker)
            }
            "workers/decline" -> {
                val workerRequest = environment.nextWorkerRequestResponse ?: aitaTestWorkerRequest(
                    id = "server-declined-employment",
                    status = WORKER_REQUEST_STATUS_DECLINED
                )
                environment.incomingWorkerRequests = environment.incomingWorkerRequests.upsertAitaTestWorkerRequest(workerRequest)
                aitaTestSuccessEnvelope(workerRequest)
            }
            "workers/invitations/accept" -> {
                val worker = environment.nextWorkerMembershipResponse ?: aitaTestWorkerMembership(id = "server-invited-worker", userId = AITA_FLOW_TEST_USER_ID)
                environment.myWorkerMemberships = environment.myWorkerMemberships.upsertAitaTestWorker(worker)
                aitaTestSuccessEnvelope(worker)
            }
            "workers/invitations/decline" -> {
                val workerRequest = environment.nextWorkerRequestResponse ?: aitaTestWorkerRequest(
                    id = "server-declined-invite",
                    direction = WORKER_REQUEST_DIRECTION_STORE_TO_USER,
                    status = WORKER_REQUEST_STATUS_DECLINED
                )
                environment.myWorkerRequests = environment.myWorkerRequests.upsertAitaTestWorkerRequest(workerRequest)
                aitaTestSuccessEnvelope(workerRequest)
            }
            "workers/updatePermissions" -> {
                val worker = environment.nextWorkerMembershipResponse ?: environment.workerMemberships.firstOrNull() ?: aitaTestWorkerMembership(id = "server-updated-worker")
                environment.workerMemberships = environment.workerMemberships.upsertAitaTestWorker(worker)
                aitaTestSuccessEnvelope(worker)
            }
            "workers/my/password" -> {
                val worker = environment.nextWorkerMembershipResponse ?: environment.myWorkerMemberships.firstOrNull()?.copy(hasWorkshiftPassword = true) ?: aitaTestWorkerMembership(id = "server-password-worker", hasWorkshiftPassword = true)
                environment.myWorkerMemberships = environment.myWorkerMemberships.upsertAitaTestWorker(worker)
                aitaTestSuccessEnvelope(worker)
            }
            "workers/remove" -> {
                val worker = environment.nextRemovedWorkerResponse ?: environment.workerMemberships.firstOrNull()?.copy(isActive = false) ?: aitaTestWorkerMembership(id = "server-removed-worker", isActive = false)
                environment.workerMemberships = environment.workerMemberships.filterNot { it.id == worker.id }
                environment.myWorkerMemberships = environment.myWorkerMemberships.filterNot { it.id == worker.id }
                aitaTestSuccessEnvelope(worker)
            }
            "workshifts/current" -> environment.activeWorkshift?.let { aitaTestSuccessEnvelope(it) } ?: aitaTestSuccessNullEnvelope()
            "workshifts/start" -> {
                val workshift = environment.nextWorkshiftResponse ?: aitaTestWorkshift(id = "server-started-workshift", isActive = true)
                environment.activeWorkshift = workshift
                aitaTestSuccessEnvelope(workshift)
            }
            "workshifts/end" -> {
                val workshift = environment.nextWorkshiftResponse ?: environment.activeWorkshift?.copy(isActive = false, endedAtMillis = 1_720_000_000_000L) ?: aitaTestWorkshift(id = "server-ended-workshift", isActive = false, endedAtMillis = 1_720_000_000_000L)
                environment.activeWorkshift = null
                aitaTestSuccessEnvelope(workshift)
            }
            "stock/get" -> aitaTestSuccessEnvelope(environment.stock)
            "stock/add" -> {
                val item = environment.nextGoodsItemResponse ?: aitaTestGoodsItem(id = "server-added")
                environment.stock = environment.stock.upsertAitaTestItem(item)
                aitaTestSuccessEnvelope(item)
            }
            "stock/update" -> {
                val item = environment.nextGoodsItemResponse ?: environment.stock.firstOrNull() ?: aitaTestGoodsItem(id = "server-updated")
                environment.stock = environment.stock.upsertAitaTestItem(item)
                aitaTestSuccessEnvelope(item)
            }
            "stock/delete" -> {
                val id = environment.nextDeletedGoodsItemId.orEmpty()
                environment.stock = environment.stock.filterNot { it.id == id }
                aitaTestSuccessEnvelope(id)
            }
            "stockBatches/get" -> aitaTestSuccessEnvelope(environment.batches)
            "stockBatches/add" -> {
                val batches = environment.nextGoodsBatchResponse.orEmpty()
                environment.batches = batches.fold(environment.batches) { acc, batch -> acc.upsertAitaTestBatch(batch) }
                aitaTestSuccessEnvelope(batches)
            }
            "stockBatches/update" -> {
                val batches = environment.nextGoodsBatchResponse.orEmpty()
                environment.batches = batches.fold(environment.batches) { acc, batch -> acc.upsertAitaTestBatch(batch) }
                aitaTestSuccessEnvelope(batches)
            }
            "stockBatches/delete" -> {
                val ids = environment.nextDeletedBatchIds.orEmpty()
                environment.batches = environment.batches.filterNot { it.id in ids }
                aitaTestSuccessEnvelope(ids)
            }
            "stockBatches/branchAvailability" -> aitaTestSuccessEnvelope(environment.availability)
            "stockBatches/move" -> {
                val result = environment.nextMoveResult ?: aitaTestMoveResult()
                environment.stock = environment.stock
                    .upsertAitaTestItem(result.sourceGoodsItem)
                    .upsertAitaTestItem(result.destinationGoodsItem)
                environment.batches = environment.batches
                    .upsertAitaTestBatch(result.sourceBatch)
                    .upsertAitaTestBatch(result.destinationBatch)
                environment.availability = result.availability
                aitaTestSuccessEnvelope(result)
            }
            "stockBatches/decideMove" -> {
                val result = environment.nextDecisionResult ?: environment.nextMoveResult ?: aitaTestMoveResult()
                environment.stock = environment.stock
                    .upsertAitaTestItem(result.sourceGoodsItem)
                    .upsertAitaTestItem(result.destinationGoodsItem)
                environment.batches = environment.batches
                    .upsertAitaTestBatch(result.sourceBatch)
                    .upsertAitaTestBatch(result.destinationBatch)
                environment.availability = result.availability
                aitaTestSuccessEnvelope(result)
            }
            "transactions/get" -> aitaTestSuccessEnvelope(environment.transactions)
            "transactions/complete" -> {
                val completed = environment.nextCompletedTransaction ?: aitaTestTransaction(id = "server-completed")
                environment.transactions = environment.transactions.upsertAitaTestTransaction(completed)
                completed.debtor?.let { debtor -> environment.debtors = environment.debtors.upsertAitaTestDebtor(debtor) }
                aitaTestSuccessEnvelope(completed)
            }
            "debtors/get" -> aitaTestSuccessEnvelope(environment.debtors)
            "debtors/add" -> {
                val debtor = environment.nextDebtorResponse ?: aitaTestDebtor(id = "server-debtor")
                environment.debtors = environment.debtors.upsertAitaTestDebtor(debtor)
                aitaTestSuccessEnvelope(debtor)
            }
            "debtors/update" -> {
                val debtor = environment.nextDebtorResponse ?: environment.debtors.firstOrNull() ?: aitaTestDebtor(id = "server-updated-debtor")
                environment.debtors = environment.debtors.upsertAitaTestDebtor(debtor)
                aitaTestSuccessEnvelope(debtor)
            }
            "debtors/pay" -> {
                val debtor = environment.nextDebtorResponse ?: environment.debtors.firstOrNull() ?: aitaTestDebtor(id = "server-paid-debtor")
                environment.debtors = environment.debtors.upsertAitaTestDebtor(debtor)
                aitaTestSuccessEnvelope(debtor)
            }
            "debtors/delete" -> {
                val id = environment.nextDeletedDebtorId.orEmpty()
                environment.debtors = environment.debtors.filterNot { it.id == id }
                aitaTestSuccessEnvelope(id)
            }
            "cashRegister/get" -> aitaTestSuccessEnvelope(
                CashRegisterStateDataModel(
                    register = StoreCashRegisterDataModel(storeId = request.headers["store_id"].orEmpty(), currentAmount = 1_000.0)
                )
            )
            else -> aitaTestSuccessNullEnvelope()
        }

        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = aitaFlowResponseHeaders()
        )
    }
) {
    expectSuccess = false
    install(ContentNegotiation) { json(jsonBase) }
    install(WebSockets)
    install(Auth) {
        bearer {
            sendWithoutRequest { true }
            loadTokens {
                environment.storedTokens?.let { BearerTokens(it.accessToken, it.refreshToken) }
            }
        }
    }
}

private fun aitaFlowResponseHeaders() = headersOf(
    AITA_SERVER_HEADER to listOf(AITA_SERVER_HEADER_VALUE),
    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())
)

private fun aitaTestSuccessNullEnvelope(): String = """{"message":null,"payload":null,"negative":false}"""

private fun aitaTestNegativeEnvelope(message: String): String =
    jsonBase.encodeToString(
        ResponseDataModel(
            message = aitaTestLocalized(message),
            payload = null as String?,
            negative = true,
            httpStatusCode = HttpStatusCode.ServiceUnavailable.value
        )
    )

private inline fun <reified T> aitaTestSuccessEnvelope(payload: T): String =
    jsonBase.encodeToString(ResponseDataModel(message = null, payload = payload, negative = false))

private fun aitaTestTokenPair(prefix: String): TokenPair = TokenPair(
    accessToken = "$prefix-access-token",
    refreshToken = "$prefix-refresh-token"
)

private fun aitaTestUserAccount(): UserAccountDataModel = UserAccountDataModel(
    id = AITA_FLOW_TEST_USER_ID,
    publicId = "AITA-TEST-USER",
    phoneNumber = "+77000000001",
    email = "tester@aita.local",
    firstName = "Aita",
    lastName = "Tester",
    countryLocale = "kz",
    workerAccountIds = null,
    supplierAccountIds = null,
    activeStoreId = null,
    appLanguage = DEFAULT_APP_LANGUAGE,
    appThemeId = DEFAULT_APP_THEME_ID,
    appSizeModeId = DEFAULT_APP_SIZE_MODE_ID,
    createdAt = 1L,
    isActive = true
)

private fun aitaTestLocalized(value: String): List<LocalizedStringDataModel> = listOf(
    LocalizedStringDataModel("main", value),
    LocalizedStringDataModel("en", value),
    LocalizedStringDataModel("ru", value),
    LocalizedStringDataModel("kk", value)
)



private fun aitaTestLocation(name: String = "Aita test location"): LocationDataModel = LocationDataModel(
    name = name,
    postalIndex = "050000",
    latitude = 43.238949,
    longitude = 76.889709
)

private fun aitaTestStore(
    id: String = AITA_FLOW_SOURCE_STORE_ID,
    publicId: String = "AITA-STORE",
    parentStoreId: String? = null,
    name: String = "Aita test store",
    userIds: List<String> = listOf(AITA_FLOW_TEST_USER_ID),
    branches: List<StoreDataModel> = emptyList()
): StoreDataModel = StoreDataModel(
    id = id,
    publicId = publicId,
    parentStoreId = parentStoreId,
    userIds = userIds,
    storeTypeIds = listOf("retail_store"),
    name = aitaTestLocalized(name),
    alias = aitaTestLocalized(name),
    description = aitaTestLocalized("$name description"),
    companyForms = emptyList(),
    location = aitaTestLocation("$name district"),
    address = "1 Launch Avenue, Almaty",
    legalIdTypeId = "kz_bin",
    legalId = "123456789012",
    phoneNumbers = listOf("+77000000001"),
    emails = listOf("store@aita.local"),
    countryLocales = listOf("kz"),
    createdAt = 1_710_000_000_000L,
    branches = branches
)

private fun aitaTestWorkerMembership(
    id: String = "worker-test",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    storePublicId: String = "AITA-STORE",
    userId: String = AITA_FLOW_WORKER_USER_ID,
    userPublicId: String = AITA_FLOW_WORKER_PUBLIC_ID,
    firstName: String = "Dauren",
    lastName: String = "Worker",
    roleId: String = WORKER_ROLE_STANDARD,
    permissions: List<String> = STANDARD_STORE_PERMISSION_IDS,
    hasWorkshiftPassword: Boolean = true,
    isActive: Boolean = true
): StoreWorkerDataModel = StoreWorkerDataModel(
    id = id,
    storeId = storeId,
    storePublicId = storePublicId,
    storeName = aitaTestLocalized("Aita test store"),
    userId = userId,
    userPublicId = userPublicId,
    phoneNumber = "+77001234567",
    email = "$id@aita.local",
    firstName = firstName,
    lastName = lastName,
    roleId = roleId,
    permissions = permissions,
    requestedAtMillis = 1_710_000_000_000L,
    acceptedAtMillis = 1_710_000_100_000L,
    acceptedByUserId = AITA_FLOW_TEST_USER_ID,
    isActive = isActive,
    hasWorkshiftPassword = hasWorkshiftPassword
)

private fun aitaTestWorkerRequest(
    id: String = "worker-request-test",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    storePublicId: String = "AITA-STORE",
    requesterUserId: String = AITA_FLOW_WORKER_USER_ID,
    requesterPublicId: String = AITA_FLOW_WORKER_PUBLIC_ID,
    direction: String = WORKER_REQUEST_DIRECTION_USER_TO_STORE,
    status: String = WORKER_REQUEST_STATUS_PENDING,
    roleId: String = WORKER_ROLE_STANDARD,
    permissions: List<String> = STANDARD_STORE_PERMISSION_IDS
): StoreWorkerRequestDataModel = StoreWorkerRequestDataModel(
    id = id,
    storeId = storeId,
    storePublicId = storePublicId,
    storeName = aitaTestLocalized("Aita test store"),
    requesterUserId = requesterUserId,
    requesterPublicId = requesterPublicId,
    direction = direction,
    invitedByUserId = if (direction == WORKER_REQUEST_DIRECTION_STORE_TO_USER) AITA_FLOW_TEST_USER_ID else null,
    phoneNumber = "+77001234567",
    email = "$id@aita.local",
    firstName = if (requesterUserId == AITA_FLOW_TEST_USER_ID) "Aita" else "Dauren",
    lastName = if (requesterUserId == AITA_FLOW_TEST_USER_ID) "Tester" else "Worker",
    status = status,
    requestedAtMillis = 1_710_000_000_000L,
    decidedAtMillis = if (status == WORKER_REQUEST_STATUS_PENDING || status == WORKER_REQUEST_STATUS_INVITED) null else 1_710_000_200_000L,
    decidedByUserId = if (status == WORKER_REQUEST_STATUS_PENDING || status == WORKER_REQUEST_STATUS_INVITED) null else AITA_FLOW_TEST_USER_ID,
    roleId = roleId,
    permissions = permissions,
    note = "Aita flow test note",
    noteLocalized = aitaTestLocalized("Aita flow test note"),
    responseNote = if (status == WORKER_REQUEST_STATUS_DECLINED) "Declined in test" else null,
    responseNoteLocalized = if (status == WORKER_REQUEST_STATUS_DECLINED) aitaTestLocalized("Declined in test") else emptyList()
)

private fun aitaTestWorkshift(
    id: String = "workshift-test",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    workerMembershipId: String = "worker-test",
    workerUserId: String = AITA_FLOW_WORKER_USER_ID,
    workerPublicId: String = AITA_FLOW_WORKER_PUBLIC_ID,
    isActive: Boolean = true,
    endedAtMillis: Long? = null
): WorkshiftDataModel = WorkshiftDataModel(
    id = id,
    storeId = storeId,
    storePublicId = "AITA-STORE",
    storeName = aitaTestLocalized("Aita test store"),
    workerMembershipId = workerMembershipId,
    workerUserId = workerUserId,
    workerPublicId = workerPublicId,
    workerDisplayName = "Dauren Worker",
    startedAtMillis = 1_710_000_000_000L,
    endedAtMillis = endedAtMillis,
    startedByUserId = workerUserId,
    endedByUserId = endedAtMillis?.let { AITA_FLOW_TEST_USER_ID },
    isActive = isActive
)

private fun aitaTestQuantity(total: Double): QuantityDataModel = QuantityDataModel(
    id = "0",
    immutableUnitName = aitaTestLocalized("pcs"),
    total = total,
    pricedAmount = 1.0,
    roundTotal = true
)

private fun aitaTestPrice(
    price: String = "1000",
    supplierId: String = "supplier-test",
    currency: String = "KZT"
): PriceDataModel = PriceDataModel(
    price = price,
    currency = currency,
    supplierId = supplierId
)

private fun aitaTestGoodsItem(
    id: String = "goods-test",
    name: String = "Test goods",
    barcode: String = "4600000000000",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    activeShelfBatchId: String? = null
): GoodsItemDataModel = GoodsItemDataModel(
    id = id,
    userId = AITA_FLOW_TEST_USER_ID,
    storeId = storeId,
    barcodes = listOf(barcode),
    name = aitaTestLocalized(name),
    measurementUnitId = "0",
    categoryIds = listOf("category-test"),
    salePrices = listOf(aitaTestPrice("1000")),
    returnPrices = listOf(aitaTestPrice("900")),
    supplyPrices = listOf(aitaTestPrice("700")),
    activeShelfBatchId = activeShelfBatchId,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
    isActive = true
)

private fun aitaTestBatch(
    id: String = "batch-test",
    goodsItemId: String = "goods-test",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    quantityTotal: Double = 10.0,
    shelfPriority: Int = 0,
    supplierId: String = "supplier-test",
    supplyPriceValue: String = "700",
    salePriceValue: String? = null
): GoodsBatchDataModel = GoodsBatchDataModel(
    id = id,
    goodsItemId = goodsItemId,
    userId = AITA_FLOW_TEST_USER_ID,
    storeId = storeId,
    supplierId = supplierId,
    quantity = aitaTestQuantity(quantityTotal),
    supplyPrice = aitaTestPrice(supplyPriceValue, supplierId = supplierId),
    salePriceOverride = salePriceValue?.let { aitaTestPrice(it, supplierId = supplierId) },
    deliveredAtMillis = 1_700_000_000_000L,
    expirationDateMillis = 1_800_000_000_000L,
    shelfPosition = "A-1",
    shelfPriority = shelfPriority,
    status = StockBatchStatusDataModel.Delivered,
    createdAtMillis = 1L,
    updatedAtMillis = 1L,
    isActive = true
)

private fun aitaTestAvailability(
    sourceItemId: String = "goods-test",
    sourceBatch: GoodsBatchDataModel? = null,
    destinationBatch: GoodsBatchDataModel? = null
): StockItemBranchAvailabilityDataModel = StockItemBranchAvailabilityDataModel(
    rootStoreId = AITA_FLOW_SOURCE_STORE_ID,
    currentStoreId = AITA_FLOW_SOURCE_STORE_ID,
    sourceGoodsItemId = sourceItemId,
    locations = listOf(
        StockBranchQuantityDataModel(
            storeId = AITA_FLOW_SOURCE_STORE_ID,
            publicId = "MAIN",
            name = aitaTestLocalized("Main branch"),
            isCurrentStore = true,
            goodsItemId = sourceItemId,
            totalQuantity = aitaTestQuantity(sourceBatch?.quantity?.total ?: 0.0),
            batchCount = listOfNotNull(sourceBatch).size,
            batches = listOfNotNull(sourceBatch)
        ),
        StockBranchQuantityDataModel(
            storeId = AITA_FLOW_DESTINATION_STORE_ID,
            publicId = "BRANCH",
            parentStoreId = AITA_FLOW_SOURCE_STORE_ID,
            name = aitaTestLocalized("Second branch"),
            goodsItemId = destinationBatch?.goodsItemId ?: sourceItemId,
            totalQuantity = aitaTestQuantity(destinationBatch?.quantity?.total ?: 0.0),
            batchCount = listOfNotNull(destinationBatch).size,
            batches = listOfNotNull(destinationBatch)
        )
    )
)

private fun aitaTestMoveResult(
    sourceItem: GoodsItemDataModel = aitaTestGoodsItem(id = "move-source-item"),
    destinationItem: GoodsItemDataModel = aitaTestGoodsItem(id = "move-destination-item", storeId = AITA_FLOW_DESTINATION_STORE_ID),
    sourceBatch: GoodsBatchDataModel = aitaTestBatch(id = "move-source-batch", goodsItemId = sourceItem.id, quantityTotal = 6.0),
    destinationBatch: GoodsBatchDataModel = aitaTestBatch(id = "move-destination-batch", goodsItemId = destinationItem.id, storeId = AITA_FLOW_DESTINATION_STORE_ID, quantityTotal = 4.0),
    status: StockBatchMovementStatusDataModel = StockBatchMovementStatusDataModel.Accepted,
    movementId: String = "movement-test"
): StockBatchMoveResultDataModel {
    val movement = StockBatchMovementDataModel(
        id = movementId,
        rootStoreId = AITA_FLOW_SOURCE_STORE_ID,
        sourceStoreId = AITA_FLOW_SOURCE_STORE_ID,
        destinationStoreId = AITA_FLOW_DESTINATION_STORE_ID,
        sourceGoodsItemId = sourceItem.id,
        destinationGoodsItemId = destinationItem.id,
        sourceBatchId = sourceBatch.id,
        destinationBatchId = destinationBatch.id,
        quantity = destinationBatch.quantity,
        movedByUserId = AITA_FLOW_TEST_USER_ID,
        movedByName = "Stock Tester",
        movedAtMillis = 2L,
        status = status,
        acceptedByUserId = if (status == StockBatchMovementStatusDataModel.Accepted) AITA_FLOW_TEST_USER_ID else null,
        acceptedByName = if (status == StockBatchMovementStatusDataModel.Accepted) "Stock Tester" else "",
        acceptedAtMillis = if (status == StockBatchMovementStatusDataModel.Accepted) 3L else null
    )
    val availability = aitaTestAvailability(
        sourceItemId = sourceItem.id,
        sourceBatch = sourceBatch,
        destinationBatch = destinationBatch
    ).copy(movements = listOf(movement))

    return StockBatchMoveResultDataModel(
        sourceBatch = sourceBatch,
        destinationBatch = destinationBatch,
        sourceGoodsItem = sourceItem,
        destinationGoodsItem = destinationItem,
        movement = movement,
        availability = availability,
        requiresAcceptance = status == StockBatchMovementStatusDataModel.PendingAcceptance
    )
}

private fun aitaTestLine(
    goodsItem: GoodsItemDataModel = aitaTestGoodsItem(),
    quantity: Double = 1.0,
    pricePerUnit: Double = 1000.0,
    saleMethodId: String = SALE_METHOD_RETAIL
): GoodsItemInTransactionDataModel = GoodsItemInTransactionDataModel(
    barcode = goodsItem.allBarcodeValues().firstOrNull() ?: "4600000000000",
    quantity = quantity,
    pricePerUnit = pricePerUnit,
    saleMethodId = saleMethodId,
    supplierIdText = "supplier-test",
    name = goodsItem.name,
    goodsItemId = goodsItem.id,
    quantityUnit = aitaTestQuantity(quantity),
    currencyCode = "KZT"
)

private fun aitaTestTransaction(
    id: String = "transaction-test",
    type: String = transactionServerType(0),
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    goodsItem: GoodsItemDataModel = aitaTestGoodsItem(),
    quantity: Double = 1.0,
    pricePerUnit: Double = 1000.0,
    paidCash: Double = quantity * pricePerUnit,
    paidCard: Double = 0.0,
    debtor: DebtorDataModel? = null
): TransactionDataModel = TransactionDataModel(
    id = id,
    workshiftId = 1L,
    type = type,
    storeId = storeId,
    goodsInTransaction = listOf(aitaTestLine(goodsItem = goodsItem, quantity = quantity, pricePerUnit = pricePerUnit)),
    paidCash = paidCash,
    paidCard = paidCard,
    cardPaymentOptionId = 0,
    debtor = debtor,
    timeMillis = 1_710_000_000_000L + id.hashCode().toLong().let { if (it == Long.MIN_VALUE) 0L else kotlin.math.abs(it) % 10_000L },
    clientOperationId = ""
)

private fun aitaTestPaymentDraft(
    transactionTypeIndex: Int,
    clientId: Int,
    paidCash: Double = 0.0,
    paidCard: Double = 0.0,
    debtor: DebtorDataModel? = null
): TransactionPaymentDraftDataModel = TransactionPaymentDraftDataModel(
    transactionTypeIndex = transactionTypeIndex,
    clientId = clientId,
    paymentModeId = "mixed",
    paidCash = paidCash,
    paidCard = paidCard,
    cardPaymentOptionId = 0,
    debtor = debtor,
    cashInputText = paidCash.takeIf { it > 0.0 }?.toString().orEmpty(),
    cardInputText = paidCard.takeIf { it > 0.0 }?.toString().orEmpty(),
    debtInputText = debtor?.debtAmount?.toString().orEmpty(),
    selectedDebtorId = debtor?.id,
    updatedAtMillis = 1L
)

private fun aitaTestReceiptSnapshot(
    transaction: TransactionDataModel,
    draft: TransactionPaymentDraftDataModel = aitaTestPaymentDraft(0, 0, paidCash = transaction.paidCash, paidCard = transaction.paidCard)
): TransactionReceiptSnapshotDataModel = TransactionReceiptSnapshotDataModel(
    transaction = transaction,
    store = null,
    lines = transaction.goodsInTransaction.mapIndexed { index, line ->
        TransactionReceiptLineDataModel(
            index = index + 1,
            goodsItemId = line.goodsItemId.orEmpty(),
            name = line.name,
            barcode = line.barcode,
            quantity = line.quantityUnit ?: aitaTestQuantity(line.quantity),
            pricePerUnit = line.pricePerUnit,
            currencyCode = line.currencyCode ?: "KZT",
            currencySymbol = "₸",
            saleMethodId = line.saleMethodId,
            saleMethodName = saleMethodLocalizedName(line.saleMethodId)
        )
    },
    paymentDraft = draft,
    currencyCode = "KZT",
    currencySymbol = "₸",
    cashierName = "Aita Tester",
    cashierPhoneNumber = "+77000000001",
    cashierEmail = "tester@aita.local"
)

private fun aitaTestDebtor(
    id: String = "debtor-test",
    debtAmount: Double = 500.0,
    firstName: String = "Dina",
    lastName: String = "Debtor"
): DebtorDataModel = DebtorDataModel(
    id = id,
    email = "$id@aita.local",
    debtAmount = debtAmount,
    currency = "KZT",
    phoneNumber = "+77000000002",
    firstName = firstName,
    lastName = lastName,
    debtCreatedAtMillis = 1_710_000_000_000L,
    originalDebtAmount = debtAmount,
    transactionIds = emptyList()
)



private fun assertAitaDoubleEquals(expected: Double, actual: Double, tolerance: Double = 0.001) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= tolerance,
        "Expected $expected ±$tolerance but got $actual"
    )
}

private data class AitaAnalyticsFixture(
    val startMillis: Long,
    val endMillis: Long,
    val supplierAlphaId: String,
    val supplierBetaId: String,
    val categoryFruitId: String,
    val categoryBakeryId: String,
    val apple: GoodsItemDataModel,
    val banana: GoodsItemDataModel,
    val stock: List<GoodsItemDataModel>,
    val batches: List<GoodsBatchDataModel>,
    val transactions: List<TransactionDataModel>
)

private fun aitaAnalyticsFixture(): AitaAnalyticsFixture {
    val startMillis = 1_710_000_000_000L
    val supplierAlphaId = "supplier-alpha"
    val supplierBetaId = "supplier-beta"
    val categoryFruitId = "category-fruit"
    val categoryBakeryId = "category-bakery"
    val apple = aitaTestGoodsItem(
        id = "analytics-apple",
        name = "Analytics apple",
        barcode = "4600000000101",
        activeShelfBatchId = "analytics-apple-batch"
    ).copy(
        categoryIds = listOf(categoryFruitId),
        salePrices = listOf(aitaTestPrice("1000", supplierId = supplierAlphaId)),
        supplyPrices = listOf(aitaTestPrice("600", supplierId = supplierAlphaId))
    )
    val banana = aitaTestGoodsItem(
        id = "analytics-banana",
        name = "Analytics banana",
        barcode = "4600000000102",
        activeShelfBatchId = "analytics-banana-batch"
    ).copy(
        categoryIds = listOf(categoryBakeryId),
        salePrices = listOf(aitaTestPrice("800", supplierId = supplierBetaId)),
        supplyPrices = listOf(aitaTestPrice("300", supplierId = supplierBetaId))
    )
    val appleBatch = aitaTestBatch(
        id = "analytics-apple-batch",
        goodsItemId = apple.id,
        quantityTotal = 10.0,
        supplierId = supplierAlphaId,
        supplyPriceValue = "600",
        salePriceValue = "1000"
    ).copy(status = StockBatchStatusDataModel.OnShelf, expirationDateMillis = null, updatedAtMillis = 10L)
    val bananaBatch = aitaTestBatch(
        id = "analytics-banana-batch",
        goodsItemId = banana.id,
        quantityTotal = 2.0,
        supplierId = supplierBetaId,
        supplyPriceValue = "300",
        salePriceValue = "800"
    ).copy(status = StockBatchStatusDataModel.OnShelf, expirationDateMillis = null, updatedAtMillis = 10L)
    val saleApple = aitaTestTransaction(
        id = "analytics-sale-apple",
        type = transactionServerType(0),
        goodsItem = apple,
        quantity = 2.0,
        pricePerUnit = 1000.0,
        paidCash = 1000.0,
        paidCard = 500.0
    ).copy(timeMillis = startMillis + 1_000L)
    val saleBanana = aitaTestTransaction(
        id = "analytics-sale-banana",
        type = transactionServerType(0),
        goodsItem = banana,
        quantity = 3.0,
        pricePerUnit = 800.0,
        paidCash = 0.0,
        paidCard = 2400.0
    ).copy(timeMillis = startMillis + 3_600_000L)
    val returnApple = aitaTestTransaction(
        id = "analytics-return-apple",
        type = transactionServerType(1),
        goodsItem = apple,
        quantity = 1.0,
        pricePerUnit = 1000.0,
        paidCash = 1000.0,
        paidCard = 0.0
    ).copy(timeMillis = startMillis + 7_200_000L)
    val supplyApple = aitaTestTransaction(
        id = "analytics-supply-apple",
        type = transactionServerType(2),
        goodsItem = apple,
        quantity = 5.0,
        pricePerUnit = 600.0,
        paidCash = 0.0,
        paidCard = 3000.0
    ).copy(timeMillis = startMillis + 10_800_000L)

    return AitaAnalyticsFixture(
        startMillis = startMillis,
        endMillis = startMillis + 86_400_000L,
        supplierAlphaId = supplierAlphaId,
        supplierBetaId = supplierBetaId,
        categoryFruitId = categoryFruitId,
        categoryBakeryId = categoryBakeryId,
        apple = apple,
        banana = banana,
        stock = listOf(apple, banana),
        batches = listOf(appleBatch, bananaBatch),
        transactions = listOf(saleApple, saleBanana, returnApple, supplyApple)
    )
}

private fun aitaTestSupplier(
    id: String = "supplier-test",
    name: String = "Aita supplier",
    userIds: List<String> = listOf(AITA_FLOW_TEST_USER_ID),
    categoryIds: List<String> = listOf("category-test")
): SupplierDataModel = SupplierDataModel(
    id = id,
    userIds = userIds,
    typeIds = listOf("wholesale_supplier"),
    categoryIds = categoryIds,
    name = aitaTestLocalized(name),
    phoneNumbers = listOf("+77005550000"),
    emails = listOf("$id@aita.local"),
    addedAt = 1_710_000_000_000L,
    isActive = true
)

private fun aitaTestSupplierGoodsPrice(
    id: String = "supplier-price-test",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    supplierId: String = "supplier-test",
    goodsItemId: String = "goods-test",
    supplyPriceValue: String = "700"
): SupplierGoodsPriceDataModel = SupplierGoodsPriceDataModel(
    id = id,
    userId = AITA_FLOW_TEST_USER_ID,
    storeId = storeId,
    supplierId = supplierId,
    goodsItemId = goodsItemId,
    supplyPrice = aitaTestPrice(supplyPriceValue, supplierId = supplierId),
    minOrderQuantity = aitaTestQuantity(1.0),
    packageQuantity = aitaTestQuantity(1.0),
    supplierBarcode = "SUP-$goodsItemId",
    supplierGoodsName = "Supplier goods $goodsItemId",
    lastUsedAtMillis = 1_710_000_000_000L,
    createdAtMillis = 1_710_000_000_000L,
    updatedAtMillis = 1_710_000_000_000L,
    isActive = true
)

private fun aitaTestSupplierOrderWithLines(
    id: String = "supplier-order-test",
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    supplierId: String = "supplier-test",
    goodsItemId: String = "goods-test",
    requestedQuantity: Double = 6.0,
    expectedSupplyPriceValue: String = "700",
    status: SupplierOrderStatusDataModel = SupplierOrderStatusDataModel.Draft
): SupplierOrderWithLinesDataModel {
    val line = SupplierOrderLineDataModel(
        id = "$id-line-1",
        orderId = id,
        goodsItemId = goodsItemId,
        requestedQuantity = aitaTestQuantity(requestedQuantity),
        expectedSupplyPrice = aitaTestPrice(expectedSupplyPriceValue, supplierId = supplierId),
        desiredExpirationDateMillis = 1_800_000_000_000L,
        additionalNotes = "Please deliver fresh stock",
        additionalNotesLocalized = aitaTestLocalized("Please deliver fresh stock"),
        isActive = true
    )
    val order = SupplierOrderDataModel(
        id = id,
        userId = AITA_FLOW_TEST_USER_ID,
        storeId = storeId,
        supplierId = supplierId,
        amount = aitaTestPrice((requestedQuantity * expectedSupplyPriceValue.toDouble()).toString(), supplierId = supplierId),
        orderedAtMillis = 1_710_000_000_000L,
        desiredDeliveryTimeMillis = 1_710_086_400_000L,
        additionalNotes = "Launch replenishment",
        additionalNotesLocalized = aitaTestLocalized("Launch replenishment"),
        status = status,
        createdAtMillis = 1_710_000_000_000L,
        updatedAtMillis = 1_710_000_000_000L,
        isActive = true
    )
    return SupplierOrderWithLinesDataModel(order = order, lines = listOf(line))
}

private fun aitaTestNotification(
    id: String = "notification-test",
    message: String = "Aita test notification",
    title: String = "Aita notification",
    type: NotificationType = NotificationType.Neutral,
    userId: String? = AITA_FLOW_TEST_USER_ID,
    storeId: String? = AITA_FLOW_SOURCE_STORE_ID,
    category: String = "general",
    createdAtMillis: Long = getCurrentTimeMillis(),
    readAtMillis: Long? = null,
    isSavedOnServer: Boolean = true
): NotificationDataModel = NotificationDataModel(
    id = id,
    userId = userId,
    storeId = storeId,
    title = title,
    message = message,
    type = type,
    category = category,
    source = "server",
    metadata = mapOf("test" to "true"),
    createdAtMillis = createdAtMillis,
    shownAtMillis = createdAtMillis,
    readAtMillis = readAtMillis,
    isSavedOnServer = isSavedOnServer
)

private fun aitaTestOperationLog(
    id: String = "operation-log-test",
    rootStoreId: String = AITA_FLOW_SOURCE_STORE_ID,
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    action: String = "test_action",
    entityType: String = "test_entity",
    entityId: String? = "test-entity-id",
    title: String = "Test operation",
    details: String = "Test operation details",
    createdAtMillis: Long = 1_710_000_000_000L
): OperationLogDataModel = OperationLogDataModel(
    id = id,
    rootStoreId = rootStoreId,
    storeId = storeId,
    storePublicId = if (storeId == AITA_FLOW_SOURCE_STORE_ID) "AITA-STORE" else "AITA-BRANCH",
    storeName = aitaTestLocalized(if (storeId == AITA_FLOW_SOURCE_STORE_ID) "Aita test store" else "Aita test branch"),
    actorUserId = AITA_FLOW_TEST_USER_ID,
    actorPublicId = "AITA-TEST-USER",
    actorDisplayName = "Aita Tester",
    workshiftId = null,
    action = action,
    entityType = entityType,
    entityId = entityId,
    title = aitaTestLocalized(title),
    details = aitaTestLocalized(details),
    metadata = mapOf("entity_type" to entityType, "action" to action),
    createdAtMillis = createdAtMillis
)

private fun AitaFlowTestEnvironment.recordAitaTestOperationLog(
    action: String,
    entityType: String,
    entityId: String?,
    storeId: String = AITA_FLOW_SOURCE_STORE_ID,
    title: String,
    details: String = ""
) {
    val rootStoreId = stores.findAitaTestStoreOrBranch(storeId)?.parentStoreId ?: storeId.ifBlank { AITA_FLOW_SOURCE_STORE_ID }
    val log = aitaTestOperationLog(
        id = "log-$action-${entityId.orEmpty()}-${operationLogs.size + 1}",
        rootStoreId = rootStoreId,
        storeId = storeId.ifBlank { AITA_FLOW_SOURCE_STORE_ID },
        action = action,
        entityType = entityType,
        entityId = entityId,
        title = title,
        details = details.ifBlank { title },
        createdAtMillis = getCurrentTimeMillis() + operationLogs.size
    )
    operationLogs = (listOf(log) + operationLogs).distinctBy { it.id }
}

private fun List<GoodsItemDataModel>.upsertAitaTestItem(item: GoodsItemDataModel): List<GoodsItemDataModel> =
    filterNot { it.id == item.id } + item

private fun List<GoodsBatchDataModel>.upsertAitaTestBatch(batch: GoodsBatchDataModel): List<GoodsBatchDataModel> =
    filterNot { it.id == batch.id } + batch

private fun List<TransactionDataModel>.upsertAitaTestTransaction(transaction: TransactionDataModel): List<TransactionDataModel> =
    filterNot { existing -> existing.id == transaction.id || (transaction.clientOperationId.isNotBlank() && existing.clientOperationId == transaction.clientOperationId) } + transaction

private fun List<DebtorDataModel>.upsertAitaTestDebtor(debtor: DebtorDataModel): List<DebtorDataModel> =
    filterNot { it.id == debtor.id } + debtor



private fun List<SupplierDataModel>.upsertAitaTestSupplier(supplier: SupplierDataModel): List<SupplierDataModel> =
    filterNot { it.id == supplier.id } + supplier

private fun List<SupplierGoodsPriceDataModel>.upsertAitaTestSupplierGoodsPrice(price: SupplierGoodsPriceDataModel): List<SupplierGoodsPriceDataModel> =
    filterNot { it.id == price.id } + price

private fun List<SupplierOrderWithLinesDataModel>.upsertAitaTestSupplierOrder(orderWithLines: SupplierOrderWithLinesDataModel): List<SupplierOrderWithLinesDataModel> =
    filterNot { it.order.id == orderWithLines.order.id } + orderWithLines

private fun List<NotificationDataModel>.upsertAitaTestNotification(notification: NotificationDataModel): List<NotificationDataModel> =
    filterNot { it.id == notification.id } + notification

private fun List<StoreDataModel>.findAitaTestStoreOrBranch(id: String?): StoreDataModel? {
    val cleanId = id?.takeIf { it.isNotBlank() } ?: return null
    return firstOrNull { it.id == cleanId }
        ?: firstNotNullOfOrNull { store -> store.branches.findAitaTestStoreOrBranch(cleanId) }
}

private fun List<StoreDataModel>.upsertAitaTestStore(store: StoreDataModel): List<StoreDataModel> {
    val parentStoreId = store.parentStoreId?.takeIf { it.isNotBlank() }
    if (parentStoreId != null) {
        var insertedIntoParent = false
        val updatedRoots = filterNot { it.id == store.id }.map { root ->
            if (root.id == parentStoreId || root.branches.findAitaTestStoreOrBranch(parentStoreId) != null || root.branches.any { it.id == store.id }) {
                insertedIntoParent = true
                root.copy(branches = root.branches.filterNot { it.id == store.id }.upsertAitaTestStore(store))
            } else {
                root
            }
        }
        return if (insertedIntoParent) updatedRoots else updatedRoots + store
    }

    val existing = findAitaTestStoreOrBranch(store.id)
    return if (existing?.parentStoreId != null) {
        upsertAitaTestStore(store.copy(parentStoreId = existing.parentStoreId))
    } else {
        filterNot { it.id == store.id } + store
    }
}

private fun List<StoreDataModel>.removeAitaTestStore(storeId: String): List<StoreDataModel> =
    filterNot { it.id == storeId || it.parentStoreId == storeId }.map { root ->
        root.copy(branches = root.branches.removeAitaTestStore(storeId))
    }

private fun List<StoreWorkerDataModel>.upsertAitaTestWorker(worker: StoreWorkerDataModel): List<StoreWorkerDataModel> =
    filterNot { it.id == worker.id } + worker

private fun List<StoreWorkerRequestDataModel>.upsertAitaTestWorkerRequest(workerRequest: StoreWorkerRequestDataModel): List<StoreWorkerRequestDataModel> =
    filterNot { it.id == workerRequest.id } + workerRequest
