package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Resource data only: selecting and propagating the locale is a separate stage. */
class UzbekNumberedFallbackTest {
    @Test
    fun everyNumberedFallbackHasUzbekAndRetainsExistingLanguages() {
        val values = buildBundledLocalizedStringFallbacks()
        assertTrue(values.size >= 2451)
        for ((id, translations) in values) {
            for (language in listOf("main", "en", "ru", "kk", "tg", "ky", "uz")) {
                assertFalse(translations[language].isNullOrBlank(), "$id/$language")
            }
        }
    }

    @Test
    fun workAndCoreActionsPreserveTheirExistingIds() {
        val values = buildBundledLocalizedStringFallbacks()
        for ((id, label) in mapOf(1L to "Kirish", 6L to "Parol", 8L to "Bekor qilish", 2658L to "Ish")) {
            assertEquals(label, values.getValue(id)["uz"], "$id")
        }
        val work = values.getValue(2658L)
        assertEquals("Work", work["en"])
        assertEquals("Работа", work["ru"])
        assertEquals("Жұмыс", work["kk"])
        assertEquals("Кор", work["tg"])
        assertEquals("Иш", work["ky"])
    }

    @Test
    fun fallbackOnlyRecoveryStringsAreTranslated() {
        val values = buildBundledLocalizedStringFallbacks()
        for (id in listOf(1728L, 1800L, 1919L, 2109L, 2299L)) {
            val translations = values.getValue(id)
            assertFalse(translations["uz"].isNullOrBlank(), "$id")
            assertNotEquals(translations["en"], translations["uz"], "$id")
        }
    }

    @Test
    fun lateSupportOverridesDoNotDiscardUzbek() {
        val values = buildBundledLocalizedStringFallbacks()
        val support = mutableMapOf<Long, Map<String, String>>()
        support.putReceiptSupportStringFallbacks()
        assertTrue(support.isNotEmpty())
        for ((id, translations) in support) {
            assertFalse(translations["uz"].isNullOrBlank(), "$id")
            assertEquals(translations["uz"], values.getValue(id)["uz"], "$id")
        }
    }

    @Test
    fun securityCodeExamplesAndFilePathsSurviveMapConstruction() {
        val values = buildBundledLocalizedStringFallbacks()
        for ((id, token) in mapOf(2623L to "xxxxxx-xxxxxx", 2631L to "Download/AITA/Receipts",
            2621L to "2FA", 2637L to "PDF", 2657L to "QR")) {
            assertTrue(values.getValue(id).getValue("uz").contains(token), "$id/$token")
        }
        val recovery = values.getValue(2623L).getValue("uz")
        assertTrue(recovery.contains("barcha seanslar"))
        assertTrue(recovery.contains("eski tiklash kodlarini bekor"))
    }

    @Test
    fun productAndDeviceProtocolNamesAreNotTranslated() {
        val values = buildBundledLocalizedStringFallbacks()
        for ((id, label) in mapOf(0L to "AITA", 161L to "PDF", 163L to "WhatsApp",
            1295L to "TSPL", 1296L to "ZPL", 1297L to "CPCL")) {
            assertEquals(label, values.getValue(id)["uz"], "$id")
        }
    }
}
