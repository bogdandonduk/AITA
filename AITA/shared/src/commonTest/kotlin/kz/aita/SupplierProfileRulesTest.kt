package kz.aita

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SupplierProfileRulesTest {
    @Test
    fun equivalentPhoneFormattingIsDeduplicated() {
        val profile = SupplierDataModel(
            name = listOf(LocalizedStringDataModel("main", "Alpha")),
            phoneNumbers = listOf("+7 (777) 123-45-67", "+77771234567")
        )
        assertEquals(1, profile.normalizedSupplierProfileFields().phoneNumbers.orEmpty().size)
        assertTrue(SupplierProfileReadinessIssue.DuplicatePhone in profile.supplierProfileReadinessIssues())
    }

    @Test
    fun emailValidationRejectsMalformedAddress() {
        assertTrue(supplierProfileEmailLooksValid("sales@example.kz"))
        assertFalse(supplierProfileEmailLooksValid("sales@localhost"))
        assertFalse(supplierProfileEmailLooksValid("sales @example.kz"))
    }

    @Test
    fun primaryContactMergePreservesAdditionalContacts() {
        assertEquals(
            listOf("new@example.kz", "other@example.kz"),
            mergeSupplierProfilePrimaryEmail(
                existing = listOf("old@example.kz", "other@example.kz"),
                primary = "NEW@example.kz"
            )
        )
        assertEquals(
            listOf("77001112233", "77009998877"),
            mergeSupplierProfilePrimaryPhone(
                existing = listOf("77000000000", "77009998877"),
                primary = "77001112233"
            )
        )
    }

    @Test
    fun editingMainNamePreservesOtherTranslations() {
        val merged = mergeSupplierProfilePrimaryName(
            existing = listOf(
                LocalizedStringDataModel("main", "Old"),
                LocalizedStringDataModel("ru", "Старое")
            ),
            primaryName = "New"
        )
        assertEquals("New", merged.first { it.language == "main" }.value)
        assertEquals("Старое", merged.first { it.language == "ru" }.value)
    }
    @Test
    fun normalizationPreservesAbsentTypeIds() {
        val profile = SupplierDataModel(
            name = listOf(LocalizedStringDataModel("main", "Alpha")),
            typeIds = null
        )
        assertEquals(null, profile.normalizedSupplierProfileFields().typeIds)
    }

}
