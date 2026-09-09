package kz.aita.auth

import kotlin.test.*

class AdditionalEmailIdentityTest {
    @Test fun normalizationMatchesLoginAndConfirmation() {
        val raw = "  Owner+receipts@Example.COM  "
        assertEquals("owner+receipts@example.com", normalizeAitaEmail(raw))
        assertEquals(normalizeAitaEmail(raw), normalizeAitaLoginIdentifier(raw)?.value)
    }
    @Test fun providerSpecificDotsAndTagsAreNotRewritten() {
        assertEquals("a.b+shop@example.com", normalizeAitaEmail("a.b+shop@example.com"))
        assertNotEquals(normalizeAitaEmail("a.b@example.com"), normalizeAitaEmail("ab@example.com"))
    }
    @Test fun headerInjectionAndMalformedIdentifiersAreRejected() {
        listOf("a@b.com\r\nBcc:x@y.com", "a\u0000b@example.com", "a b@example.com", "@example.com",
            "a@@example.com", ".a@example.com", "a..b@example.com", "a@example..com", "a@localhost", "x".repeat(255))
            .forEach { assertNull(normalizeAitaEmail(it)) }
    }
    @Test fun additionalAddressesDoNotOverwritePrimaryAddressInSettings() {
        val settings = AitaAuthenticationSettingsDataModel(email = "primary@example.com", additionalLoginEmails = listOf("extra@example.com"))
        assertEquals("primary@example.com", settings.email)
        assertEquals(listOf("extra@example.com"), settings.additionalLoginEmails)
    }
    @Test fun olderCapabilitiesDoNotAdvertiseTheNewRoutes() {
        assertFalse(AitaAuthCapabilitiesDataModel().additionalEmailLoginEnabled)
        assertEquals(emptyList(), AitaAuthenticationSettingsDataModel().additionalLoginEmails)
    }
    @Test fun pastedConfirmationDigitsAreCanonicalized() {
        assertEquals("123456", normalizeAitaOneTimeCode(" ١٢٣ ٤٥٦ "))
        assertNull(normalizeAitaOneTimeCode("12345"))
    }
}
