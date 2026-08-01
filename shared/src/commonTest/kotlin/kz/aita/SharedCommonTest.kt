package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun resolvedAddressUsesLocalizedSnapshotsAndCoordinatesAsIdentity() {
        val location = LocationDataModel(
            name = "Astana, Dostyk Street, 1",
            latitude = 51.1282,
            longitude = 71.4304,
            provider = AITA_ADDRESS_PROVIDER_YANDEX,
            providerObjectId = "ymapsbm1://geo?data=test",
            countryCode = "KZ",
            primaryLanguage = "ru",
            localizedNames = listOf(
                LocalizedStringDataModel("en", "Dostyk Street, 1"),
                LocalizedStringDataModel("ru", "улица Достык, 1"),
                LocalizedStringDataModel("kk", "Достық көшесі, 1")
            ),
            localizedAddresses = listOf(
                LocalizedStringDataModel("main", "Казахстан, Астана, улица Достык, 1"),
                LocalizedStringDataModel("en", "Kazakhstan, Astana, Dostyk Street, 1"),
                LocalizedStringDataModel("ru", "Казахстан, Астана, улица Достык, 1"),
                LocalizedStringDataModel("kk", "Қазақстан, Астана, Достық көшесі, 1")
            ),
            fallbackAddress = "Казахстан, Астана, улица Достык, 1",
            resolvedAtMillis = 100L,
            lastCheckedAtMillis = 200L,
            providerRevision = "revision"
        )

        assertTrue(location.isResolvedAddress())
        assertEquals("Kazakhstan, Astana, Dostyk Street, 1", location.displayAddress("en"))
        assertEquals("Қазақстан, Астана, Достық көшесі, 1", location.displayAddress("kk"))
        assertTrue(location.matchesDisplayedAddress("  Kazakhstan,   Astana, Dostyk Street, 1 ", "en"))
        assertFalse(location.matchesDisplayedAddress("Astana, somewhere else", "en"))
    }

    @Test
    fun legacyTextOnlyLocationRemainsDisplayableButCannotBeSavedAsVerified() {
        val legacy = LocationDataModel(
            name = "Legacy address text",
            postalIndex = "010000",
            latitude = 0.0,
            longitude = 0.0
        )

        assertEquals("Legacy address text", legacy.displayAddress("en"))
        assertFalse(legacy.isResolvedAddress())
        assertFalse(legacy.hasValidCoordinates())
    }
}
