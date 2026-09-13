#!/usr/bin/env python3
"""Source/resource integration checks; these are not rendered Compose UI or Gradle tests.

Run with: python3 scripts/repository/tests/test-ui-layout-contracts.py
"""
from pathlib import Path
import json
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
KOTLIN = ROOT / "composeApp/src/commonMain/kotlin/kz/aita"


def source(name: str) -> str:
    return (KOTLIN / name).read_text(encoding="utf-8")


def function(name: str, symbol: str) -> str:
    text = source(name)
    start = text.index("fun " + symbol + "(")
    end = text.find("\n@Composable", start + 1)
    return text[start:] if end == -1 else text[start:end]


class UiLayoutContractsTest(unittest.TestCase):
    def test_screen_body_cap_is_below_the_full_width_bar(self):
        shell = source("AitaScreenColumn.kt")
        self.assertIn("Column(modifier = modifier.fillMaxWidth())", shell)
        self.assertLess(shell.index("appBar()"), shell.index(".aitaWidthCap(maximumContentWidth)"))
        self.assertNotIn("aitaWidthCap", shell[:shell.index("appBar()")])
        self.assertIn(".weight(1f)", shell)
        self.assertIn("content = content", shell)

    def test_navigation_hosts_do_not_cap_screen_bars(self):
        menu = source("AitaLoadingMotion.kt")
        self.assertIn("LocalAitaScreenContentMaximumWidth provides paneWidth", menu)
        self.assertNotIn(".aitaWidthCap(paneWidth)", menu)
        self.assertNotIn(".fillMaxSize().aitaWidthCap(1600.dp)", source("CommonMainComposeMenuB.kt"))

    def test_buyer_body_caps_are_preserved(self):
        for name, maximum in (("BuyerMarketplaceScreen.kt", 1440), ("BuyerShoppingListScreen.kt", 1120)):
            with self.subTest(name=name):
                text = source(name)
                self.assertIn(f"maximumContentWidth = {maximum}.dp", text)
                self.assertNotIn(f".fillMaxSize().aitaWidthCap({maximum}.dp)", text)

    def test_app_bar_modifier_applies_to_the_whole_bar(self):
        text = function("CommonMainComposeNavigation.kt", "AppConfiguration.ScreenAppBarWidget")
        self.assertRegex(text, r"Column\(\s*modifier = modifier\s*\.fillMaxWidth\(\)")
        self.assertNotIn("aitaWidthCap", text)

    def test_full_width_buttons_have_no_independent_cap(self):
        button = function("CommonMainComposeWidgetsConfig.kt", "AppConfiguration.actionButton")
        sizing = button[button.index("Row(\n        modifier = modifier"):]
        sizing = sizing[:sizing.index("if (fillMaxHeight)")]
        self.assertLess(sizing.index("if (!textPresent || !fillMaxWidthIfTextPresent)"), sizing.index("aitaWidthCap"))
        compact, normal = sizing.split("else", 1)
        self.assertIn("aitaWidthCap().wrapContentWidth()", compact)
        self.assertIn("fillMaxWidth()", normal)
        self.assertNotIn("aitaWidthCap", normal)
        self.assertIn("horizontalArrangement = Arrangement.Center", button)

    def test_country_flag_padding_is_an_opt_in_using_field_spacing(self):
        domain = function("CommonMainComposeWidgetsConfig.kt", "AppConfiguration.domainSelectionTextField")
        phone = function("CommonMainComposeWidgetsConfig.kt", "AppConfiguration.countrySelectionPhoneNumberTextField")
        self.assertIn("secondaryDomainLeadingPadding: Dp = 0.dp", domain)
        self.assertIn("Modifier.padding(start = secondaryDomainLeadingPadding)", domain)
        self.assertIn("secondaryDomainLeadingPadding = stateValues.textFieldIconPadding", phone)
        # No unrelated domain selector opts into phone-only spacing.
        self.assertEqual(1, source("CommonMainComposeWidgetsConfig.kt").count(
            "secondaryDomainLeadingPadding = stateValues.textFieldIconPadding"))

    def test_app_mode_is_a_stable_normal_menu_row(self):
        menu = source("CommonMainComposeMenuA.kt")
        self.assertNotIn("AppModeQuickSwitchMenuTile", menu)
        self.assertNotIn("app-mode-quick-switch", menu)
        listing = function("CommonMainComposeMenuA.kt", "AppConfiguration.MenuListScreen")
        self.assertIn("items = filteredMenuDestinations()", listing)
        self.assertIn("key = { model -> model.route }", listing)
        self.assertIn("contentDescription = model.name", listing)

    def test_menu_order_in_every_declared_list(self):
        lists = source("CommonMainComposeMenuA.kt") + source("CommonMainComposeNavigation.kt")
        expected = r"NavigationScreenModel\.Menu\.UserAccount,\s*NavigationScreenModel\.Menu\.AppMode,\s*NavigationScreenModel\.Menu\.Work,"
        self.assertEqual(3, len(re.findall(expected, lists)))

    def test_work_is_wired_in_all_three_menu_dispatchers(self):
        menu = function("CommonMainComposeMenuA.kt", "AppConfiguration.MenuScreen")
        self.assertEqual(3, len(re.findall(r"is NavigationScreenModel\.Menu\.Work ->\s*\{\s*MenuWorkScreen\(\)", menu)))

    def test_worker_identity_was_moved_not_duplicated(self):
        account = function("CommonMainComposeMenuA.kt", "AppConfiguration.MenuUserAccountScreen")
        work = source("MenuWorkScreen.kt")
        for text in ("Your public worker ID", "Use this ID when a store owner invites you as a worker", "visibleWorkerInviteId()"):
            self.assertNotIn(text, account)
            self.assertIn(text, work)
        self.assertIn("ClipboardCopyButton(textToCopy = account.visibleWorkerInviteId())", work)
        self.assertIn("if (account == null)", work)
        self.assertIn("rememberMenuScreenLazyListState(NavigationScreenModel.Menu.Work)", work)

    def test_personal_work_does_not_change_worker_authorization(self):
        menu = source("CommonMainComposeMenuA.kt")
        start = menu.index("internal fun menuDestinationRequiresStoreSubscription")
        end = menu.index("internal fun AppConfiguration.canOpenMenuDestination", start)
        self.assertNotRegex(menu[start:end], r"NavigationScreenModel\.Menu\.Work(?:\s|,|->)")
        self.assertIn("NavigationScreenModel.Menu.Workers -> activeOwnerFallback || currentUserCanViewWorkers(activeStoreId)", menu)
        self.assertNotIn("getStoreWorkers(", source("MenuWorkScreen.kt"))

    def test_work_route_is_registered_for_persistence(self):
        nav = source("CommonMainComposeNavigation.kt")
        self.assertEqual(1, nav.count('Menu("MenuWorkNavigationScreenModelRoute")'))
        registry = nav.split("internal fun persistentAppNavigationScreens()", 1)[1].split("internal fun persistentAppRouteToScreen", 1)[0]
        self.assertEqual(1, len(re.findall(r"NavigationScreenModel\.Menu\.Work,", registry)))

    def test_new_label_is_mirrored_and_available_offline(self):
        paths = [ROOT / "server/assets/values/strings.json",
                 ROOT / "composeApp/src/commonMain/composeResources/files/assets/values/strings.json"]
        entries = []
        for path in paths:
            payload = json.loads(path.read_text(encoding="utf-8"))["payload"]
            matching = [entry for entry in payload if entry["id"] == 2658]
            self.assertEqual(1, len(matching))
            entries.append(matching[0])
        self.assertEqual(entries[0], entries[1])
        values = {value["language"]: value["value"] for value in entries[0]["values"]}
        expected = {"main": "Work", "en": "Work", "ru": "Работа", "kk": "Жұмыс", "tg": "Кор", "ky": "Иш"}
        for language, label in expected.items():
            self.assertEqual(label, values[language])
        fallback = source("CommonMainComposeResourceFallbacksA.kt")
        entry = re.search(r'put\(2658L, mapOf\((.+)\)\)', fallback)
        self.assertIsNotNone(entry)
        pairs = re.findall(r'"([^"\\]+)" to "((?:[^"\\]|\\.)*)"', entry.group(1))
        self.assertEqual(len(pairs), len(dict(pairs)))
        offline = {language: json.loads('"' + text + '"') for language, text in pairs}
        self.assertEqual(values, offline)

    def test_work_reuses_the_existing_android_compatible_worker_icon(self):
        work = source("MenuWorkScreen.kt")
        self.assertIn("iconRes = stateValues.drawableResIconWorkers.value", work)
        for variant in (0, 1):
            svg = ROOT / f"composeApp/src/commonMain/composeResources/drawable/22_{variant}.svg"
            vector = ROOT / f"composeApp/src/androidMain/res/drawable/ic_aita_22_{variant}.xml"
            self.assertTrue(svg.is_file())
            self.assertEqual("vector", ET.parse(vector).getroot().tag)


if __name__ == "__main__":
    unittest.main(verbosity=2)
