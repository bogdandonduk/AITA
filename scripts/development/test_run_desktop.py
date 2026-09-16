"""Source-snapshot safety and wrapper process tests; these do not compile or launch AITA."""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("aita_frozen_run", Path(__file__).with_name("run-desktop.py"))
RUN = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RUN)


class DesktopSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.parent = Path(self.temp.name).resolve()
        self.root = self.parent / "project"
        self.runs = self.parent / "runs"
        for name in RUN.REQUIRED:
            path = self.root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text("source fixture")
        (self.root / "gradlew").write_text("printf 'fixture only\\n'; exit 7\n")

    def execute(self, **kwargs):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return RUN.execute(self.root, self.runs, **kwargs)

    def test_dry_run_creates_nothing(self):
        self.assertEqual(0, self.execute(dry_run=True))
        self.assertFalse(self.runs.exists())

    def test_missing_new_profile_source_stops_before_snapshot(self):
        (self.root / RUN.REQUIRED[-1]).unlink()
        with self.assertRaises(RUN.SnapshotError): self.execute()
        self.assertFalse(self.runs.exists())

    def test_untracked_source_is_included_not_only_last_commit(self):
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-C", str(self.root), "add", "settings.gradle.kts"], check=True)
        before = (self.root / ".git/index").read_bytes()
        paths = RUN.source_paths(self.root)
        self.assertIn("composeApp/src/commonMain/kotlin/kz/aita/UserProfilePhoto.kt", paths)
        self.assertEqual(before, (self.root / ".git/index").read_bytes())

    def test_live_classpath_and_secrets_are_not_copied(self):
        for name in ["shared/build/libs/shared-jvm.jar", ".env", "secret.p12", ".gradle/foo", ".idea/workspace.xml"]:
            path = self.root / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text("private")
        paths = RUN.source_paths(self.root)
        self.assertFalse(any(name in paths for name in [".env", "secret.p12", "shared/build/libs/shared-jvm.jar"]))
        (self.root / "local.properties").write_text("sdk.dir=/some/sdk\nSECRET=not-for-the-snapshot\n")
        target = self.parent / "frozen"
        RUN.copy_snapshot(self.root, target, paths)
        self.assertEqual("sdk.dir=/some/sdk\n", (target / "local.properties").read_text())

    def test_snapshot_is_independent_of_later_main_checkout_builds(self):
        target = self.parent / "frozen"
        RUN.copy_snapshot(self.root, target, RUN.source_paths(self.root))
        (self.root / "settings.gradle.kts").write_text("later edits")
        self.assertEqual("source fixture", (target / "settings.gradle.kts").read_text())

    def test_source_change_during_copy_refuses_to_launch(self):
        copy = RUN.shutil.copy2
        def racing(source, target):
            value = copy(source, target)
            if source.name == "gradlew": (self.root / "settings.gradle.kts").write_text("raced")
            return value
        with patch.object(RUN.shutil, "copy2", side_effect=racing):
            with self.assertRaises(RUN.SnapshotError): RUN.copy_snapshot(self.root, self.parent / "frozen", RUN.source_paths(self.root))

    def test_symlinked_source_is_not_followed(self):
        secret = self.parent / "outside.kt"; secret.write_text("private")
        path = self.root / RUN.REQUIRED[-1]; path.unlink(); path.symlink_to(secret)
        with self.assertRaises(RUN.SnapshotError): self.execute(dry_run=True)

    def test_run_root_cannot_be_inside_checkout(self):
        self.runs = self.root / "runs"
        with self.assertRaises(RUN.SnapshotError): self.execute()
        self.assertFalse(self.runs.exists())

    def test_wrapper_failure_is_propagated_not_success(self):
        self.assertEqual(7, self.execute(verify=True))
        snapshots = list(self.runs.glob("*/AITA")); self.assertEqual(1, len(snapshots))
        self.assertIn("fixture only", snapshots[0].parent.joinpath("gradle.log").read_text())
        self.assertEqual("source fixture", (self.root / "settings.gradle.kts").read_text())

    def test_native_run_is_not_a_package_install_or_publish(self):
        command = RUN.gradle_command(packaged=True)
        self.assertIn(":composeApp:runDistributable", command)
        self.assertFalse(any(x in " ".join(command) for x in ["packageDmg", "publish", "installDist", "sudo"]))
        command = RUN.gradle_command(verify=True)
        self.assertIn(":server:compileKotlin", command)
        self.assertNotIn(":composeApp:run", command)


if __name__ == "__main__": unittest.main()
