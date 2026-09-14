package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class MarketComparisonWindowSerializationTest {
    @Test fun expandedWindowRoundTripsWithItsExactAccountScopeAndQuantity() {
        val f = ComparisonWindowFixtures
        val result = f.result(f.request.copy(candidateLimit = 400), 400)
        assertEquals(result, jsonBase.decodeFromString<MarketComparisonWindowResult>(jsonBase.encodeToString(result)))
    }
    @Test fun requiredWindowIdentityCannotBeSuppliedByALegacyPage() {
        val f = ComparisonWindowFixtures
        val old = MarketComparisonPage(f.selection, f.quote(), checkedAtMillis = f.checkedAt)
        assertFails { jsonBase.decodeFromString<MarketComparisonWindowResult>(jsonBase.encodeToString(old)) }
    }
    @Test fun requestHasNoCursorCommandOrPrivateInventoryIdentifiers() {
        val encoded = jsonBase.encodeToString(ComparisonWindowFixtures.request)
        val objectValue = jsonBase.parseToJsonElement(encoded).jsonObject
        assertEquals(setOf("selection", "city", "candidateLimit"), objectValue.keys)
        listOf("after", "goodsItemId", "commandId", "batchId", "payment", "supplierId").forEach { assertFalse(encoded.contains(it)) }
    }
}
