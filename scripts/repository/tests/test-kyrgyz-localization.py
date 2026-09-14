#!/usr/bin/env python3
"""Offline Kyrgyz resource/placeholder contracts. No network, Gradle or third-party modules.

Run: python3 scripts/repository/tests/test-kyrgyz-localization.py
These checks do not replace the Kotlin tests or native-speaker proofreading.
"""
from collections import Counter
import csv
import importlib.util
import json
from pathlib import Path
import re
import struct
import unicodedata
import unittest

ROOT = Path(__file__).resolve().parents[3]
SHARED = ROOT / "shared/src/commonMain/kotlin/kz/aita"
COMPOSE = ROOT / "composeApp/src/commonMain/kotlin/kz/aita"
SERVER = ROOT / "server/src/main/kotlin/kz/aita/server"
TEST_SUPPORT = Path(__file__).with_name("test-tajik-localization.py")
spec = importlib.util.spec_from_file_location("aita_resource_contracts", TEST_SUPPORT)
contracts = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contracts)
Q = r'"(?:[^"\\]|\\.)*"'
AUTH = re.compile(r'\bauthUiText\(\s*(' + Q + r')\s*,\s*(' + Q + r')\s*,\s*(' + Q + r')\s*,\s*(' + Q + r')\s*\)')
EVENT = re.compile(r'\bEventMessageTemplate\(\s*(' + Q + r')\s*,\s*(' + Q + r')\s*,\s*(' + Q + r')\s*,\s*(' + Q + r')\s*,\s*(?:' + Q + r'\s*,\s*)?ky\s*=\s*(' + Q + r')(?:\s*,\s*(?:tg|uz)\s*=\s*' + Q + r')*\s*\)')
PARAMETERS = re.compile(r'\$\{[^}]*\}|\$[A-Za-z_]\w*|\{(?:[A-Za-z_]\w*|\d+)\}'
                        r'|%(?:\d+\$)?[-+0#]*(?:\d+|\*)?(?:\.(?:\d+|\*))?[sSdDfFeEgGcCbBhHxXoOn]')
LIVE_EVENTS = ("CoreEventMessages.kt", "ApplicationEventMessages.kt", "AuthenticationEventMessages.kt",
               "SubscriptionEventMessages.kt", "CompanySupportMessages.kt", "MarketplaceEventMessages.kt")
UNCHANGED = contracts.UNCHANGED_LABELS | {"Email", "PIN", "ID", "SKU", "USB", "LAN", "Wi-Fi", "kg", "g", "ml", "l", "KZT", "TJS"}


def kotlin_values(match):
    return tuple(json.loads(value) for value in match.groups())


def compiled_strings():
    text = (SHARED / "KyrgyzStringResources.kt").read_text(encoding="utf-8")
    return [(int(m[1]), json.loads(m[2])) for m in re.finditer(r'\bput\((\d+)L,\s*(' + Q + r')\)', text)]


def auth_values():
    for path in COMPOSE.glob("*.kt"):
        for match in AUTH.finditer(path.read_text(encoding="utf-8")):
            yield path.name, kotlin_values(match)


def event_values():
    for name in LIVE_EVENTS:
        for match in EVENT.finditer((SHARED / "messages" / name).read_text(encoding="utf-8")):
            yield name, kotlin_values(match)


class KyrgyzLocalizationContractsTest(unittest.TestCase):
    def test_all_json_lists_have_one_nonblank_kyrgyz_value(self):
        for filename in contracts.RESOURCE_FILES:
            for location, items in contracts.localized_lists(contracts.read_json(filename)):
                with self.subTest(file=filename.name, location=location):
                    codes = [item["language"] for item in items]
                    self.assertEqual(len(codes), len(set(codes)))
                    self.assertEqual(1, codes.count("ky"))
                    self.assertNotIn("kg", codes)
                    self.assertTrue(contracts.language_values(items)["ky"].strip())

    def test_catalogue_counts_and_client_server_byte_parity(self):
        self.assertEqual(contracts.SERVER_STRINGS.read_bytes(), contracts.CLIENT_STRINGS.read_bytes())
        entries = contracts.read_json(contracts.SERVER_STRINGS)["payload"]
        self.assertGreaterEqual(len(entries), 1920)
        self.assertEqual(len(entries), len({item["id"] for item in entries}))
        self.assertGreaterEqual(len(contracts.read_json(contracts.RESPONSES)), 83)

    def test_all_previous_languages_remain_in_numbered_resources(self):
        for group in contracts.read_json(contracts.SERVER_STRINGS)["payload"]:
            self.assertTrue({"main", "en", "ru", "kk", "tg", "ky"}.issubset(contracts.language_values(group["values"])))

    def test_compiled_table_is_unique_and_matches_every_json_value(self):
        rows = compiled_strings()
        values = dict(rows)
        self.assertGreaterEqual(len(rows), 2491)
        self.assertEqual(len(rows), len(values))
        for group in contracts.read_json(contracts.SERVER_STRINGS)["payload"]:
            self.assertEqual(contracts.language_values(group["values"])["ky"], values[group["id"]], group["id"])

    def test_every_compose_fallback_id_has_a_shared_kyrgyz_translation(self):
        translations = dict(compiled_strings())
        count = 0
        for path in COMPOSE.glob("CommonMainComposeResourceFallbacks*.kt"):
            for line in path.read_text(encoding="utf-8").splitlines():
                match = re.search(r'\bput\((\d+)L, mapOf\(', line)
                if not match:
                    continue
                key = int(match[1]); count += 1
                self.assertTrue(translations[key].strip())
                literal = re.search(r'"ky"\s+to\s+(' + Q + r')', line)
                self.assertIsNotNone(literal, (path.name, key))
                self.assertEqual(translations[key], json.loads(literal[1]), (path.name, key))
        self.assertGreaterEqual(count, 2399)

    def test_every_literal_inline_label_has_the_required_fourth_language(self):
        calls = list(auth_values())
        count = sum(len(re.findall(r'\bauthUiText\(\s*"', p.read_text(encoding="utf-8"))) for p in COMPOSE.glob("*.kt"))
        # Two comparison labels moved from inline text to six-language event templates.
        comparison = (COMPOSE / "MarketComparisonDialog.kt").read_text(encoding="utf-8")
        migrated = sum(f'eventMessage("{key}"' in comparison for key in (
            "market.comparison_window_empty_more", "market.comparison_window_scope"))
        self.assertEqual(migrated, 2)
        self.assertGreaterEqual(count + migrated, 604)
        self.assertEqual(count, len(calls))
        for filename, (en, ru, kk, ky) in calls:
            self.assertTrue(ky.strip(), (filename, en))
        helper = (COMPOSE / "AdvancedAuthenticationLoginScreen.kt").read_text(encoding="utf-8")
        self.assertIn("kk: String, ky: String", helper)
        self.assertIn('"ky" -> ky', helper)
        self.assertIn('effectiveAppLanguage(stateValues.appLanguage)', helper)

    def test_event_parser_keeps_checking_kyrgyz_when_more_languages_are_added(self):
        sample = 'EventMessageTemplate("key", "en", "ru", "kk", ky = "Кыргызча", tg = "Тоҷикӣ", uz = "O‘zbekcha")'
        self.assertIsNotNone(EVENT.fullmatch(sample))
        self.assertEqual("Кыргызча", kotlin_values(EVENT.fullmatch(sample))[-1])
        self.assertIsNone(EVENT.fullmatch(sample.replace('ky = "Кыргызча", ', '')))

    def test_all_current_events_have_explicit_kyrgyz_templates(self):
        calls = list(event_values())
        total = sum((SHARED / "messages" / name).read_text(encoding="utf-8").count('EventMessageTemplate("') for name in LIVE_EVENTS)
        self.assertGreaterEqual(total, 518)
        self.assertEqual(total, len(calls))
        keys = [values[0] for _, values in calls]
        self.assertEqual(len(keys), len(set(keys)))
        for filename, (key, en, ru, kk, ky) in calls:
            self.assertTrue(ky.strip(), (filename, key))

    def test_json_numbers_placeholders_and_technical_literals_are_preserved(self):
        for location, values in contracts.all_texts():
            en = values.get("en", values.get("main", "")); ky = values["ky"]
            with self.subTest(location=location):
                self.assertEqual(Counter(contracts.NUMBER.findall(en)), Counter(contracts.NUMBER.findall(ky)))
                self.assertEqual(Counter(PARAMETERS.findall(en)), Counter(PARAMETERS.findall(ky)))
                self.assertEqual(en.count("%"), ky.count("%"))
                self.assertEqual(en.count("\n"), ky.count("\n"))
                for literal in contracts.PROTECTED_LITERALS:
                    self.assertEqual(en.count(literal), ky.count(literal), literal)

    def test_inline_and_event_interpolations_are_preserved_exactly(self):
        pairs = [(f"{file}:{en}", en, ky) for file, (en, ru, kk, ky) in auth_values()]
        pairs += [(key, en, ky) for file, (key, en, ru, kk, ky) in event_values()]
        for location, en, ky in pairs:
            with self.subTest(location=location):
                self.assertEqual(Counter(PARAMETERS.findall(en)), Counter(PARAMETERS.findall(ky)))
                self.assertEqual(en.count("\n"), ky.count("\n"))
                self.assertEqual(en.count("\t"), ky.count("\t"))

    def test_kyrgyz_text_is_clean_normalized_cyrillic(self):
        texts = [value for _, value in compiled_strings()]
        texts += [values[-1] for _, values in auth_values()]
        texts += [values[-1] for _, values in event_values()]
        texts += [values["ky"] for _, values in contracts.all_texts()]
        for text in texts:
            self.assertEqual(text, unicodedata.normalize("NFC", text))
            self.assertNotIn("\ufffd", text)
            self.assertNotRegex(text, "[ӘәҒғҚқҰұҺһІі]")
            self.assertFalse(any(unicodedata.category(c) in {"Cc", "Cf"} and c not in "\n\r\t" for c in text), text)

    def test_json_has_no_english_prose_standin(self):
        for location, values in contracts.all_texts():
            original = values.get("en", values.get("main", ""))
            if original == values["ky"] and re.search(r"[A-Za-z]", original):
                self.assertIn(original, UNCHANGED, location)

    def test_language_descriptor_and_both_flag_assets_resolve(self):
        languages = contracts.read_json(contracts.GLOBAL)["payload"]["languages"]
        kyrgyz = next(item for item in languages if item["language"] == "ky")
        self.assertEqual("Кыргызча", contracts.language_values(kyrgyz["name"])["ky"])
        self.assertEqual("png/flag_kg.png", kyrgyz["flagDrawablePath"])
        server = ROOT / "server/assets/drawable/png/flag_kg.png"
        client = ROOT / "composeApp/src/commonMain/composeResources/drawable/flag_kg.png"
        self.assertEqual(server.read_bytes(), client.read_bytes())
        self.assertEqual(b"\x89PNG\r\n\x1a\n", server.read_bytes()[:8])
        self.assertEqual((512, 512), struct.unpack(">II", server.read_bytes()[16:24]))
        mapping = (COMPOSE / "CommonMainComposeWidgetsConfig.kt").read_text(encoding="utf-8")
        self.assertIn('"ky" -> Res.drawable.flag_kg', mapping)
        self.assertIn('"tg", "tj" -> Res.drawable.flag_tj', mapping)

    def test_runtime_selection_accepts_ky_without_changing_default(self):
        common = (SHARED / "CommonMain.kt").read_text(encoding="utf-8")
        self.assertIn('const val DEFAULT_APP_LANGUAGE = "ru"', common)
        self.assertIn('listOf("en", "ru", "kk", "tg", "ky", "uz")', common)
        self.assertIn('value in SUPPORTED_APP_LANGUAGES', common)
        self.assertIn('withBundledAppLanguages()', (COMPOSE / "CommonMainComposeAuthTransaction.kt").read_text(encoding="utf-8"))
        catalogue = (SHARED / "AppearanceCatalog.kt").read_text(encoding="utf-8")
        self.assertIn('SUPPORTED_APP_LANGUAGES.associateWith', catalogue)
        self.assertIn('resolveLocalizedResource(id, language', catalogue)
        self.assertIn('"ky" -> bundledKyrgyzStringResource(id)', (SHARED / "TajikUzbekStringResources.kt").read_text(encoding="utf-8"))

    def test_category_rows_and_original_seeds_have_kyrgyz_names(self):
        with (ROOT / "server/src/main/resources/catalogue/goods-categories.tsv").open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle, delimiter="\t"))
        self.assertGreaterEqual(len(rows), 644)
        self.assertEqual(len(rows), len({row["slug"] for row in rows}))
        for row in rows:
            self.assertTrue(all(row[language].strip() for language in ("en", "ru", "kk", "ky")))
            self.assertIn(row["quantity_unit_id"], {"0", "1"})
        source = (SERVER / "Server.kt").read_text(encoding="utf-8")
        seeds = re.findall(r'categoryText\(\s*' + Q + r'\s*,\s*' + Q + r'\s*,\s*' + Q + r'\s*,\s*(' + Q + r')\s*\)', source)
        self.assertEqual(len(re.findall(r'categoryText\(\s*"', source)), len(seeds))
        self.assertGreaterEqual(len(seeds), 120)
        self.assertTrue(all(json.loads(value).strip() for value in seeds))
        self.assertIn('path("ky", definition.ky)', source)

    def test_current_security_email_has_kyrgyz_copy_and_html_language(self):
        source = (SERVER / "auth/AitaAuthEmailTemplate.kt").read_text(encoding="utf-8")
        self.assertGreaterEqual(source.count('ky -> "'), 14)
        self.assertIn('kz.aita.normalizeAuthEmailLocale(locale)', source)
        self.assertIn('val ky = emailLanguage == "ky"', source)
        self.assertIn('"AITA", emailLanguage)', source)
        self.assertIn('$ttlMinutes мүнөт жарактуу', source)
        self.assertIn('өзүңүз сурансаңыз гана', source)
        self.assertIn('бардык кирүү сеанстарын аяктатат', source)
        self.assertIn('эч кимге бербеңиз', source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
