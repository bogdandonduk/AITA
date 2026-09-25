import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('draft', Path(__file__).with_name('windows-draft-artifacts.py'))
draft = importlib.util.module_from_spec(spec)
spec.loader.exec_module(draft)


class WindowsDraftTests(unittest.TestCase):
    def test_published_wrong_source_or_tag_cannot_supply_installers(self):
        tag = draft.identity(123, 2, 'a'*40)
        good = dict(isDraft=True, tagName=tag, targetCommitish='a'*40)
        draft.validate_draft(good, tag, 'a'*40)
        for change in (dict(isDraft=False), dict(targetCommitish='b'*40), dict(tagName='v1.0.0')):
            with self.assertRaises(RuntimeError):
                draft.validate_draft(good | change, tag, 'a'*40)

    def test_attempts_cannot_overwrite_prior_evidence(self):
        self.assertNotEqual(draft.identity(123, 1, 'a'*40), draft.identity(123, 2, 'a'*40))
        for run in ('../123', '-1', '0', '123;cmd'):
            with self.assertRaises(RuntimeError): draft.identity(run, 1, 'a'*40)

    def test_safe_package_extracts_and_refuses_replacement(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            archive = root / 'package.zip'
            with zipfile.ZipFile(archive, 'w') as z:
                for name in ('app.exe', 'app.msi', 'windows-verification.json', 'windows-client-build.json'):
                    z.writestr(name, 'fixture')
            draft.extract_package(archive, root / 'output')
            self.assertEqual((root / 'output/app.exe').read_text(), 'fixture')
            with self.assertRaises(FileExistsError): draft.extract_package(archive, root / 'output')

    def test_path_traversal_and_missing_receipts_are_rejected_before_extraction(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            for unsafe in ('../app.exe', '/app.exe', 'folder\\app.exe', 'unknown.txt'):
                archive = root / 'package.zip'
                with zipfile.ZipFile(archive, 'w') as z:
                    for name in ('app.msi', 'windows-verification.json', 'windows-client-build.json', unsafe):
                        z.writestr(name, 'fixture')
                with self.assertRaises(RuntimeError): draft.extract_package(archive, root / 'output')
                self.assertFalse((root / 'output').exists())

    def test_failed_workflow_cannot_supply_installers(self):
        with patch.object(draft, 'gh', return_value=json.dumps(dict(conclusion='failure', name='Build AITA Windows Release'))):
            with self.assertRaisesRegex(RuntimeError, 'did not pass'):
                draft.download(SimpleNamespace(run=123))

    def test_custom_run_title_does_not_replace_workflow_identity(self):
        good = dict(name='AITA Windows v1.1.9-b20', conclusion='success', status='completed',
                    path='.github/workflows/build-windows-release.yml', event='workflow_dispatch')
        draft.validate_run(good)
        for change in (dict(path='.github/workflows/unrelated.yml'), dict(event='pull_request'), dict(status='in_progress')):
            with self.assertRaises(RuntimeError): draft.validate_run(good | change)

    def test_existing_asset_is_immutable(self):
        args = SimpleNamespace(run=123, attempt=1, revision='a'*40, diagnostics=False)
        with patch.object(draft, 'gh', return_value='[{"tagName":"windows-build-123-1"}]') as gh, \
                patch.object(draft, 'draft_view', return_value=dict(assets=[dict(name='windows-package.zip')])):
            with self.assertRaisesRegex(RuntimeError, 'already exists'): draft.upload(args)
            self.assertEqual(gh.call_count, 1)


if __name__ == '__main__':
    unittest.main()
