package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupplierCatalogOfferDraftTest {
    private val owner = supplierCatalogOfferDraftKey("account", "supplier", "store", "goods")
    private val saved = SupplierCatalogOfferFields(price = "120", name = "Tea", barcode = "123456")

    @Test
    fun pristineEditorAdoptsNewSavedOffer() {
        val next = saved.copy(price = "140", minimum = "6")
        val refreshed = SupplierCatalogOfferDraft(owner, saved).refreshed(next)
        assertEquals(next, refreshed.fields)
        assertFalse(refreshed.edited)
    }

    @Test
    fun refreshNeverReplacesUnsavedTextOrItsCurrency() {
        val draft = SupplierCatalogOfferDraft(owner, saved, saved.copy(price = "150", name = "Tea premium"))
        val changedByColleague = saved.copy(price = "2", currency = "USD")
        assertEquals(draft, draft.refreshed(changedByColleague))
        assertEquals("KZT", draft.refreshed(changedByColleague).fields.currency)
        assertTrue(draft.edited)
    }

    @Test
    fun acknowledgementClearsOnlyTheSubmittedEdits() {
        val submitted = saved.copy(price = "150.00", minimum = "6")
        val normalized = submitted.copy(price = "150")
        val finished = SupplierCatalogOfferDraft(owner, saved, submitted).acknowledged(submitted, normalized)
        assertEquals(normalized, finished.fields)
        assertFalse(finished.edited)
    }

    @Test
    fun editingWhileSaveRunsPreservesEveryNewerField() {
        val submitted = saved.copy(price = "150.00", minimum = "6", packageSize = "12")
        val newer = submitted.copy(price = "175", minimum = "8", packageSize = "24", name = "Loose tea", barcode = "654321")
        val normalized = submitted.copy(price = "150")
        val acknowledged = SupplierCatalogOfferDraft(owner, saved, newer).acknowledged(submitted, normalized)
        assertEquals(newer, acknowledged.fields)
        assertEquals(normalized, acknowledged.baseline)
        assertTrue(acknowledged.edited)
    }

    @Test
    fun unchangedFieldsAcceptServerNormalizationWithoutDroppingNewEdits() {
        val submitted = saved.copy(price = "150.00", name = " Tea ")
        val newer = submitted.copy(barcode = "987654")
        val normalized = submitted.copy(price = "150", name = "Tea")
        val acknowledged = SupplierCatalogOfferDraft(owner, saved, newer).acknowledged(submitted, normalized)
        assertEquals("150", acknowledged.fields.price)
        assertEquals("Tea", acknowledged.fields.name)
        assertEquals("987654", acknowledged.fields.barcode)
        assertTrue(acknowledged.edited)
    }

    @Test
    fun newerQuantityKeepsTheUnitInWhichItWasEntered() {
        val kilograms = QuantityDataModel("kg", listOf(LocalizedStringDataModel("en", "kg")), roundTotal = false)
        val pieces = QuantityDataModel("piece", listOf(LocalizedStringDataModel("en", "pieces")), roundTotal = true)
        val submitted = saved.copy(minimum = "2", minimumTemplate = kilograms)
        val newer = submitted.copy(minimum = "2.5")
        val normalized = submitted.copy(minimumTemplate = pieces)
        val acknowledged = SupplierCatalogOfferDraft(owner, saved, newer).acknowledged(submitted, normalized)
        assertEquals("2.5", acknowledged.fields.minimum)
        assertEquals(kilograms, acknowledged.fields.minimumTemplate)
        assertEquals(pieces, acknowledged.baseline.minimumTemplate)
    }

    @Test
    fun confirmedRemoteSnapshotConsumesTheMatchingDraftAfterLeavingScreen() {
        val edited = saved.copy(price = "150")
        val refreshed = SupplierCatalogOfferDraft(owner, saved, edited).refreshed(edited)
        assertFalse(refreshed.edited)
        assertEquals(edited, refreshed.baseline)
    }

    @Test
    fun restoredDraftBelongsToOneAccountSupplierStoreAndProduct() {
        val draft = SupplierCatalogOfferDraft(owner, saved, saved.copy(price = "175"))
        assertEquals(draft, decodeSupplierCatalogOfferDraft(draft.encoded(), owner))
        val otherOwners = listOf(
            supplierCatalogOfferDraftKey("other-account", "supplier", "store", "goods"),
            supplierCatalogOfferDraftKey("account", "other-supplier", "store", "goods"),
            supplierCatalogOfferDraftKey("account", "supplier", "other-store", "goods"),
            supplierCatalogOfferDraftKey("account", "supplier", "store", "other-goods")
        )
        otherOwners.forEach { other ->
            assertNotEquals(owner, other)
            assertNull(decodeSupplierCatalogOfferDraft(draft.encoded(), other))
        }
        assertTrue(appStateSafeKey(owner))
        assertEquals(owner, supplierCatalogOfferDraftKey(" ACCOUNT ", "SUPPLIER", "Store", "Goods"))
    }

    @Test
    fun corruptOrOversizedDraftDoesNotReplaceTheServerOffer() {
        assertNull(decodeSupplierCatalogOfferDraft("{broken", owner))
        assertNull(decodeSupplierCatalogOfferDraft("x".repeat(8193), owner))
        assertNull(decodeSupplierCatalogOfferDraft(null, owner))
        val noCurrency = SupplierCatalogOfferDraft(owner, saved, saved.copy(currency = ""))
        assertNull(decodeSupplierCatalogOfferDraft(noCurrency.encoded(), owner))
    }
}
