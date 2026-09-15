package kz.aita

import kotlinx.serialization.json.Json
import kz.aita.auth.AitaAuthFlowDataModel
import kz.aita.auth.AitaAuthNextStep
import kz.aita.auth.AitaAuthenticationSettingsDataModel
import kz.aita.auth.AitaContactCodeRequest
import kz.aita.auth.AitaContactPurpose
import kz.aita.auth.AitaContactTarget
import kz.aita.auth.aitaAuthCodeDigits
import kz.aita.auth.normalizeAitaEmail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Compile the actual protocol declarations and generated serializers, not fixture stand-ins. */
class SharedProtocolLinkageTest {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test fun marketplaceModelsAndSerializersResolveTogether() {
        val shop = MarketStorefront("shop", displayName = "Shop", shareBranchAvailability = true)
        val listing = MarketListing("listing", shop.storeId, "item", "Tea", product = MarketProductDetails(brand = "Maker"))
        val mutation = MarketListingUpdate(listing, replaceProduct = true)
        assertEquals(mutation, json.decodeFromString(MarketListingUpdate.serializer(),
            json.encodeToString(MarketListingUpdate.serializer(), mutation)))
        val offer = MarketOffer(listing.id, shop, listing.title, checkedAtMillis = 100, sourceUpdatedAtMillis = 90)
        assertEquals(MARKET_AVAILABILITY_CONFIRM, offer.availability)
        val page = MarketPage(listOf(offer), checkedAtMillis = 100)
        assertEquals(page, json.decodeFromString(MarketPage.serializer(), json.encodeToString(MarketPage.serializer(), page)))
        assertEquals("04601234567893", marketCanonicalGtin("4601234567893"))
    }

    @Test fun authenticationAndContactModelsResolveWithoutUiOrNetwork() {
        val flow = AitaAuthFlowDataModel(flowId = "flow", nextStep = AitaAuthNextStep.EMAIL_CODE, serverTimeMillis = 1)
        assertEquals(flow, json.decodeFromString(AitaAuthFlowDataModel.serializer(),
            json.encodeToString(AitaAuthFlowDataModel.serializer(), flow)))
        assertNull(flow.tokenPair)
        assertFalse(AitaAuthenticationSettingsDataModel().authenticatorEnabled)
        assertEquals("123456", aitaAuthCodeDigits("١٢٣ ٤٥٦"))
        assertEquals("test@example.com", normalizeAitaEmail(" TEST@EXAMPLE.COM "))
        val contact = AitaContactCodeRequest(AitaContactTarget(AitaContactPurpose.REGISTRATION,
            "new:00000000-0000-0000-0000-000000000001"), "test@example.com")
        assertEquals(contact, json.decodeFromString(AitaContactCodeRequest.serializer(),
            json.encodeToString(AitaContactCodeRequest.serializer(), contact)))
    }
}
