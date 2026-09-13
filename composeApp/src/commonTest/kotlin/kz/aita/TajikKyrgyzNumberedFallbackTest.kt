package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Numbered catalogue batch only; this does not claim inline UI or picker readiness. */
class TajikKyrgyzNumberedFallbackTest {
    @Test
    fun everyExistingNumberedFallbackHasBothTranslations() {
        val values = buildBundledLocalizedStringFallbacks()
        assertTrue(values.isNotEmpty())
        for ((id, translations) in values) {
            for (language in listOf("tg", "ky")) {
                assertFalse(translations[language].isNullOrBlank(), "$id/$language")
            }
            for (language in listOf("main", "en", "ru", "kk")) {
                assertFalse(translations[language].isNullOrBlank(), "$id/$language")
            }
        }
    }

    @Test
    fun workAndCoreActionsPreserveTheirExistingIds() {
        val values = buildBundledLocalizedStringFallbacks()
        assertEquals("Кор", values.getValue(2658L)["tg"])
        assertEquals("Иш", values.getValue(2658L)["ky"])
        assertEquals("Work", values.getValue(2658L)["en"])
        assertEquals("Работа", values.getValue(2658L)["ru"])
        assertEquals("Жұмыс", values.getValue(2658L)["kk"])
        assertEquals("Ворид шудан", values.getValue(1L)["tg"])
        assertEquals("Кирүү", values.getValue(1L)["ky"])
        assertEquals("Гузарвожа", values.getValue(6L)["tg"])
        assertEquals("Сырсөз", values.getValue(6L)["ky"])
    }

    @Test
    fun fallbackOnlyRecoveryIdsAreTranslatedToo() {
        val values = buildBundledLocalizedStringFallbacks()
        for (id in listOf(1728L, 1800L, 1919L, 2109L, 2299L)) {
            val translations = values.getValue(id)
            for (language in listOf("tg", "ky")) {
                assertFalse(translations[language].isNullOrBlank(), "$id/$language")
                assertNotEquals(translations["en"], translations[language], "$id/$language")
            }
        }
    }

    @Test
    fun lastAppliedSupportOverridesKeepBothLanguages() {
        val values = buildBundledLocalizedStringFallbacks()
        val support = mutableMapOf<Long, Map<String, String>>()
        support.putReceiptSupportStringFallbacks()
        for ((id, translations) in support) {
            for (language in listOf("tg", "ky")) {
                assertFalse(translations[language].isNullOrBlank(), "$id/$language")
                assertEquals(translations[language], values.getValue(id)[language])
            }
        }
    }

    @Test
    fun codePatternsAndReceiptPathsSurviveRuntimeMapConstruction() {
        val values = buildBundledLocalizedStringFallbacks()
        for (language in listOf("tg", "ky")) {
            assertTrue(values.getValue(2623L).getValue(language).contains("xxxxxx-xxxxxx"))
            assertTrue(values.getValue(2631L).getValue(language).contains("Download/AITA/Receipts"))
            assertTrue(values.getValue(2621L).getValue(language).contains("2FA"))
            assertTrue(values.getValue(2657L).getValue(language).contains("QR"))
        }
    }

    @Test
    fun brandAndDeviceProtocolNamesRemainUntranslated() {
        val values = buildBundledLocalizedStringFallbacks()
        for ((id, label) in mapOf(0L to "AITA", 161L to "PDF", 163L to "WhatsApp",
            1295L to "TSPL", 1296L to "ZPL", 1297L to "CPCL")) {
            for (language in listOf("tg", "ky")) {
                assertEquals(label, values.getValue(id)[language], "$id/$language")
            }
        }
    }
}
