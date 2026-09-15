"""Safety tests for the rebuild helper, plus source integration regression contracts.

Fake wrappers below exercise process exit handling ONLY, not Kotlin compilation.
The linkage tests in Kotlin must be executed by the real Gradle build.
"""
from contextlib import redirect_stdout, redirect_stderr
import argparse
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("aita_build_repair", ROOT / "scripts/repository/rebuild-aita.py")
REPAIR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPAIR)


class RebuildSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve() / "AITA"
        for relative in REPAIR.REQUIRED_FILES:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("source fixture", encoding="utf-8")
        # Real source fixtures exercise the preflight. The wrapper below remains a process-only fake.
        for relative in REPAIR.SOURCE_CONSISTENCY_FILES:
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((ROOT / relative).read_bytes())
        (self.root / "gradlew").write_text('if [ "$1" = "--stop" ]; then echo stopped; exit 0; fi\necho fixture-build\nexit 0\n')
        self.env = {"GRADLE_USER_HOME": str(self.root.parent / "gradle-downloads")}

    def args(self, **overrides):
        values = dict(reset_generated=False, dry_run=False, compile_only=False, android=False, offline=False)
        values.update(overrides)
        return argparse.Namespace(**values)

    def run_repair(self, **overrides):
        output = io.StringIO()
        with redirect_stdout(output), redirect_stderr(output):
            result = REPAIR.execute(self.root, self.args(**overrides), self.env)
        return result, output.getvalue()

    def generated(self, relative=".kotlin"):
        path = self.root / relative
        path.mkdir(parents=True, exist_ok=True)
        (path / "old-cache").write_text("old")
        return path

    def test_source_missing_refuses_before_cleanup(self):
        old = self.generated()
        (self.root / REPAIR.REQUIRED_FILES[-1]).unlink()
        with self.assertRaises(REPAIR.RebuildError):
            self.run_repair(reset_generated=True)
        self.assertTrue((old / "old-cache").exists())

    def test_nested_root_refused(self):
        p = self.root / "AITA/settings.gradle.kts"
        p.parent.mkdir(); p.write_text("nested")
        with self.assertRaises(REPAIR.RebuildError):
            REPAIR.validate_root(self.root)

    def test_filesystem_root_refused(self):
        with self.assertRaises(REPAIR.RebuildError):
            REPAIR.validate_root(Path(self.root.anchor))

    def test_home_directory_refused(self):
        with self.assertRaises(REPAIR.RebuildError):
            REPAIR.validate_root(Path.home().resolve())

    def test_generated_file_not_directory_refused(self):
        (self.root / ".kotlin").write_text("not a directory")
        with self.assertRaises(REPAIR.RebuildError):
            self.run_repair(reset_generated=True)

    def test_symlinked_cache_refused(self):
        outside = self.root.parent / "outside"
        outside.mkdir(); (outside / "keep").write_text("keep")
        (self.root / ".kotlin").symlink_to(outside, target_is_directory=True)
        with self.assertRaises(REPAIR.RebuildError):
            self.run_repair(reset_generated=True)
        self.assertEqual("keep", (outside / "keep").read_text())

    def test_symlinked_module_refused(self):
        module = self.root / "server"
        saved = self.root.parent / "server-real"
        module.rename(saved); module.symlink_to(saved, target_is_directory=True)
        with self.assertRaises(REPAIR.RebuildError):
            self.run_repair(reset_generated=True)

    def test_symlinked_log_refused_even_without_cleanup(self):
        outside = self.root.parent / "do-not-truncate"
        outside.write_text("keep")
        (self.root / "build").mkdir()
        (self.root / "build/aita-build-repair.log").symlink_to(outside)
        with self.assertRaises(REPAIR.RebuildError):
            self.run_repair()
        self.assertEqual("keep", outside.read_text())

    def test_global_downloads_inside_cleanup_target_refused(self):
        self.env["GRADLE_USER_HOME"] = str(self.root / ".gradle/downloads")
        with self.assertRaises(REPAIR.RebuildError):
            self.run_repair(reset_generated=True)

    def test_dry_run_does_not_launch_or_modify_any_files(self):
        self.generated()
        before = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        with patch.object(REPAIR, "run", side_effect=AssertionError("must not launch")):
            result, output = self.run_repair(reset_generated=True, dry_run=True)
        after = {p.relative_to(self.root): p.read_bytes() for p in self.root.rglob("*") if p.is_file()}
        self.assertEqual(before, after); self.assertEqual(0, result)
        self.assertIn("Dry run only", output); self.assertNotIn("PASSED", output)

    def test_failed_stop_preserves_all_previous_generated_files(self):
        old = self.generated()
        (self.root / "gradlew").write_text('echo cannot-start-wrapper\nexit 13\n')
        result, output = self.run_repair(reset_generated=True)
        self.assertEqual(13, result); self.assertTrue((old / "old-cache").exists())
        self.assertNotIn("PASSED", output)

    def test_cleanup_preserves_source_git_runtime_and_external_files(self):
        for relative in REPAIR.GENERATED_PATHS:
            self.generated(relative)
        safe = (".git/config", "local.properties", "runtime/cache.db", "runtime/aita-prod.env", "notes.sql",
                "shared/src/main/kotlin/Keep.kt")
        for relative in safe:
            path = self.root / relative; path.parent.mkdir(parents=True, exist_ok=True); path.write_text("keep")
        external = self.root.parent / "external"; external.mkdir(); (external / "data").write_text("keep")
        (self.root / ".kotlin/external-link").symlink_to(external, target_is_directory=True)
        # This test isolates cleanup safety; artifact verification has its own fixture tests.
        with patch.object(REPAIR, "verify_compiled_outputs", return_value=[]):
            result, output = self.run_repair(reset_generated=True)
        self.assertEqual(0, result)
        self.assertTrue(all((self.root / relative).read_text() == "keep" for relative in safe))
        self.assertEqual("keep", (external / "data").read_text())
        self.assertTrue(all(not (self.root / p / "old-cache").exists() for p in REPAIR.GENERATED_PATHS))
        self.assertIn("PASSED", output)

    def test_compile_failure_propagates_nonzero_and_writes_log(self):
        (self.root / "gradlew").write_text('echo "e: first-compiler-error"\nexit 77\n')
        result, output = self.run_repair()
        self.assertEqual(77, result); self.assertNotIn("PASSED", output)
        self.assertIn("first-compiler-error", (self.root / "build/aita-build-repair.log").read_text())

    def test_compile_only_does_not_run_tests_but_compiles_test_sources(self):
        command = REPAIR.build_command(self.root, compile_only=True, android=False, offline=False)
        self.assertIn(":shared:compileTestKotlinJvm", command)
        self.assertIn(":shared:jvmJar", command)
        self.assertIn(":composeApp:compileTestKotlinJvm", command)
        self.assertIn(":server:compileTestKotlin", command)
        self.assertNotIn("--tests", command)

    def test_common_metadata_and_cache_bypass_are_not_skipped(self):
        command = REPAIR.build_command(self.root, compile_only=False, android=False, offline=False)
        for value in (":shared:compileCommonMainKotlinMetadata", ":composeApp:compileCommonMainKotlinMetadata",
                      "--no-build-cache", "--no-configuration-cache", "--rerun-tasks", "-Pkotlin.incremental=false"):
            self.assertIn(value, command)
        self.assertNotIn("--continue", command)
        self.assertIn("-Paita.serverOnly=false", command); self.assertIn("-Paita.webOnly=false", command)

    def test_only_selected_non_database_tests_are_run(self):
        command = REPAIR.build_command(self.root, compile_only=False, android=False, offline=False)
        self.assertIn("kz.aita.JsonCacheQueryBindingTest", command)
        self.assertIn("kz.aita.SharedProtocolClientLinkageTest", command)
        self.assertIn("kz.aita.server.SharedProtocolServerLinkageTest", command)
        self.assertNotIn("MarketplaceRepositoryDatabaseTest", " ".join(command))

    def test_android_and_offline_are_explicit_options(self):
        command = REPAIR.build_command(self.root, compile_only=True, android=True, offline=True)
        self.assertIn(":composeApp:compileDebugKotlinAndroid", command)
        self.assertIn(":shared:compileDebugKotlinAndroid", command)
        self.assertIn("--offline", command)

    def test_cleanup_is_opt_in(self):
        old = self.generated()
        with patch.object(REPAIR, "verify_compiled_outputs", return_value=[]):
            result, _ = self.run_repair()
        self.assertEqual(0, result); self.assertTrue((old / "old-cache").exists())

    def test_exit_zero_without_actual_outputs_is_not_success(self):
        result, output = self.run_repair()
        self.assertEqual(2, result)
        self.assertNotIn("PASSED", output)
        self.assertIn("Compiled-output verification FAILED", output)
        self.assertIn("shared-jvm", (self.root / "build/aita-build-repair.log").read_text())


class SourceIntegrationRegressionTest(unittest.TestCase):
    def read(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_effect_captures_stable_domain_field(self):
        source = self.read("composeApp/src/commonMain/kotlin/kz/aita/CommonMainComposeWidgetsConfig.kt")
        self.assertIn("val renderedTextFieldContent = genericTextField(", source)
        self.assertIn("LaunchedEffect(renderedTextFieldContent.isFocused)", source)
        self.assertNotIn("LaunchedEffect(textFieldContent.isFocused)", source)
        self.assertIn("val renderedContent = checkNotNull(textFieldContent)", source)

    def test_common_legacy_regex_has_no_platform_only_option(self):
        source = self.read("shared/src/commonMain/kotlin/kz/aita/messages/EventMessageLegacy.kt")
        self.assertNotIn("RegexOption.DOT_MATCHES_ALL", source)
        self.assertIn(r'append("([\\s\\S]*?)")', source)
        self.assertIn("pattern.regex.matchEntire(text)", source)
        self.assertIn("text.length > 8_192", source)

    def test_branch_arguments_have_an_explicit_heterogeneous_type(self):
        source = self.read("server/src/main/kotlin/kz/aita/server/marketplace/MarketplaceRepository.kt")
        self.assertIn("val matchArguments = buildList<Any?>", source)
        self.assertIn("*matchArguments.toTypedArray()", source)
        self.assertIn("val matching: List<GoodsItemDataModel>", source)

    def test_release_signing_uses_typed_android_container_element(self):
        source = self.read("composeApp/build.gradle.kts")
        self.assertIn("val aitaReleaseSigningConfig: com.android.build.api.dsl.ApkSigningConfig?", source)
        self.assertIn("val releaseSigning: com.android.build.api.dsl.ApkSigningConfig", source)
        self.assertIn('signingConfigs.maybeCreate("aitaRelease")', source)
        self.assertNotIn('signingConfigs.create("aitaRelease")', source)
        for field, setting in (("storeFile", "project.file(aitaAndroidKeystorePath)"),
                               ("storePassword", "aitaAndroidKeystorePassword"),
                               ("keyAlias", "aitaAndroidKeyAlias"),
                               ("keyPassword", "aitaAndroidKeyPassword")):
            self.assertIn(f"releaseSigning.{field} = {setting}", source)
        self.assertIn("aitaReleaseSigningConfig?.let { signingConfig = it }", source)

    def test_query_tests_use_the_generated_reserved_value_property(self):
        source = self.read("shared/src/jvmTest/kotlin/kz/aita/JsonCacheQueryBindingTest.kt")
        self.assertNotIn("!!.value)", source)
        self.assertEqual(2, source.count("!!.value_)"))
        self.assertIn("nullableStoredValueIsDifferentFromAnAbsentRow", source)
        self.assertIn("assertNull(storedRow.value_)", source)

    def test_linkage_tests_use_real_serializers_and_module_dependency(self):
        for path in (
            "shared/src/commonTest/kotlin/kz/aita/SharedProtocolLinkageTest.kt",
            "composeApp/src/commonTest/kotlin/kz/aita/SharedProtocolClientLinkageTest.kt",
            "server/src/test/kotlin/kz/aita/server/SharedProtocolServerLinkageTest.kt",
        ):
            source = self.read(path)
            self.assertIn("MarketListingUpdate.serializer()", source)
            self.assertIn("AitaAuthFlowDataModel.serializer()", source)
            self.assertNotIn("data class Market", source)
        self.assertIn("implementation(projects.shared)", self.read("composeApp/build.gradle.kts"))
        self.assertIn('implementation(project(":shared"))', self.read("server/build.gradle.kts"))


if __name__ == "__main__":
    unittest.main()
