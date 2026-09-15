package kz.aita.auth

import kotlin.test.*

class ContactVerificationPolicyTest {
    private val first = "11111111-1111-4111-8111-111111111111"
    private val second = "22222222-2222-4222-8222-222222222222"
    private val target = AitaContactTarget(AitaContactPurpose.ACCOUNT_CONTACT, first)
    private fun matches(requested: AitaContactTarget = target, stored: AitaContactTarget = target,
        actor: String? = first, storedActor: String? = first, email: String = "new@example.test", storedEmail: String = "new@example.test",
        channel: AitaContactChannel = AitaContactChannel.EMAIL, now: Long = 1_000L,
        expiry: Long = 2_000L, consumed: Long? = null) = aitaContactProofMatches(requested, stored, actor, storedActor,
            email, storedEmail, channel, now, expiry, consumed)

    @Test fun canonicalAddressCannotSmuggleAnotherRecipient() {
        for (invalid in listOf("alice@example.test,bob@example.test", "a@example.test\r\nBcc:x@example.test", "x", "a@", "@b.test")) {
            assertNull(canonicalAitaContactEmails(listOf(invalid)), invalid)
        }
    }
    @Test fun emailCaseWhitespaceAndDuplicatesAreCanonical() {
        assertEquals(listOf("a@example.test", "b@example.test"), canonicalAitaContactEmails(listOf(" A@EXAMPLE.TEST ", "a@example.test", "", "b@example.test")))
    }
    @Test fun contactListsAreBoundedBeforeNormalization() { assertNull(canonicalAitaContactEmails(List(11) { "" })) }
    @Test fun emptyOptionalContactsNeedNoProof() { assertEquals(emptyList(), aitaContactEmailsRequiringProof(listOf(" "), emptyList())) }
    @Test fun unchangedLegacyContactsDoNotRequireRetroactiveProof() {
        assertEquals(emptyList(), aitaContactEmailsRequiringProof(listOf(" MAIN@EXAMPLE.TEST "), listOf("main@example.test")))
    }
    @Test fun onlyNewContactsNeedConfirmation() {
        assertEquals(listOf("new@example.test"), aitaContactEmailsRequiringProof(listOf("main@example.test", "new@example.test"), listOf("main@example.test")))
    }
    @Test fun invalidNewAddressDoesNotSilentlyBecomeOptional() { assertNull(aitaContactEmailsRequiringProof(listOf("no-at-sign"), emptyList())) }
    @Test fun registrationRequiresAnOpaqueDraftNotAnExistingUserId() {
        assertNull(canonicalAitaContactTarget(AitaContactTarget(AitaContactPurpose.REGISTRATION, first)))
        assertNotNull(canonicalAitaContactTarget(aitaContactDraftTarget(AitaContactPurpose.REGISTRATION, "", first)))
    }
    @Test fun accountChangeCannotTargetANewAccount() {
        assertNull(canonicalAitaContactTarget(aitaContactDraftTarget(AitaContactPurpose.ACCOUNT_CONTACT, "", first)))
        assertEquals(target, canonicalAitaContactTarget(target))
    }
    @Test fun newBranchMustBindItsParentButRootCanBeNew() {
        val branch = aitaContactDraftTarget(AitaContactPurpose.STORE_CONTACT, "", first, second)
        assertEquals(branch, canonicalAitaContactTarget(branch))
        assertNotNull(canonicalAitaContactTarget(branch.copy(parentId = "")))
        assertNull(canonicalAitaContactTarget(branch.copy(parentId = "not-a-uuid")))
    }
    @Test fun editedBranchesNeverTrustAnAlternateClientParent() {
        assertNull(canonicalAitaContactTarget(AitaContactTarget(AitaContactPurpose.STORE_CONTACT, first, second)))
    }
    @Test fun supplierAndRegistrationScopesHaveNoParent() {
        for (purpose in listOf(AitaContactPurpose.SUPPLIER_CONTACT, AitaContactPurpose.REGISTRATION)) {
            assertNull(canonicalAitaContactTarget(aitaContactDraftTarget(purpose, "", first, second)))
        }
    }
    @Test fun malformedAndOverlongScopeIdentifiersFailClosed() {
        for (id in listOf("", "new:", "1-1-1-1-1", first + "\n" + second, "a".repeat(200))) assertNull(canonicalAitaContactTarget(target.copy(entityId = id)))
    }
    @Test fun identifiersAreCanonicalBeforeComparison() { assertEquals(target, canonicalAitaContactTarget(target.copy(entityId = " $first "))) }
    @Test fun exactMatchingProofIsAccepted() { assertTrue(matches()) }
    @Test fun anotherAccountCannotUseAProof() { assertFalse(matches(actor = second)); assertFalse(matches(actor = null)) }
    @Test fun aProofCannotBeMovedBetweenEntities() { assertFalse(matches(requested = target.copy(entityId = second))) }
    @Test fun aProofCannotBeMovedBetweenPurposes() {
        assertFalse(matches(requested = target.copy(purpose = AitaContactPurpose.SUPPLIER_CONTACT)))
    }
    @Test fun aNewEntityProofCannotBeMovedBetweenDraftsOrParents() {
        val a = aitaContactDraftTarget(AitaContactPurpose.STORE_CONTACT, "", first, second)
        assertTrue(matches(a, a))
        assertFalse(matches(a, a.copy(parentId = first)))
        assertFalse(matches(a, a.copy(entityId = "new:$second")))
    }
    @Test fun wrongAddressAndUnavailablePhoneChannelFailClosed() {
        assertFalse(matches(email = "other@example.test")); assertFalse(matches(channel = AitaContactChannel.PHONE))
        assertTrue(matches(email = " NEW@EXAMPLE.TEST "))
    }
    @Test fun exactExpiryBoundaryIsClosed() { assertFalse(matches(now = 2_000L)); assertFalse(matches(now = 2_001L)); assertTrue(matches(now = 1_999L)) }
    @Test fun anyConsumedTimestampClosesTheProof() { assertFalse(matches(consumed = 0L)); assertFalse(matches(consumed = 900L)) }
    @Test fun invalidTargetsAndAddressesNeverMatchEachOther() {
        assertFalse(matches(target.copy(entityId = "x"), target.copy(entityId = "x")))
        assertFalse(matches(email = "invalid", storedEmail = "invalid"))
    }
    @Test fun anonymousRegistrationProofIsStillDraftAndAddressBound() {
        val draft = aitaContactDraftTarget(AitaContactPurpose.REGISTRATION, "", first)
        assertTrue(matches(draft, draft, actor = null, storedActor = null))
        assertFalse(matches(draft, draft, actor = first, storedActor = null))
    }
}
