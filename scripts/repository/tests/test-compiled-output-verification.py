"""Output-verifier tests with explicit archive/source fixtures, NOT a Kotlin compilation.

Real generated query signatures and serializer round trips are exercised by Gradle's
JsonCacheQueryBindingTest and SharedProtocol*LinkageTest, never by these fake entries.
"""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("aita_output_verifier", ROOT / "scripts/repository/rebuild-aita.py")
REPAIR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REPAIR)

# Only the shape is tested. These are not implementations of SQLDelight or class files.
QUERY_FIXTURE = """
package kz.aita
class App_databaseQueries {
    fun <T : Any> selectKvSlice(sliceOffset: Long?, sliceLength: Long?, cacheKey: String,
                               mapper: (String, String?) -> T): Query<T> = fixture()
    fun selectKvSlice(sliceOffset: Long?, sliceLength: Long?, cacheKey: String): Query<Row> = fixture()
    fun deleteKvPrefixExcept(prefix: String?, manifestKey: String, keepPrefix: String?): Unit = fixture()
}
"""


class CompiledOutputVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve() / "AITA"
        self.libs = self.root / "shared/build/libs"
        self.libs.mkdir(parents=True)
        self.jar = self.libs / "shared-jvm.jar"
        self.queries = self.root / "shared/build/generated/sqldelight/code/AppDatabase/commonMain/kz/aita/App_databaseQueries.kt"
        self.queries.parent.mkdir(parents=True)
        self.queries.write_text(QUERY_FIXTURE)

    def jar_fixture(self, *, missing=None, metadata=True, magic=b"\xca\xfe\xba\xbe"):
        with zipfile.ZipFile(self.jar, "w") as archive:
            for name in REPAIR.REQUIRED_SHARED_CLASSES:
                if name != missing:
                    archive.writestr(name, magic + b"explicit-test-fixture-not-bytecode")
            if metadata:
                archive.writestr("META-INF/shared.kotlin_module", b"explicit-metadata-fixture")

    def test_missing_jar_refused(self):
        with self.assertRaisesRegex(REPAIR.RebuildError, "Expected one"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_old_and_current_jars_are_ambiguous_not_randomly_selected(self):
        self.jar_fixture()
        (self.libs / "shared-jvm-old.jar").write_bytes(self.jar.read_bytes())
        with self.assertRaisesRegex(REPAIR.RebuildError, "found 2"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_sources_and_javadoc_jars_are_not_compiled_candidates(self):
        self.jar_fixture()
        (self.libs / "shared-jvm-sources.jar").write_bytes(b"fixture")
        (self.libs / "shared-jvm-javadoc.jar").write_bytes(b"fixture")
        self.assertEqual(3, len(REPAIR.verify_compiled_outputs(self.root)))

    def test_missing_each_required_class_or_serializer_refused(self):
        for entry in REPAIR.REQUIRED_SHARED_CLASSES:
            with self.subTest(entry=entry):
                self.jar_fixture(missing=entry)
                with self.assertRaisesRegex(REPAIR.RebuildError, "missing or duplicates"):
                    REPAIR.verify_compiled_outputs(self.root)

    def test_class_name_without_class_magic_refused(self):
        self.jar_fixture(magic=b"text")
        with self.assertRaisesRegex(REPAIR.RebuildError, "Invalid compiled class"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_missing_kotlin_module_metadata_refused(self):
        self.jar_fixture(metadata=False)
        with self.assertRaisesRegex(REPAIR.RebuildError, "module metadata"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_corrupt_jar_refused(self):
        self.jar.write_text("not-a-zip")
        with self.assertRaisesRegex(REPAIR.RebuildError, "Cannot read compiled"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_symlinked_jar_refused(self):
        target = self.root / "outside.jar"
        target.write_bytes(b"keep")
        self.jar.symlink_to(target)
        with self.assertRaisesRegex(REPAIR.RebuildError, "symlinked"):
            REPAIR.verify_compiled_outputs(self.root)
        self.assertEqual(b"keep", target.read_bytes())

    def test_missing_generated_queries_refused(self):
        self.jar_fixture()
        self.queries.unlink()
        with self.assertRaisesRegex(REPAIR.RebuildError, "No generated SQLDelight"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_stale_names_refused_even_when_jar_fixture_is_present(self):
        self.jar_fixture()
        self.queries.write_text(QUERY_FIXTURE.replace("sliceOffset:", "value_:"))
        with self.assertRaisesRegex(REPAIR.RebuildError, "sliceOffset"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_incorrect_binding_type_refused(self):
        for method, names in REPAIR.GENERATED_QUERY_PARAMETERS.items():
            for name, correct in names.items():
                with self.subTest(name=name):
                    wrong = "String" if correct == "Long" else "Long"
                    source = QUERY_FIXTURE.replace(f"{name}: {correct}", f"{name}: {wrong}")
                    self.assertTrue(REPAIR.generated_query_errors(source))

    def test_nullable_nonnullable_and_qualified_bindings_are_accepted(self):
        self.assertEqual([], REPAIR.generated_query_errors(QUERY_FIXTURE))
        self.assertEqual([], REPAIR.generated_query_errors(QUERY_FIXTURE.replace("Long?", "kotlin.Long").replace("String?", "kotlin.String")))

    def test_generated_comment_is_not_a_function_declaration(self):
        self.assertTrue(REPAIR.generated_query_errors("/*" + QUERY_FIXTURE + "*/"))

    def test_missing_overload_bindings_are_not_hidden_by_good_overload(self):
        text = QUERY_FIXTURE.replace("fun selectKvSlice(sliceOffset: Long?", "fun selectKvSlice(value_: String?", 1)
        self.assertTrue(REPAIR.generated_query_errors(text))

    def test_stale_other_generated_variant_is_not_ignored(self):
        self.jar_fixture()
        other = self.queries.with_name("OldQueries.kt")
        other.write_text(QUERY_FIXTURE.replace("manifestKey:", "value_:"))
        with self.assertRaisesRegex(REPAIR.RebuildError, "manifestKey"):
            REPAIR.verify_compiled_outputs(self.root)

    def test_valid_fixtures_only_report_presence_not_runtime_certification(self):
        self.jar_fixture()
        messages = REPAIR.verify_compiled_outputs(self.root)
        self.assertIn("shared/build/libs/shared-jvm.jar", messages[0])
        self.assertIn("are present", messages[1])
        self.assertIn("1 generated interface(s)", messages[2])
        self.assertNotIn("PASSED", " ".join(messages))


if __name__ == "__main__":
    unittest.main()
