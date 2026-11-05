package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.*

class SupplierPendingRequestsTest {
    companion object { private fun id(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0') }
    private class Session(var account: String = id(1), var generation: Long = 1) {
        fun owner() = SupplierSessionOwner(account, generation, account, { account }, { generation })
    }
    private class Disk {
        val values = mutableMapOf<String,String>()
        var loseWrites = false
        suspend fun read(key: String) = values[key]
        suspend fun write(key: String, value: String) { if (!loseWrites) values[key] = value }
        fun store() = SupplierPendingRequestStore(::read, ::write)
    }
    private val q = QuantityDataModel("0", emptyList(), 5.0, 1.0, true)
    private fun create() = SupplierOrderWithLinesDataModel(SupplierOrderDataModel(storeId = id(2), supplierId = id(3), orderedAtMillis = 1, status = SupplierOrderStatusDataModel.Sent), listOf(SupplierOrderLineDataModel(orderId = "", goodsItemId = id(4), requestedQuantity = q)))
    private fun receive() = ReceiveSupplierOrderRequestDataModel(id(5), listOf(ReceiveSupplierOrderLineDataModel(id(6), id(4), q, PriceDataModel("10","KZT",id(3)))))
    @Test fun createRetryAfterRestartKeepsIdentityAndOriginalTimestamp() = runTest {
        val disk=Disk();val session=Session();val first=disk.store().prepareCreate(session.owner(),create())
        assertTrue(first.order.id.isNotBlank())
        val retried=disk.store().prepareCreate(session.owner(),create().copy(order=create().order.copy(orderedAtMillis=999)))
        assertEquals(first,retried)
        disk.store().completeCreate(session.owner(),retried)
        assertNotEquals(first.order.id,disk.store().prepareCreate(session.owner(),create()).order.id)
    }
    @Test fun uncertainRequestCannotSilentlyChangeSupplierOrQuantity() = runTest {
        val disk=Disk();val session=Session();disk.store().prepareCreate(session.owner(),create())
        assertFailsWith<SupplierPendingRequestConflict> { disk.store().prepareCreate(session.owner(),create().copy(order=create().order.copy(supplierId=id(7)))) }
        assertFailsWith<SupplierPendingRequestConflict> { disk.store().prepareCreate(session.owner(),create().copy(lines=create().lines.map{it.copy(requestedQuantity=q.copy(total=3.0))})) }
    }
    @Test fun receiptRetryKeepsReceiptIdButConfirmedNextDeliveryGetsNewOne() = runTest {
        val disk=Disk();val owner=Session().owner();val first=disk.store().prepareReceipt(owner,receive())
        assertEquals(first,disk.store().prepareReceipt(owner,receive()))
        disk.store().completeReceipt(owner,first)
        assertNotEquals(first.receiptId,disk.store().prepareReceipt(owner,receive()).receiptId)
    }
    @Test fun storageFailureAndAccountChangeNeverReturnARequestToSend() = runTest {
        val disk=Disk();disk.loseWrites=true;val session=Session()
        assertFails { disk.store().prepareCreate(session.owner(),create()) }
        disk.loseWrites=false;val owner=session.owner();session.account=id(8);session.generation++
        assertFails { disk.store().prepareCreate(owner,create()) }
        assertTrue(disk.values.isEmpty())
    }
    @Test fun pendingRequestsAreNotVisibleToAnotherAccount() = runTest {
        val disk=Disk();val a=Session();disk.store().prepareCreate(a.owner(),create())
        val b=Session(id(8));assertNull(disk.store().pendingCreate(b.owner(),id(2),id(4)))
    }
    @Test fun concurrentIndependentTabsKeepBothPendingRequests() = runTest {
        val disk=Disk();val both=CompletableDeferred<Unit>();var reads=0
        suspend fun read(key:String):String? { val value=disk.read(key);if(value==null){reads++;if(reads==2)both.complete(Unit);both.await()};return value }
        val one=SupplierPendingRequestStore(::read,disk::write);val two=SupplierPendingRequestStore(::read,disk::write)
        val owner=Session().owner();val a=async { one.prepareCreate(owner,create()) }
        val other=create().copy(lines=create().lines.map { it.copy(goodsItemId=id(9)) })
        val b=async { two.prepareCreate(owner,other) };a.await();b.await()
        assertNotNull(disk.store().pendingCreate(owner,id(2),id(4)))
        assertNotNull(disk.store().pendingCreate(owner,id(2),id(9)))
    }
    @Test fun malformedForeignAndOversizedPendingRequestsNeverReachSend() = runTest {
        val disk=Disk();val owner=Session().owner();disk.store().prepareCreate(owner,create());val key=disk.values.keys.single();val original=disk.values.getValue(key)
        disk.values[key]="x".repeat(1_000_001);assertFails { disk.store().pendingCreate(owner,id(2),id(4)) }
        disk.values[key]=original.replace(id(1),id(8));assertFails { disk.store().pendingCreate(owner,id(2),id(4)) }
        disk.values[key]=jsonBase.encodeToString(jsonBase.decodeFromString<SupplierPendingRequest>(original).copy(schema=2));assertFails { disk.store().pendingCreate(owner,id(2),id(4)) }
        disk.values[key]=original.replace(id(4),id(9));assertFails { disk.store().pendingCreate(owner,id(2),id(4)) }
    }
    @Test fun cancellationDuringStoragePropagatesWithoutSending() = runTest {
        val store=SupplierPendingRequestStore({null},{_,_->throw kotlinx.coroutines.CancellationException("stopped")})
        assertFailsWith<kotlinx.coroutines.CancellationException> { store.prepareCreate(Session().owner(),create()) }
    }
}
