package kz.aita.server.auth

import kz.aita.auth.*
import kotlin.test.*

class AitaStoredPhoneLookupTest {
    /** Same character-by-character semantics as PostgreSQL translate; SQL execution has a separate test. */
    private fun compact(value: String): String = buildString {
        value.forEach { character ->
            val i = AitaStoredPhoneLookup.from.indexOf(character)
            if (i < 0) append(character) else AitaStoredPhoneLookup.to.getOrNull(i)?.let { append(it) }
        }
    }

    @Test fun lookupPreservesAsciiDigitsAndRemovesOnlySupportedFormatting() {
        listOf("+7 (777) 123-45-67", "7.777.123.45.67", "7/777/123/45/67", "7\u00a0777\u202f1234567").forEach {
            assertEquals("77771234567", compact(it))
        }
        assertEquals("7abc7771234567", compact("7abc7771234567"))
        assertEquals("7_7771234567", compact("7_7771234567"))
    }

    @Test fun everySupportedDecimalCharacterMapsToTheSameAsciiDigit() {
        (Char.MIN_VALUE..Char.MAX_VALUE).forEach { character ->
            character.digitToIntOrNull()?.let { assertEquals(it.toString(), compact(character.toString())) }
        }
        assertEquals("77771234567", compact("+７ (７７７) １２３-４５-６７"))
        assertEquals("77771234567", compact("+٧ (٧٧٧) ١٢٣-٤٥-٦٧"))
    }

    @Test fun lookupNeverSilentlyAcceptsMalformedPhoneOrAnotherExplicitCountry() {
        listOf("++77771234567", "7+7771234567", "+87771234567").forEach { raw ->
            val candidates = listOf(raw to "wrong")
            assertTrue(aitaMatchingPhoneLoginOwners("+77771234567", candidates).isEmpty())
        }
    }

    @Test fun mainAndExtraPhoneIdentifiersSendOnlyToTheOwnersMainEmail() {
        listOf("+77771234567", "+79991234567").forEach { phone ->
            val identifier = assertNotNull(normalizeAitaLoginIdentifier(phone))
            assertEquals("owner@example.com", aitaEmailCodeDestination(identifier, " Owner@Example.COM "))
            assertNull(aitaEmailCodeDestination(identifier, ""))
            assertNull(aitaEmailCodeDestination(identifier, phone))
        }
    }

    @Test fun verifiedExtraEmailLoginUsesThatEmailNotAnUnrelatedMainAddress() {
        val identifier = assertNotNull(normalizeAitaLoginIdentifier("Extra@Example.COM"))
        assertEquals("extra@example.com", aitaEmailCodeDestination(identifier, "main@example.com"))
    }
}
