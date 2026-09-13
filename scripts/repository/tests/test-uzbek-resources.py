#!/usr/bin/env python3
"""Uzbek Latin translation data contracts; standard library only, no network.

Run: python3 scripts/repository/tests/test-uzbek-resources.py
This suite checks resource coverage, not app-wide locale selection or inline UI.
"""
from collections import Counter
import json
from pathlib import Path
import re
import unicodedata
import unittest

ROOT = Path(__file__).resolve().parents[3]
KOTLIN = ROOT / "composeApp/src/commonMain/kotlin/kz/aita"
CATALOGUES = (
    ROOT / "server/assets/values/strings.json",
    ROOT / "composeApp/src/commonMain/composeResources/files/assets/values/strings.json",
)
FALLBACK_FILES = (
    "CommonMainComposeResourceFallbacksA.kt",
    "CommonMainComposeResourceFallbacksB.kt",
    "SupportFaqUpdates.kt",
)
ENTRY = re.compile(r'put\((\d+)L, mapOf\((.+)\)\)')
PAIR = re.compile(r'"([^"\\]+)" to "((?:[^"\\]|\\.)*)"')
PLACEHOLDER = re.compile(r'\{[\w.]+\}|\$\{[^}]+\}|%(?:\d+\$)?[-+0 #]*\d*(?:\.\d+)?[sdif]')
DIGITS = re.compile(r'\d+(?:[.,]\d+)*')
TECHNICAL_TOKENS = (
    "AITA", "WhatsApp", "Bluetooth", "WebSocket", "Android", "PDF", "ESC/POS",
    "TSPL", "ZPL", "CPCL", "EAN-13", "A4", "KZT", "B2B", "MOQ", "SKU", "CRM",
    "2FA", "xxxxxx-xxxxxx", "Download/AITA/Receipts",
)
# Server and Import are also Uzbek words; do not force spurious translations.
INVARIANT_NUMBERED_LABELS = {0, 161, 163, 242, 551, 606, 1295, 1296, 1297}
INVARIANT_CONFIG_LABELS = {"TOO", "BIN", "QR", "Kaspi RED", "Rakhmet", "Dushanbe", "TIN", "kg.", "Tenge"}


def localized_groups(node, path="$", result=None):
    if result is None:
        result = []
    if isinstance(node, list):
        if node and all(isinstance(v, dict) and "language" in v and "value" in v for v in node):
            result.append((path, node))
        else:
            for i, item in enumerate(node):
                localized_groups(item, f"{path}[{i}]", result)
    elif isinstance(node, dict):
        for key, value in node.items():
            localized_groups(value, f"{path}.{key}", result)
    return result


def read_fallbacks():
    rows = []
    for name in FALLBACK_FILES:
        for match in ENTRY.finditer((KOTLIN / name).read_text(encoding="utf-8")):
            pairs = PAIR.findall(match.group(2))
            languages = [language for language, _ in pairs]
            if len(languages) != len(set(languages)):
                raise AssertionError(f"Duplicate language: {name}:{match.group(1)}")
            values = {language: json.loads('"' + value.replace(r'\$', '$') + '"')
                      for language, value in pairs}
            rows.append((int(match.group(1)), values))
    return rows


class UzbekResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.payload = json.loads(CATALOGUES[0].read_text(encoding="utf-8"))["payload"]
        cls.catalogue = {row["id"]: {v["language"]: v["value"] for v in row["values"]} for row in cls.payload}
        cls.fallback_rows = read_fallbacks()
        cls.fallbacks = dict(cls.fallback_rows)
        cls.combined = cls.fallbacks | cls.catalogue
        cls.responses = json.loads((ROOT / "server/config/app/responses.json").read_text(encoding="utf-8"))
        cls.config = json.loads((ROOT / "server/config/app/global.json").read_text(encoding="utf-8"))
        cls.config_groups = localized_groups(cls.config)
        cls.response_groups = localized_groups(cls.responses)

    def test_catalogues_are_byte_identical(self):
        self.assertEqual(CATALOGUES[0].read_bytes(), CATALOGUES[1].read_bytes())

    def test_every_catalogue_id_has_one_uzbek_translation_and_keeps_existing_languages(self):
        self.assertEqual(len(self.payload), len(self.catalogue))
        self.assertGreaterEqual(len(self.catalogue), 1920)
        for row in self.payload:
            with self.subTest(id=row["id"]):
                self.assertIsInstance(row["id"], int)
                languages = [v["language"] for v in row["values"]]
                self.assertEqual(len(languages), len(set(languages)))
                for language in ("main", "en", "ru", "kk", "tg", "ky", "uz"):
                    self.assertTrue(self.catalogue[row["id"]][language].strip())

    def test_every_fallback_row_including_late_overrides_has_uzbek(self):
        self.assertGreaterEqual(len(self.fallbacks), 2451)
        for identifier, values in self.fallback_rows:
            with self.subTest(id=identifier):
                self.assertTrue(values["uz"].strip())
                for language in ("main", "en", "ru", "kk", "tg", "ky"):
                    self.assertTrue(values[language].strip())

    def test_catalogue_and_offline_uzbek_values_match(self):
        for identifier, values in self.fallback_rows:
            if identifier in self.catalogue:
                with self.subTest(id=identifier):
                    self.assertEqual(self.catalogue[identifier]["uz"], values["uz"])

    def test_fallback_only_recovery_strings_are_not_omitted(self):
        self.assertGreaterEqual(len(self.fallbacks.keys() - self.catalogue.keys()), 571)
        for identifier in (1728, 1800, 1919, 2109, 2299):
            self.assertNotIn(identifier, self.catalogue)
            self.assertNotEqual(self.fallbacks[identifier]["en"], self.fallbacks[identifier]["uz"])

    def test_support_map_replacements_retain_uzbek(self):
        for identifier in (875, 885, 893, 905):
            matches = [values for key, values in self.fallback_rows if key == identifier]
            self.assertGreaterEqual(len(matches), 2)
            for values in matches:
                self.assertEqual(self.catalogue[identifier]["uz"], values["uz"])

    def test_english_is_not_used_as_an_uzbek_placeholder(self):
        for identifier, values in self.combined.items():
            if identifier not in INVARIANT_NUMBERED_LABELS:
                with self.subTest(id=identifier):
                    self.assertNotEqual(values["en"], values["uz"])
        for path, group in self.response_groups + self.config_groups:
            values = {v["language"]: v["value"] for v in group}
            english = values.get("en", values.get("main"))
            if english not in INVARIANT_CONFIG_LABELS:
                with self.subTest(path=path):
                    self.assertNotEqual(english, values["uz"])

    def test_format_placeholders_numbers_newlines_and_percent_signs_survive(self):
        pairs = [(str(key), values["en"], values["uz"]) for key, values in self.combined.items()]
        for path, group in self.response_groups + self.config_groups:
            values = {v["language"]: v["value"] for v in group}
            pairs.append((path, values.get("en", values.get("main")), values["uz"]))
        for path, english, uzbek in pairs:
            with self.subTest(path=path):
                for pattern in (PLACEHOLDER, DIGITS):
                    self.assertEqual(Counter(pattern.findall(english)), Counter(pattern.findall(uzbek)))
                self.assertEqual(english.count("\n"), uzbek.count("\n"))
                self.assertEqual(english.count("%"), uzbek.count("%"))

    def test_machine_paths_protocols_and_brands_survive(self):
        for identifier, values in self.combined.items():
            for token in TECHNICAL_TOKENS:
                if token in values["en"]:
                    with self.subTest(id=identifier, token=token):
                        self.assertIn(token, values["uz"])

    def test_all_uzbek_values_are_normalized_latin_text(self):
        values = [(str(key), data["uz"]) for key, data in self.combined.items()]
        for path, group in self.response_groups + self.config_groups:
            values.append((path, next(v["value"] for v in group if v["language"] == "uz")))
        for path, value in values:
            with self.subTest(path=path):
                self.assertTrue(value.strip())
                self.assertEqual(unicodedata.normalize("NFC", value), value)
                self.assertNotIn("\ufffd", value)
                self.assertFalse(any(unicodedata.category(c) == "Cc" and c not in "\n\t" for c in value))
                self.assertLess(len(value.encode("utf-8")), 65535)
                # The tax heading deliberately retains recognizable existing acronyms.
                if path != "155":
                    self.assertFalse(any("CYRILLIC" in unicodedata.name(c, "") for c in value))

    def test_response_ids_remain_unique_and_every_message_is_translated(self):
        self.assertGreaterEqual(len(self.responses), 83)
        ids = [row["id"] for row in self.responses]
        self.assertEqual(len(ids), len(set(ids)))
        for path, group in self.response_groups:
            with self.subTest(path=path):
                languages = [v["language"] for v in group]
                self.assertEqual(1, languages.count("uz"))
                self.assertEqual(len(languages), len(set(languages)))
        by_id = {r["id"]: {v["language"]: v["value"] for v in r["message"]} for r in self.responses}
        # IDs are not contiguous. Never associate a translation by treating an ID as an array index.
        self.assertIn("44", by_id)
        self.assertEqual("Faol seanslar yuklandi", by_id["44"]["uz"])
        self.assertIn("107", by_id)

    def test_all_configuration_label_groups_have_uzbek_even_without_english(self):
        self.assertGreaterEqual(len(self.config_groups), 33)
        for path, group in self.config_groups:
            with self.subTest(path=path):
                languages = [v["language"] for v in group]
                self.assertEqual(1, languages.count("uz"))
                self.assertEqual(len(languages), len(set(languages)))
                self.assertTrue(next(v["value"] for v in group if v["language"] == "uz").strip())
        description = self.config["payload"]["paymentProviders"][0]["description"]
        self.assertIn("API", next(v["value"] for v in description if v["language"] == "uz"))

    def test_work_title_and_core_actions_keep_their_public_ids(self):
        for identifier, label in {1: "Kirish", 6: "Parol", 8: "Bekor qilish", 2658: "Ish"}.items():
            self.assertEqual(label, self.catalogue[identifier]["uz"])
            self.assertEqual(label, self.fallbacks[identifier]["uz"])
        expected = {"main": "Work", "en": "Work", "ru": "Работа", "kk": "Жұмыс", "tg": "Кор", "ky": "Иш"}
        for language, label in expected.items():
            self.assertEqual(label, self.catalogue[2658][language])

    def test_security_help_keeps_exact_code_and_path_examples(self):
        for identifier, token in {2623: "xxxxxx-xxxxxx", 2631: "Download/AITA/Receipts", 2621: "2FA", 2637: "PDF", 2657: "QR"}.items():
            self.assertIn(token, self.fallbacks[identifier]["uz"])
        self.assertIn("barcha seanslar", self.fallbacks[2623]["uz"])
        self.assertIn("eski tiklash kodlarini bekor", self.fallbacks[2623]["uz"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
