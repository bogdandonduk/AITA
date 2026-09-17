package kz.aita

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class ReceiptPrinterCanonicalIdentityTest {
    private fun isolated(block: suspend () -> Unit) = runBlocking {
        val oldList = listPlatformReceiptPrinterDevicesAction
        val oldConfigure = configurePlatformReceiptPrinterDeviceAction
        val oldSelection = configuredReceiptPrinterDeviceIdState.value
        val oldDevices = receiptPrinterDevicesState.value
        try {
            configuredReceiptPrinterDeviceIdState.value = "saved"
            receiptPrinterDevicesState.value = listOf(
                PlatformReceiptPrinterDataModel("saved", "Saved", configured = true),
                PlatformReceiptPrinterDataModel("usb:canonical", "USB")
            )
            listPlatformReceiptPrinterDevicesAction = { error("temporarily disconnected") }
            withTimeout(5_000) { block() }
        } finally {
            listPlatformReceiptPrinterDevicesAction = oldList
            configurePlatformReceiptPrinterDeviceAction = oldConfigure
            configuredReceiptPrinterDeviceIdState.value = oldSelection
            receiptPrinterDevicesState.value = oldDevices
        }
    }
    private suspend fun select(id: String?): ReceiptPlatformActionResult {
        val completed = CompletableDeferred<ReceiptPlatformActionResult>()
        configureReceiptPrinterDevice(id) { completed.complete(it) }
        return completed.await()
    }

    @Test fun canonicalIdentitySurvivesDiscoveryFailure() = isolated {
        configurePlatformReceiptPrinterDeviceAction = {
            ReceiptPlatformActionResult(true, selectedDeviceId = "usb:canonical")
        }
        assertTrue(select("usb:temporary").success)
        assertEquals("usb:canonical", configuredReceiptPrinterDeviceIdState.value)
        assertEquals("usb:canonical", receiptPrinterDevicesState.value.single { it.configured }.id)
    }

    @Test fun failedSaveCannotPublishCanonicalIdentity() = isolated {
        configurePlatformReceiptPrinterDeviceAction = {
            ReceiptPlatformActionResult(false, selectedDeviceId = "usb:canonical")
        }
        assertFalse(select("usb:temporary").success)
        assertEquals("saved", configuredReceiptPrinterDeviceIdState.value)
        assertEquals("saved", receiptPrinterDevicesState.value.single { it.configured }.id)
    }

    @Test fun clearingDoesNotAdoptAnUnexpectedPlatformIdentity() = isolated {
        configurePlatformReceiptPrinterDeviceAction = {
            ReceiptPlatformActionResult(true, selectedDeviceId = "usb:canonical")
        }
        assertTrue(select(null).success)
        assertNull(configuredReceiptPrinterDeviceIdState.value)
        assertTrue(receiptPrinterDevicesState.value.none { it.configured })
    }

    @Test fun unchangedPlatformsRetainRequestedIdentity() = isolated {
        configurePlatformReceiptPrinterDeviceAction = { ReceiptPlatformActionResult(true) }
        assertTrue(select("  bluetooth-target  ").success)
        assertEquals("bluetooth-target", configuredReceiptPrinterDeviceIdState.value)
    }
}
