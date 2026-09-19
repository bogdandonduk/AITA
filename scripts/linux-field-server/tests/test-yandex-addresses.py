import contextlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

MODULE = Path(__file__).resolve().parents[1] / 'setup-yandex-addresses.py'
spec = importlib.util.spec_from_file_location('address_setup', MODULE)
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)

class AddressSetupTest(unittest.TestCase):
    def test_blank_keys_and_comments_keep_the_next_setting(self):
        text = 'AITA_YANDEX_GEOCODER_API_KEY=\nDB_PASS=retained-value\n# Keep this comment\n'
        self.assertEqual('', setup.existing_value(text, 'AITA_YANDEX_GEOCODER_API_KEY'))
        updated = setup.updated_text(text, {'AITA_YANDEX_GEOCODER_API_KEY': 'new-test-key'})
        self.assertEqual(text.replace('API_KEY=', 'API_KEY=new-test-key'), updated)

    def test_duplicate_settings_and_invalid_keys_are_rejected(self):
        with self.assertRaises(RuntimeError): setup.updated_text('KEY=one\nKEY=two\n', {'KEY': 'three'})
        for invalid in ['short', 'key\nNEXT=bad', '$(command)', 'value with spaces']:
            with self.assertRaises(RuntimeError): setup.checked_key(invalid)

    def test_backup_replacement_and_stale_write_preserve_other_configuration(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'server.env'
            old = 'DB_PASS=retained-test-value\nKEY=before\n'
            path.write_text(old); path.chmod(0o640)
            backup = setup.save_configuration(path, old, {'KEY': 'after'})
            self.assertEqual(old, backup.read_text())
            self.assertEqual(0o600, backup.stat().st_mode & 0o777)
            self.assertEqual(0o640, path.stat().st_mode & 0o777)
            self.assertEqual(old.replace('KEY=before', 'KEY=after'), path.read_text())
            with self.assertRaises(RuntimeError): setup.save_configuration(path, old, {'KEY': 'stale'})
            self.assertIn('KEY=after', path.read_text())

    def test_provider_error_never_prints_key_or_provider_response(self):
        key = 'private-test-key-do-not-print'
        error = urllib.error.HTTPError('https://provider.invalid?apikey=' + key, 403, key, {}, None)
        output = io.StringIO()
        with patch.object(setup.urllib.request, 'urlopen', side_effect=error), contextlib.redirect_stdout(output):
            with self.assertRaises(RuntimeError) as failure:
                setup.check_service('AITA_YANDEX_GEOCODER_API_KEY', key)
        self.assertIn('HTTP 403', str(failure.exception))
        self.assertNotIn(key, str(failure.exception) + output.getvalue())

    def test_symlink_configuration_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            original = Path(folder) / 'original'; original.write_text('KEY=before\n')
            link = Path(folder) / 'link'; link.symlink_to(original)
            with self.assertRaises(RuntimeError): setup.save_configuration(link, original.read_text(), {'KEY': 'after'})
            self.assertEqual('KEY=before\n', original.read_text())

if __name__ == '__main__': unittest.main()
