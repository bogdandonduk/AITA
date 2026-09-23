"""Read real MSI databases through Windows Installer COM before starting a long build."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import struct
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location('windows_verifier', Path(__file__).with_name('verify-windows-release.py'))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class IconFileTests(unittest.TestCase):
    def test_reference_icon_contains_all_expected_resolutions(self):
        icon = Path(__file__).resolve().parents[2] / 'composeApp/src/jvmMain/resources/drawable/app_icon.ico'
        frames = verifier.read_ico_frames(icon.read_bytes())
        self.assertEqual([16, 20, 24, 32, 40, 48, 64, 128, 256], [metadata[0] or 256 for metadata, _ in frames])
        self.assertTrue(all(image.startswith(b'\x89PNG\r\n\x1a\n') for _, image in frames))

    def test_empty_truncated_and_out_of_bounds_icon_files_are_rejected(self):
        for content in (b'', struct.pack('<HHH', 0, 1, 1),
                        struct.pack('<HHH', 0, 1, 1) + bytes(8) + struct.pack('<II', 100, 22)):
            with self.assertRaises(RuntimeError):
                verifier.read_ico_frames(content)


@unittest.skipUnless(os.name == 'nt', 'MSI databases require native Windows Installer COM')
class WindowsPackageTests(unittest.TestCase):
    def test_extracted_executable_icon_matches_its_own_artwork_and_rejects_another_icon(self):
        with TemporaryDirectory(prefix="AITA icon ' ") as name:
            icon = Path(name) / 'original.ico'
            frames = verifier.executable_icon_frames(Path(sys.executable))
            entries, images = [], []
            offset = 6 + 16 * len(frames)
            for metadata, image in frames:
                entries.append(metadata + struct.pack('<II', len(image), offset))
                images.append(image)
                offset += len(image)
            icon.write_bytes(struct.pack('<HHH', 0, 1, len(frames)) + b''.join(entries + images))
            self.assertTrue(verifier.verify_icon(Path(sys.executable), icon)['matchesAitaIcon'])
            damaged = bytearray(icon.read_bytes()); damaged[-1] ^= 1; icon.write_bytes(damaged)
            with self.assertRaisesRegex(RuntimeError, 'EXE must display the AITA icon'):
                verifier.verify_icon(Path(sys.executable), icon)
            aita_icon = Path(__file__).resolve().parents[2] / 'composeApp/src/jvmMain/resources/drawable/app_icon.ico'
            with self.assertRaisesRegex(RuntimeError, 'EXE must display the AITA icon'):
                verifier.verify_icon(Path(sys.executable), aita_icon)

    def inspect(self, version='1.0.2', upgrade=verifier.UPGRADE_CODE, remove=True, transactional=True, dialog=True):
        with TemporaryDirectory(prefix="AITA MSI ' ") as name:
            path = Path(name) / 'identity.msi'
            script = r'''$ErrorActionPreference='Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
$db = $installer.OpenDatabase($env:AITA_TEST_MSI, 3)
function Execute($sql) {
  $view=$db.OpenView($sql); [void]$view.Execute(); [void]$view.Close()
}
Execute 'CREATE TABLE `Property` (`Property` CHAR(72) NOT NULL, `Value` CHAR(0) LOCALIZABLE PRIMARY KEY `Property`)'
Execute 'CREATE TABLE `Upgrade` (`UpgradeCode` CHAR(38) NOT NULL PRIMARY KEY `UpgradeCode`)'
Execute 'CREATE TABLE `Dialog` (`Dialog` CHAR(72) NOT NULL PRIMARY KEY `Dialog`)'
Execute 'CREATE TABLE `InstallExecuteSequence` (`Action` CHAR(72) NOT NULL, `Sequence` SHORT PRIMARY KEY `Action`)'
Execute "INSERT INTO ``Property`` (``Property``, ``Value``) VALUES ('UpgradeCode', '{$env:AITA_TEST_UPGRADE}')"
Execute "INSERT INTO ``Property`` (``Property``, ``Value``) VALUES ('ProductVersion', '$env:AITA_TEST_VERSION')"
Execute "INSERT INTO ``Upgrade`` (``UpgradeCode``) VALUES ('{$env:AITA_TEST_UPGRADE}')"
Execute "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('InstallInitialize', 1500)"
Execute "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('InstallFiles', 4000)"
if ($env:AITA_TEST_DIALOG -eq 'true') { Execute "INSERT INTO ``Dialog`` (``Dialog``) VALUES ('MsiRMFilesInUse')" }
$removeSequence = if ($env:AITA_TEST_TRANSACTIONAL -eq 'true') { 1501 } else { 801 }
if ($env:AITA_TEST_REMOVE -eq 'true') {
  Execute "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('RemoveExistingProducts', $removeSequence)"
}
[void]$db.Commit()
'''
            result = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command', script],
                env=dict(os.environ, AITA_TEST_MSI=str(path), AITA_TEST_VERSION=version,
                         AITA_TEST_UPGRADE=upgrade, AITA_TEST_REMOVE=str(remove).lower(), AITA_TEST_TRANSACTIONAL=str(transactional).lower(), AITA_TEST_DIALOG=str(dialog).lower()),
                text=True, capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            return verifier.verify_upgrade_identity(path, '1.0.2')

    def test_com_returns_scalar_identity_and_upgrade_action(self):
        identity = self.inspect()
        self.assertTrue(all(isinstance(value, str) for value in identity.values()), identity)
        self.assertEqual(identity['removeExisting'], '1501')
        self.assertEqual(identity['version'], '1.0.2')

    def test_removal_before_rollback_or_missing_close_apps_dialog_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'rollback'):
            self.inspect(transactional=False)
        with self.assertRaisesRegex(RuntimeError, 'Restart Manager'):
            self.inspect(dialog=False)

    def test_wrong_product_version_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'preserve AITA identity'):
            self.inspect(version='1.0.1')

    def test_changed_upgrade_identity_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'preserve AITA identity'):
            self.inspect(upgrade='11111111-2222-3333-4444-555555555555')

    def test_missing_removal_of_old_installation_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, 'preserve AITA identity'):
            self.inspect(remove=False)


if __name__ == '__main__':
    unittest.main()
