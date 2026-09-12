import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import re
import stat
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("subscription_admin", Path(__file__).with_name("subscription-admin.py"))
admin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admin)


class SubscriptionAdminTest(unittest.TestCase):
    def args(self, *values):
        return admin.parser().parse_args([*values, "--sql", "unused.sql"])

    def test_ascii_normalization(self):
        self.assertEqual("AITA-EXAMPLE", admin.normalize_code("  aita-example  "))
        for bad in ("ßßßabc", "АITA-CODE", "AITA\nCODE", "AITA CODE", "abc", "a" * 97):
            with self.assertRaises(ValueError):
                admin.normalize_code(bad)

    def test_lifetime_has_hash_but_no_plaintext(self):
        sql = admin.promo_sql(self.args("promo", "--type", "lifetime"), "aita-secret-test")
        self.assertNotIn("AITA-SECRET-TEST", sql)
        self.assertIn(hashlib.sha256(b"AITA-SECRET-TEST").hexdigest(), sql)
        self.assertIn("'lifetime'", sql)

    def test_duration_is_exact_and_bounded(self):
        sql = admin.promo_sql(self.args("promo", "--type", "timed", "--duration-days", "30"), "AITA-TIMED")
        self.assertIn("2592000000", sql)
        for bad in ("0", "-1", "NaN", "Infinity", "0.00000000001", "1000000"):
            with self.assertRaises(ValueError):
                admin.promo_sql(self.args("promo", "--type", "timed", "--duration-days", bad), "AITA-TIMED")

    def test_percentage_is_basis_points(self):
        sql = admin.promo_sql(self.args("promo", "--type", "discount", "--discount-percent", "25.25"), "AITA-DISCOUNT")
        self.assertIn("2525", sql)
        for invalid in ("0", "100.01", "0.001", "NaN"):
            with self.assertRaises(ValueError):
                admin.promo_sql(self.args("promo", "--type", "discount", "--discount-percent", invalid), "AITA-DISCOUNT")

    def test_fixed_discount_requires_currency(self):
        with self.assertRaises(ValueError):
            admin.promo_sql(self.args("promo", "--type", "discount", "--discount-minor", "100"), "AITA-FIXED")
        sql = admin.promo_sql(self.args("promo", "--type", "discount", "--discount-minor", "100", "--currency", "kzt"), "AITA-FIXED")
        self.assertIn("'KZT'", sql)

    def test_incompatible_types_are_rejected(self):
        invalid = [
            ("lifetime", "--duration-days", "1"), ("lifetime", "--discount-percent", "5"),
            ("timed",), ("timed", "--duration-days", "2", "--discount-percent", "1"),
            ("discount",), ("discount", "--discount-percent", "10", "--discount-minor", "50"),
        ]
        for fields in invalid:
            with self.assertRaises(ValueError):
                admin.promo_sql(self.args("promo", "--type", *fields), "AITA-INVALID")

    def test_explicit_bindings_and_limits(self):
        sql = admin.promo_sql(self.args("promo", "--type", "lifetime", "--region", "kz", "--store-id",
            "a4dd4118-f19b-49a8-a560-4b29315aa246", "--max-redemptions", "2"), "AITA-BOUND")
        self.assertIn("'KZ'", sql); self.assertIn("'a4dd4118-f19b-49a8-a560-4b29315aa246'", sql)
        for bad in ("0", "-1", "9223372036854775808"):
            with self.assertRaises(ValueError):
                admin.promo_sql(self.args("promo", "--type", "lifetime", "--max-redemptions", bad), "AITA-BOUND")

    def test_validity_requires_timezone_and_increasing_window(self):
        self.assertEqual(0, admin.timestamp("1970-01-01T00:00:00Z"))
        self.assertEqual(admin.timestamp("2026-09-12T12:00:00+05:00"), admin.timestamp("2026-09-12T07:00:00Z"))
        with self.assertRaises(ValueError):
            admin.timestamp("2026-09-12T12:00:00")
        with self.assertRaises(ValueError):
            admin.promo_sql(self.args("promo", "--type", "lifetime", "--valid-from", "2026-09-12T07:00:00Z",
                "--valid-until", "2026-09-12T07:00:00Z"), "AITA-WINDOW")

    def test_tags_and_codes_cannot_inject_sql(self):
        for bad in ("KZ';DROP TABLE users;--", "ＫＺ", "КZ"):
            with self.assertRaises(ValueError):
                admin.promo_sql(self.args("promo", "--type", "lifetime", "--region", bad), "AITA-BOUND")
        self.assertEqual("'a''b'", admin.sql_literal("a'b"))

    def test_price_is_explicit_minor_units_and_preserves_renewals(self):
        sql = admin.price_sql(self.args("price", "--region", "kz", "--currency", "kzt", "--price-minor", "799000"))
        self.assertIn("799000", sql); self.assertIn("'KZT'", sql)
        self.assertIn("ON CONFLICT", sql); self.assertNotIn("UPDATE store_subscription_states", sql)
        for bad in ("-1", "1000000000001"):
            with self.assertRaises(ValueError):
                admin.price_sql(self.args("price", "--region", "KZ", "--currency", "KZT", "--price-minor", bad))

    def test_generated_code_exclusive_file_and_private_mode(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "promo.sql"
            stdout, stderr = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                self.assertEqual(0, admin.main(["promo", "--type", "lifetime", "--sql", str(path)]))
            code = stderr.getvalue().strip().splitlines()[-1]
            self.assertRegex(code, r"^AITA-[A-F0-9]{40}$")
            self.assertNotIn(code, path.read_text()); self.assertNotIn(code, stdout.getvalue())
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))
            original = path.read_bytes()
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                admin.main(["promo", "--type", "lifetime", "--sql", str(path)])
            self.assertEqual(original, path.read_bytes())

    def test_environment_secret_is_not_in_the_sql(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {"AITA_TEST_PROMO_CODE": "aita-private-code"}):
            path = Path(folder) / "code.sql"
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                admin.main(["promo", "--type", "lifetime", "--code-env", "AITA_TEST_PROMO_CODE", "--sql", str(path)])
            self.assertIn(hashlib.sha256(b"AITA-PRIVATE-CODE").hexdigest(), path.read_text())
            self.assertNotIn("PRIVATE-CODE", path.read_text())

    def test_invalid_input_creates_no_file(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "invalid.sql"
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                admin.main(["promo", "--type", "timed", "--sql", str(path)])
            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
