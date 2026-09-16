#!/usr/bin/env python3
"""UI/resource wiring checks complement actual Kotlin state, server and Compose measurement tests."""
from pathlib import Path
import re
import unittest
ROOT=Path(__file__).resolve().parents[3]
UI=ROOT/'composeApp/src/commonMain/kotlin/kz/aita'
class AccountPresentationTests(unittest.TestCase):
    def test_modes_are_icon_and_title_only(self):
        s=(UI/'CommonMainComposeMenuB.kt').read_text()
        card=s.split('internal fun AppConfiguration.AppModeSelectionCard(',1)[1].split('@Composable',1)[0]
        self.assertIn('Role.RadioButton',card);self.assertIn('accountPresentationText',card)
        for old in ('option.subtitle','option.promise','option.features','AppModeFeatureChip'):self.assertNotIn(old,card)
        screen=s.split('fun AppConfiguration.MenuAppModeScreen()',1)[1].split('@Composable',1)[0]
        self.assertNotIn('Choose how AITA behaves',screen);self.assertNotIn('SupplierOverview',screen)
        self.assertIn('setAppMode(option.modeId)',screen)
    def test_worker_headers_and_response_empty_regions(self):
        s=(UI/'CommonMainComposeMenuA.kt').read_text().split('fun AppConfiguration.MenuWorkersScreen()',1)[1].split('internal fun AppConfiguration.workerRequestStatusLabel',1)[0]
        for label in ('Worker responses','My response history','Store response history','Store workers'):
            chunk=s[s.index('Text(text = localizedStringResource',s.index('"'+label+'"')-80):]
            self.assertIn('textAlign = TextAlign.Center',chunk[:420])
        for key in (8,12):self.assertIn(f'remainingListSpace(workerListState, "MenuWorkersScreen:$section:{key}")',s)
    def test_extra_contacts_and_fixed_method_editor(self):
        s=(UI/'AccountAuthenticationSettings.kt').read_text()
        self.assertEqual(2,s.count('accountPresentationText("not_added")'))
        self.assertIn('FactorSelectionAction.CONFIRM_DISABLE',s);self.assertIn('disableLoginDialog',s)
        self.assertNotIn('AuthenticatorLoginRequirementToggle(',s)
        policy=(UI/'AuthenticationEmailUi.kt').read_text().split('internal fun AppConfiguration.LoginPolicyEditor(',1)[1].split('internal data class ProfileSecurityConfirmation',1)[0]
        for token in ('settings.securityRevision','AitaSecurityEmailAction.LOGIN_POLICY','settings.authenticatorEnabled','password.isNotBlank()','authenticatedSessionGenerationIsCurrent'):self.assertIn(token,policy)
    def test_about_stays_informational_without_disabling_the_updater(self):
        s=(UI/'ClientUpdatesScreen.kt').read_text().split('fun AppConfiguration.AboutScreen()',1)[1]
        self.assertNotIn('UpdateCheckFooter(',s);self.assertNotIn('Menu.ClientUpdate',s)
        main=(UI/'CommonMainComposeMenuB.kt').read_text()
        self.assertIn('AppUpdateEffects()',main);self.assertIn('MenuUpdateMarker(',main)
    def test_new_copy_includes_all_six_languages_and_no_false_save_promise(self):
        s=(ROOT/'shared/src/commonMain/kotlin/kz/aita/messages/AccountPresentationMessages.kt').read_text()
        rows=[l for l in s.splitlines() if 'EventMessageTemplate(' in l]
        self.assertGreaterEqual(len(rows),28)
        for line in rows:
            for lang in ('ky','tg','uz'):self.assertIn(lang+' = ',line)
        keys=re.findall(r'EventMessageTemplate\("([^"]+)"',s);self.assertEqual(len(keys),len(set(keys)))
        self.assertNotIn('Your saved picture is unchanged',s)
    def test_help_matches_the_updated_security_and_about_screens(self):
        import json
        book=json.loads((ROOT/'server/src/main/resources/help/tutorials.json').read_text())
        guides={t['id']:t for t in book['tutorials']}
        self.assertGreaterEqual(book['revision'],2)
        self.assertIn('account.profile-photo',guides);self.assertIn('account.two-factor',guides)
        self.assertNotIn('About → Check for updates',json.dumps(guides['settings.updates'],ensure_ascii=False))
        self.assertNotIn('Use Check for updates',json.dumps(guides['settings.about']))
        self.assertEqual((ROOT/'server/src/main/resources/help/tutorials.json').read_bytes(),
            (ROOT/'composeApp/src/commonMain/composeResources/files/assets/help/tutorials.json').read_bytes())
    def test_photo_is_account_owned_and_migration_unique(self):
        s=(ROOT/'server/src/main/kotlin/kz/aita/server/profile/ProfilePhotoRoutes.kt').read_text()
        self.assertIn('authenticate("auth-jwt")',s);self.assertIn('call.checkPrincipal()',s)
        self.assertNotIn('request.queryParameters["user',s)
        migration=(ROOT/'server/src/main/resources/db/migration/V114__user_profile_photos.sql').read_text()
        self.assertIn('ON DELETE CASCADE',migration);self.assertIn('524288',migration)
        versions=[p.name.split('__')[0] for p in (ROOT/'server/src/main/resources/db/migration').glob('V*__*')]
        self.assertEqual(len(versions),len(set(versions)))
if __name__=='__main__':unittest.main()
