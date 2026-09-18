"""Read real MSI databases through Windows Installer COM before starting a long build."""
import importlib.util
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location('windows_verifier', Path(__file__).with_name('verify-windows-release.py'))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


@unittest.skipUnless(os.name == 'nt', 'MSI databases require native Windows Installer COM')
class WindowsPackageTests(unittest.TestCase):
    def inspect(self, version='1.0.2', upgrade=verifier.UPGRADE_CODE, remove=True):
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
Execute 'CREATE TABLE `InstallExecuteSequence` (`Action` CHAR(72) NOT NULL, `Sequence` SHORT PRIMARY KEY `Action`)'
Execute "INSERT INTO ``Property`` (``Property``, ``Value``) VALUES ('UpgradeCode', '{$env:AITA_TEST_UPGRADE}')"
Execute "INSERT INTO ``Property`` (``Property``, ``Value``) VALUES ('ProductVersion', '$env:AITA_TEST_VERSION')"
Execute "INSERT INTO ``Upgrade`` (``UpgradeCode``) VALUES ('{$env:AITA_TEST_UPGRADE}')"
if ($env:AITA_TEST_REMOVE -eq 'true') {
  Execute "INSERT INTO ``InstallExecuteSequence`` (``Action``, ``Sequence``) VALUES ('RemoveExistingProducts', 1401)"
}
[void]$db.Commit()
'''
            result = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command', script],
                env=dict(os.environ, AITA_TEST_MSI=str(path), AITA_TEST_VERSION=version,
                         AITA_TEST_UPGRADE=upgrade, AITA_TEST_REMOVE=str(remove).lower()),
                text=True, capture_output=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            return verifier.verify_upgrade_identity(path, '1.0.2')

    def test_com_returns_scalar_identity_and_upgrade_action(self):
        identity = self.inspect()
        self.assertTrue(all(isinstance(value, str) for value in identity.values()), identity)
        self.assertEqual(identity['removeExisting'], '1401')
        self.assertEqual(identity['version'], '1.0.2')

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
