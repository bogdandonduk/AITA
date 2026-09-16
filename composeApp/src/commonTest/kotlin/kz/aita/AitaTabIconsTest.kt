package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AitaTabIconsTest {
    @Test fun familiesAreUniqueAndBeyondExistingArtwork() {
        assertEquals(AitaTabIcon.entries.size, AitaTabIcon.entries.map { it.family }.distinct().size)
        assertTrue(AitaTabIcon.entries.all { it.family >= 149 })
    }
    @Test fun checkoutFiltersHaveSpecificSemanticArt() {
        val expected = mapOf("all" to AitaTabIcon.All, "quick" to AitaTabIcon.Quick,
            "fresh" to AitaTabIcon.Fresh, "in_stock" to AitaTabIcon.Stock, "popular" to AitaTabIcon.Popular,
            "recent" to AitaTabIcon.Recent, "restock" to AitaTabIcon.Restock, "low_stock" to AitaTabIcon.LowStock,
            "expiring" to AitaTabIcon.Expiring, "slow" to AitaTabIcon.Slow)
        expected.forEach { (id, icon) -> assertEquals(icon, aitaTabIconForId(id)) }
    }
    @Test fun iconsDoNotDependOnTranslatedLabel() {
        assertEquals(TabContent("quick", "Quick").icon, TabContent("quick", "Быстро").icon)
    }
    @Test fun dynamicCurrencyCanDeclareItsOwnIcon() {
        assertEquals(AitaTabIcon.Money, TabContent("KZT", "KZT", icon = AitaTabIcon.Money).icon)
    }
    @Test fun paymentModesHaveDifferentIcons() {
        assertEquals(AitaTabIcon.Cash, aitaTabIconForId("0"))
        assertEquals(AitaTabIcon.Card, aitaTabIconForId("1"))
        assertEquals(AitaTabIcon.Mixed, aitaTabIconForId("2"))
    }
}
