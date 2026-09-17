#!/usr/bin/env python3
"""Cross-module contact-confirmation contracts; these do not replace compiled/DB/UI tests."""
from pathlib import Path
import json
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / 'composeApp/src/commonMain/kotlin/kz/aita'
SHARED = ROOT / 'shared/src/commonMain/kotlin/kz/aita'
SERVER = ROOT / 'server/src/main/kotlin/kz/aita/server'

def source(path): return path.read_text(encoding='utf-8')

def method(text, start, end):
    return text.split(start, 1)[1].split(end, 1)[0]

class ContactEmailConfirmationContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.auth = source(SERVER / 'auth/AitaAdvancedAuthentication.kt')
        cls.routes = source(SERVER / 'Server.kt')
        cls.ui = source(UI / 'ContactEmailConfirmation.kt')
        cls.models = source(SHARED / 'auth/ContactVerificationModels.kt')
        cls.migration = source(ROOT / 'server/src/main/resources/db/migration/V111__scoped_contact_email_confirmation.sql')

    def test_phone_channel_is_reserved_but_rejected_by_server_and_schema(self):
        self.assertIn('enum class AitaContactChannel { EMAIL, PHONE }', self.models)
        self.assertIn('request.channel != AitaContactChannel.EMAIL', self.auth)
        self.assertIn("CHECK (channel = 'EMAIL')", self.migration)
        for key in ('REGISTRATION', 'ACCOUNT_CONTACT', 'STORE_CONTACT', 'SUPPLIER_CONTACT'):
            self.assertIn(key, self.models)
            self.assertIn("'"+key+"'", self.migration)

    def test_receipts_are_scoped_to_actor_purpose_entity_parent_and_address(self):
        scope = method(self.auth, 'private fun contactScope(', 'private fun contactAuthorizationValidInside')
        for token in ('EMAIL.name', 'actor?.toString()', 'target.purpose.name', 'target.entityId', 'target.parentId', 'address'):
            self.assertIn(token, scope)
        validate = method(self.auth, 'internal fun requireContactProofsInside(', 'internal fun consumeContactProofsInside(')
        for token in ('proofs.size != needed.size', 'distinct().size', 'proofs.sortedBy', 'forUpdate()', 'aitaContactProofMatches(',
                      'contactAuthorizationValidInside(binding)', 'constantTimeEquals(', 'System.currentTimeMillis() >= earliestExpiry'):
            self.assertIn(token, validate)

    def test_code_and_save_are_separate_and_consumption_records_real_entity(self):
        verify = method(self.auth, 'suspend fun verifyContactCode(', 'internal fun requireContactProofsInside(')
        self.assertNotIn('Users.insert', verify)
        self.assertNotIn('tokenService.newPair', verify)
        consume = method(self.auth, 'internal fun consumeContactProofsInside(', 'internal fun markPrimaryEmailVerifiedInside(')
        for token in ('it[consumedAtMillis] = now', 'it[receiptCiphertext] = ""', 'it[AuthContactVerifications.appliedEntityId] = appliedEntityId'):
            self.assertIn(token, consume)
        self.assertIn('applied_entity_id uuid', self.migration)

    def test_registration_route_itself_rejects_old_client_bypass(self):
        signup = method(self.routes, 'post("/signUp") {', 'post("/logIn") {')
        self.assertEqual(2, signup.count('requireContactProofsInside('))
        self.assertLess(signup.rindex('requireContactProofsInside('), signup.index('Users.insert'))
        self.assertLess(signup.index('Users.insert'), signup.index('consumeContactProofsInside('))
        self.assertLess(signup.index('consumeContactProofsInside('), signup.index('tokenService.newPair'))
        self.assertIn('markPrimaryEmailVerifiedInside(id, email)', signup)

    def test_public_and_authenticated_routes_have_separate_ownership(self):
        public = method(self.auth, 'route("/registration/email")', 'get("/capabilities")')
        self.assertIn('installContactVerificationActions(service, registration = true)', public)
        authenticated = self.auth.split('authenticate("auth-jwt")', 1)[1]
        self.assertIn('route("/security")', authenticated)
        self.assertIn('route("/contact-verification")', authenticated)
        self.assertIn('installContactVerificationActions(service, registration = false)', authenticated)
        actions = self.auth.split('private fun Route.installContactVerificationActions(', 1)[1]
        for action in ('request', 'resend', 'verify'):
            self.assertIn('post("/' + action + '")', actions)
        self.assertEqual(3, actions.count('if (registration) null else (call.checkPrincipal() ?: return@post)'))
        self.assertIn('requestContactCode(actor,', actions)
        self.assertIn('verifyContactCode(actor,', actions)
        self.assertIn('(request.target.purpose == AitaContactPurpose.REGISTRATION) != registration', actions)

    def test_unknown_login_stays_neutral_and_registration_is_the_only_anonymous_delivery(self):
        self.assertIn('(userId != null || purpose == AUTH_PURPOSE_CONTACT)', self.auth)
        self.assertIn('if (!contactPurpose && user == null)', self.auth)
        self.assertIn('contactDeliveryDestinationInside(challenge)', self.auth)

    def test_destination_rate_limit_does_not_use_draft_as_quota_key(self):
        issue = method(self.auth, 'private suspend fun issueContactCode(', 'suspend fun resendContactCode(')
        self.assertIn('crypto.hmac("contact-destination", address)', issue)
        self.assertIn('checkEmailQuotaInside(emailHash, ipHash, now)', issue)
        self.assertIn('lockEmailBucketsInside(emailHash, ipHash, actor)', issue)
        self.assertIn('hourly.size >= 30', issue)
        self.assertLess(issue.index('lockEmailBucketsInside'), issue.index('val now = System.currentTimeMillis()'))

    def test_resend_keeps_original_deadline_and_idempotent_verify_does_not_extend_it(self):
        issue = method(self.auth, 'private suspend fun issueContactCode(', 'suspend fun resendContactCode(')
        self.assertIn('else requireNotNull(old)[AuthOneTimeChallenges.expiresAtMillis]', issue)
        verify = method(self.auth, 'suspend fun verifyContactCode(', 'internal fun requireContactProofsInside(')
        self.assertIn('receiptExpiresAtMillis] ?: (now + config.resetTtlMillis)', verify)
        self.assertIn('crypto.decrypt("contact-receipt:$publicId", it)', verify)
        self.assertIn('if (oldReceipt == null)', verify)

    def test_wrong_codes_commit_attempts_without_throwing_transaction_rollback(self):
        verify = method(self.auth, 'suspend fun verifyContactCode(', 'internal fun requireContactProofsInside(')
        failure = method(verify, 'if (!crypto.constantTimeEquals(', 'val oldReceipt =')
        self.assertIn('it[attempts] = row[AuthOneTimeChallenges.attempts] + 1', failure)
        self.assertIn('return@newSuspendedTransaction null', failure)
        self.assertNotIn('throw ', failure)
        self.assertLess(verify.index('.forUpdate()'), verify.index('val now = System.currentTimeMillis()'))

    def test_entity_authorization_is_checked_independently_of_contact_receipts(self):
        permission = method(self.routes, 'internal fun contactTargetIsAuthorizedInside(', 'private fun ResultRow.toSupplierDataModel(')
        for token in ('Users.isActive', 'STORE_PERMISSION_BRANCHES_MANAGE', 'STORE_PERMISSION_STORE_MANAGE',
                      'parentStoreId', 'it.userIds.contains(userId.toString())', 'normalized.entityId == userId.toString()'):
            self.assertIn(token, permission)

    def test_all_business_mutations_have_contact_gates_and_actor_first_locking(self):
        # Four mutation transactions: add/update store and add/update supplier.
        self.assertEqual(4, self.routes.count('contactAuth.lockContactActorInside(userId)'))
        self.assertEqual(4, self.routes.count('val contactProofIds = contactAuth.requireContactProofsInside(userId,'))
        self.assertEqual(7, self.routes.count('contactAuth.requireContactProofsInside('))
        self.assertEqual(6, self.routes.count('contactAuth.consumeContactProofsInside('))

    def test_account_change_keeps_current_password_and_factor_and_notifies_old_address(self):
        account = self.routes.split('val cleanNewPassword = body.newPassword', 1)[1]
        self.assertLess(account.index('Pw.verify(body.password'), account.index('requireContactProofsInside('))
        self.assertLess(account.index('requireContactProofsInside('), account.index('verifyAdvancedAuthProfileChangeInside('))
        self.assertLess(account.index('Users.update('), account.index('markPrimaryEmailVerifiedInside(uuid, email)'))
        self.assertIn('enqueueOldEmailChangeNoticeInside(uuid, existingUser[Users.email], appLanguage)', account)
        notice = method(self.auth, 'internal fun enqueueOldEmailChangeNoticeInside(', 'private fun contactDeliveryDestinationInside(')
        self.assertIn('AUTH_PURPOSE_CONTACT_NOTICE', notice)
        self.assertIn('it[address] = destination', notice)

    def test_email_notice_has_no_otp_and_confirmation_mail_is_not_a_login_email(self):
        template = source(SERVER / 'auth/AitaAuthEmailTemplate.kt')
        notice = method(template, 'if (purpose == AUTH_PURPOSE_CONTACT_NOTICE)', 'if (purpose == "TOTP_RESET_NOTICE")')
        self.assertIn('aitaAuthEmailLayout(title, detail, null,', notice)
        self.assertIn('contact.notice_detail', notice)
        self.assertIn('contact.email_instruction', template)
        self.assertIn('contact.title', template)

    def test_password_eye_uses_one_shared_transient_state(self):
        reset = source(UI / 'PasswordRecoveryScreen.kt')
        self.assertEqual(2, reset.count('passwordRevealed = passwordsRevealed'))
        self.assertEqual(2, reset.count('onPasswordRevealedChange = { passwordsRevealed = it }'))
        self.assertIn('passwordsRevealed by remember {', reset)
        field = source(UI / 'CommonMainComposeWidgetsConfig.kt')
        self.assertIn('passwordRevealed: Boolean? = null', field)

    def test_pending_registration_is_transient_and_created_only_after_confirmation(self):
        signup = method(source(UI / 'CommonMainComposeAuthTransaction.kt'), 'fun AppConfiguration.UserAuthSignUpScreen(', '\n@Composable')
        self.assertIn('pendingRegistration by remember', signup)
        self.assertIn('RegistrationEmailConfirmationScreen', signup)
        self.assertNotIn('signUpUser(', signup)
        self.assertIn('confirmation.ready && !stateValues.signUpInProgress', self.ui)
        self.assertIn('contactVerificationId = confirmation.draftId', self.ui)

    def test_receipts_and_codes_never_go_into_saveable_state(self):
        self.assertNotIn('rememberSaveable', self.ui)
        self.assertNotIn('StateHost(', self.ui)
        for token in ('DisposableEffect(state)', 'state.disposed = true', 'state.clear()', 'TimeSource.Monotonic.markNow()',
                      'authenticatedSessionGenerationIsCurrent(generation)', 'userAccountState.payloadValue?.id == owner'):
            self.assertIn(token, self.ui)
        self.assertIn('remember(owner, generation, target, required, stateValues.userAccount?.email)', self.ui)

    def test_network_result_owner_channel_target_address_and_receipt_are_checked(self):
        for token in ('!ownsCurrentSession()', 'result.channel != AitaContactChannel.EMAIL', 'result.address != flowAddress',
                      'result.proof.flowId != requestedFlow.flowId', 'canonicalAitaContactTarget(result.target)',
                      'result.proof.receipt.length !in 40..128', 'withTimeoutOrNull(30_000L)', 'catch (cancel: CancellationException)'):
            self.assertIn(token, self.ui)
        client = source(SHARED / 'auth/ContactVerificationClient.kt')
        self.assertEqual(3, client.count('expectedSessionGeneration ='))

    def test_account_store_supplier_and_quick_supplier_editors_share_the_same_confirmation(self):
        for file in ('CommonMainComposeMenuA.kt', 'CommonMainComposeMenuB.kt', 'SupplierModeProfilesComponents.kt', 'CommonMainComposeCartStockA.kt'):
            text = source(UI / file)
            self.assertIn('rememberContactEmailConfirmation(', text, file)
            self.assertIn('ContactEmailConfirmationContent(', text, file)
            self.assertIn('contactEmailProofs =', text, file)
            self.assertRegex(text, r'\w+\.ready', file)

    def test_store_save_failure_does_not_close_the_editor_or_throw_away_confirmation(self):
        store = method(source(UI / 'CommonMainComposeMenuB.kt'), 'fun AppConfiguration.MenuAddEditStoreScreen(', '\n@Composable')
        self.assertIn('storeSaveInProgress by remember', store)
        self.assertEqual(4, store.count('storeSaveInProgress = false'))
        self.assertEqual(4, store.count('if (result is DataState.Success)'))

    def test_request_receipts_are_stripped_before_model_cache_or_business_response(self):
        cleanup = source(SHARED / 'ContactVerificationStorage.kt')
        self.assertEqual(2, cleanup.count('contactEmailProofs = emptyList()'))
        self.assertIn('branches = branches.map { it.withoutContactVerification() }', cleanup)
        self.assertGreaterEqual(source(SHARED / 'CommonMain.kt').count('.withoutContactVerification()'), 4)
        self.assertIn('.withoutContactVerification()', self.routes)

    def test_legacy_contacts_are_not_backfilled_as_verified(self):
        self.assertIn('if (needed.isEmpty()) return emptyList()', self.auth)
        self.assertNotRegex(self.migration, r'(?i)UPDATE\s+(users|stores|suppliers)\b')
        self.assertIn('if (state.requiredAddresses?.isEmpty() == true) return', self.ui)

    def test_all_confirmation_messages_have_six_languages_and_matching_placeholders(self):
        messages = source(SHARED / 'messages/ContactVerificationMessages.kt')
        rows = [line for line in messages.splitlines() if 'EventMessageTemplate(' in line]
        self.assertEqual(27, len(rows))
        keys = set()
        for row in rows:
            values = re.findall(r'"((?:[^"\\]|\\.)*)"', row)
            self.assertEqual(7, len(values), row)
            self.assertNotIn(values[0], keys)
            keys.add(values[0])
            expected = set(re.findall(r'\{\w+\}', values[1]))
            for value in values[1:]:
                self.assertTrue(value.strip())
                self.assertEqual(expected, set(re.findall(r'\{\w+\}', value)))
            for lang in ('ky', 'tg', 'uz'): self.assertIn(lang+' =', row)
        self.assertIn('contactVerificationMessageTemplates()', source(SHARED / 'messages/EventMessageReference.kt'))

    def test_new_icon_pairs_match_and_have_android_counterparts_and_catalogue_entries(self):
        for variant in (0, 1):
            name = f'145_{variant}'
            svg = ROOT / f'composeApp/src/commonMain/composeResources/drawable/{name}.svg'
            matches = list(ROOT.glob(f'server/assets/**/{name}.svg'))
            self.assertEqual(1, len(matches))
            self.assertEqual(svg.read_bytes(), matches[0].read_bytes())
            ET.parse(svg)
            vector = ROOT / f'composeApp/src/androidMain/res/drawable/ic_aita_{name}.xml'
            self.assertEqual('vector', ET.parse(vector).getroot().tag)
            self.assertIn('Res.drawable._'+name, self.ui)
            self.assertIn('@drawable/ic_aita_'+name, source(ROOT / 'composeApp/src/androidMain/res/raw/aita_contact_confirmation_keep.xml'))
            fallback = source(UI / 'CommonMainComposeWidgetsConfig.kt')
            self.assertIn('"'+name+'" -> Res.drawable._'+name, fallback)
            android_ns = '{http://schemas.android.com/apk/res/android}'
            svg_paths = ET.parse(svg).getroot().findall('{http://www.w3.org/2000/svg}path')
            vector_paths = ET.parse(vector).getroot().findall('path')
            self.assertEqual([item.attrib['d'] for item in svg_paths], [item.attrib[android_ns+'pathData'] for item in vector_paths])
        catalogues = [p for p in ROOT.rglob('drawables.json')]
        self.assertGreaterEqual(len(catalogues), 2)
        for catalogue in catalogues:
            self.assertIn('145_0.svg', source(catalogue))
            self.assertIn('145_1.svg', source(catalogue))
        self.assertIn('extractPath(145L, theme)', self.ui)

    def test_opt_in_database_fixture_runs_real_additive_migration_and_adversarial_cases(self):
        text = source(ROOT / 'server/src/test/kotlin/kz/aita/server/auth/AitaAuthenticatorRecoveryDatabaseTest.kt')
        for token in ('V111__scoped_contact_email_confirmation.sql', 'startsWith("aita_test_")',
                      'contactWrongAttemptsCommit', 'contactProofCannotCrossPurpose', 'concurrentContactConsumers',
                      'destinationQuotaCannotBeBypassed', 'contactConsumptionAndActualMutationRollBackTogether'):
            self.assertIn(token, text)

if __name__ == '__main__': unittest.main()
