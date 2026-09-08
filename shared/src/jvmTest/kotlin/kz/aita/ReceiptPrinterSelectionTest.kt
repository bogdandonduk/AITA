package kz.aita

import kotlinx.coroutines.*
import kotlin.test.*

class ReceiptPrinterSelectionTest {
    private fun isolated(block: suspend () -> Unit) = runBlocking {
        val oldList=listPlatformReceiptPrinterDevicesAction
        val oldConfigure=configurePlatformReceiptPrinterDeviceAction
        val oldPrepare=preparePlatformReceiptPrinterAction
        val oldSelection=configuredReceiptPrinterDeviceIdState.value
        val oldDevices=receiptPrinterDevicesState.value
        try {
            configuredReceiptPrinterDeviceIdState.value="saved"
            receiptPrinterDevicesState.value=listOf(PlatformReceiptPrinterDataModel("saved","Saved",configured=true))
            preparePlatformReceiptPrinterAction=null
            withTimeout(5000L){block()}
        } finally {
            listPlatformReceiptPrinterDevicesAction=oldList;configurePlatformReceiptPrinterDeviceAction=oldConfigure
            preparePlatformReceiptPrinterAction=oldPrepare
            configuredReceiptPrinterDeviceIdState.value=oldSelection;receiptPrinterDevicesState.value=oldDevices
        }
    }
    private suspend fun refresh(permission:Boolean=false):ReceiptPlatformActionResult {
        val result=CompletableDeferred<ReceiptPlatformActionResult>()
        refreshReceiptPrinterDevices(permission){result.complete(it)}
        return result.await()
    }
    private suspend fun select(id:String?):ReceiptPlatformActionResult {
        val result=CompletableDeferred<ReceiptPlatformActionResult>()
        configureReceiptPrinterDevice(id){result.complete(it)}
        return result.await()
    }
    @Test fun emptyDiscoveryRetainsSavedSelection()=isolated {
        listPlatformReceiptPrinterDevicesAction={emptyList()}
        assertTrue(refresh().success);assertEquals("saved",configuredReceiptPrinterDeviceIdState.value)
    }
    @Test fun permissionFailurePreservesPreviousListAndSelection()=isolated {
        listPlatformReceiptPrinterDevicesAction={error("Nearby devices permission required")}
        assertFalse(refresh().success);assertEquals("saved",configuredReceiptPrinterDeviceIdState.value)
        assertEquals(1,receiptPrinterDevicesState.value.size)
    }
    @Test fun failedSaveDoesNotPublishNewSelection()=isolated {
        configurePlatformReceiptPrinterDeviceAction={ReceiptPlatformActionResult(false,"disk error")}
        assertFalse(select("new").success);assertEquals("saved",configuredReceiptPrinterDeviceIdState.value)
    }
    @Test fun savedSelectionSurvivesSubsequentDiscoveryFailure()=isolated {
        configurePlatformReceiptPrinterDeviceAction={ReceiptPlatformActionResult(true)}
        listPlatformReceiptPrinterDevicesAction={error("not visible")}
        assertTrue(select("new").success);assertEquals("new",configuredReceiptPrinterDeviceIdState.value)
    }
    @Test fun explicitPermissionRefusalDoesNotStartDiscovery()=isolated {
        var calls=0
        preparePlatformReceiptPrinterAction={ReceiptPlatformActionResult(false,"denied")}
        listPlatformReceiptPrinterDevicesAction={calls++;emptyList()}
        assertFalse(refresh(true).success);assertEquals(0,calls)
    }
    @Test fun refreshAndSelectionCannotOverwriteEachOther()=isolated {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        var listCalls=0;var durable="saved"
        listPlatformReceiptPrinterDevicesAction={
            listCalls++
            if(listCalls==1){entered.complete(Unit);release.await()}
            listOf(PlatformReceiptPrinterDataModel(durable,durable,configured=true))
        }
        configurePlatformReceiptPrinterDeviceAction={durable=it.orEmpty();ReceiptPlatformActionResult(true)}
        val refreshDone=CompletableDeferred<ReceiptPlatformActionResult>()
        refreshReceiptPrinterDevices{refreshDone.complete(it)};entered.await()
        val selectDone=CompletableDeferred<ReceiptPlatformActionResult>()
        configureReceiptPrinterDevice("new"){selectDone.complete(it)}
        release.complete(Unit);assertTrue(refreshDone.await().success);assertTrue(selectDone.await().success)
        assertEquals("new",configuredReceiptPrinterDeviceIdState.value)
        assertEquals("new",receiptPrinterDevicesState.value.single().id)
    }
}
