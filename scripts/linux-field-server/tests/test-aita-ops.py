#!/usr/bin/env python3
"""Offline AITA operator tests. No production service, DB or cloud is accessed.

Real Git, Bash, locks, archives, pipes and hashes are exercised in temp trees.
Service/backup/network control-flow cases use explicit doubles, not live hosts.
"""
from __future__ import annotations
import argparse
from contextlib import redirect_stdout, contextmanager
import fcntl
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import pwd
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
from unittest import mock

SCRIPTS = Path(__file__).resolve().parents[1]

def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module

ops = load_module("aita_ops_under_test", SCRIPTS / "aita-ops.py")
legacy = load_module("aita_deploy_fixtures", SCRIPTS / "tests/test-deployment-scripts.py")


class TempCase(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="aita-ops-test-")
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)

    def file(self, name, data=b"fixture"):
        path = self.base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return path


class ConfigurationTests(TempCase):
    def test_quotes_crlf_and_exports(self):
        path = self.file("settings", b'# comment\r\nA="value with spaces"\r\nexport B=abc\nC=\n')
        self.assertEqual(ops.read_env(path), {"A": "value with spaces", "B": "abc", "C": ""})

    def test_never_executes_substitution(self):
        target = self.base / "MUST_NOT_EXIST"
        path = self.file("settings", f'VALUE=$(touch {target})\n'.encode())
        self.assertEqual(ops.read_env(path)["VALUE"], f'$(touch {target})')
        self.assertFalse(target.exists())

    def test_duplicate_fails_without_secret_value(self):
        path = self.file("settings", b'A=secret_fixture\nA=another_secret\n')
        with self.assertRaises(ops.OpsError) as ctx:
            ops.read_env(path)
        self.assertIn("Duplicate", str(ctx.exception))
        self.assertNotIn("secret_fixture", str(ctx.exception))

    def test_malformed_hides_line_contents(self):
        path = self.file("settings", b'PRIVATE_DATA_NOT_AN_ASSIGNMENT\n')
        with self.assertRaises(ops.OpsError) as ctx:
            ops.read_env(path)
        self.assertNotIn("PRIVATE_DATA", str(ctx.exception))

    def test_missing_file_help(self):
        with self.assertRaisesRegex(ops.OpsError, "permissions"):
            ops.read_env(self.base / "absent")

    def test_production_values_redacted(self):
        with mock.patch.object(ops, "SECRETS", []):
            ops.protect_secrets({"DB_PASS": "fixture_password", "AITA_AUTH_CODE_PEPPER": "fixture_pepper"})
            self.assertEqual(ops.redact("fixture_password / fixture_pepper"), "<redacted> / <redacted>")

    def test_url_credential_and_github_token_redaction(self):
        result = ops.redact("https://user:pw@example.test ghp_FIXTURETOKEN Authorization: Bearer abcd")
        self.assertNotIn("user:pw", result)
        self.assertNotIn("FIXTURETOKEN", result)
        self.assertNotIn("abcd", result)

    def test_clean_build_environment_excludes_secrets(self):
        user = pwd.getpwuid(os.getuid())
        with mock.patch.dict(os.environ, {"DB_PASS": "private", "AITA_RESEND_API_KEY": "private"}):
            env = ops.clean_user_env(user)
            self.assertNotIn("DB_PASS", env)
            self.assertNotIn("AITA_RESEND_API_KEY", env)
            self.assertEqual(env["HOME"], user.pw_dir)

    def test_build_command_drops_privilege_before_env_reset(self):
        user = pwd.getpwuid(os.getuid())
        cmd = ops.user_command(user, ["bash", "build.sh"])
        self.assertEqual(cmd[:6], ["runuser", "-u", user.pw_name, "--", "env", "-i"])
        self.assertEqual(cmd[-2:], ["bash", "build.sh"])

    def test_explicit_service_rclone_config_overrides_login_home(self):
        user = pwd.getpwuid(os.getuid())
        with mock.patch.object(ops.pwd, "getpwnam", return_value=user):
            settings = {"AITA_BACKUP_RCLONE_CONFIG": "/tmp/service.conf", "DB_PASS": "PRIVATE"}
            cmd = ops.rclone_command(settings, "listremotes")
            env = ops.rclone_environment(settings)
        self.assertEqual(env["RCLONE_CONFIG"], "/tmp/service.conf")
        self.assertEqual(env["HOME"], user.pw_dir)
        self.assertNotIn("DB_PASS", env)
        self.assertFalse(any("PRIVATE" in v for v in cmd))

    def test_rclone_credentials_never_enter_process_arguments(self):
        settings = {"RCLONE_CONFIG_PASS": "private_test_pass", "RCLONE_CONFIG_GDRIVE_TOKEN": "private_test_token"}
        with mock.patch.object(ops.pwd, "getpwnam", return_value=pwd.getpwuid(os.getuid())):
            cmd = ops.rclone_command(settings, "listremotes")
            env = ops.rclone_environment(settings)
        self.assertEqual(env["RCLONE_CONFIG_PASS"], "private_test_pass")
        self.assertEqual(env["RCLONE_CONFIG_GDRIVE_TOKEN"], "private_test_token")
        self.assertFalse(any("private_test" in arg for arg in cmd))
        self.assertIn("--preserve-environment", cmd)

    def test_remote_validation(self):
        self.assertEqual(ops.validate_remote("gdrive:AITA/private-db/linux-prod/"), "gdrive:AITA/private-db/linux-prod")
        for value in ("", ":drive,token=secret:/", "https://host", "-option:path", "remote:path\nother"):
            with self.subTest(value=value), self.assertRaises(ops.OpsError):
                ops.validate_remote(value)

    def test_storage_path_does_not_allow_root(self):
        for value in ("/", "/srv/aita", "/srv/aita/../../etc", "relative"):
            with self.subTest(value=value), self.assertRaises(ops.OpsError):
                ops.ensure_storage_path(Path(value))

    def test_remote_required_by_default(self):
        with self.assertRaisesRegex(ops.OpsError, "NOT configured"):
            ops.remote_preflight({}, allow_local=False)

    def test_local_override_only_accepts_empty_remote(self):
        with redirect_stdout(io.StringIO()):
            self.assertEqual(ops.remote_preflight({}, allow_local=True), "")
        with self.assertRaises(ops.OpsError):
            ops.remote_preflight({"AITA_BACKUP_RCLONE_REMOTE": ":bad"}, allow_local=True)

    def test_timeout_range_and_watch_minimum(self):
        for argv in (["update", "--ready-timeout", "0"], ["update", "--ready-timeout", "901"],
                     ["backups", "--interval", "1"]):
            with self.subTest(argv=argv), self.assertRaises(ops.OpsError):
                ops.main(argv)


class ArchiveTests(TempCase):
    def archive(self, name="file.txt", kind=tarfile.REGTYPE, data=b"abc"):
        path = self.base / "snapshot.tar"
        with tarfile.open(path, "w") as archive:
            member = tarfile.TarInfo(name)
            member.type = kind
            member.size = len(data) if kind == tarfile.REGTYPE else 0
            member.linkname = "../../outside"
            archive.addfile(member, io.BytesIO(data) if member.size else None)
        return path

    def test_traversal_absolute_git_and_links_rejected(self):
        for name, kind in (("../escape", tarfile.REGTYPE), ("/etc/escape", tarfile.REGTYPE),
                           (".git/config", tarfile.REGTYPE), ("link", tarfile.SYMTYPE),
                           ("hardlink", tarfile.LNKTYPE), ("pipe", tarfile.FIFOTYPE),
                           ("bad\nname", tarfile.REGTYPE), ("bad\\name", tarfile.REGTYPE)):
            with self.subTest(name=name, kind=kind), tarfile.open(self.archive(name, kind)) as archive:
                with self.assertRaises(ops.OpsError):
                    ops.validate_archive(archive)

    def test_duplicate_entry_rejected(self):
        path = self.archive()
        with tarfile.open(path, "a") as archive:
            member = tarfile.TarInfo("file.txt")
            archive.addfile(member)
        with tarfile.open(path) as archive, self.assertRaises(ops.OpsError):
            ops.validate_archive(archive)

    def test_real_extract_and_hash(self):
        path = self.archive("src/test.txt")
        source = self.base / "source"
        user = pwd.getpwuid(os.getuid())
        # Same uid/gid is legal as a non-root file owner too.
        hashes = ops.extract_source(path, source, user)
        self.assertEqual((source / "src/test.txt").read_bytes(), b"abc")
        self.assertEqual(hashes, {"src/test.txt": hashlib.sha256(b"abc").hexdigest()})
        ops.verify_source(source, hashes)

    def test_changed_or_symlinked_source_rejected(self):
        source = self.base / "source"
        hashes = ops.extract_source(self.archive(), source, pwd.getpwuid(os.getuid()))
        (source / "file.txt").write_bytes(b"xyz")
        with self.assertRaisesRegex(ops.OpsError, "changed"):
            ops.verify_source(source, hashes)
        (source / "file.txt").unlink()
        (source / "file.txt").symlink_to(self.file("elsewhere", b"abc"))
        with self.assertRaises(ops.OpsError):
            ops.verify_source(source, hashes)

    def test_generated_build_files_do_not_change_pinned_source(self):
        source = self.base / "source"
        hashes = ops.extract_source(self.archive(), source, pwd.getpwuid(os.getuid()))
        (source / "build").mkdir()
        (source / "build/generated").write_bytes(b"output")
        ops.verify_source(source, hashes)


@unittest.skipUnless(shutil.which("git"), "requires Git")
class GitGuardTests(TempCase):
    def setUp(self):
        super().setUp()
        self.repo = self.base / "repo with spaces"
        self.repo.mkdir()
        self.git("init", "-b", "master")
        self.git("config", "user.name", "fixture")
        self.git("config", "user.email", "fixture@example.test")
        (self.repo / "file.txt").write_text("first\n")
        self.git("add", ".")
        self.git("commit", "-m", "fixture")

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.repo), *args], check=True, capture_output=True)

    def test_clean_checkout_is_accepted(self):
        ops.check_worktree(self.repo, "master")

    def test_wrong_branch_is_rejected(self):
        with self.assertRaisesRegex(ops.OpsError, "must be on"):
            ops.check_worktree(self.repo, "main")

    def test_untracked_file_blocks_without_deletion(self):
        path = self.repo / "private-local-work"
        path.write_text("keep")
        with self.assertRaisesRegex(ops.OpsError, "local changes"):
            ops.check_worktree(self.repo, "master")
        self.assertEqual(path.read_text(), "keep")

    def test_modified_file_blocks_without_reset(self):
        (self.repo / "file.txt").write_text("my changes")
        with self.assertRaises(ops.OpsError):
            ops.check_worktree(self.repo, "master")
        self.assertEqual((self.repo / "file.txt").read_text(), "my changes")

    def test_assume_unchanged_is_not_ignored(self):
        self.git("update-index", "--assume-unchanged", "file.txt")
        with self.assertRaisesRegex(ops.OpsError, "flags"):
            ops.check_worktree(self.repo, "master")

    def test_skip_worktree_is_not_ignored(self):
        self.git("update-index", "--skip-worktree", "file.txt")
        with self.assertRaisesRegex(ops.OpsError, "flags"):
            ops.check_worktree(self.repo, "master")

    def test_subdirectory_cannot_be_mistaken_for_repo(self):
        path = self.repo / "sub"
        path.mkdir()
        with self.assertRaisesRegex(ops.OpsError, "root"):
            ops.check_worktree(path, "master")


class LockAndProcessTests(TempCase):
    def test_shared_check_cannot_enter_during_backup(self):
        lock = self.file("backup.lock", b"")
        with ops.file_lock(lock):
            with self.assertRaises(ops.OpsError):
                with ops.file_lock(lock, shared=True, create=False):
                    self.fail("overlap")
        with ops.file_lock(lock, shared=True, create=False):
            pass

    def test_lock_does_not_follow_symlink(self):
        target = self.file("target", b"keep")
        link = self.base / "link"
        link.symlink_to(target)
        with self.assertRaises(OSError):
            with ops.file_lock(link):
                pass
        self.assertEqual(target.read_bytes(), b"keep")

    def test_capture_timeout(self):
        with self.assertRaisesRegex(ops.OpsError, "within 1s"):
            ops.capture([sys.executable, "-c", "import time; time.sleep(20)"], timeout=1)

    def test_stream_redacts_and_returns_failure(self):
        out = io.StringIO()
        with mock.patch.object(ops, "SECRETS", ["private_fixture"]), redirect_stdout(out):
            with self.assertRaises(ops.OpsError):
                ops.stream_command([sys.executable, "-c", "print('private_fixture'); raise SystemExit(3)"])
        self.assertNotIn("private_fixture", out.getvalue())
        self.assertIn("<redacted>", out.getvalue())

    def test_large_no_newline_output_bounded_and_preserved(self):
        out = io.StringIO()
        with redirect_stdout(out):
            ops.stream_command([sys.executable, "-c", "import sys; sys.stdout.write('x'*200000)"])
        self.assertEqual(out.getvalue().count("x"), 200000)


class CloudVerificationTests(TempCase):
    def setUp(self):
        super().setUp()
        self.local = self.file("aita_latest.dump.age", b"age-encryption.org/v1\nfixture encrypted data")
        self.md5 = hashlib.md5(self.local.read_bytes()).hexdigest()
        patcher = mock.patch.object(ops, "rclone_environment", return_value=ops.clean_user_env(pwd.getpwuid(os.getuid())))
        patcher.start()
        self.addCleanup(patcher.stop)

    def verify(self, objects, **kwargs):
        result = subprocess.CompletedProcess([], 0, json.dumps(objects), "")
        with mock.patch.object(ops, "rclone_command", return_value=["fake-rclone"]), mock.patch.object(ops, "capture", return_value=result):
            return ops.verify_remote_file({}, self.local, "gdrive:fixture", **kwargs)

    def obj(self, **overrides):
        return dict(Name=self.local.name, Size=self.local.stat().st_size, Hashes={"md5": self.md5}, **overrides)

    def test_metadata_hash_success(self):
        self.assertIn("MD5", self.verify([self.obj()]))

    def test_duplicate_name_rejected(self):
        with self.assertRaisesRegex(ops.OpsError, "duplicate"):
            self.verify([self.obj(), self.obj()])

    def test_old_test_file_is_not_accepted(self):
        with self.assertRaisesRegex(ops.OpsError, "test file"):
            self.verify([{"Name": "aita-rclone-test.txt", "Size": 5}])

    def test_wrong_size_rejected(self):
        item = self.obj(); item["Size"] += 1
        with self.assertRaisesRegex(ops.OpsError, "size"):
            self.verify([item])

    def test_wrong_hash_rejected(self):
        item = self.obj(); item["Hashes"] = {"md5": "f" * 32}
        with self.assertRaisesRegex(ops.OpsError, "checksums"):
            self.verify([item])

    def test_size_alone_is_not_success(self):
        item = self.obj(); item["Hashes"] = {}
        with self.assertRaisesRegex(ops.OpsError, "no comparable"):
            self.verify([item])

    def test_download_bytes_match(self):
        item = self.obj(); item["Hashes"] = {}
        with mock.patch.object(ops, "download_hash", return_value=(self.local.stat().st_size, ops.sha256(self.local))):
            self.assertIn("Downloaded", self.verify([item], download=True))

    def test_download_mismatch_rejected(self):
        with mock.patch.object(ops, "download_hash", return_value=(self.local.stat().st_size, "a" * 64)):
            with self.assertRaisesRegex(ops.OpsError, "do NOT match"):
                self.verify([self.obj()], download=True)

    def test_real_pipe_download_hash(self):
        content = b"age-encryption.org/v1\n" + b"data" * 100000
        script = self.file("cat.py", b"import sys\nsys.stdout.buffer.write(" + repr(content).encode() + b")\n")
        with mock.patch.object(ops, "rclone_command", return_value=[sys.executable, str(script)]):
            size, digest = ops.download_hash({}, "gdrive:fixture", timeout=5)
        self.assertEqual(size, len(content))
        self.assertEqual(digest, hashlib.sha256(content).hexdigest())

    def test_download_failure_never_success(self):
        with mock.patch.object(ops, "rclone_command", return_value=[sys.executable, "-c", "raise SystemExit(1)"]):
            with self.assertRaisesRegex(ops.OpsError, "download failed"):
                ops.download_hash({}, "gdrive:fixture", timeout=5)

    def test_download_timeout(self):
        with mock.patch.object(ops, "rclone_command", return_value=[sys.executable, "-c", "import time; time.sleep(30)"]):
            with self.assertRaisesRegex(ops.OpsError, "timed out"):
                ops.download_hash({}, "gdrive:fixture", timeout=1)

    def test_stale_fresh_future_backup_classification(self):
        now = time.time()
        os.utime(self.local, (now, now))
        self.assertEqual(ops.freshness(self.local, 900, now).level, "PASS")
        self.assertEqual(ops.freshness(self.local, 900, now + 901).level, "FAIL")
        self.assertEqual(ops.freshness(self.local, 900, now - 120).level, "FAIL")
        self.local.write_bytes(b"")
        self.assertEqual(ops.freshness(self.local, 900, now).level, "FAIL")


class AdviceTests(unittest.TestCase):
    def test_compilation_is_distinct_from_migration(self):
        self.assertIn("does not compile", ops.fault_advice("Unresolved reference", "build"))
        self.assertIn("Do not repair", ops.fault_advice("FlywayValidateException", "restart"))

    def test_readiness_timeout_does_not_suggest_blind_retry(self):
        self.assertIn("do not start another", ops.fault_advice("readiness deadline", "restart"))

    def test_backup_failure_stops_before_restart(self):
        self.assertIn("before restarting", ops.fault_advice("failed", "backup"))

    def test_cloud_configuration_is_service_user_specific(self):
        self.assertIn("aita service account", ops.fault_advice("invalid_grant", "backup"))


@unittest.skipUnless(shutil.which("bash") and shutil.which("flock") and shutil.which("unzip"), "Linux deployment fixtures required")
class InheritedDeployLockTests(unittest.TestCase):
    def setUp(self):
        self.fx = legacy.DeployControlFlowTests()
        self.fx.setUp()
        self.addCleanup(self.fx.doCleanups)

    def test_valid_inherited_descriptor_is_not_relocked_via_new_fd(self):
        state = Path(self.fx.env["XDG_STATE_HOME"]) / "aita"
        state.mkdir(parents=True)
        with ops.file_lock(state / "deploy.lock") as fd:
            env = dict(self.fx.env, AITA_DEPLOY_LOCK_FD=str(fd))
            result = subprocess.run(["bash", str(self.fx.script), "--project-root", str(self.fx.project), "--verify-only"],
                                    env=env, pass_fds=(fd,), capture_output=True, text=True, timeout=10)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_unrelated_descriptor_is_rejected(self):
        state = Path(self.fx.env["XDG_STATE_HOME"]) / "aita"
        state.mkdir(parents=True)
        (state / "deploy.lock").touch()
        with ops.file_lock(state / "other.lock") as fd:
            env = dict(self.fx.env, AITA_DEPLOY_LOCK_FD=str(fd))
            result = subprocess.run(["bash", str(self.fx.script), "--project-root", str(self.fx.project), "--verify-only"],
                                    env=env, pass_fds=(fd,), capture_output=True, text=True, timeout=10)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not match", result.stderr)
        self.assertEqual(self.fx.current.read_bytes(), self.fx.old)

    def test_same_candidate_does_not_overwrite_previous_release(self):
        self.fx.current.write_bytes(self.fx.jar.read_bytes())
        previous = self.fx.app / "aita-server-all.previous.jar"
        previous.write_bytes(b"older good release")
        result = self.fx.deploy()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(previous.read_bytes(), b"older good release")


BACKUP_DOUBLE = r'''import os, pathlib, sys
name = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
mode = os.environ.get('FIXTURE_MODE', '')
if name == 'pg_dump':
    if mode == 'dump-fails': raise SystemExit(1)
    pathlib.Path(args[args.index('--file') + 1]).write_bytes(b'PGDMP fixture data')
elif name == 'pg_restore':
    if mode == 'invalid-dump': raise SystemExit(1)
elif name == 'age':
    if mode == 'age-fails': raise SystemExit(1)
    pathlib.Path(args[args.index('--output') + 1]).write_bytes(b'age-encryption.org/v1\nfixture, not real encryption')
elif name == 'rclone':
    pathlib.Path(os.environ['FIXTURE_EVENTS']).write_text('rclone:' + repr(args) + '\n' + os.environ.get('RCLONE_CONFIG', ''))
    if mode == 'cloud-fails': raise SystemExit(1)
else: raise SystemExit('unexpected fixture tool')
'''


class BackupShellTests(TempCase):
    def setUp(self):
        super().setUp()
        self.bin = self.base / "bin"
        self.bin.mkdir()
        for name in ("pg_dump", "pg_restore", "age", "rclone"):
            path = self.bin / name
            path.write_text(f"#!{sys.executable}\n" + BACKUP_DOUBLE)
            path.chmod(0o755)
        self.envfile = self.file("production.env", ("\n".join([
            "AITA_ENV=production", "AITA_JWT_SECRET=" + "a" * 70,
            "AITA_REFRESH_PEPPER=" + "b" * 70, "AITA_JWT_ISSUER=fixture", "AITA_JWT_AUDIENCE=fixture",
            "AITA_PUBLIC_SERVER_URL=https://example.test", "AITA_DB_URL=jdbc:postgresql://127.0.0.1:5432/fixture",
            "DB_USER=fixture", "DB_PASS=fixture_password", "AITA_BACKUP_AGE_RECIPIENT=age1fixture",
            "AITA_BACKUP_DIR=" + str(self.base / "backups"),
            "AITA_BACKUP_LOCK_FILE=" + str(self.base / "backup.lock"),
            "AITA_BACKUP_LOCK_WAIT_SECONDS=0",
            "AITA_BACKUP_RCLONE_CONFIG=" + str(self.base / "service-rclone.conf"),
        ]) + "\n").encode())

    def run_backup(self, mode="", remote="", daily=False):
        with self.envfile.open("a") as f:
            f.write("AITA_BACKUP_RCLONE_REMOTE=" + remote + "\n")
        env = dict(os.environ, PATH=str(self.bin) + ":" + os.environ["PATH"], FIXTURE_MODE=mode,
                   FIXTURE_EVENTS=str(self.base / "events"))
        return subprocess.run(["bash", str(SCRIPTS / "backup-aita-postgres.sh"), "--env-file", str(self.envfile)]
                              + (["--daily"] if daily else []), env=env, capture_output=True, text=True, timeout=15)

    def test_local_only_is_explicit_not_cloud_success(self):
        result = self.run_backup()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("LOCAL ONLY", result.stdout)
        self.assertNotIn("Cloud upload command succeeded", result.stdout)
        self.assertTrue((self.base / "backups/aita_latest.dump.age").is_file())
        self.assertFalse(list((self.base / "backups/.tmp").iterdir()))

    def test_success_uses_explicit_service_config(self):
        result = self.run_backup(remote="gdrive:fixture")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Cloud upload command succeeded", result.stdout)
        self.assertIn("service-rclone.conf", (self.base / "events").read_text())

    def test_cloud_failure_retains_encrypted_local_only(self):
        result = self.run_backup(mode="cloud-fails", remote="gdrive:fixture")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("LOCAL backup is preserved", result.stderr)
        self.assertTrue((self.base / "backups/aita_latest.dump.age").exists())
        self.assertFalse(list((self.base / "backups/.tmp").iterdir()))

    def test_dump_failure_does_not_publish(self):
        result = self.run_backup(mode="dump-fails")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.base / "backups/aita_latest.dump.age").exists())

    def test_bad_dump_list_does_not_publish(self):
        result = self.run_backup(mode="invalid-dump")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.base / "backups/aita_latest.dump.age").exists())

    def test_encryption_failure_cleans_plaintext(self):
        result = self.run_backup(mode="age-fails")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(list((self.base / "backups/.tmp").iterdir()))
        self.assertFalse((self.base / "backups/aita_latest.dump.age").exists())

    def test_daily_archive_name(self):
        result = self.run_backup(remote="gdrive:fixture", daily=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(list((self.base / "backups/daily").glob("aita_*.dump.age"))), 1)
        self.assertFalse((self.base / "backups/aita_latest.dump.age").exists())

    def test_lock_collision_never_claims_backup_created(self):
        with ops.file_lock(self.base / "backup.lock"):
            result = self.run_backup()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no new backup was created", result.stderr)
        self.assertNotIn("Backup completed", result.stdout)


class BackupDoctorTests(TempCase):
    def setUp(self):
        super().setUp()
        self.directory = self.base / "backups"
        self.directory.mkdir()
        self.local = self.file("backups/aita_latest.dump.age", b"age-encryption.org/v1\nfixture")
        self.daily = self.file("backups/daily/aita_20260908T000000Z.dump.age", self.local.read_bytes())
        self.lock = self.file("backup.lock", b"")
        self.env = {"AITA_BACKUP_DIR": str(self.directory), "AITA_BACKUP_LOCK_FILE": str(self.lock),
                    "AITA_BACKUP_RCLONE_REMOTE": "gdrive:fixture", "AITA_BACKUP_RCLONE_CONFIG": "/var/lib/aita/.config/rclone/rclone.conf"}
        self.addCleanup(mock.patch.stopall)
        mock.patch.object(ops, "read_env", return_value=self.env).start()
        mock.patch.object(ops, "ensure_storage_path", side_effect=lambda p: p).start()
        mock.patch.object(ops, "require_tools").start()
        mock.patch.object(ops.pwd, "getpwnam", return_value=pwd.getpwuid(os.getuid())).start()
        mock.patch.object(ops, "properties", side_effect=lambda unit, *n: {
            "ActiveState": "active", "UnitFileState": "enabled", "User": "aita", "Result": "success", "ExecMainStatus": "0"
        } if unit.endswith(".timer") else {
            "ActiveState": "inactive", "User": "aita", "Result": "success", "ExecMainStatus": "0"}).start()
        self.verify = mock.patch.object(ops, "verify_remote_file", return_value="hash match").start()

    def test_both_current_and_daily_verified_not_restored(self):
        findings = ops.backup_snapshot()
        self.assertFalse([f for f in findings if f.level == "FAIL"])
        self.assertEqual(self.verify.call_count, 2)
        self.assertTrue(any(f.name == "Recoverability" and f.level == "NOT TESTED" for f in findings))

    def test_blank_remote_reports_local_only_problem(self):
        self.env["AITA_BACKUP_RCLONE_REMOTE"] = ""
        findings = ops.backup_snapshot()
        self.assertTrue(any(f.name == "Offsite" and f.level == "FAIL" for f in findings))
        self.verify.assert_not_called()

    def test_busy_writer_is_wait_not_hash_failure(self):
        with ops.file_lock(self.lock):
            findings = ops.backup_snapshot()
        self.assertTrue(any(f.level == "WAIT" and f.name == "Cloud verification" for f in findings))
        self.verify.assert_not_called()

    def test_failed_upload_is_not_hidden_by_fresh_local_file(self):
        self.verify.side_effect = ops.OpsError("upload checksum mismatch")
        findings = ops.backup_snapshot()
        self.assertTrue(any(f.level == "FAIL" and f.name == "Cloud verification" for f in findings))

    def test_plaintext_disguised_as_age_rejected(self):
        self.local.write_bytes(b"PGDMP plain text masquerading as .age")
        findings = ops.backup_snapshot()
        self.assertTrue(any(f.level == "FAIL" and "no age header" in f.detail for f in findings))
        self.verify.assert_not_called()

    def test_home_config_cannot_be_reported_fully_healthy(self):
        self.env["AITA_BACKUP_RCLONE_CONFIG"] = "/home/bogdan/.config/rclone/rclone.conf"
        findings = ops.backup_snapshot()
        self.assertTrue(any(f.level == "FAIL" and f.name == "Service sandbox" for f in findings))


class WorkerControlFlowTests(TempCase):
    """Entire orchestrator order with all external service/build effects replaced."""
    def setUp(self):
        super().setUp()
        self.root = self.base / "ops"
        self.run = self.root / "runs/20260908T000000Z-123456abcdef"
        self.source = self.run / "source"
        self.source.mkdir(parents=True)
        (self.run / "tools").mkdir()
        (self.source / "main.txt").write_text("pinned")
        (self.source / "server/assets").mkdir(parents=True)
        self.home = self.base / "home"
        self.home.mkdir()
        self.owner = pwd.struct_passwd(("fixture_owner", "x", 12345, os.getgid(), "", str(self.home), "/bin/bash"))
        self.jar = self.file("installed/aita-server-all.jar", b"old artifact")
        self.envfile = self.file("prod.env", b"DB_PASS=fixture_password\n")
        self.lock = self.file("backup.lock", b"")
        self.env = {"AITA_HOST": "127.0.0.1", "AITA_PORT": "8080",
                    "AITA_BACKUP_DIR": str(self.base / "backups"), "AITA_BACKUP_LOCK_FILE": str(self.lock),
                    "AITA_PUBLIC_SERVER_URL": "https://example.test", "AITA_ASSETS_ROOT": str(self.base / "assets"),
                    "DB_PASS": "fixture_password"}
        self.meta = {"owner": self.owner.pw_name, "gid": self.owner.pw_gid, "commit": "a" * 40,
                     "java_home": "/fixture/jdk", "ready_timeout": 600, "with_tests": False,
                     "force": False, "allow_local_backup": False,
                     "source_hashes": {"main.txt": ops.sha256(self.source / "main.txt")}}
        (self.run / "meta.json").write_text(json.dumps(self.meta))
        (self.run / "update.log").write_text("")
        self.events = []
        self.fail_on = None
        self.mutate_after_build = False
        self.http_fail_public = False
        self.http_fail_local = False
        self.addCleanup(mock.patch.stopall)
        mock.patch.object(ops, "ROOT", self.root).start()
        mock.patch.object(ops, "CURRENT_JAR", self.jar).start()
        mock.patch.object(ops, "ENV_FILE", self.envfile).start()
        mock.patch.object(ops.os, "geteuid", return_value=0).start()
        mock.patch.object(ops.os, "chown").start()
        mock.patch.object(ops.os, "fchown").start()
        mock.patch.object(ops.pwd, "getpwnam", return_value=self.owner).start()
        mock.patch.object(ops, "require_tools").start()
        mock.patch.object(ops, "read_env", return_value=self.env).start()
        mock.patch.object(ops, "protect_secrets").start()
        mock.patch.object(ops, "properties", return_value={"User": "aita", "Group": "aita", "ActiveState": "active"}).start()
        mock.patch.object(ops, "capture", return_value=subprocess.CompletedProcess([], 0, "", 'openjdk version "21.0.12"')).start()
        mock.patch.object(ops.shutil, "disk_usage", return_value=shutil._ntuple_diskusage(20*1024**3, 0, 20*1024**3)).start()
        mock.patch.object(ops, "remote_preflight", return_value="gdrive:fixture").start()
        self.backup_findings = mock.patch.object(ops, "backup_snapshot", return_value=[]).start()
        mock.patch.object(ops, "ensure_storage_path", side_effect=lambda p: p).start()
        mock.patch.object(ops, "http_status", side_effect=lambda url, **kw:
                          "503" if (kw.get("local") and self.http_fail_local) or
                          (url.startswith("https") and self.http_fail_public) else "200").start()
        mock.patch.object(ops, "sync_helpers", side_effect=lambda *a: self.events.append("helpers")).start()
        mock.patch.object(ops, "preserve_predeploy_backup", side_effect=lambda *a: self.events.append("preserved-backup")).start()
        mock.patch.object(ops, "stream_command", side_effect=self.stream).start()

    def stream(self, argv, **kwargs):
        argv = list(map(str, argv))
        if any("test-aita-readiness.sh" in a for a in argv): kind = "preflight"
        elif any("test-deployment-scripts.py" in a or "test-aita-ops.py" in a for a in argv): kind = "tests"
        elif any("build-aita-server.sh" in a for a in argv): kind = "build"
        elif argv[:2] == ["systemctl", "start"]: kind = "backup"
        elif argv[0] == "rsync": kind = "assets"
        elif any("deploy-aita-server.sh" in a for a in argv): kind = "restart"
        else: raise AssertionError(argv)
        self.events.append(kind)
        if kind == "build":
            self.assertEqual(argv[:4], ["runuser", "-u", self.owner.pw_name, "--"])
            self.assertNotIn("fixture_password", str(argv))
            if self.mutate_after_build:
                (self.source / "main.txt").write_text("oops changed")
        if kind == "restart":
            fd = kwargs["pass_fds"][0]
            self.assertEqual(str(fd), kwargs["env"]["AITA_DEPLOY_LOCK_FD"])
            # Parent still owns the scheduled-backup lock while the JAR switches.
            with self.assertRaises(ops.OpsError):
                with ops.file_lock(self.lock): pass
            self.jar.write_bytes(b"new artifact")
        if kind == self.fail_on:
            raise ops.OpsError("injected " + kind + " failure")

    def work(self):
        out = io.StringIO()
        with redirect_stdout(out):
            code = ops.worker(argparse.Namespace(run_dir=str(self.run)))
        self.output = out.getvalue()
        self.state = json.loads((self.run / "state.json").read_text())
        return code

    def test_success_order_and_commit_record(self):
        self.assertEqual(self.work(), 0, self.output)
        self.assertEqual(self.events, ["preflight", "tests", "tests", "build", "helpers", "backup", "preserved-backup", "assets", "restart"])
        self.assertEqual(self.state["result"], "success")
        self.assertTrue(self.state["server_touched"])
        self.assertFalse(self.source.exists())
        record = json.loads((self.root / "last-success.json").read_text())
        self.assertEqual(record["commit"], "a" * 40)
        self.assertEqual(record["jar_sha256"], ops.sha256(self.jar))

    def test_build_failure_never_stops_or_backs_up(self):
        self.fail_on = "build"
        self.assertEqual(self.work(), 1)
        self.assertNotIn("restart", self.events)
        self.assertNotIn("backup", self.events)
        self.assertFalse(self.state["server_touched"])
        self.assertEqual(self.jar.read_bytes(), b"old artifact")
        self.assertTrue(self.source.exists())

    def test_test_failure_never_builds(self):
        self.fail_on = "tests"
        self.assertEqual(self.work(), 1)
        self.assertNotIn("build", self.events)
        self.assertEqual(self.jar.read_bytes(), b"old artifact")

    def test_backup_failure_never_stops(self):
        self.fail_on = "backup"
        self.assertEqual(self.work(), 1)
        self.assertNotIn("restart", self.events)
        self.assertIn("before restarting", self.output)
        self.assertEqual(self.jar.read_bytes(), b"old artifact")

    def test_changed_snapshot_never_installs(self):
        self.mutate_after_build = True
        self.assertEqual(self.work(), 1)
        self.assertNotIn("restart", self.events)
        self.assertIn("Pinned source changed", self.output)

    def test_readiness_failure_no_automatic_rollback(self):
        self.fail_on = "restart"
        self.assertEqual(self.work(), 1)
        self.assertTrue(self.state["server_touched"])
        self.assertFalse((self.root / "last-success.json").exists())
        self.assertIn("No automatic", self.output)
        self.assertEqual(self.jar.read_bytes(), b"new artifact")

    def test_public_failure_is_warning_not_false_success_or_restart(self):
        self.http_fail_public = True
        self.assertEqual(self.work(), 2)
        self.assertEqual(self.state["result"], "warning")
        self.assertEqual(self.events.count("restart"), 1)
        self.assertTrue((self.root / "last-success.json").exists())

    def test_backup_warning_does_not_undo_successful_release(self):
        self.backup_findings.return_value = [ops.Finding("FAIL", "Offsite", "missing")]
        self.assertEqual(self.work(), 2)
        self.assertEqual(self.state["result"], "warning")
        self.assertEqual(self.jar.read_bytes(), b"new artifact")
        self.assertTrue((self.root / "last-success.json").exists())

    def test_existing_unready_service_is_not_interrupted(self):
        self.http_fail_local = True
        self.assertEqual(self.work(), 1)
        self.assertNotIn("build", self.events)
        self.assertNotIn("restart", self.events)
        self.assertIn("may still be migrating", self.output)

    def test_same_commit_hash_and_env_skip_restart(self):
        (self.root / "last-success.json").write_text(json.dumps({"commit": self.meta["commit"],
            "jar_sha256": ops.sha256(self.jar), "env_sha256": ops.sha256(self.envfile)}))
        self.assertEqual(self.work(), 0, self.output)
        self.assertEqual(self.events, ["preflight"])
        self.assertIn("UNCHANGED", self.output)

    def test_changed_environment_does_not_skip_restart(self):
        (self.root / "last-success.json").write_text(json.dumps({"commit": self.meta["commit"],
            "jar_sha256": ops.sha256(self.jar), "env_sha256": "different"}))
        self.assertEqual(self.work(), 0, self.output)
        self.assertIn("restart", self.events)

    def test_parallel_legacy_deployer_blocks_managed_update(self):
        state_dir = self.home / ".local/state/aita"
        state_dir.mkdir(parents=True)
        with ops.file_lock(state_dir / "deploy.lock"):
            self.assertEqual(self.work(), 1)
        self.assertEqual(self.events, [])
        self.assertEqual(self.jar.read_bytes(), b"old artifact")



class ManagedLaunchTests(TempCase):
    """Actual snapshot/files/permissions; systemd and uid changes are explicit doubles."""
    def setUp(self):
        super().setUp()
        self.root = self.base / "operator-state"
        self.archive = self.base / "source.tar"
        with tarfile.open(self.archive, "w") as tar:
            for name, content in {
                "scripts/linux-field-server/aita-ops.py": b"# trusted test operator\n",
                "scripts/linux-field-server/build-aita-server.sh": b"#!/bin/bash\n",
                "server/src/main/resources/application.yaml": b"ktor: {}\n",
            }.items():
                info = tarfile.TarInfo(name)
                info.size = len(content)
                info.mode = 0o644
                tar.addfile(info, io.BytesIO(content))
        self.owner = pwd.struct_passwd(("fixture-builder", "x", 12345, os.getgid(), "fixture", str(self.base / "home"), "/bin/bash"))
        self.args = argparse.Namespace(archive=str(self.archive), digest=ops.sha256(self.archive),
            owner=self.owner.pw_name, commit="a" * 40, java_home="/fixture/java21",
            ready_timeout=600, with_tests=False, allow_local_backup=False, force=False)
        self.commands = []
        def fake_capture(argv, **kwargs):
            self.commands.append(list(argv))
            return subprocess.CompletedProcess(argv, 0, "", "")
        self.addCleanup(mock.patch.stopall)
        mock.patch.object(ops, "ROOT", self.root).start()
        mock.patch.object(ops.os, "geteuid", return_value=0).start()
        mock.patch.object(ops.os, "chown").start()
        mock.patch.object(ops.pwd, "getpwnam", return_value=self.owner).start()
        self.busy = mock.patch.object(ops, "unit_busy", return_value=False).start()
        self.capture = mock.patch.object(ops, "capture", side_effect=fake_capture).start()

    def launch(self):
        with redirect_stdout(io.StringIO()):
            return ops.start_job(self.args)

    def test_launch_is_detached_exec_not_tty_bound(self):
        self.assertEqual(self.launch(), 0)
        self.assertEqual(len(self.commands), 1)
        cmd = self.commands[0]
        self.assertEqual(cmd[0], "systemd-run")
        for flag in ("--collect", "--service-type=exec", "--expand-environment=no", "--property=Nice=10"):
            self.assertIn(flag, cmd)
        for flag in ("--pipe", "--pty", "--scope", "--wait"):
            self.assertNotIn(flag, cmd)
        run = ops.latest_run()
        self.assertIsNotNone(run)
        self.assertFalse((run / "source.tar").exists())
        self.assertEqual((run / "meta.json").stat().st_mode & 0o777, 0o600)
        self.assertEqual((run / "update.log").stat().st_mode & 0o777, 0o640)
        meta = json.loads((run / "meta.json").read_text())
        self.assertEqual(meta["commit"], "a" * 40)
        self.assertEqual(meta["owner"], self.owner.pw_name)
        ops.verify_source(run / "source", meta["source_hashes"])
        self.assertEqual((run / "tools/aita-ops.py").read_bytes(), (run / "source/scripts/linux-field-server/aita-ops.py").read_bytes())

    def test_changed_archive_never_starts_job(self):
        self.args.digest = "0" * 64
        with self.assertRaisesRegex(ops.OpsError, "snapshot changed"):
            self.launch()
        self.assertFalse(self.commands)
        self.assertFalse((self.root / "latest.json").exists())

    def test_second_job_never_overwrites_active_run(self):
        self.busy.return_value = True
        with self.assertRaisesRegex(ops.OpsError, "Another managed"):
            self.launch()
        self.assertFalse(self.commands)
        self.assertFalse((self.root / "latest.json").exists())

    def test_launch_rejection_records_failure_not_success(self):
        self.capture.side_effect = None
        self.capture.return_value = subprocess.CompletedProcess([], 1, "", "unit launch rejected")
        with self.assertRaisesRegex(ops.OpsError, "could not start"):
            self.launch()
        state = json.loads((ops.latest_run() / "state.json").read_text())
        self.assertEqual(state["result"], "failed")
        self.assertFalse(state["server_touched"])

    def test_late_confirmation_keeps_active_job_and_log(self):
        self.capture.side_effect = ops.OpsError("timed out")
        self.busy.side_effect = [False, True]
        self.assertEqual(self.launch(), 0)
        self.assertTrue((ops.latest_run() / "update.log").exists())
        self.assertEqual(json.loads((ops.latest_run() / "state.json").read_text())["result"], "starting")

    def test_build_as_root_is_rejected_before_staging(self):
        mock.patch.object(ops.pwd, "getpwnam", return_value=pwd.struct_passwd(("root", "x", 0, 0, "", "/root", "/bin/bash"))).start()
        with self.assertRaisesRegex(ops.OpsError, "Refusing to build as root"):
            self.launch()
        self.assertFalse(self.commands)
        self.assertFalse(self.root.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
