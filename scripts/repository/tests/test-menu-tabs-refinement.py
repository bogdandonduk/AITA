#!/usr/bin/env python3
"""Repository resource/source contracts. Actual behavior is tested in commonTest and Gradle."""
from pathlib import Path
import json
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
KOTLIN = ROOT / "composeApp/src/commonMain/kotlin/kz/aita"
ANDROID = "{http://schemas.android.com/apk/res/android}"

class MenuTabsRefinementTest(unittest.TestCase):
    def test_numbered_artwork_matches_all_platforms(self):
        text=(KOTLIN/"AitaTabIcons.kt").read_text()
        families=[int(x) for x in re.findall(r"^    \w+\((\d+)\)",text,re.M)]
        self.assertEqual(61,len(families)); self.assertEqual(len(families),len(set(families)))
        keep=(ROOT/"composeApp/src/androidMain/res/raw/aita_tab_icons_keep.xml").read_text()
        for family in families:
            for variant in (0,1):
                stem=f"{family}_{variant}"
                svg=ROOT/f"server/assets/drawable/svg/{stem}.svg"
                compose=ROOT/f"composeApp/src/commonMain/composeResources/drawable/{stem}.svg"
                vector=ROOT/f"composeApp/src/androidMain/res/drawable/ic_aita_{stem}.xml"
                self.assertEqual(svg.read_bytes(),compose.read_bytes())
                paths=[p.attrib["d"] for p in ET.parse(svg).getroot().iter() if p.tag.endswith("path")]
                android=[p.attrib[ANDROID+"pathData"] for p in ET.parse(vector).getroot().iter("path")]
                self.assertEqual(paths,android,stem)
                self.assertIn("@drawable/ic_aita_"+stem,keep)
                self.assertIn("Res.drawable._"+stem,text)

    def test_both_drawable_registries_have_single_matching_entries(self):
        server=json.loads((ROOT/"server/assets/drawable/drawables.json").read_text())
        compose=json.loads((ROOT/"composeApp/src/commonMain/composeResources/files/assets/drawable/drawables.json").read_text())
        self.assertEqual(server,compose)
        ids=[e["id"] for e in server["payload"]]
        for f in range(149,210): self.assertEqual(1,ids.count(f))

    def test_live_tab_ids_are_not_guessed_from_translated_labels(self):
        resolver=(KOTLIN/"AitaTabIcons.kt").read_text()
        known=set(re.findall(r'"([^"\n]+)"',resolver.split("internal fun AppConfiguration.tabIconResource")[0]))
        for f in KOTLIN.glob("*.kt"):
            for id in re.findall(r'TabContent\(\s*"([^"\n]+)"',f.read_text()):
                self.assertIn(id,known,str(f)+":"+id)
        chips=(KOTLIN/"AitaTabChips.kt").read_text()
        self.assertIn("role = Role.Tab",chips)
        self.assertIn(".selectableGroup()",chips)
        self.assertIn("Alignment.Start",chips)
        self.assertNotIn(".weight(1f)",chips)

    def test_subscription_redirect_uses_real_roots(self):
        nav=(KOTLIN/"CommonMainComposeNavigation.kt").read_text()
        block=nav.split("suspend fun showSubscriptionRecovery()",1)[1].split("suspend fun goMain",1)[0]
        self.assertIn("Menu.clearLeft()",block);self.assertIn("Menu.clearRight()",block)
        self.assertIn("Menu.go(recovery",block)
        self.assertNotIn("clearRight(recovery)",block)

    def test_lifetime_presentation_omits_tabs_not_billing_safety(self):
        text=(KOTLIN/"StoreSubscriptionScreen.kt").read_text()
        self.assertIn('if (lifetimeOnly) "current" else sectionTabsWidget',text)
        self.assertIn("hasAccess && subscription?.accessKind == SUBSCRIPTION_ACCESS_LIFETIME",text)
        self.assertNotIn("store?.name",text)
        self.assertIn("if (lifetime) LifetimeSubscriptionCard()",text)
        self.assertIn("Navigation.Menu.isVeryFirstScreen",text)

    def test_debtor_phone_uses_standard_editor_and_one_draft_owner(self):
        text=(KOTLIN/"DebtorPhoneInput.kt").read_text()
        self.assertIn("countrySelectionPhoneNumberTextField(",text)
        self.assertIn("persistTextDraft = false",text)
        self.assertIn("valueIsNationalNumber = true",text)
        self.assertIn("onSelectedCountryCodeChange",text)
        payment=(KOTLIN/"CommonMainComposeAuthTransaction.kt").read_text()
        self.assertIn("value = newDebtorPhone",payment)
        self.assertIn("newDebtorPhone = it",payment)

    def test_empty_rows_use_residual_viewport_not_full_height_below_headers(self):
        for file in ("SupplierModeOrdersScreen.kt","SupplierModeCustomersScreen.kt","SupplierModeDispatchScreen.kt","SupplierModeContractsScreen.kt","SupplierModeCatalogScreen.kt","SupplierModeProfilesScreen.kt"):
            self.assertIn("remainingListSpace(emptySpaceListState",(KOTLIN/file).read_text(),file)
        workers=(KOTLIN/"CommonMainComposeMenuA.kt").read_text().split("fun AppConfiguration.MenuWorkersScreen()",1)[1].split("\n@Composable",1)[0]
        self.assertLess(workers.index("WorkerIdentityCard("),workers.index("LazyColumn("))
        self.assertIn("Modifier.fillParentMaxSize()",workers)

if __name__=="__main__": unittest.main(verbosity=2)
