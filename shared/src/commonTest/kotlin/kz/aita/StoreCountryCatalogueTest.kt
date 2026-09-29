package kz.aita

import kotlin.test.*
import kz.aita.auth.*

class StoreCountryCatalogueTest {
    @Test fun cachedConfigurationUpgradePreservesExistingSettingsAndLegacyFormLabels() {
        val current = globalAppConfigurationState.payloadValue
        val oldForm = defaultCompanyForms().first().copy(name = listOf(LocalizedStringDataModel("en", "Operator LLP")), countryLocales = emptyList(), legalIdFormatId = "")
        val old = current.copy(countries = defaultStoreCountries().take(2), companyForms = listOf(oldForm), legalIdFormats = defaultLegalIdFormats().take(1))
        val upgraded = old.withStoreCountryConfiguration()
        assertEquals(5, upgraded.countries.size)
        assertEquals(old.countries, upgraded.countries.take(2))
        assertEquals(oldForm.name, upgraded.companyForms.first().name)
        assertEquals("kz_bin", upgraded.companyForms.first().legalIdFormatId)
        assertEquals(old.serverUrl, upgraded.serverUrl)
        assertEquals(upgraded, upgraded.withStoreCountryConfiguration())
    }

    @Test fun allSupportedCountriesWorkWithoutRemoteConfiguration() {
        val countries = defaultStoreCountries()
        assertEquals(setOf("kz", "tj", "kg", "uz", "ru"), countries.map { it.locale }.toSet())
        assertEquals(countries.map { it.locale }, countries.take(1).withSupportedCountries().map { it.locale })
        assertEquals(countries, countries.withSupportedCountries())
        for ((country, phone, currency) in listOf(Triple("kz", "+7 777 123 45 67", "KZT"),
            Triple("tj", "+992 900123456", "TJS"), Triple("kg", "+996 555123456", "KGS"), Triple("uz", "+998 901234567", "UZS"))) {
            assertEquals(country, storeCountryFromPhones(listOf(phone))?.locale)
            assertEquals(currency, countryCurrency(country))
            assertTrue(defaultCompanyForms().count { country in it.countryLocales } >= 2)
        }
        assertEquals("ru", storeCountryFromPhones(listOf("+7 912 1234567"))?.locale) // Shared +7 must not misclassify Russia.
        assertNull(storeCountryFromPhones(listOf("7771234567")))
        assertNull(storeCountryFromPhones(listOf("+996")))
        assertNull(storeCountryFromPhones(listOf("+996555123456", "+998901234567")))
    }
    @Test fun companyFormsSelectTheirOwnTaxIdentifiersAndPreserveLegacyKzId() {
        val forms = defaultCompanyForms().associateBy { it.id }
        assertEquals("kz_bin", forms.getValue("0").legalIdFormatId)
        assertEquals("kz_iin", forms.getValue("kz_ip").legalIdFormatId)
        assertEquals("kz_bin", forms.getValue("kz_joint_ip").legalIdFormatId)
        assertEquals("uz_pinfl", forms.getValue("uz_ip").legalIdFormatId)
        val lengths = mapOf("kz_bin" to 12, "kz_iin" to 12, "tj_tin" to 9, "kg_tin" to 14, "uz_tin" to 9, "uz_pinfl" to 14, "ru_inn_10" to 10, "ru_inn_12" to 12)
        for ((id, length) in lengths) {
            val format = defaultLegalIdFormats().single { it.id == id }
            assertTrue("1".repeat(length).matchesLegalIdFormat(format))
            assertFalse("1".repeat(length - 1).matchesLegalIdFormat(format))
            assertFalse("1".repeat(length + 1).matchesLegalIdFormat(format))
            assertFalse(("A" + "1".repeat(length - 1)).matchesLegalIdFormat(format))
        }
    }
    @Test fun nationalPhoneLoginCompatibilityIncludesNewCountriesWithoutCrossCountryGuessing() {
        assertEquals("+996555123456", normalizeAitaStoredMainPhone("555123456", "kg"))
        assertEquals("+998901234567", normalizeAitaStoredMainPhone("901234567", "uz"))
        assertEquals("555123456" to setOf("kg", "ky"), aitaMainPhoneNationalCandidate("+996555123456"))
        assertEquals("901234567" to setOf("uz"), aitaMainPhoneNationalCandidate("+998901234567"))
    }
    @Test fun onlyVerifiedAccountOwnershipIsReusableAndOnlyForStoreContacts() {
        val settings = AitaAuthenticationSettingsDataModel(email = "owner@example.test", emailVerified = true,
            phoneLoginAlias = "+996555123456", phoneLoginAliasVerified = true, additionalLoginEmails = listOf("extra@example.test"))
        assertTrue(aitaReusableAccountContact(AitaContactPurpose.STORE_CONTACT, AitaContactChannel.EMAIL,
            " OWNER@EXAMPLE.TEST ", settings.verifiedAccountContacts(AitaContactChannel.EMAIL)))
        assertFalse(aitaReusableAccountContact(AitaContactPurpose.REGISTRATION, AitaContactChannel.EMAIL,
            "owner@example.test", settings.verifiedAccountContacts(AitaContactChannel.EMAIL)))
        assertFalse(aitaReusableAccountContact(AitaContactPurpose.STORE_CONTACT, AitaContactChannel.EMAIL,
            "owner@example.test", settings.copy(emailVerified = false).verifiedAccountContacts(AitaContactChannel.EMAIL)))
        assertTrue(settings.verifiedAccountContacts(AitaContactChannel.PHONE).isEmpty()) // Email-approved login alias is not SMS ownership.
        assertTrue(aitaReusableAccountContact(AitaContactPurpose.STORE_CONTACT, AitaContactChannel.PHONE,
            "+996 555123456", listOf("+996555123456"))) // Future SMS provider supplies the verified list.
    }
}
