from pathlib import Path
import json
import unittest
ROOT=Path(__file__).resolve().parents[3]
UI=ROOT/'composeApp/src/commonMain/kotlin/kz/aita'
SERVER=ROOT/'server/src/main/kotlin/kz/aita/server'
SHARED=ROOT/'shared/src/commonMain/kotlin/kz/aita'
class StorePeopleAndScaleContracts(unittest.TestCase):
    def test_connection_information_is_concentrated_in_top_banner(self):
        ui=(UI/'InventoryLoadingUi.kt').read_text()
        self.assertNotIn('actionButton(',ui);self.assertNotIn('connection.probe_codes',ui)
        self.assertIn('InventoryFeedbackKind.CacheWriteFailure',ui);self.assertIn('InventoryFeedbackKind.Denied',ui)
        banner=(UI/'CommonMainComposeMenuB.kt').read_text().split('internal fun AppConfiguration.CloudConnectionStatusBanner()',1)[1].split('@Composable',1)[0]
        self.assertIn('storePeopleText("offline")',banner);self.assertNotIn('Check Wi',banner)
    def test_tutorials_have_normal_top_spacing_without_offline_paragraph(self):
        ui=(UI/'TutorialsScreen.kt').read_text()
        self.assertNotIn('tutorialText("offline")',ui);self.assertNotIn('state.remoteUnavailable',ui)
        self.assertIn('padding(top=stateValues.marginTextField)',ui)
    def test_theme_selection_is_bounded_and_large_is_exposed(self):
        mark=(UI/'ThemeSelectionMark.kt').read_text();self.assertIn('size(44.dp)',mark);self.assertIn('size(22.dp)',mark)
        card=(UI/'CommonMainComposeWidgetsConfig.kt').read_text().split('fun AppConfiguration.AppThemeSettingsItemWidget(',1)[1].split('@Composable',1)[0]
        self.assertIn('ThemeSelectionMark',card);self.assertNotIn('fillMaxHeight',card)
        self.assertIn('storePeopleText("large")',(UI/'CommonMainComposeMenuB.kt').read_text())
        self.assertIn('storePeopleText("large")',(UI/'CommonMainComposeAuthTransaction.kt').read_text())
    def test_photo_editor_is_mode_bound_in_all_requests_and_replies(self):
        text=(UI/'UserProfilePhoto.kt').read_text()
        for token in ('key(owner,generation,mode)','ProfilePhotoClient.load(generation,mode)','ProfilePhotoClient.preview(bytes,generation,mode)','ProfilePhotoClient.save(value,generation,mode)','validProfilePhotoSnapshot(photo,owner,mode)'):self.assertIn(token,text)
        self.assertNotIn('accountPresentationText("photo.preview")',text)
    def test_existing_private_pictures_are_not_published_to_colleagues(self):
        migration=(ROOT/'server/src/main/resources/db/migration/V115__mode_profile_photos_and_people_indexes.sql').read_text()
        self.assertIn("SELECT user_id, 'MARKETPLACE'",migration)
        self.assertNotIn("SELECT user_id, 'STORE'",migration)
        routes=(SERVER/'profile/ProfilePhotoRoutes.kt').read_text()
        self.assertIn('DatabaseModeProfilePhotos(ProfilePhotoMode.MARKETPLACE)',routes)
        self.assertIn('ProfilePhotoMode.entries.forEach',routes)
    def test_profile_is_linked_from_work_and_history_without_login_data(self):
        text=(UI/'CommonMainComposeMenuA.kt').read_text()
        for token in ('StorePersonLink(workerStoreId,worker.userId','StorePersonLink(transaction.storeId,transaction.actorUserId)','StorePersonLink(log.storeId,log.actorUserId'):self.assertIn(token,text)
        model=(SHARED/'StorePeople.kt').read_text().split('fun validStorePersonProfile',1)[0]
        for token in ('password','phoneNumber','email','salary','token'):self.assertNotIn(token,model)
        repo=(SERVER/'profile/StorePeopleRepository.kt').read_text()
        self.assertIn('userHasStoreAccessInsideTransaction(viewer,store)',repo)
        self.assertIn('storePeopleCanAnalyzeInsideTransaction(viewer,store)',repo)
        self.assertIn("'purchase'",repo);self.assertIn('StatementType.SELECT',repo)
    def test_handbook_mode_photo_and_employee_guidance_matches_sources(self):
        path=ROOT/'server/src/main/resources/help/tutorials.json';book=json.loads(path.read_text())
        self.assertEqual(path.read_bytes(),(ROOT/'composeApp/src/commonMain/composeResources/files/assets/help/tutorials.json').read_bytes())
        guides={t['id']:t for t in book['tutorials']}
        self.assertEqual(['STORE'],guides['team.employee-profile']['modes'])
        self.assertNotIn('MANUFACTURER',guides['account.profile-photo']['modes'])
        self.assertIn('Large',json.dumps(guides['settings.appearance']))
    def test_new_messages_cover_all_supported_languages(self):
        rows=[x for x in (SHARED/'messages/StorePeopleMessages.kt').read_text().splitlines() if 'EventMessageTemplate(' in x]
        self.assertGreaterEqual(len(rows),30)
        for row in rows:
            for language in ('ky=','tg=','uz='):self.assertIn(language,row.replace(' ',''))
if __name__=='__main__':unittest.main()
