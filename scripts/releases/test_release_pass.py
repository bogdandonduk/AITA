import importlib.util
import json
import os
import subprocess
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
    def test_metadata_sequence_advances_after_partial_platform_publication(self):
        import base64
        with TemporaryDirectory() as folder:
            catalog = Path(folder)
            self.assertEqual(release.next_feed_sequence(catalog, 3), 3)
            def saved(sequence):
                payload = base64.b64encode(json.dumps({'sequence':sequence}).encode()).decode()
                (catalog / 'release.json').write_text(json.dumps({'payload':payload}))
            saved(3)
            self.assertEqual(release.next_feed_sequence(catalog, 3), 4)
            saved(4)
            self.assertEqual(release.next_feed_sequence(catalog, 4), 5)
            saved(8)
            self.assertEqual(release.next_feed_sequence(catalog, 12), 12)
            for invalid in (True, 0, -1, '4', 9_007_199_254_740_991):
                saved(invalid)
                with self.assertRaises(RuntimeError): release.next_feed_sequence(catalog, 4)

    def test_corrupt_catalog_never_resets_the_metadata_sequence(self):
        with TemporaryDirectory() as folder:
            catalog = Path(folder)
            (catalog / 'release.json').write_text('not a release')
            with self.assertRaises(ValueError): release.next_feed_sequence(catalog, 4)

    def test_reused_windows_run_must_match_source_identity_workflow_and_success(self):
        with TemporaryDirectory() as folder, patch.object(release, 'ROOT', Path(folder)):
            run = release.Run(SimpleNamespace(windows_run=123)); run.revision = 'a'*40; run.tag = 'v1.0.2-b3'
            good = dict(headSha=run.revision, workflowName='Build AITA Windows Release',
                        displayTitle='AITA Windows v1.0.2-b3', status='completed', conclusion='success')
            with patch.object(release, 'capture', return_value=json.dumps(good)):
                run.reuse_windows()
                self.assertEqual(run.state['windowsRun'], 123)
            for change in ({'headSha':'b'*40}, {'workflowName':'Unrelated'}, {'displayTitle':'AITA Windows v1.0.1-b2'}, {'conclusion':'failure'}):
                with patch.object(release, 'capture', return_value=json.dumps(good | change)):
                    with self.assertRaises(RuntimeError): run.reuse_windows()
            run.log.close()

    def test_server_result_uses_owner_readable_verified_state_not_root_only_metadata(self):
        with TemporaryDirectory() as folder, patch.object(release, 'ROOT', Path(folder)):
            root = Path(folder)
            helper = root / 'helper'; helper.touch()
            pointer = root / 'latest.json'; pointer.write_text('{"run_id":"run"}')
            runs = root / 'runs'; (runs / 'run').mkdir(parents=True)
            state = runs / 'run/state.json'
            state.write_text(json.dumps(dict(commit='a'*40, result='success', deployment_verified=True)))
            actual_path = Path
            def path(value):
                return {'/usr/local/sbin/aita-release-server': helper, '/var/lib/aita-ops/latest.json': pointer,
                        '/var/lib/aita-ops/runs': runs}.get(str(value), actual_path(value))
            run = release.Run(SimpleNamespace()); run.revision = 'a'*40
            with patch.object(release, 'Path', side_effect=path), patch.object(run, 'command'):
                run.server()
                self.assertEqual(run.state['serverRun'], 'run')
                state.write_text(json.dumps(dict(commit='b'*40, result='success', deployment_verified=True)))
                with self.assertRaises(RuntimeError): run.server()
            run.log.close()

    def test_public_catalog_setup_contains_no_private_key_and_refuses_trust_rotation(self):
        with TemporaryDirectory() as folder:
            root = Path(folder); key_dir = root / '.config/aita/release-signing'; key_dir.mkdir(parents=True)
            private = root / 'private.pem'; public_der = root / 'public.der'
            subprocess.run(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)], check=True, capture_output=True)
            subprocess.run(['openssl', 'pkey', '-in', str(private), '-pubout', '-outform', 'DER', '-out', str(public_der)], check=True, capture_output=True)
            import base64
            (key_dir / 'update-public.txt').write_text(base64.b64encode(public_der.read_bytes()).decode())
            owner = SimpleNamespace(pw_dir=str(root), pw_uid=os.getuid(), pw_gid=os.getgid())
            env = root / 'etc/client-releases.env'; dropin = root / 'systemd/40-client-releases.conf'
            run = subprocess.run
            def command(args, **kwargs):
                if args[0] == '/usr/bin/systemctl': return SimpleNamespace(returncode=0)
                return run(args, **kwargs)
            with patch.object(access, 'CATALOG', root / 'catalog'), patch.object(access, 'FEED_ENV', env), patch.object(access, 'FEED_DROPIN', dropin), \
                    patch.object(access.pwd, 'getpwnam', return_value=owner), patch.object(access.os, 'chown'), patch.object(access.subprocess, 'run', side_effect=command):
                access.configure_public_catalog({'owner': 'bogdan'})
                self.assertIn('AITA_UPDATE_PUBLIC_KEY=', env.read_text())
                self.assertNotIn('PRIVATE', env.read_text())
                access.configure_public_catalog({'owner': 'bogdan'})
                env.write_text('AITA_UPDATE_PUBLIC_KEY=different\n')
                with self.assertRaisesRegex(RuntimeError, 'rotation'): access.configure_public_catalog({'owner': 'bogdan'})

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
