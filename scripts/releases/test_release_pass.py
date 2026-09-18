import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    value = importlib.util.module_from_spec(spec); spec.loader.exec_module(value)
    return value


release = module('release_pass', 'release-pass.py')
access = module('server_access', 'server-release-access.py')
windows = module('windows_verify', 'verify-windows-release.py')


class ReleasePassTests(unittest.TestCase):
    def test_versions_cannot_escape_release_folder_or_exceed_native_limits(self):
        for value in ('../keys', '1.2.3/next', '01.0.0', '256.0.1', '1.256.0', '1.0.65536', '1.2.3;cmd'):
            with self.assertRaises(RuntimeError): release.release_identity(value, 2)
        self.assertEqual(release.release_identity('1.2.3', 8), 'v1.2.3-b8')
        for number in (0, -1, 2100000001):
            with self.assertRaises(RuntimeError): release.release_identity('1.0.1', number)

    def test_secret_redaction_covers_known_values_tokens_and_bearer(self):
        text = release.redact('my-secret-value password=hunter22 Bearer oauth-value ghp_abcdef123', ['my-secret-value'])
        for value in ('my-secret-value', 'hunter22', 'oauth-value', 'ghp_abcdef123'):
            self.assertNotIn(value, text)

    def test_failed_stage_cannot_publish_partially_verified_artifacts(self):
        with TemporaryDirectory() as folder, patch.object(release, 'ROOT', Path(folder)):
            run = release.Run(SimpleNamespace())
            run.state['artifacts'].append({'name': 'previous-good.apk'})
            def fail():
                run.state['artifacts'].append({'name': 'unverified.exe'})
                raise RuntimeError('Signature invalid')
            self.assertFalse(run.stage('windows', fail))
            self.assertEqual([e['name'] for e in run.state['artifacts']], ['previous-good.apk'])
            saved = json.loads((run.folder / 'result.json').read_text())
            self.assertEqual(saved['stages']['windows']['status'], 'failed')
            run.log.close()

    def test_changed_source_is_rejected_before_publication(self):
        with TemporaryDirectory() as folder, patch.object(release, 'ROOT', Path(folder)):
            run = release.Run(SimpleNamespace()); run.revision = 'a' * 40
            with patch.object(release, 'capture', side_effect=['a' * 40, ' M source.kt']):
                with self.assertRaisesRegex(RuntimeError, 'Source changed'): run.unchanged()
            run.log.close()

    def test_wrong_commit_and_injected_arguments_never_start_privileged_updater(self):
        for commit in ('', 'a'*39, 'a'*40+' --force', '../../root', '$(id)', 'A'*40):
            with self.assertRaisesRegex(RuntimeError, 'full reviewed commit'): access.launch(commit)

    def test_untrusted_or_untimestamped_windows_signature_is_rejected(self):
        for status, thumbprint, stamp in [('NotSigned', '', ''), ('Valid', 'OTHER', 'stamp'), ('Valid', 'EXPECTED', '')]:
            result = SimpleNamespace(stdout=json.dumps(dict(status=status, thumbprint=thumbprint, timestamp=stamp)))
            with patch.object(windows.subprocess, 'run', return_value=result):
                with self.assertRaises(RuntimeError): windows.verify_signature(Path('app.exe'), True, 'EXPECTED')
        result = SimpleNamespace(stdout=json.dumps(dict(status='Valid', thumbprint='EXPECTED', timestamp='stamp')))
        with patch.object(windows.subprocess, 'run', return_value=result):
            self.assertEqual(windows.verify_signature(Path('app.exe'), True, 'EXPECTED')['status'], 'Valid')

    def test_authorized_unsigned_pilot_still_rejects_a_broken_signature(self):
        unsigned = SimpleNamespace(stdout=json.dumps(dict(status='NotSigned', thumbprint=None, timestamp=None)))
        with patch.object(windows.subprocess, 'run', return_value=unsigned):
            self.assertEqual(windows.verify_signature(Path('app.exe'), False, '')['status'], 'NotSigned')
        broken = SimpleNamespace(stdout=json.dumps(dict(status='HashMismatch', thumbprint='CERT', timestamp='stamp')))
        with patch.object(windows.subprocess, 'run', return_value=broken):
            with self.assertRaises(RuntimeError): windows.verify_signature(Path('app.exe'), False, '')


if __name__ == '__main__': unittest.main()
