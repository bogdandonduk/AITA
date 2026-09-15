package kz.aita.server

import kotlinx.serialization.json.Json
import kz.aita.MARKET_AVAILABILITY_CONFIRM
import kz.aita.MarketListing
import kz.aita.MarketListingUpdate
import kz.aita.MarketOffer
import kz.aita.MarketPage
import kz.aita.MarketProductDetails
import kz.aita.MarketStorefront
import kz.aita.auth.AitaAuthFlowDataModel
import kz.aita.auth.AitaAuthNextStep
import kz.aita.auth.AitaAuthenticationSettingsDataModel
import kz.aita.auth.normalizeAitaEmail
import kz.aita.auth.normalizeAitaPhoneAlias
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** No database or service environment: verifies the server's actual project(:shared) API. */
class SharedProtocolServerLinkageTest {
    @Test fun serverCanReadSharedMarketplaceTypesAndGeneratedSerializers() {
        val listing = MarketListing("listing", "shop", "stock", "Tea", product = MarketProductDetails(brand = "Maker"))
        val update = MarketListingUpdate(listing, replaceProduct = true)
        assertEquals(update, Json.decodeFromString(MarketListingUpdate.serializer(),
            Json.encodeToString(MarketListingUpdate.serializer(), update)))
        val offer = MarketOffer("listing", MarketStorefront("shop"), "Tea", checkedAtMillis = 1, sourceUpdatedAtMillis = 1)
        assertEquals(MARKET_AVAILABILITY_CONFIRM, offer.availability)
        assertEquals("listing", MarketPage(listOf(offer)).offers.single().id)
    }

    @Test fun serverCanReadSharedAuthenticationTypesAndNormalizationFunctions() {
        val flow = AitaAuthFlowDataModel(flowId = "flow", nextStep = AitaAuthNextStep.EMAIL_CODE)
        assertEquals(flow, Json.decodeFromString(AitaAuthFlowDataModel.serializer(),
            Json.encodeToString(AitaAuthFlowDataModel.serializer(), flow)))
        assertFalse(AitaAuthenticationSettingsDataModel().authenticatorEnabled)
        assertEquals("test@example.com", normalizeAitaEmail(" TEST@EXAMPLE.COM "))
        assertEquals("+79991234567", normalizeAitaPhoneAlias("8 (999) 123-45-67"))
    }
}
