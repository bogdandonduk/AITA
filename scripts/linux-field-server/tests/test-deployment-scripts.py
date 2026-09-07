#!/usr/bin/env python3
"""Offline regression tests. No real service, database, email, or /opt path is touched.

Run from the repository root:
  python3 scripts/linux-field-server/tests/test-deployment-scripts.py

JARs below are structural fixtures, not executable Kotlin applications. Real
unzip, Bash, Git, and rsync are used; service/network calls are sandboxed doubles.
"""
from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
import warnings
import zipfile

ROOT = Path(__file__).resolve().parents[3]
COMMON = ROOT / 'scripts/linux-field-server/aita-linux-common.sh'
DEPLOY = ROOT / 'scripts/linux-field-server/deploy-aita-server.sh'
APPLY = ROOT / 'scripts/repository/apply-aita-full-bundle.sh'
MAIN = 'kz/aita/server/ServerKt.class'
AUTH = 'kz/aita/server/auth/AitaAdvancedAuthenticationKt.class'
MANIFEST = 'META-INF/MANIFEST.MF'
RESOURCES = {
    'application.yaml': b'ktor:\n  application:\n    modules: [kz.aita.server.ServerKt.module]\n',
    'logback.xml': b'<configuration/>\n',
    'db/migration/V1__init.sql': b'-- structural fixture; never executed\n',
    'db/migration/V93__auth.sql': b'-- auth structural fixture; never executed\n',
    'db/migration/V94__aliases.sql': b'-- aliases structural fixture; never executed\n',
}


def run(args: list[str], *, cwd: Path | None = None, env: dict | None = None,
        timeout: int = 25) -> subprocess.CompletedProcess:
    return subprocess.run(args, cwd=cwd, env=env, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)


def make_source(root: Path) -> None:
    for name, data in RESOURCES.items():
        target = root / 'server/src/main/resources' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    auth = root / 'server/src/main/kotlin/kz/aita/server/auth/AitaAdvancedAuthentication.kt'
    auth.parent.mkdir(parents=True, exist_ok=True)
    auth.write_text('package kz.aita.server.auth\n// structural fixture only\n')


def make_jar(path: Path, *, omit: tuple[str, ...] = (), overrides: dict | None = None,
             entries: int = 0, duplicate: str | None = None) -> None:
    files = {
        MAIN: b'structural fixture: not executable bytecode',
        MANIFEST: b'Manifest-Version: 1.0\r\nMain-Class: kz.aita.server.ServerKt\r\n\r\n',
        AUTH: b'structural fixture: not executable bytecode',
        **RESOURCES,
        'padding.bin': bytes(range(256)) * 4096,
    }
    files.update(overrides or {})
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_STORED) as archive:
        for name, data in files.items():
            if name not in omit:
                archive.writestr(name, data)
        for i in range(entries):
            archive.writestr(f'org/example/dependency/LongGeneratedClassName{i:06d}.class', b'fixture')
        if duplicate:
            with warnings.catch_warnings():
                warnings.simplefilter('ignore', UserWarning)
                archive.writestr(duplicate, files[duplicate])


@unittest.skipUnless(shutil.which('bash') and shutil.which('unzip'), 'requires Bash and unzip')
class JarValidationTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='aita-jar-test-')
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.project = self.base / 'project with spaces'
        make_source(self.project)
        self.jar = self.project / 'server/build/libs/aita-server-all.jar'

    def validate(self, *, source: bool = True):
        return run(['bash', '-c', 'source "$1"; aita_verify_server_jar "$2" "${3:-}"',
                    '_', str(COMMON), str(self.jar), str(self.project) if source else ''])

    def test_large_valid_jar_is_not_rejected_by_pipefail(self):
        make_jar(self.jar, entries=12000)
        result = self.validate()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn('3 source migration(s) match', result.stdout)

    def test_small_listing_valid_jar(self):
        make_jar(self.jar)
        self.assertEqual(self.validate().returncode, 0)

    def test_lf_manifest(self):
        make_jar(self.jar, overrides={MANIFEST: b'Manifest-Version: 1.0\nMain-Class: kz.aita.server.ServerKt\n\n'})
        self.assertEqual(self.validate().returncode, 0)

    def test_large_manifest_is_fully_read(self):
        value = b'Main-Class: kz.aita.server.ServerKt\r\n' + b'X-Padding: fixture\r\n' * 12000
        make_jar(self.jar, overrides={MANIFEST: value})
        self.assertEqual(self.validate().returncode, 0)

    def test_missing_class_rejected(self):
        make_jar(self.jar, omit=(MAIN,))
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(MAIN, result.stdout)

    def test_class_name_uses_literal_matching(self):
        make_jar(self.jar, omit=(MAIN,), overrides={'kz/aita/server/ServerKtXclass': b'not the class'})
        self.assertNotEqual(self.validate().returncode, 0)

    def test_wrong_manifest_rejected(self):
        make_jar(self.jar, overrides={MANIFEST: b'Main-Class: example.Wrong\r\n'})
        self.assertNotEqual(self.validate().returncode, 0)

    def test_duplicate_manifest_main_class_rejected(self):
        make_jar(self.jar, overrides={MANIFEST: b'Main-Class: kz.aita.server.ServerKt\nMain-Class: wrong.Main\n\n'})
        self.assertNotEqual(self.validate().returncode, 0)

    def test_named_manifest_section_cannot_override_wrong_main_class(self):
        make_jar(self.jar, overrides={MANIFEST: b'Main-Class: wrong.Main\n\nName: other.class\nMain-Class: kz.aita.server.ServerKt\n'})
        self.assertNotEqual(self.validate().returncode, 0)

    def test_missing_manifest_rejected(self):
        make_jar(self.jar, omit=(MANIFEST,))
        self.assertNotEqual(self.validate().returncode, 0)

    def test_duplicate_entrypoint_rejected(self):
        make_jar(self.jar, duplicate=MAIN)
        self.assertNotEqual(self.validate().returncode, 0)

    def test_missing_yaml_rejected(self):
        make_jar(self.jar, omit=('application.yaml',))
        self.assertNotEqual(self.validate().returncode, 0)

    def test_missing_auth_class_rejected(self):
        make_jar(self.jar, omit=(AUTH,))
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('compiled route class is missing', result.stdout)

    def test_legacy_auth_package_derived_from_source(self):
        path = self.project / 'server/src/main/kotlin/kz/aita/server/auth/AitaAdvancedAuthentication.kt'
        path.write_text('package kz.aita.server\n')
        make_jar(self.jar, omit=(AUTH,), overrides={'kz/aita/server/AitaAdvancedAuthenticationKt.class': b'fixture'})
        self.assertEqual(self.validate().returncode, 0)

    def test_pending_source_migration_must_be_packaged(self):
        make_jar(self.jar, omit=('db/migration/V94__aliases.sql',))
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Source resource is missing', result.stdout)

    def test_source_and_packaged_sql_bytes_must_match(self):
        make_jar(self.jar, overrides={'db/migration/V93__auth.sql': b'-- different SQL'})
        result = self.validate()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Packaged resource differs', result.stdout)

    def test_config_bytes_must_match(self):
        make_jar(self.jar, overrides={'application.yaml': b'ktor: old'})
        self.assertNotEqual(self.validate().returncode, 0)

    def test_corrupt_archive_rejected(self):
        make_jar(self.jar)
        self.jar.write_bytes(self.jar.read_bytes()[:-100])
        self.assertNotEqual(self.validate().returncode, 0)

    def test_crc_failure_rejected(self):
        make_jar(self.jar)
        data = bytearray(self.jar.read_bytes())
        position = data.index(bytes(range(256)) * 2)
        data[position] ^= 1
        self.jar.write_bytes(data)
        self.assertNotEqual(self.validate().returncode, 0)

    def test_tiny_artifact_rejected(self):
        self.jar.parent.mkdir(parents=True)
        self.jar.write_bytes(b'not a fat jar')
        self.assertNotEqual(self.validate().returncode, 0)

    def test_malformed_environment_does_not_disclose_value(self):
        env_file = self.base / 'private.env'
        marker = 'SENSITIVE_TEST_VALUE_DO_NOT_ECHO'
        env_file.write_text(marker + '\n')
        result = run(['bash', '-c', 'source "$1"; aita_load_env "$2"', '_', str(COMMON), str(env_file)])
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(marker, result.stdout)
        self.assertIn('line 1', result.stdout)

    def test_invalid_environment_name_does_not_disclose_value(self):
        env_file = self.base / 'private.env'
        marker = 'SENSITIVE_TEST_VALUE_DO_NOT_ECHO'
        env_file.write_text('bad name ' + marker + '=value\n')
        result = run(['bash', '-c', 'source "$1"; aita_load_env "$2"', '_', str(COMMON), str(env_file)])
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(marker, result.stdout)

    def test_legacy_rollback_does_not_require_new_source(self):
        make_jar(self.jar, omit=(AUTH, 'db/migration/V93__auth.sql', 'db/migration/V94__aliases.sql'))
        result = self.validate(source=False)
        self.assertEqual(result.returncode, 0, result.stdout)


MOCK_TOOL = r'''#!/usr/bin/env python3
import json, os, pathlib, shutil, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
state_path = pathlib.Path(os.environ['AITA_TEST_STATE'])
state = json.loads(state_path.read_text())
with open(os.environ['AITA_TEST_EVENTS'], 'a') as log:
    log.write(json.dumps([name, *args]) + '\n')

def save(): state_path.write_text(json.dumps(state))
if name == 'sudo':
    if args == ['-v']: sys.exit(0)
    os.execvp(args[0], args)
if name == 'chown': sys.exit(0)
if name == 'install':
    mode = 0o644
    i = 0
    while args[i].startswith('-'):
        if args[i] == '-m': mode = int(args[i + 1], 8)
        i += 2
    source, dest = args[i:]
    assert str(pathlib.Path(dest).resolve()).startswith(os.environ['AITA_TEST_BASE'] + '/')
    shutil.copyfile(source, dest)
    os.chmod(dest, mode)
    sys.exit(0)
if name == 'systemctl':
    if 'show' in args:
        if '--property=MainPID' in args: print(state.get('pid', 111))
        elif '--property=InvocationID' in args: print(state.get('invocation', 'old-invocation'))
        else: sys.exit('Unexpected show request')
    elif 'aita-backup.service' in args:
        sys.exit(1 if state.get('fail_backup') else 0)
    elif args[0] == 'stop':
        if state.get('fail_stop'): sys.exit(1)
        state.update(running=False, pid=111 if state.get('stuck_pid') else 0)
        save()
    elif args[0] == 'start':
        if state.get('fail_start'): sys.exit(1)
        state.update(running=True, pid=222,
                     invocation='old-invocation' if state.get('same_invocation') else 'new-invocation')
        save()
    elif args[0] == 'is-active':
        sys.exit(0 if state.get('running', True) else 3)
    elif 'status' in args: print('Sandbox service status')
    else: sys.exit('Unexpected systemctl command: ' + repr(args))
    sys.exit(0)
if name == 'journalctl':
    print('Sandbox journal; no real service was read.')
    sys.exit(0)
if name == 'ss':
    pid = 9999 if state.get('wrong_listener') else state.get('pid', 111)
    print('LISTEN 0 4096 127.0.0.1:8080 *:* users:(("java",pid=%s,fd=95))' % pid)
    sys.exit(0)
if name == 'curl':
    assert '--noproxy' in args and '*' in args
    url = args[-1]
    assert url.startswith('http://127.0.0.1:8080/')
    print(state.get('auth_status', '200') if url.endswith('/auth/capabilities')
          else state.get('ready_status', '200'), end='')
    sys.exit(0)
sys.exit('Unexpected mock executable: ' + name)
'''


@unittest.skipUnless(all(shutil.which(x) for x in ('bash', 'unzip', 'flock')), 'requires Linux shell utilities')
class DeployControlFlowTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='aita-deploy-test-')
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.project = self.base / 'checkout'
        make_source(self.project)
        self.jar = self.project / 'server/build/libs/aita-server-all.jar'
        make_jar(self.jar)
        self.app = self.base / 'installed'
        self.app.mkdir()
        self.current = self.app / 'aita-server-all.jar'
        self.old = b'old installed artifact; must survive validation failure'
        self.current.write_bytes(self.old)
        scripts = self.base / 'scripts'
        scripts.mkdir()
        shutil.copyfile(COMMON, scripts / COMMON.name)
        self.script = scripts / DEPLOY.name
        # Only fixed installation paths are rewritten. Control flow is unmodified.
        self.script.write_text(DEPLOY.read_text().replace('/opt/aita/app', str(self.app)))
        self.bin = self.base / 'bin'
        self.bin.mkdir()
        for name in ('sudo', 'install', 'chown', 'systemctl', 'ss', 'curl', 'journalctl'):
            path = self.bin / name
            path.write_text(MOCK_TOOL.replace("#!/usr/bin/env python3", f"#!{sys.executable} -S", 1))
            path.chmod(0o755)
        self.state = self.base / 'state.json'
        self.events = self.base / 'events.jsonl'
        self.state.write_text('{}')
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        HOME=str(self.base), XDG_STATE_HOME=str(self.base / 'state'),
                        AITA_TEST_BASE=str(self.base), AITA_TEST_STATE=str(self.state),
                        AITA_TEST_EVENTS=str(self.events))

    def deploy(self, *, state: dict | None = None, args: tuple[str, ...] = ()):
        self.state.write_text(json.dumps(state or {}))
        return run(['bash', str(self.script), '--project-root', str(self.project),
                    '--ready-timeout', '1', *args], env=self.env)

    def event_text(self):
        return self.events.read_text() if self.events.exists() else ''

    def test_verify_only_changes_no_service_or_installed_file(self):
        result = self.deploy(args=('--verify-only',))
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.current.read_bytes(), self.old)
        self.assertEqual(self.event_text(), '')

    def test_bad_jar_fails_before_service_or_backup(self):
        self.jar.write_bytes(b'broken')
        result = self.deploy(args=('--backup',))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.current.read_bytes(), self.old)
        self.assertEqual(self.event_text(), '')
        self.assertIn('did not replace', result.stdout)

    def test_backup_failure_keeps_old_server_untouched(self):
        result = self.deploy(state={'fail_backup': True}, args=('--backup',))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.current.read_bytes(), self.old)
        self.assertNotIn('"stop"', self.event_text())

    def test_stop_failure_never_installs_candidate(self):
        result = self.deploy(state={'fail_stop': True})
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.current.read_bytes(), self.old)
        self.assertNotIn('"curl"', self.event_text())
        self.assertFalse(list(self.app.glob('*.new')))

    def test_successful_stop_must_release_mainpid(self):
        result = self.deploy(state={'stuck_pid': True})
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.current.read_bytes(), self.old)

    def test_success_requires_new_service_and_both_routes(self):
        result = self.deploy(args=('--backup',))
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn('DEPLOYMENT COMPLETE', result.stdout)
        self.assertIn('New service PID: 222', result.stdout)
        self.assertEqual(self.current.read_bytes(), self.jar.read_bytes())
        self.assertEqual((self.app / 'aita-server-all.previous.jar').read_bytes(), self.old)
        self.assertIn('/auth/capabilities', self.event_text())

    def test_start_failure_preserves_previous_and_does_not_claim_success(self):
        result = self.deploy(state={'fail_start': True})
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('DEPLOYMENT COMPLETE', result.stdout)
        self.assertEqual((self.app / 'aita-server-all.previous.jar').read_bytes(), self.old)

    def test_old_invocation_cannot_satisfy_readiness(self):
        result = self.deploy(state={'same_invocation': True})
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('"curl"', self.event_text())

    def test_another_process_on_8080_cannot_satisfy_readiness(self):
        result = self.deploy(state={'wrong_listener': True})
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('"curl"', self.event_text())

    def test_missing_auth_route_is_not_a_successful_deployment(self):
        result = self.deploy(state={'auth_status': '404'})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('/auth/capabilities=404', result.stdout)
        self.assertNotIn('DEPLOYMENT COMPLETE', result.stdout)

    def test_unready_database_is_not_a_successful_deployment(self):
        result = self.deploy(state={'ready_status': '503'})
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('DEPLOYMENT COMPLETE', result.stdout)

    def test_invalid_timeout_is_rejected_before_changes(self):
        result = self.deploy(args=('--ready-timeout', '0'))
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.event_text(), '')


@unittest.skipUnless(all(shutil.which(x) for x in ('bash', 'git', 'unzip', 'rsync')), 'requires Git and rsync')
class BundleInstallerTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='aita-bundle-test-')
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.repo = self.base / 'repository with spaces'
        self.repo.mkdir()
        self.files = {
            'settings.gradle.kts': b'// fixture settings\n',
            'composeApp/build.gradle.kts': b'// fixture compose\n',
            'shared/build.gradle.kts': b'// fixture shared\n',
            'server/build.gradle.kts': b'// fixture server\n',
            'gradlew': b'#!/usr/bin/env bash\nexit 0\n',
            'scripts/check.sh': b'#!/usr/bin/env bash\nexit 0\n',
            'sample.kt': b'OLD\n',
            'other.kt': b'OLD\n',
            '.gitignore': b'local.properties\n',
        }
        for name, data in self.files.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        self.git('init', '-q')
        self.git('config', 'user.name', 'AITA test')
        self.git('config', 'user.email', 'aita-test@example.invalid')
        self.git('config', 'core.filemode', 'true')
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture baseline')
        self.head = self.git('rev-parse', 'HEAD').stdout.strip()
        self.bundle = self.base / 'full.zip'
        self.env = dict(os.environ, TZ='UTC')

    def git(self, *args):
        result = run(['git', '-C', str(self.repo), *args])
        self.assertEqual(result.returncode, 0, result.stdout)
        return result

    def make_bundle(self, *, changes: dict | None = None, omit: tuple[str, ...] = (),
                    unsafe: bool = False):
        files = {**self.files, **(changes or {})}
        with zipfile.ZipFile(self.bundle, 'w') as archive:
            if unsafe:
                archive.writestr('../escaped-file', 'must not be extracted')
            for name, data in files.items():
                if name in omit:
                    continue
                entry = zipfile.ZipInfo('AITA/' + name, (2026, 9, 7, 12, 0, 0))
                entry.create_system = 3
                entry.external_attr = 0o100644 << 16
                archive.writestr(entry, data)
            if unsafe:
                for i in range(12000):
                    archive.writestr(f'AITA/many/files/{i:06d}.txt', b'fixture')

    def apply(self):
        return run(['bash', str(APPLY), str(self.bundle), str(self.repo)], env=self.env)

    def test_equal_size_equal_timestamp_changes_are_copied(self):
        self.make_bundle(changes={'sample.kt': b'NEW\n'})
        from datetime import datetime, timezone
        stamp = datetime(2026, 9, 7, 12, 0, tzinfo=timezone.utc).timestamp()
        os.utime(self.repo / 'sample.kt', (stamp, stamp))
        result = self.apply()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual((self.repo / 'sample.kt').read_bytes(), b'NEW\n')
        self.assertIn('sample.kt', self.git('status', '--short').stdout)

    def test_lost_executable_bits_do_not_trigger_rollback(self):
        self.make_bundle()
        result = self.apply()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertTrue(os.access(self.repo / 'gradlew', os.X_OK))
        self.assertTrue(os.access(self.repo / 'scripts/check.sh', os.X_OK))
        self.assertNotIn('restoring the previous', result.stdout)

    def test_clears_both_git_flags_for_changed_paths(self):
        self.git('update-index', '--assume-unchanged', 'sample.kt')
        self.git('update-index', '--skip-worktree', 'other.kt')
        self.make_bundle(changes={'sample.kt': b'NEW\n', 'other.kt': b'NEW\n'})
        result = self.apply()
        self.assertEqual(result.returncode, 0, result.stdout)
        status = self.git('status', '--short').stdout
        self.assertIn('sample.kt', status)
        self.assertIn('other.kt', status)
        flags = self.git('ls-files', '-v', 'sample.kt', 'other.kt').stdout
        self.assertTrue(all(line.startswith('H ') for line in flags.splitlines()), flags)

    def test_git_history_local_sdk_and_backup_are_preserved(self):
        local = self.repo / 'local.properties'
        local.write_text('sdk.dir=/private/local/sdk\n')
        self.make_bundle(changes={'sample.kt': b'NEW\n'})
        result = self.apply()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(self.git('rev-parse', 'HEAD').stdout.strip(), self.head)
        self.assertEqual(local.read_text(), 'sdk.dir=/private/local/sdk\n')
        backups = list(self.base.glob('repository with spaces-before-*'))
        self.assertEqual(len(backups), 1)
        self.assertEqual((backups[0] / 'sample.kt').read_bytes(), b'OLD\n')

    def test_obsolete_source_removed_but_visible_in_git(self):
        (self.repo / 'obsolete.kt').write_text('// old\n')
        self.git('add', 'obsolete.kt')
        self.git('commit', '-qm', 'old tracked file')
        self.make_bundle()
        result = self.apply()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertFalse((self.repo / 'obsolete.kt').exists())
        self.assertIn('D obsolete.kt', self.git('status', '--short').stdout)

    def test_partial_bundle_is_rejected_without_changes(self):
        self.make_bundle(omit=('server/build.gradle.kts',))
        result = self.apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((self.repo / 'sample.kt').read_bytes(), b'OLD\n')
        self.assertEqual(self.git('status', '--short').stdout, '')

    def test_unsafe_path_in_large_listing_rejected_before_extraction(self):
        self.make_bundle(unsafe=True)
        result = self.apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('unsafe', result.stdout)
        self.assertFalse(list(self.base.rglob('escaped-file')))
        self.assertEqual(self.git('status', '--short').stdout, '')

    def test_broken_archive_leaves_worktree_untouched(self):
        self.bundle.write_bytes(b'not a ZIP')
        result = self.apply()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.git('status', '--short').stdout, '')


if __name__ == '__main__':
    unittest.main(verbosity=2)
