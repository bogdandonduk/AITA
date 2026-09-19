"""Source/resource integration checks, not a substitute for Kotlin or rendered-device tests."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[3]
UI = ROOT / "composeApp/src/commonMain/kotlin/kz/aita"
SHARED = ROOT / "shared/src/commonMain/kotlin/kz/aita"


class AppearanceLiveSupportTest(unittest.TestCase):
    def test_colors_are_mirrored_with_white_text_and_unchanged_accent(self):
        server = ROOT / "server/assets/values/colors.json"
        bundled = ROOT / "composeApp/src/commonMain/composeResources/files/assets/values/colors.json"
        self.assertEqual(server.read_bytes(), bundled.read_bytes())
        colors = {x['id']: {v['themeId']: v['valueHex'] for v in x['values']} for x in json.loads(server.read_text())['payload']}
        self.assertEqual("ff241c36", colors[1][2]); self.assertEqual("ff17212b", colors[1][3])
        self.assertEqual("ffffffff", colors[2][2]); self.assertEqual("ffffffff", colors[2][3])
        self.assertEqual({"ffffba24"}, set(colors[0].values()))
        self.assertEqual("ffffffff", colors[1][0]); self.assertEqual("ff000000", colors[1][1])

    def test_icon_brightness_is_separate_from_theme_identity(self):
        palette = (SHARED / "AppThemePalette.kt").read_text()
        self.assertIn("APP_THEME_PURPLE = 2L", palette); self.assertIn("APP_THEME_BLUE = 3L", palette)
        self.assertIn('if (isDarkAppTheme(theme)) 1L else 0L', palette)
        widget = (UI / "CommonMainComposeWidgetsConfig.kt").read_text()
        self.assertIn("val themeId = appDrawableThemeId(appThemeIdState.value)", widget)
        self.assertIn('colors[normalizeAppThemePreference(theme)]', (SHARED / "AppearanceCatalog.kt").read_text())
        self.assertIn('drawables[appDrawableThemeId(theme)]', (SHARED / "AppearanceCatalog.kt").read_text())

    def test_handbook_has_no_manual_refresh_or_mode_count_banner(self):
        text = (UI / "TutorialsScreen.kt").read_text()
        for expression in ['tutorialText("refresh")', 'tutorialText("mode.', 'tutorialText("count"']:
            self.assertNotIn(expression, text)
        worker = (UI / "TutorialWorkspace.kt").read_text()
        self.assertIn("TutorialRefreshQueue", worker)
        self.assertIn("PublicContentSignals.revision", worker)
        self.assertIn("refreshObserved()", worker)
        self.assertIn("withTimeoutOrNull(30_000L)", worker)
        queue = (UI / "TutorialRefreshQueue.kt").read_text()
        self.assertIn("Channel.CONFLATED", queue); self.assertIn("forceRequested.compareAndSet(true,false)", queue)

    def test_server_announces_validated_help_and_owned_photo_invalidations(self):
        help_route = (ROOT / "server/src/main/kotlin/kz/aita/server/help/HelpRoutes.kt").read_text()
        self.assertIn('RealtimeServerBus.publish(entity="help/tutorials")', help_route)
        self.assertLess(help_route.index("val current=store.catalogue()"), help_route.index("previous!=current"))
        photo_route = (ROOT / "server/src/main/kotlin/kz/aita/server/profile/ProfilePhotoRoutes.kt").read_text()
        self.assertIn('RealtimeServerBus.publish(entity="users/profile-photo",userId=owner.toString())', photo_route)
        self.assertIn("if(!result.conflict)onChanged(owner)", photo_route)
        editor = (UI / "UserProfilePhoto.kt").read_text()
        self.assertIn("shouldRefreshProfilePhoto(photoRevision,observedPhotoRevision,state.preview!=null,state.busy)", editor)

    def test_tabs_have_one_horizontal_rail_and_compact_sort_spacing(self):
        rail = (UI / "AitaTabChips.kt").read_text()
        self.assertIn("LazyRow(", rail); self.assertNotIn("FlowRow(", rail)
        self.assertIn("if(compact)0.dp else 2.dp", rail)
        self.assertIn("fullyVisible", rail)
        for filename in ['CommonMainComposeCartStockA.kt','CommonMainComposeMenuA.kt','CommonMainComposeMenuB.kt']:
            self.assertGreaterEqual((UI / filename).read_text().count("compact = true, textSize = stateValues.smallTextSize"), 2)

    def test_open_conversations_are_separate_from_inbox_and_inline_actions(self):
        text = (UI / "SupportMessengerScreen.kt").read_text()
        self.assertIn('add(TabContent("chat"', text)
        self.assertIn('TabContent(conversation.key,title,AitaTabIcon.Chat,actions=', text)
        self.assertIn('AitaTabAction("resolve"', text); self.assertIn('AitaTabAction("close"', text)
        self.assertIn('SupportDelivery.saveDraft(account, tab.agent, tab.ticketId', text)
        self.assertIn('supportDraftNeedsSaving(memory.draftLoaded,memory.edited)', text)
        pane = text.split('private fun AppConfiguration.SupportConversationPane', 1)[1].split('private fun AppConfiguration.SupportConversationBubble')[0]
        self.assertNotIn('onBack:', pane)
        self.assertNotIn('actionButton(text=authUiText("Chats"', pane)
        self.assertNotIn('actionButton(text=authUiText("Resolve"', pane)
        self.assertIn('val ownerGeneration=remember(account,agent,ticketId)', pane)
        self.assertIn('newestSupportTabTicket(ticket,data.ticket,ticketId)', pane)

    def test_send_is_bound_to_the_opened_session_even_after_waiting(self):
        client = (SHARED / "CompanySupportClient.kt").read_text()
        send = client.split("suspend fun send(command:", 1)[1]
        self.assertIn("expectedSessionGeneration: Long = currentAuthenticatedSessionGeneration()", send)
        self.assertIn("val generation = expectedSessionGeneration", send)
        pane = (UI / "SupportMessengerScreen.kt").read_text().split("private fun AppConfiguration.SupportConversationPane", 1)[1]
        self.assertIn("val sendGeneration=ownerGeneration", pane)
        self.assertIn("SupportDelivery.send(command,sendGeneration)", pane)
        self.assertIn("if(sending || closing || acting || !owned()) return@actionButton", pane)

    def test_handbook_instructions_match_new_tabs_and_themes(self):
        book = ROOT / "server/src/main/resources/help/tutorials.json"
        mirror = ROOT / "composeApp/src/commonMain/composeResources/files/assets/help/tutorials.json"
        self.assertEqual(book.read_bytes(), mirror.read_bytes())
        data = json.loads(book.read_text()); by_id = {t['id']: t for t in data['tutorials']}
        self.assertIn('it does not resolve', by_id['settings.support']['steps'][3]['text']['en'])
        self.assertIn('18 named shades', by_id['settings.appearance']['steps'][1]['text']['en'])
        self.assertIn('Dark shades use white text; light shades use dark text', by_id['settings.appearance']['steps'][1]['text']['en'])


if __name__ == "__main__": unittest.main()
