package kz.aita

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.*

class MarketOfferDetailSerializationTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val account = "00000000-0000-0000-0000-000000000099"
    private val offer = MarketOffer(id, MarketStorefront(id, "Shop", "Astana", "Door", published = true, revision = 1),
        "Tea", checkedAtMillis = 100, sourceUpdatedAtMillis = 90, saved = true)
    @Test fun accountAndSavedMarkerSurviveWireRoundTrip() {
        val value = MarketOfferDetailResult(account, offer)
        assertEquals(value, jsonBase.decodeFromString<MarketOfferDetailResult>(jsonBase.encodeToString(value)))
    }
    @Test fun unboundLegacyCardCannotDecodeAsADetailEnvelope() {
        assertFails { jsonBase.decodeFromString<MarketOfferDetailResult>(jsonBase.encodeToString(offer)) }
        assertFails { jsonBase.decodeFromString<MarketOfferDetailResult>("{\"offer\":${jsonBase.encodeToString(offer)}}") }
    }
    @Test fun genericResponseKeepsItsOwnedPayloadAndStatus() {
        val value = ResponseDataModel(null, MarketOfferDetailResult(account, offer), false, 200)
        assertEquals(value, jsonBase.decodeFromString<ResponseDataModel<MarketOfferDetailResult>>(jsonBase.encodeToString(value)))
    }
}
