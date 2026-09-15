package kz.aita

import kotlinx.serialization.json.Json
import kz.aita.auth.AitaAuthFlowDataModel
import kz.aita.auth.AitaAuthNextStep
import kz.aita.auth.AitaAuthenticationSettingsDataModel
import kz.aita.auth.aitaAuthCodeDigits
import kz.aita.auth.normalizeAitaPhoneAlias
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Cross-module compile/runtime test: these symbols must come from projects.shared. */
class SharedProtocolClientLinkageTest {
    @Test fun clientCanReadTheRealSharedMarketplaceApi() {
        val listing = MarketListing("listing", "shop", "stock", "Tea", product = MarketProductDetails(brand = "Maker"))
        val update = MarketListingUpdate(listing, replaceProduct = true)
        val wire = Json.encodeToString(MarketListingUpdate.serializer(), update)
        assertEquals(update, Json.decodeFromString(MarketListingUpdate.serializer(), wire))
        val offer = MarketOffer("listing", MarketStorefront("shop"), "Tea", checkedAtMillis = 1, sourceUpdatedAtMillis = 1)
        assertEquals(MARKET_AVAILABILITY_CONFIRM, offer.availability)
        assertEquals("listing", MarketPage(listOf(offer)).offers.single().id)
    }

    @Test fun clientCanReadTheRealSharedAuthenticationApi() {
        val flow = AitaAuthFlowDataModel(flowId = "flow", nextStep = AitaAuthNextStep.EMAIL_CODE)
        assertEquals(flow, Json.decodeFromString(AitaAuthFlowDataModel.serializer(),
            Json.encodeToString(AitaAuthFlowDataModel.serializer(), flow)))
        assertFalse(AitaAuthenticationSettingsDataModel().authenticatorEnabled)
        assertEquals("123456", aitaAuthCodeDigits("１２３４５６"))
        assertEquals("+79991234567", normalizeAitaPhoneAlias("8 (999) 123-45-67"))
    }
}
