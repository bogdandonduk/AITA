package kz.aita

import kotlin.test.*

class ManualStoreAddressTest {
    @Test fun manualAddressRetainsTextWithoutAPretendMapPoint() {
        val address = assertNotNull(manualStoreAddress("  Алматы,   Абая 10 ", "ru", "kz"))
        assertEquals("Алматы, Абая 10", address.displayAddress("ru"))
        assertEquals("KZ", address.countryCode)
        assertTrue(address.isManualStoreAddress())
        assertFalse(address.hasValidCoordinates())
        assertFalse(address.isResolvedAddress())
        assertFalse(address.needsProviderRefresh())
        assertNull(manualStoreAddress("   ", "en"))
        assertNull(manualStoreAddress("a".repeat(501), "en"))
    }
    @Test fun providerObjectCannotMasqueradeAsManual() {
        assertFalse(LocationDataModel(name = "Address", provider = "yandex", providerObjectId = "id").isManualStoreAddress())
    }
}
