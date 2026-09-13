#!/usr/bin/env python3
"""Offline JSON translation contracts, not runtime locale or device-rendering tests.

Run with: python3 scripts/repository/tests/test-tajik-localization.py
"""
from collections import Counter
import json
from pathlib import Path
import re
import unicodedata
import unittest

ROOT = Path(__file__).resolve().parents[3]
SERVER_STRINGS = ROOT / "server/assets/values/strings.json"
CLIENT_STRINGS = ROOT / "composeApp/src/commonMain/composeResources/files/assets/values/strings.json"
RESPONSES = ROOT / "server/config/app/responses.json"
GLOBAL = ROOT / "server/config/app/global.json"
RESOURCE_FILES = (SERVER_STRINGS, CLIENT_STRINGS, RESPONSES, GLOBAL)
LEGACY_STRING_LANGUAGES = {"main", "en", "ru", "kk"}
# These labels are brands, file/printer formats, currency codes or legal-form
# abbreviations. Keeping them intact is deliberate, not an English fallback.
UNCHANGED_LABELS = {
    "AITA", "PDF", "WhatsApp", "AITA KZT", "TSPL", "ZPL", "CPCL",
    "TOO", "QR", "Kaspi RED", "Rakhmet",
}
PROTECTED_LITERALS = (
    "AITA", "PDF", "WhatsApp", "Android", "Bluetooth", "ESC/POS",
    "TSPL", "ZPL", "CPCL", "EAN-13", "B2B", "2FA",
    "xxxxxx-xxxxxx", "Download/AITA/Receipts",
)
PLACEHOLDER = re.compile(
    r"%(?:\d+\$)?[-+0#]*(?:\d+|\*)?(?:\.(?:\d+|\*))?(?:[tT][A-Za-z]|[sSdDfFeEgGcCbBhHxXoOn])"
    r"|\$\{[A-Za-z_]\w*\}|\$[A-Za-z_]\w*|\{(?:[A-Za-z_]\w*|\d+)\}"
)
NUMBER = re.compile(r"\d+(?:[.,]\d+)*")


def unique_object(pairs):
    """Reject duplicate JSON keys instead of silently discarding earlier text."""
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def localized_lists(node, path="$"):
    if isinstance(node, list):
        # Also return a damaged localized array so shape checks can reject it.
        if any(isinstance(item, dict) and "language" in item and "value" in item for item in node):
            yield path, node
        else:
            for index, child in enumerate(node):
                yield from localized_lists(child, f"{path}[{index}]")
    elif isinstance(node, dict):
        for key, child in node.items():
            yield from localized_lists(child, f"{path}.{key}")


def language_values(items):
    return {item["language"]: item["value"] for item in items}


def all_texts():
    # Client byte equality is checked separately; no need to count it twice.
    for filename in (SERVER_STRINGS, RESPONSES, GLOBAL):
        for location, items in localized_lists(read_json(filename)):
            yield f"{filename.relative_to(ROOT)}:{location}", language_values(items)


class TajikLocalizationContractsTest(unittest.TestCase):
    def test_json_is_utf8_without_duplicate_keys(self):
        for path in RESOURCE_FILES:
            with self.subTest(file=path.relative_to(ROOT)):
                self.assertNotIn(b"\xef\xbb\xbf", path.read_bytes()[:3])
                self.assertIsNotNone(read_json(path))
        with self.assertRaises(ValueError):
            json.loads('{"language":"en","language":"tg"}', object_pairs_hook=unique_object)

    def test_client_and_server_string_resources_are_byte_identical(self):
        self.assertEqual(SERVER_STRINGS.read_bytes(), CLIENT_STRINGS.read_bytes())

    def test_numbered_ids_are_unique_and_work_label_is_present(self):
        entries = read_json(SERVER_STRINGS)["payload"]
        self.assertTrue(entries)
        ids = [item["id"] for item in entries]
        self.assertTrue(all(type(item) is int and item >= 0 for item in ids))
        self.assertEqual(len(ids), len(set(ids)))
        work = next(item for item in entries if item["id"] == 2658)
        self.assertEqual("Кор", language_values(work["values"])["tg"])

    def test_all_numbered_strings_keep_existing_languages_and_add_tajik(self):
        for entry in read_json(SERVER_STRINGS)["payload"]:
            with self.subTest(id=entry["id"]):
                values = language_values(entry["values"])
                self.assertTrue((LEGACY_STRING_LANGUAGES | {"tg"}).issubset(values))
                self.assertTrue(values["tg"].strip())

    def test_response_ids_and_tajik_messages_are_complete(self):
        entries = read_json(RESPONSES)
        self.assertTrue(entries)
        ids = [item["id"] for item in entries]
        self.assertTrue(all(isinstance(item, str) and item.isdecimal() for item in ids))
        self.assertEqual(len(ids), len(set(ids)))
        for entry in entries:
            with self.subTest(id=entry["id"]):
                values = language_values(entry["message"])
                self.assertTrue((LEGACY_STRING_LANGUAGES | {"tg"}).issubset(values))
                self.assertTrue(values["tg"].strip())

    def test_every_localized_array_has_exactly_one_tajik_value(self):
        for filename in RESOURCE_FILES:
            groups = list(localized_lists(read_json(filename)))
            self.assertTrue(groups)
            for location, items in groups:
                with self.subTest(file=filename.relative_to(ROOT), path=location):
                    self.assertTrue(all(isinstance(item, dict) for item in items))
                    for item in items:
                        self.assertIsInstance(item.get("language"), str)
                        self.assertIsInstance(item.get("value"), str)
                    codes = [item["language"] for item in items]
                    self.assertEqual(len(codes), len(set(codes)))
                    self.assertEqual(1, codes.count("tg"))
                    self.assertNotIn("tj", codes)
                    self.assertTrue(language_values(items)["tg"].strip())

    def test_language_registry_uses_tg_with_the_tajik_autonym(self):
        entries = read_json(GLOBAL)["payload"]["languages"]
        codes = [item["language"] for item in entries]
        self.assertEqual(len(codes), len(set(codes)))
        self.assertTrue({"en", "ru", "kk", "tg"}.issubset(codes))
        self.assertNotIn("tj", codes)
        tajik = next(item for item in entries if item["language"] == "tg")
        self.assertEqual("png/flag_tj.png", tajik["flagDrawablePath"])
        self.assertEqual({"en": "Tajik", "ru": "Таджикский", "kk": "Тәжікше", "tg": "Тоҷикӣ"},
                         {key: language_values(tajik["name"])[key] for key in ("en", "ru", "kk", "tg")})

    def test_country_locale_phone_and_currency_are_not_language_codes(self):
        countries = read_json(GLOBAL)["payload"]["countries"]
        tajikistan = next(item for item in countries if item["locale"] == "tj")
        self.assertEqual("tg", tajikistan["language"])
        self.assertEqual("png/flag_tj.png", tajikistan["flagDrawablePath"])
        self.assertEqual("992", tajikistan["phoneNumberCode"])
        self.assertEqual(9, tajikistan["phoneNumberSize"])
        somoni = next(item for item in tajikistan["currencies"] if item["code"] == "TJS")
        self.assertEqual("SM", somoni["symbol"])
        self.assertEqual("Сомонӣ", language_values(somoni["name"])["tg"])

    def test_existing_flag_assets_resolve_for_server_and_compose(self):
        for path in (ROOT / "server/assets/drawable/png/flag_tj.png",
                     ROOT / "composeApp/src/commonMain/composeResources/drawable/flag_tj.png"):
            with self.subTest(file=path.relative_to(ROOT)):
                self.assertTrue(path.is_file())
                self.assertEqual(b"\x89PNG\r\n\x1a\n", path.read_bytes()[:8])

    def test_tajik_text_is_normalized_and_has_no_hidden_control_characters(self):
        for location, values in all_texts():
            with self.subTest(path=location):
                text = values["tg"]
                self.assertEqual(text, unicodedata.normalize("NFC", text))
                self.assertNotIn("\ufffd", text)
                self.assertEqual([], [char for char in text if unicodedata.category(char) in {"Cc", "Cf"}
                                      and char not in "\n\r\t"])

    def test_no_english_prose_was_copied_as_a_tajik_fallback(self):
        for location, values in all_texts():
            with self.subTest(path=location):
                original = values.get("en", values.get("main", ""))
                if original == values["tg"] and re.search(r"[A-Za-z]", original):
                    self.assertIn(original, UNCHANGED_LABELS)

    def test_numbers_and_percent_markers_are_preserved(self):
        for location, values in all_texts():
            with self.subTest(path=location):
                original = values.get("en", values.get("main", ""))
                self.assertEqual(Counter(NUMBER.findall(original)), Counter(NUMBER.findall(values["tg"])))
                self.assertEqual(original.count("%"), values["tg"].count("%"))

    def test_placeholders_and_line_breaks_are_preserved(self):
        for location, values in all_texts():
            with self.subTest(path=location):
                original = values.get("en", values.get("main", ""))
                self.assertEqual(Counter(PLACEHOLDER.findall(original)), Counter(PLACEHOLDER.findall(values["tg"])))
                self.assertEqual(original.count("\n"), values["tg"].count("\n"))
                self.assertEqual(original.count("\t"), values["tg"].count("\t"))
        # Exercise matching even when the current catalog has no interpolations.
        self.assertEqual(Counter(["%1$s", "%02d", "${name}", "$count", "{amount}", "{0}"]),
                         Counter(PLACEHOLDER.findall("%1$s %02d ${name} $count {amount} {0} 10% %%")))

    def test_technical_names_paths_and_code_examples_are_preserved(self):
        for location, values in all_texts():
            original = values.get("en", values.get("main", ""))
            for literal in PROTECTED_LITERALS:
                if literal in original:
                    with self.subTest(path=location, literal=literal):
                        self.assertIn(literal, values["tg"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
