"""Exercise the shipped PowerShell helper with mocked OS handoff, never a real installer."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest


@unittest.skipUnless(os.name == 'nt', 'PowerShell handoff is executed by Windows CI')
class WindowsHandoffTests(unittest.TestCase):
    def simulate(self, matching=True, exit_code=0):
        source = (Path(__file__).resolve().parents[2] / 'composeApp/src/jvmMain/kotlin/kz/aita/WindowsUpdateHandoff.kt').read_text()
        script = source.split('internal val windowsUpdateScript = """', 1)[1].split('""".trimIndent()', 1)[0].replace("${'$'}", '$')
        with TemporaryDirectory(prefix="AITA pilot ' ") as name:
            folder = Path(name)
            installer = folder / 'update.msi'; installer.write_bytes(b'installer-test')
            launcher = folder / 'AITA.exe'; launcher.write_bytes(b'launcher-test')
            user_data = folder / 'pending-transactions.db'; user_data.write_bytes(b'keep this work')
            helper = folder / 'helper.ps1'; output = folder / 'calls.json'
            prefix = '''$script:calls = [System.Collections.Generic.List[string]]::new()
function Get-Process { param($Id, $ErrorAction) return $null }
function Start-Process {
 param($FilePath, $ArgumentList, $Verb, [switch]$Wait, [switch]$PassThru)
 $script:calls.Add($FilePath)
 if ($FilePath.EndsWith('msiexec.exe')) { return [pscustomobject]@{ ExitCode = [int]$env:AITA_TEST_EXIT } }
}
'''
            helper.write_text(prefix + script + "\nConvertTo-Json -InputObject @($script:calls) | Set-Content -Encoding UTF8 -LiteralPath $env:AITA_TEST_CALLS\n")
            env = dict(os.environ, AITA_UPDATE_READY=str(folder / 'ready'), AITA_UPDATE_LAUNCHER=str(launcher),
                AITA_UPDATE_INSTALLER=str(installer), AITA_UPDATE_PARENT='99999999', AITA_UPDATE_LOG=str(folder / 'install.log'),
                AITA_UPDATE_SHA256=hashlib.sha256(installer.read_bytes()).hexdigest() if matching else '0'*64,
                AITA_TEST_EXIT=str(exit_code), AITA_TEST_CALLS=str(output))
            execution = subprocess.run(['powershell.exe', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', str(helper)],
                           env=env, check=True, capture_output=True, text=True, timeout=30)
            calls = json.loads(output.read_text(encoding='utf-8-sig'))
            self.assertEqual(user_data.read_bytes(), b'keep this work')
            self.assertTrue(installer.exists(), 'New app acknowledges installation before installer cleanup')
            self.assertFalse(helper.exists())
            self.assertEqual(len(calls), 2 if matching else 0, execution.stdout + execution.stderr)
            if matching: self.assertEqual(calls[-1], str(launcher))

    def test_installs_and_relaunches_without_deleting_local_work(self): self.simulate()
    def test_hash_mismatch_never_launches_installer_or_exits_app(self): self.simulate(False)
    def test_cancelled_install_reopens_existing_version(self): self.simulate(exit_code=1602)


if __name__ == '__main__': unittest.main()
