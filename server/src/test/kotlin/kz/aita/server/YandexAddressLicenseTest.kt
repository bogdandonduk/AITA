package kz.aita.server

import kotlin.test.*

class YandexAddressLicenseTest {
    @Test fun keysAloneAndFreeOrTemporaryPlansDoNotEnablePermanentAddressUse() {
        listOf(null, "", "free", "30-days", "true", "yes").forEach { assertFalse(yandexStoredAddressUseAllowed(it)) }
        assertTrue(yandexStoredAddressUseAllowed("permanent-storage-permitted"))
        assertFalse(YandexAddressService.suggestionsConfigured)
        assertFalse(YandexAddressService.geocoderConfigured)
        assertFalse(YandexAddressService.staticMapsConfigured)
    }
}
