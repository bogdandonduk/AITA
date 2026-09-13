#!/usr/bin/env python3
"""Numbered translation contracts; no network, Gradle or third-party Python packages.

Run: python3 scripts/repository/tests/test-tajik-kyrgyz-numbered-resources.py
These checks cover the numbered catalogue/fallback batch, not all inline app text.
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
# This is also the order used by buildBundledLocalizedStringFallbacks: support
# overrides replace four older FAQ entries, including every language in the map.
FALLBACK_FILES = (
    "CommonMainComposeResourceFallbacksA.kt",
    "CommonMainComposeResourceFallbacksB.kt",
    "SupportFaqUpdates.kt",
)
TRANSLATED_LANGUAGES = ("tg", "ky")
LEGACY_LANGUAGES = ("main", "en", "ru", "kk")
UNCHANGED_PRODUCT_LABELS = {0, 161, 163, 606, 1295, 1296, 1297}
ENTRY = re.compile(r'put\((\d+)L, mapOf\((.+)\)\)')
PAIR = re.compile(r'"([^"\\]+)" to "((?:[^"\\]|\\.)*)"')
PLACEHOLDER = re.compile(r'\{[\w.]+\}|\$\{[^}]+\}|%(?:\d+\$)?[-+0 #]*\d*(?:\.\d+)?[sdif]')
DIGITS = re.compile(r'\d+(?:[.,]\d+)*')
TECHNICAL_TOKENS = (
    "AITA", "WhatsApp", "Bluetooth", "WebSocket", "Android", "PDF", "ESC/POS",
    "TSPL", "ZPL", "CPCL", "EAN-13", "A4", "KZT", "B2B", "MOQ", "SKU", "CRM",
    "2FA", "xxxxxx-xxxxxx", "Download/AITA/Receipts",
)


def kotlin_literal(value: str) -> str:
    # Kotlin's escaped dollar is a literal, unlike an interpolation. The remaining
    # escapes in these plain string constants are the standard JSON-compatible ones.
    return json.loads('"' + value.replace(r'\$', '$') + '"')


def fallback_rows():
    rows = []
    for name in FALLBACK_FILES:
        text = (KOTLIN / name).read_text(encoding="utf-8")
        for entry in ENTRY.finditer(text):
            pairs = PAIR.findall(entry.group(2))
            languages = [language for language, _ in pairs]
            if len(languages) != len(set(languages)):
                raise AssertionError(f"Duplicate language: {name}:{entry.group(1)}")
            rows.append((int(entry.group(1)), {
                language: kotlin_literal(value) for language, value in pairs
            }))
    return rows


class TajikKyrgyzNumberedResourcesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.payload = json.loads(CATALOGUES[0].read_text(encoding="utf-8"))["payload"]
        cls.catalogue = {
            row["id"]: {v["language"]: v["value"] for v in row["values"]}
            for row in cls.payload
        }
        cls.fallback_rows = fallback_rows()
        cls.fallbacks = dict(cls.fallback_rows)

    def test_client_and_server_catalogues_are_byte_identical(self):
        self.assertEqual(CATALOGUES[0].read_bytes(), CATALOGUES[1].read_bytes())

    def test_catalogue_ids_and_languages_are_unique(self):
        self.assertEqual(len(self.payload), len(self.catalogue))
        for row in self.payload:
            with self.subTest(id=row["id"]):
                self.assertIsInstance(row["id"], int)
                languages = [v["language"] for v in row["values"]]
                self.assertEqual(len(languages), len(set(languages)))

    def test_every_catalogue_entry_keeps_existing_languages_and_adds_both_translations(self):
        for identifier, values in self.catalogue.items():
            for language in (*LEGACY_LANGUAGES, *TRANSLATED_LANGUAGES):
                with self.subTest(id=identifier, language=language):
                    self.assertIn(language, values)
                    self.assertTrue(values[language].strip())

    def test_every_offline_fallback_including_support_overrides_has_both_translations(self):
        self.assertTrue(self.fallback_rows)
        for identifier, values in self.fallback_rows:
            for language in TRANSLATED_LANGUAGES:
                with self.subTest(id=identifier, language=language):
                    self.assertIn(language, values)
                    self.assertTrue(values[language].strip())

    def test_overlapping_catalogue_and_fallback_translations_match(self):
        for identifier, values in self.fallback_rows:
            if identifier in self.catalogue:
                for language in TRANSLATED_LANGUAGES:
                    with self.subTest(id=identifier, language=language):
                        self.assertEqual(self.catalogue[identifier][language], values[language])

    def test_fallback_only_supplier_recovery_copy_is_translated(self):
        # These entries already exist only in the offline maps. Do not drop them
        # when updating the network catalogue or silently count only JSON rows.
        for identifier in (1728, 1800, 1919, 2109, 2299):
            self.assertIn(identifier, self.fallbacks)
            for language in TRANSLATED_LANGUAGES:
                self.assertNotEqual(self.fallbacks[identifier]["en"], self.fallbacks[identifier][language])

    def test_only_intentionally_invariant_labels_reuse_the_english_value(self):
        combined = self.fallbacks | self.catalogue
        for identifier, values in combined.items():
            for language in TRANSLATED_LANGUAGES:
                with self.subTest(id=identifier, language=language):
                    if identifier not in UNCHANGED_PRODUCT_LABELS:
                        self.assertNotEqual(values["en"], values[language])

    def test_translations_preserve_format_placeholders_and_numeric_values(self):
        for identifier, values in (self.fallbacks | self.catalogue).items():
            for language in TRANSLATED_LANGUAGES:
                with self.subTest(id=identifier, language=language):
                    self.assertEqual(Counter(PLACEHOLDER.findall(values["en"])),
                                     Counter(PLACEHOLDER.findall(values[language])))
                    self.assertEqual(Counter(DIGITS.findall(values["en"])),
                                     Counter(DIGITS.findall(values[language])))
                    self.assertEqual(values["en"].count("\n"), values[language].count("\n"))

    def test_device_protocols_brands_and_machine_paths_are_preserved(self):
        for identifier, values in (self.fallbacks | self.catalogue).items():
            for token in TECHNICAL_TOKENS:
                if token in values["en"]:
                    for language in TRANSLATED_LANGUAGES:
                        with self.subTest(id=identifier, language=language, token=token):
                            self.assertIn(token, values[language])

    def test_translations_are_valid_normalized_unicode_without_control_characters(self):
        for identifier, values in (self.fallbacks | self.catalogue).items():
            for language in TRANSLATED_LANGUAGES:
                value = values[language]
                with self.subTest(id=identifier, language=language):
                    self.assertNotIn("\ufffd", value)
                    self.assertEqual(unicodedata.normalize("NFC", value), value)
                    self.assertFalse(any(unicodedata.category(c) == "Cc" and c not in "\n\t" for c in value))
                    self.assertLess(len(value.encode("utf-8")), 65535)

    def test_work_title_has_translations_without_changing_the_previous_labels(self):
        expected = {"main": "Work", "en": "Work", "ru": "Работа", "kk": "Жұмыс",
                    "tg": "Кор", "ky": "Иш"}
        for language, label in expected.items():
            self.assertEqual(label, self.catalogue[2658][language])
        self.assertEqual(self.catalogue[2658], self.fallbacks[2658])

    def test_security_and_receipt_help_keep_critical_literal_examples(self):
        for language in TRANSLATED_LANGUAGES:
            self.assertIn("xxxxxx-xxxxxx", self.fallbacks[2623][language])
            self.assertIn("Download/AITA/Receipts", self.fallbacks[2631][language])
            self.assertIn("2FA", self.fallbacks[2621][language])
            self.assertIn("PDF", self.fallbacks[2637][language])
            self.assertIn("QR", self.fallbacks[2657][language])

    def test_faq_override_translations_are_not_lost_by_map_replacement(self):
        for identifier in (875, 885, 893, 905):
            matching = [values for key, values in self.fallback_rows if key == identifier]
            self.assertGreaterEqual(len(matching), 2)
            for language in TRANSLATED_LANGUAGES:
                self.assertEqual(self.catalogue[identifier][language], matching[-1][language])


if __name__ == "__main__":
    unittest.main(verbosity=2)
