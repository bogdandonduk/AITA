package kz.aita.auth

import kotlin.test.*

class PhoneLoginInputTest {
    @Test fun nationalDigitsAreNotMistakenForACountryPrefix() {
        listOf("", "7", "77", "7771234567", "9123456789", "7011234567").forEach {
            assertEquals(it, normalizeAitaPhoneFieldInput(it, "+7", 10))
        }
    }

    @Test fun fullFormattedAndTrunkNumbersAreAcceptedAsNationalInput() {
        listOf("+7 (777) 123-45-67", "77771234567", "8 777 123 45 67", "+７ ７７７ １２３ ４５ ６７").forEach {
            assertEquals("7771234567", normalizeAitaPhoneFieldInput(it, "+7", 10))
        }
    }

    @Test fun anotherCountryIsNotSilentlyPrependedToTheCurrentCallingCode() {
        assertNull(normalizeAitaPhoneFieldInput("+992 900123456", "+7", 10))
        assertEquals("900123456", normalizeAitaPhoneFieldInput("+992 900123456", "+992", 9))
    }

    @Test fun invalidOrOverlongInputIsRejectedWithoutInventingAPhoneIdentity() {
        listOf("7abc7771234567", "++77771234567", "7+7771234567", "1234567890123456").forEach {
            assertNull(normalizeAitaPhoneFieldInput(it, "+7", 10))
        }
        assertNull(normalizeAitaPhoneAlias("7abc7771234567"))
        assertNull(normalizeAitaPhoneAlias("++77771234567"))
        assertNull(normalizeAitaPhoneAlias("7+7771234567"))
    }

    @Test fun signUpAndCanonicalPhoneIdentifiersHaveTheSameIndexedCandidates() {
        val expected = setOf("+77771234567", "77771234567", "87771234567")
        listOf("+77771234567", "77771234567", "87771234567", "+7 (777) 123-45-67").forEach {
            assertEquals(expected, aitaPhoneLoginStorageCandidates(it))
        }
    }

    @Test fun otherCallingCodesNeverReceiveARussianTrunkAlias() {
        assertEquals(setOf("+992900123456", "992900123456"), aitaPhoneLoginStorageCandidates("+992900123456"))
        assertTrue(aitaPhoneLoginStorageCandidates("not a number").isEmpty())
    }

    @Test fun duplicateRepresentationsOfOneOwnerResolveButDifferentOwnersFailClosed() {
        assertEquals("a", uniqueAitaPhoneLoginOwner(listOf("a"), emptyList()))
        assertEquals("a", uniqueAitaPhoneLoginOwner(emptyList(), listOf("a")))
        assertEquals("a", uniqueAitaPhoneLoginOwner(listOf("a", "a"), listOf("a")))
        assertNull(uniqueAitaPhoneLoginOwner(listOf("a"), listOf("b")))
        assertNull(uniqueAitaPhoneLoginOwner(listOf("a", "b"), emptyList()))
        assertNull(uniqueAitaPhoneLoginOwner(emptyList<String>(), emptyList()))
    }

    @Test fun countryCodeAndPartialNationalNumberNeverSubmitAnEmailChallenge() {
        listOf("", "7", "777123", "777123456").forEach {
            assertNull(aitaPhoneLoginFromNationalInput(it, "+7", 10))
        }
        assertEquals("+77771234567", aitaPhoneLoginFromNationalInput("7771234567", "+7", 10))
        assertEquals("+992900123456", aitaPhoneLoginFromNationalInput("900123456", "+992", 9))
        assertNull(aitaPhoneLoginFromNationalInput("7771234567", "+7", 0))
        assertNull(aitaPhoneLoginFromNationalInput("7771234567", "country", 10))
    }

    @Test fun completePastedNumbersAndLocalizedDigitsKeepTheirOwner() {
        listOf("+7 (777) 123-45-67", "8 777 123 45 67", "７７７１２３４５６７").forEach {
            assertEquals("+77771234567", aitaPhoneLoginFromNationalInput(it, "+7", 10))
        }
        assertNull(aitaPhoneLoginFromNationalInput("+992900123456", "+7", 10))
    }

    @Test fun legacyFormattedCandidatesResolveWithoutTreatingPunctuationAsIdentity() {
        val records = listOf("+7 (777) 123-45-67" to "a", "8 777 123 45 67" to "a", "77771234567" to "a")
        assertEquals(setOf("a"), aitaMatchingPhoneLoginOwners("+77771234567", records))
    }

    @Test fun formattedCollisionIsNotHiddenByAnExactMatch() {
        val records = listOf("77771234567" to "a", "+7 (777) 123-45-67" to "b")
        val owners = aitaMatchingPhoneLoginOwners("+77771234567", records)
        assertEquals(setOf("a", "b"), owners)
        assertNull(uniqueAitaPhoneLoginOwner(owners, emptyList()))
    }

    @Test fun broadSqlCandidatesAreValidatedBeforeTrustingTheirOwner() {
        val records = listOf(
            "+87771234567" to "explicit-other-country", "7+7771234567" to "misplaced-plus",
            "++77771234567" to "double-plus", "7abc7771234567" to "letters",
            "7771234567" to "national-only", null to "missing", "77771234568" to "different"
        )
        assertTrue(aitaMatchingPhoneLoginOwners("+77771234567", records).isEmpty())
        assertTrue(aitaMatchingPhoneLoginOwners("invalid", records).isEmpty())
    }
}
