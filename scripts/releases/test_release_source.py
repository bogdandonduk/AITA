"""A tooling repair may reuse a release source only when application files are unchanged."""
import importlib.util
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location('source_validator', Path(__file__).with_name('validate-windows-source.py'))
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class ReleaseSourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.tooling = Path(self.temp.name) / 'tools'
        self.source = Path(self.temp.name) / 'source'
        self.tooling.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.email', 'release-test@example.invalid')
        self.git('config', 'user.name', 'Release test')
        (self.tooling / 'app.kt').write_text('original application')
        self.commit()
        self.revision = self.git('rev-parse', 'HEAD')
        self.git('worktree', 'add', '--detach', str(self.source), self.revision)

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.tooling), *args], stderr=subprocess.PIPE, text=True).strip()

    def commit(self):
        self.git('add', '.')
        self.git('commit', '-qm', 'test fixture')

    def validate(self):
        validator.validate(self.source, self.tooling, self.revision, self.git('rev-parse', 'HEAD'))

    def test_identical_source_passes(self):
        self.validate()

    def test_tooling_only_repair_passes(self):
        script = self.tooling / 'scripts/releases/verifier.py'
        script.parent.mkdir(parents=True)
        script.write_text('repaired verification')
        self.commit()
        self.validate()

    def test_application_change_cannot_reuse_source(self):
        (self.tooling / 'app.kt').write_text('changed application')
        self.commit()
        with self.assertRaisesRegex(RuntimeError, 'changes application source'):
            self.validate()

    def test_deleted_application_file_cannot_reuse_source(self):
        (self.tooling / 'app.kt').unlink()
        self.commit()
        with self.assertRaisesRegex(RuntimeError, 'changes application source'):
            self.validate()

    def test_incorrect_checkout_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'Checked out commits'):
            validator.validate(self.source, self.tooling, '0' * 40, self.revision)

    def test_non_descendant_tooling_is_rejected(self):
        self.git('checkout', '--orphan', 'unrelated')
        (self.tooling / 'app.kt').write_text('unrelated application history')
        self.commit()
        with self.assertRaises(subprocess.CalledProcessError):
            self.validate()


if __name__ == '__main__':
    unittest.main()
