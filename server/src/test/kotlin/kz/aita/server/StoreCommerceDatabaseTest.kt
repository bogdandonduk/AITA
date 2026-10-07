package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Exercises the actual normalization/mutation methods against an isolated disposable schema. */
class StoreCommerceDatabaseTest {
    private val owner = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val store = UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val otherStore = UUID.fromString("33333333-3333-4333-8333-333333333333")
    private val item = UUID.fromString("44444444-4444-4444-8444-444444444444")
    private val first = UUID.fromString("55555555-5555-4555-8555-555555555555")
    private val second = UUID.fromString("66666666-6666-4666-8666-666666666666")
    private val price = PriceDataModel("10", "KZT", "")
    private fun q(total: Double) = QuantityDataModel("0", emptyList(), total, 1.0, true)

    private fun fixture(block: (Database) -> Unit) {
        val url = System.getenv("AITA_RETURN_TEST_JDBC")?.takeIf { it.isNotBlank() }
            ?: System.getenv("AITA_APP_STATE_TEST_JDBC").orEmpty()
        assumeTrue("Requires disposable aita_return_test or aita_app_state_test database", url.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_(return|app_state)_test").matches(url))
        val user = System.getProperty("user.name")
        val schema = "return_test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("CREATE SCHEMA $schema") } }
        val db = Database.connect("$url?currentSchema=$schema", driver = "org.postgresql.Driver", user = user, password = "")
        try {
            transaction(db) {
                SchemaUtils.create(Users,Stores,StoreUsers,StoreWorkerMemberships,Workshifts,OperationLogs,StockItems,StockBatchesV2,Transactions,TransactionReturnItems,StockBatchMovements)
                exec("ALTER TABLE transactions DROP COLUMN buyer")
                exec(javaClass.getResource("/db/migration/V132__store_buyers_and_stock_writeoffs.sql")!!.readText())
                Users.insert {
                    it[Users.id]=owner;it[publicId]="COMMERCE";it[phoneNumber]="test-commerce";it[email]="commerce@example.invalid"
                    it[firstName]="Test";it[lastName]="Owner";it[countryLocale]="KZ";it[passwordHash]="not-a-real-hash"
                }
                listOf(store,otherStore).forEachIndexed {index,id->Stores.insert {
                    it[Stores.id]=id;it[publicId]="COMMERCE$index";it[ownerUserIds]=listOf(owner.toString());it[storeTypeIds]=emptyList()
                    it[name]=listOf(LocalizedStringDataModel("en","Test"));it[alias]=emptyList();it[description]=emptyList();it[companyForms]=emptyList()
                    it[location]=LocationDataModel();it[phoneNumbers]=emptyList();it[emails]=emptyList();it[countryLocales]=emptyList()
                }}
                exec(javaClass.getResource("/db/migration/V118__stock_batch_kind.sql")!!.readText())
                StockItems.insert {
                    it[id] = item; it[userId] = owner; it[storeId] = store
                    it[barcodes] = listOf("123456789"); it[name] = listOf(LocalizedStringDataModel("en", "Milk"))
                    it[description] = emptyList(); it[measurementUnitId] = "0"; it[categoryIds] = emptyList()
                    it[salePrices] = listOf(price); it[returnPrices] = listOf(price); it[supplyPrices] = listOf(price)
                    it[isQuickItem] = false; it[imagePaths] = emptyList(); it[activeShelfBatchId] = first
                    it[createdAtMillis] = 1L; it[updatedAtMillis] = 1L; it[isActive] = true
                }
                seedBatch(first, 2.0, 0)
                seedBatch(second, 8.0, 1)
            }
            block(db)
        } finally {
            TransactionManager.closeAndUnregister(db)
            DriverManager.getConnection(url, user, "").use { c -> c.createStatement().use { it.execute("DROP SCHEMA $schema CASCADE") } }
        }
    }

    private fun seedBatch(batch: UUID, total: Double, priority: Int, kind: StockBatchKindDataModel = StockBatchKindDataModel.NORMAL) {
        StockBatchesV2.insert {
            it[id] = batch; it[goodsItemId] = item; it[userId] = owner; it[storeId] = store
            it[quantity] = q(total); it[supplyPrice] = price; it[discounts] = emptyList()
            it[shelfPriority] = priority; it[status] = StockBatchStatusDataModel.Delivered.name; it[StockBatchesV2.kind] = kind.name
            it[createdAtMillis] = 1L; it[updatedAtMillis] = 1L; it[isActive] = true
        }
    }
    private fun line(total: Double = 1.0) = GoodsItemInTransactionDataModel(
        barcode = "123456789", quantity = total, pricePerUnit = 10.0, goodsItemId = item.toString(), currencyCode = "KZT")
    private fun transactionModel(type: String, lines: List<GoodsItemInTransactionDataModel>) = TransactionDataModel(
        id = "", workshiftId = 0L, type = type, storeId = store.toString(), goodsInTransaction = lines,
        paidCash = lines.sumOf { it.quantity * it.pricePerUnit }, paidCard = 0.0, cardPaymentOptionId = 0,
        timeMillis = 100L, clientOperationId = "txn-${UUID.randomUUID()}")
    private fun save(type: String, lines: List<GoodsItemInTransactionDataModel>): TransactionDataModel {
        val normalized = assertNotNull(normalizeTransactionGoodsInsideTransaction(store, type, lines, listOf(store)).lines)
        val model = transactionModel(type, normalized)
        val mutated = assertNotNull(applyTransactionStockMutationInsideTransaction(owner, store, model, 100L, listOf(store)))
        val saved = model.copy(id = UUID.randomUUID().toString(), goodsInTransaction = mutated)
        Transactions.insert {
            it[id] = UUID.fromString(saved.id); it[userId] = owner; it[storeId] = store; it[workshiftId] = 0L
            it[Transactions.type] = type; it[goodsInTransaction] = saved.goodsInTransaction
            it[paidCash] = saved.paidCash; it[paidCard] = 0.0; it[cardPaymentOptionId] = 0
            it[timeMillis] = saved.timeMillis; it[clientOperationId] = saved.clientOperationId
        }
        syncTransactionReturnItemsInsideTransaction(owner, store, UUID.fromString(saved.id), saved, saved.clientOperationId, saved.timeMillis)
        return saved
    }
    private fun returnLine(sale: TransactionDataModel, total: Double = 1.0) = line(total).copy(
        originalTransactionId = sale.id, originalTransactionLineIndex = 0, originalClientOperationId = sale.clientOperationId,
        stockBatchId = first.toString())
    private fun total(batch: UUID) = StockBatchesV2.selectAll().where { StockBatchesV2.id eq batch }.single()[StockBatchesV2.quantity].total

    private fun command(q:Double=1.0)=StockWriteOffCommand(UUID.randomUUID().toString(),store.toString(),first.toString(),q,WriteOffReason.DAMAGED,"Broken jar")
    private fun buyer()=StoreBuyer(UUID.randomUUID().toString(),store.toString(),"Buyer",email="buyer@example.test",discountPercent=10.0)
    @Test fun retryingWriteOffReturnsOriginalRecordAndNeverSubtractsTwice()=fixture {db->
        val c=command()
        val firstResult=transaction(db){writeOffInsideTransaction(owner,store,c)}
        val retry=transaction(db){writeOffInsideTransaction(owner,store,c)}
        assertEquals(firstResult,retry)
        transaction(db){assertEquals(1.0,total(first));assertEquals(1L,StockWriteOffs.selectAll().count());assertEquals(10.0,retry.record.cost)}
    }
    @Test fun replayWithChangedContentCannotSpendTwice()=fixture {db->
        val c=command();transaction(db){writeOffInsideTransaction(owner,store,c)}
        assertFailsWith<CommerceProblem> {transaction(db){writeOffInsideTransaction(owner,store,c.copy(quantity=2.0))}}
        transaction(db){assertEquals(1.0,total(first));assertEquals(1L,StockWriteOffs.selectAll().count())}
    }
    @Test fun zeroRemainderSelectsNextShelfBatchAndOverdrawHasNoLedgerEntry()=fixture {db->
        transaction(db){
            writeOffInsideTransaction(owner,store,command(2.0))
            assertEquals(0.0,total(first));assertEquals(second,StockItems.selectAll().single()[StockItems.activeShelfBatchId])
            assertEquals(StockBatchStatusDataModel.WrittenOff.name,StockBatchesV2.selectAll().where {StockBatchesV2.id eq first}.single()[StockBatchesV2.status])
        }
        assertFailsWith<CommerceProblem>{transaction(db){writeOffInsideTransaction(owner,store,command(1.0))}}
        transaction(db){assertEquals(1L,StockWriteOffs.selectAll().count())}
    }
    @Test fun concurrentDeductionsSerializeAgainstActualBalance()=fixture {db->
        val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try {
            val jobs=List(2){pool.submit(Callable {check(start.await(5,TimeUnit.SECONDS));try {transaction(db){writeOffInsideTransaction(owner,store,command(1.5))};true} catch(_:CommerceProblem){false}})}
            // Weight quantities permit this test's fractional debit.
            transaction(db){StockBatchesV2.update({StockBatchesV2.id eq first}){it[quantity]=q(2.0).copy(roundTotal=false,id="1")}}
            start.countDown();assertEquals(1,jobs.count {it.get(15,TimeUnit.SECONDS)})
            transaction(db){assertEquals(0.5,total(first));assertEquals(1L,StockWriteOffs.selectAll().count())}
        } finally {pool.shutdownNow()}
    }
    @Test fun permissionsAndStoreBoundaryRejectUnauthorizedDeductions()=fixture {db->
        assertFailsWith<CommerceProblem>{transaction(db){writeOffInsideTransaction(UUID.randomUUID(),store,command())}}
        assertFailsWith<CommerceProblem>{transaction(db){writeOffInsideTransaction(owner,otherStore,command().copy(storeId=otherStore.toString()))}}
        transaction(db){assertEquals(2.0,total(first));assertTrue(StockWriteOffs.selectAll().empty())}
    }
    @Test fun repeatedFractionalWriteOffsReachExactlyZero()=fixture {db->
        transaction(db){StockBatchesV2.update({StockBatchesV2.id eq first}){it[quantity]=q(0.3).copy(roundTotal=false,id="1")}}
        transaction(db){writeOffInsideTransaction(owner,store,command(0.1));assertEquals(0.2,total(first))}
        transaction(db){
            writeOffInsideTransaction(owner,store,command(0.2))
            assertEquals(0.0,total(first));assertEquals(2L,StockWriteOffs.selectAll().count())
            assertEquals(second,StockItems.selectAll().single()[StockItems.activeShelfBatchId])
        }
    }
    @Test fun buyerUpdatesHaveRevisionFencesAndImmutableReplayResults()=fixture {db->
        val request=BuyerWrite(UUID.randomUUID().toString(),buyer())
        val saved=transaction(db){saveBuyerInsideTransaction(owner,store,request)}
        assertEquals(1L,saved.revision)
        assertEquals(saved,transaction(db){saveBuyerInsideTransaction(owner,store,request)})
        assertFailsWith<CommerceProblem>{transaction(db){saveBuyerInsideTransaction(owner,store,request.copy(operationId=UUID.randomUUID().toString()))}}
        val changed=transaction(db){saveBuyerInsideTransaction(owner,store,BuyerWrite(UUID.randomUUID().toString(),saved.copy(discountPercent=20.0)))}
        assertEquals(2L,changed.revision)
        assertEquals(saved,transaction(db){saveBuyerInsideTransaction(owner,store,request)})
        assertFailsWith<CommerceProblem>{transaction(db){canonicalBuyerInsideTransaction(store,saved.receiptSnapshot())}}
        assertEquals(20.0,transaction(db){canonicalBuyerInsideTransaction(store,changed.receiptSnapshot())}?.discountPercent)
    }
    @Test fun foreignParentCopyIsDeniedAndContactCannotMoveBetweenStores()=fixture {db->
        val saved=transaction(db){saveBuyerInsideTransaction(owner,store,BuyerWrite(UUID.randomUUID().toString(),buyer()))}
        assertFailsWith<CommerceProblem>{transaction(db){saveBuyerInsideTransaction(owner,otherStore,BuyerWrite(UUID.randomUUID().toString(),saved.copy(storeId=otherStore.toString())))}}
        assertFailsWith<CommerceProblem>{transaction(db){saveBuyerInsideTransaction(owner,store,BuyerWrite(UUID.randomUUID().toString(),buyer().copy(sourceParentBuyerId=UUID.randomUUID().toString())))}}
    }
    @Test fun parentBuyerCopyCanBeCustomizedWithoutChangingSource()=fixture {db->
        transaction(db){
            val migration=javaClass.getResource("/db/migration/V46__paging_user_finances_and_store_subscriptions.sql")!!.readText()
            exec(migration.substringAfter("CREATE TABLE IF NOT EXISTS store_subscription_states (").substringBefore("CREATE TABLE IF NOT EXISTS store_subscription_charge_events").let {"CREATE TABLE store_subscription_states ("+it})
            exec("ALTER TABLE store_subscription_states ADD access_kind TEXT DEFAULT 'lifetime', ADD region_code TEXT DEFAULT 'KZ', ADD renewal_price_minor BIGINT DEFAULT 0, ADD currency_code TEXT DEFAULT 'KZT', ADD revision BIGINT DEFAULT 1")
            Stores.update({Stores.id eq otherStore}){it[parentStoreId]=store}
            exec("INSERT INTO store_subscription_states(store_id,owner_user_id,status,plan_id,current_period_start_millis) VALUES ('$otherStore','$owner','active','internal_lifetime',1)")
        }
        val source=transaction(db){saveBuyerInsideTransaction(owner,store,BuyerWrite(UUID.randomUUID().toString(),buyer()))}
        val copy=transaction(db){saveBuyerInsideTransaction(owner,otherStore,BuyerWrite(UUID.randomUUID().toString(),source.copy(id=UUID.randomUUID().toString(),storeId=otherStore.toString(),sourceParentBuyerId=source.id,revision=0,discountPercent=25.0)))}
        assertEquals(25.0,copy.discountPercent)
        transaction(db){assertEquals(10.0,canonicalBuyerInsideTransaction(store,source.receiptSnapshot())?.discountPercent)}
        assertFailsWith<CommerceProblem>{transaction(db){saveBuyerInsideTransaction(owner,otherStore,BuyerWrite(UUID.randomUUID().toString(),copy.copy(id=UUID.randomUUID().toString(),revision=0)))}}
    }
    @Test fun migrationMakesAcceptedLedgersImmutable()=fixture {db->
        val c=command();transaction(db){writeOffInsideTransaction(owner,store,c)}
        assertFailsWith<org.jetbrains.exposed.exceptions.ExposedSQLException>{transaction(db){StockWriteOffs.update({StockWriteOffs.id eq UUID.fromString(c.id)}){it[time]=0}}}
        transaction(db){assertEquals(1.0,total(first));assertEquals(c,StockWriteOffs.selectAll().single()[StockWriteOffs.request])}
    }
    @Test fun missingAuditDependencyCannotAcknowledgeRolledBackWriteOff()=fixture {db->
        transaction(db){exec("DROP TABLE workshifts")}
        assertFailsWith<org.jetbrains.exposed.exceptions.ExposedSQLException>{transaction(db){writeOffInsideTransaction(owner,store,command())}}
        transaction(db){assertEquals(2.0,total(first));assertTrue(StockWriteOffs.selectAll().empty())}
    }
    @Test fun lineAndCartDiscountsUseServerGrossAndRemainOnReturn()=fixture {db->transaction(db){
        val parts=SaleDiscounts(itemPercent=10.0,cartPercent=20.0)
        val sale=save("purchase",listOf(line().copy(pricePerUnit=0.01,discounts=parts,quickDiscountPercent=99.0)))
        assertEquals(7.2,sale.goodsInTransaction.single().pricePerUnit)
        assertEquals(28.0,sale.goodsInTransaction.single().quickDiscountPercent)
        val back=save("return",listOf(returnLine(sale)))
        assertEquals(parts,back.goodsInTransaction.single().discounts);assertEquals(7.2,back.goodsInTransaction.single().pricePerUnit)
        assertNull(normalizeTransactionGoodsInsideTransaction(store,"purchase",listOf(line().copy(discounts=SaleDiscounts(buyerPercent=50.0))),listOf(store)).lines)
    }}
}
