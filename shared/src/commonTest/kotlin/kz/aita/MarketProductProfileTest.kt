package kz.aita

import kotlin.test.*

class MarketProductProfileTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private fun text(value: String) = listOf(LocalizedStringDataModel("en", value))
    private fun item() = GoodsItemDataModel(id = id(1), storeId = id(2), name = text("Tea"),
        description = text("Loose leaf"), imagePaths = listOf("https://images.example.com/tea.jpg"),
        barcodes = listOf("4006381333931"), measurementUnitId = "piece")
    private fun offer() = MarketOffer(id(3), MarketStorefront(id(2), "Shop", "Astana", "Door", published = true,
        revision = 1, shareBranchAvailability = true), "Tea", checkedAtMillis = 50, sourceUpdatedAtMillis = 40)
    private fun branch() = MarketBranchAvailability(id(4), text("Branch"), "Public door", MARKET_AVAILABILITY_CONFIRM, 50)
    private fun status() = MarketStockPublicationStatus(id(9), id(2), id(2), true,
        listOf(MarketStockPublicationEntry(id(1), "04006381333931", "piece", true)), 50)

    @Test fun missingProfileSeedsPublicFieldsWithoutInventingFacts() {
        val profile = item().effectiveMarketplaceProfile()
        assertEquals(text("Tea"), profile.name); assertEquals(text("Loose leaf"), profile.description)
        assertEquals(item().imagePaths, profile.product.imageUrls)
        assertEquals("", profile.product.brand); assertTrue(profile.product.attributes.isEmpty())
        assertTrue(profile.automaticFromStock)
    }
    @Test fun newListingIsPrivateUntilReviewedAndExplicitlyPublished() {
        val draft = item().marketListingDraft(id(2), "en")
        assertEquals("Tea", draft.title); assertEquals("Loose leaf", draft.description)
        assertEquals("04006381333931", draft.gtin); assertFalse(draft.published)
        assertEquals(0L, draft.revision); assertEquals("", draft.id)
    }
    @Test fun automaticProfileTracksStockButKeepsAuthoredFacts() {
        val stock = item().copy(name = text("Green tea"), marketplaceProfile = StockMarketplaceProfile(
            name = text("Old"), product = MarketProductDetails(brand = "Owner supplied")))
        assertEquals(text("Green tea"), stock.effectiveMarketplaceProfile().name)
        assertEquals("Owner supplied", stock.effectiveMarketplaceProfile().product.brand)
    }
    @Test fun independentProfileDoesNotFollowStockRenamingOrPictures() {
        val custom = StockMarketplaceProfile(false, text("Public title"), text("Public description"),
            MarketProductDetails(imageUrls = listOf("https://images.example.org/public.png"), brand = "Brand"))
        assertEquals(custom, item().copy(marketplaceProfile = custom).effectiveMarketplaceProfile())
    }
    @Test fun parentProfileCopyKeepsResolvedTextAndPhotosIndependentOfBranchStockAndEdits() {
        val parent = item().copy(marketplaceProfile = StockMarketplaceProfile(product = MarketProductDetails(brand = "Generic brand")))
        val original = parent.effectiveMarketplaceProfile()
        val copied = parent.marketplaceProfileForBranchCopy()
        assertFalse(copied.automaticFromStock)
        val branch = item().copy(storeId = id(7), name = text("Branch stock title"), imagePaths = emptyList(), marketplaceProfile = copied)
        assertEquals(original.copy(automaticFromStock = false), branch.effectiveMarketplaceProfile())
        val edited = branch.copy(marketplaceProfile = copied.copy(name = text("Internet special"), product = copied.product.copy(brand = "Branch brand")))
        assertEquals(text("Internet special"), edited.effectiveMarketplaceProfile().name)
        assertEquals(original, parent.effectiveMarketplaceProfile())
    }
    @Test fun parentProfileCopyAlsoPreservesAuthoredIndependentFields() {
        val parent = item().copy(marketplaceProfile = StockMarketplaceProfile(false, text("Public title"), text("Public description"),
            MarketProductDetails(imageUrls = listOf("https://images.example.org/public.png"), brand = "Parent brand")))
        assertEquals(parent.marketplaceProfile, parent.marketplaceProfileForBranchCopy())
    }
    @Test fun legacyEditCannotEraseIndependentProfile() {
        val custom = StockMarketplaceProfile(false, text("Reviewed draft"), product = MarketProductDetails(brand = "Brand"))
        assertEquals(custom, item().marketplaceProfileForSave(custom))
    }
    @Test fun explicitReplacementCanReturnToAutomaticMode() {
        val previous = StockMarketplaceProfile(false, text("Custom"))
        assertEquals(text("Tea"), item().copy(marketplaceProfile = StockMarketplaceProfile()).marketplaceProfileForSave(previous).name)
    }
    @Test fun publicTitleLanguageFallbackDoesNotUsePrivateNotes() {
        val draft = item().copy(name = listOf(LocalizedStringDataModel("main", "Fallback")), note = "SECRET")
            .marketListingDraft(id(2), "ru")
        assertEquals("Fallback", draft.title); assertFalse(draft.description.contains("SECRET"))
    }
    @Test fun automaticProfileIgnoresLocalAndCredentialledImages() {
        val product = item().copy(imagePaths = listOf("/tmp/photo.png", "file:///tmp/x", "https://a.example.com/x?secret=1",
            "https://images.example.com/real.jpg")).effectiveMarketplaceProfile().product
        assertEquals(listOf("https://images.example.com/real.jpg"), product.imageUrls)
    }
    @Test fun imageHostIsCanonicalButCaseSensitivePathIsPreserved() {
        assertEquals("https://cdn.example.com/Food/A.PNG", marketPublicImageUrl(" HTTPS://CDN.EXAMPLE.COM/Food/A.PNG "))
    }
    @Test fun imagesRejectTokensCredentialsPortsAndLocalDestinations() {
        listOf("http://cdn.example.com/x", "https://user:pass@cdn.example.com/x", "https://cdn.example.com:443/x",
            "https://127.0.0.1/x", "https://[::1]/x", "https://x.local/x", "https://x.internal/x",
            "https://cdn.example.com/x?key=secret", "https://cdn.example.com/x#token", "https://cdn.example.com/\nx",
            "https://cdn.example.com\\x", "https://cdn..example.com/x", "https://-cdn.example.com/x").forEach {
            assertNull(marketPublicImageUrl(it), it)
        }
    }
    @Test fun imageListsAreBoundedAndDeduplicated() {
        val value = MarketProductDetails(imageUrls = List(9) { "https://cdn.example.com/$it.jpg" })
        assertTrue(value.hasInvalidMarketProductInput()); assertEquals(6, value.normalizedMarketProduct().imageUrls.size)
        assertEquals(1, value.copy(imageUrls = List(3) { "https://cdn.example.com/x.jpg" }).normalizedMarketProduct().imageUrls.size)
    }
    @Test fun factsHaveSeparateLengthLimitsAndRemoveControlCharacters() {
        val product = MarketProductDetails(brand = "b".repeat(200), manufacturer = "m".repeat(200), countryOfOrigin = "c".repeat(200),
            article = "a".repeat(200), ingredients = "i".repeat(2100), allergens = "a".repeat(1100), storageInstructions = "s".repeat(1100))
            .normalizedMarketProduct()
        assertEquals(listOf(120,160,100,120,2000,1000,1000), listOf(product.brand, product.manufacturer, product.countryOfOrigin,
            product.article, product.ingredients, product.allergens, product.storageInstructions).map { it.length })
        assertEquals("A\nB", MarketProductDetails(brand = "A\u0000\nB").normalizedMarketProduct().brand)
    }
    @Test fun incompleteAttributeRowsCannotBeSavedButBlankRowsCanBeDiscarded() {
        assertTrue(MarketProductDetails(attributes = listOf(MarketProductAttribute("Size", ""))).hasInvalidMarketProductInput())
        assertFalse(MarketProductDetails(attributes = listOf(MarketProductAttribute())).hasInvalidMarketProductInput())
        assertTrue(MarketProductDetails(attributes = listOf(MarketProductAttribute())).normalizedMarketProduct().attributes.isEmpty())
    }
    @Test fun attributesAreBoundedAndCaseInsensitiveDuplicatesAreRemoved() {
        val facts = listOf(MarketProductAttribute(" Size ", " Small "), MarketProductAttribute("size", "Large"))
        assertEquals(listOf(MarketProductAttribute("Size", "Small")), MarketProductDetails(attributes = facts).normalizedMarketProduct().attributes)
        assertEquals(16, MarketProductDetails(attributes = List(20) { MarketProductAttribute("Field$it", "Value") }).normalizedMarketProduct().attributes.size)
    }
    @Test fun malformedPublicSnapshotIsRejectedInsteadOfSilentlyNormalized() {
        assertTrue(MarketProductDetails().isValidMarketProduct())
        assertFalse(MarketProductDetails(brand = " padded ").isValidMarketProduct())
        assertFalse(MarketProductDetails(imageUrls = listOf("file:///tmp/private")).isValidMarketProduct())
    }
    @Test fun parentAndBranchNeedExactStandardGtinAndSameUnit() {
        assertTrue(marketSameBranchProduct(item(), item().copy(id = id(5), storeId = id(4), barcodes = listOf("04006381333931"))))
        assertFalse(marketSameBranchProduct(item(), item().copy(id = id(5), measurementUnitId = "kg")))
    }
    @Test fun sameNameOrInternalBarcodeCannotLinkDifferentItems() {
        assertFalse(marketSameBranchProduct(item().copy(barcodes = emptyList()), item().copy(id = id(5), barcodes = emptyList())))
        assertFalse(marketSameBranchProduct(item(), item().copy(id = id(5), barcodeModels = listOf(
            GoodsItemBarcodeDataModel("4006381333931", GOODS_ITEM_BARCODE_TYPE_INTERNAL, id(4))))))
    }
    @Test fun sharedParentItemIdentityWorksWithoutBarcode() {
        assertTrue(marketSameBranchProduct(item().copy(barcodes = emptyList()), item().copy(barcodes = emptyList())))
    }
    @Test fun inactiveItemsNeverCreateAvailabilityLinks() {
        assertFalse(marketSameBranchProduct(item(), item().copy(isActive = false)))
        assertFalse(marketSameBranchProduct(item().copy(isActive = false), item()))
    }
    @Test fun branchHintIsBoundToParentAndSameCheckInstant() {
        assertTrue(branch().isValidMarketBranch(offer()))
        assertFalse(branch().copy(branchId = id(2)).isValidMarketBranch(offer()))
        assertFalse(branch().copy(checkedAtMillis = 49).isValidMarketBranch(offer()))
    }
    @Test fun branchHintHasOnlyConservativeAvailabilityStates() {
        assertTrue(branch().copy(availability = MARKET_AVAILABILITY_RECORDED).isValidMarketBranch(offer()))
        assertFalse(branch().copy(availability = "reserved_for_you").isValidMarketBranch(offer()))
        assertFalse(branch().copy(publicAddress = "").isValidMarketBranch(offer()))
    }
    @Test fun statusCannotCrossAccountOrStore() {
        assertTrue(status().isValidStockPublicationStatus(id(9), id(2)))
        assertFalse(status().isValidStockPublicationStatus(id(8), id(2)))
        assertFalse(status().isValidStockPublicationStatus(id(9), id(4)))
    }
    @Test fun disabledMarketplaceCannotLeakItsPublicationIndex() {
        assertFalse(status().copy(marketplaceEnabled = false).isValidStockPublicationStatus(id(9), id(2)))
        assertTrue(status().copy(marketplaceEnabled = false, entries = emptyList()).isValidStockPublicationStatus(id(9), id(2)))
    }
    @Test fun statusRejectsDuplicateItemsAndInvalidGtin() {
        val entry = status().entries.single()
        assertFalse(status().copy(entries = listOf(entry,entry)).isValidStockPublicationStatus(id(9), id(2)))
        assertFalse(status().copy(entries = listOf(entry.copy(gtin = "4006381333931"))).isValidStockPublicationStatus(id(9), id(2)))
    }
    @Test fun olderListingUpdateDefaultsToPreservingReviewedProduct() {
        assertFalse(MarketListingUpdate(item().marketListingDraft(id(2), "en")).replaceProduct)
        assertTrue(MarketListingUpdate(item().marketListingDraft(id(2), "en"), replaceProduct = true).replaceProduct)
    }
}
