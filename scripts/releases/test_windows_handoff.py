"""Execute the shipped handoff logic against fake processes. Never start an installer or request UAC."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
import unittest

POWERSHELL = os.environ.get('AITA_TEST_POWERSHELL') or shutil.which('powershell.exe') or shutil.which('pwsh')

@unittest.skipUnless(POWERSHELL, 'PowerShell runtime is not installed')
class WindowsHandoffTests(unittest.TestCase):
    def simulate(self, matching=True, exit_code=0, relaunch=True, kind='msi', scenario='normal', staged=False):
        source = (Path(__file__).resolve().parents[2] / 'composeApp/src/jvmMain/kotlin/kz/aita/WindowsUpdateHandoff.kt').read_text()
        script = source.split('internal val windowsUpdateScript = """', 1)[1].split('""".trimIndent()', 1)[0].replace("${'$'}", '$')
        with TemporaryDirectory(prefix="AITA pilot ' ") as name:
            folder = Path(name)
            installer = folder / ('update.' + kind); installer.write_bytes(b'installer-test')
            launcher = folder / 'AITA.exe'; launcher.write_bytes(b'launcher-test')
            user_data = folder / 'pending-transactions.db'; user_data.write_bytes(b'keep this work')
            helper = folder / 'helper.ps1'; output = folder / 'calls.json'
            mocks = '''
$script:calls = [System.Collections.Generic.List[string]]::new()
$script:phases = [System.Collections.Generic.List[string]]::new()
function Initialize-UpdateWindow {
 $script:phases.Add('window_visible')
 if ($env:AITA_TEST_SCENARIO -eq 'window_failure') { throw 'Window creation failed' }
}
function Set-UpdateStatus([string]$key) { $script:phases.Add($key) }
function Pump-UpdateWindow { }
function Finish-UpdateWindow([bool]$success) { }
function Get-Process {
 param($Id, $Name, $ErrorAction)
 if ($env:AITA_TEST_SCENARIO -eq 'already_relaunched' -and $Name) { return [pscustomobject]@{Path=$env:AITA_UPDATE_LAUNCHER} }
 return $null
}
function Start-Process {
 param($FilePath, $ArgumentList, $Verb, $WindowStyle, [switch]$Wait, [switch]$PassThru)
 $script:calls.Add($FilePath)
 if ($FilePath.EndsWith('msiexec.exe') -or $FilePath -eq $env:AITA_UPDATE_INSTALLER) {
   if ($Wait) { throw 'Waiting for the whole process tree would hang on relaunched AITA' }
   if ($WindowStyle -ne 'Normal') { throw 'Installer must be visible' }
   if ($Verb -ne 'RunAs') { throw 'Elevation request missing' }
   if (-not (Test-Path -LiteralPath $env:AITA_UPDATE_READY)) { throw 'App handoff was not ready' }
   [IO.File]::WriteAllText($env:AITA_TEST_INSTALL_ARGS, [string]$ArgumentList)
   if ($env:AITA_TEST_SCENARIO -eq 'uac_cancelled') { throw 'User cancelled permission prompt' }
   if ($env:AITA_TEST_SCENARIO -eq 'already_relaunched') { $env:AITA_TEST_SCENARIO = 'normal' }
   $fake = [pscustomobject]@{ExitCode=[int]$env:AITA_TEST_EXIT; Checks=0}
   $fake | Add-Member ScriptMethod WaitForExit { param($millis) $this.Checks++; return $this.Checks -gt 2 }
   $fake | Add-Member ScriptMethod Refresh { }
   return $fake
 }
}
if ($env:AITA_TEST_SCENARIO -eq 'duplicate') {
 $held = [IO.File]::Open($env:AITA_UPDATE_LOCK, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
}
'''
            script = script.replace('# BEGIN_HANDOFF', mocks + '\n# BEGIN_HANDOFF')
            tail = '''
if ($held) { $held.Dispose() }
ConvertTo-Json -InputObject @($script:calls) | Set-Content -Encoding UTF8 -LiteralPath $env:AITA_TEST_CALLS
ConvertTo-Json -InputObject @($script:phases) | Set-Content -Encoding UTF8 -LiteralPath $env:AITA_TEST_PHASES
'''
            helper.write_text(script + tail)
            last = folder / 'last-result'; last.write_text('previous_result')
            env = dict(os.environ, AITA_UPDATE_READY=str(folder/'ready'), AITA_UPDATE_LAUNCHER=str(launcher),
                AITA_UPDATE_INSTALLER=str(installer), AITA_UPDATE_PARENT='99999999', AITA_UPDATE_LOG=str(folder/'install.log'),
                AITA_UPDATE_SHA256=hashlib.sha256(installer.read_bytes()).hexdigest() if matching else '0'*64,
                AITA_UPDATE_ACTIVE=str(folder/'active'), AITA_UPDATE_RESULT=str(folder/'result'), AITA_UPDATE_LAST_RESULT=str(last),
                AITA_UPDATE_LOCK=str(folder/'lock'), AITA_UPDATE_RELAUNCH=str(relaunch).lower(), AITA_TEST_EXIT=str(exit_code),
                AITA_TEST_CALLS=str(output), AITA_TEST_PHASES=str(folder/'phases'), AITA_TEST_INSTALL_ARGS=str(folder/'args.txt'),
                AITA_TEST_SCENARIO=scenario, AITA_UPDATE_STAGED=str(staged).lower())
            # Windows PowerShell needs the real SystemRoot to initialize .NET.
            # Start-Process is mocked, so keeping this path cannot run an installer.
            # Linux's portable pwsh has no SystemRoot and uses the fixture path.
            if not any(key.casefold() == 'systemroot' for key in env):
                env['SystemRoot'] = str(folder)
            # os.environ uppercases keys on Windows; dict(os.environ) does not
            # retain that case-insensitive lookup behavior.
            for key in list(env):
                if key.casefold() == 'psmodulepath':
                    del env[key]  # Match the shipped handoff launcher.
            execution = subprocess.run([POWERSHELL, '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', str(helper)],
                env=env, capture_output=True, text=True, timeout=30)
            self.assertEqual(execution.returncode, 0, execution.stdout + execution.stderr)
            calls = json.loads(output.read_text(encoding='utf-8-sig'))
            phases = json.loads((folder/'phases').read_text(encoding='utf-8-sig'))
            success = matching and exit_code in (0,3010) and scenario == 'normal'
            self.assertEqual(user_data.read_bytes(), b'keep this work')
            self.assertEqual(installer.exists(), not (success and staged))
            self.assertFalse(helper.exists())
            self.assertFalse((folder/'active').exists())
            self.assertFalse((folder/'ready').exists())
            self.assertEqual((folder/'result').read_text().startswith('success'), success, execution.stdout+execution.stderr)
            if scenario == 'duplicate':
                self.assertEqual(calls, []); self.assertEqual(last.read_text(), 'previous_result')
                self.assertIn('another_update_is_running', (folder/'result').read_text())
            elif not matching or scenario == 'window_failure':
                self.assertEqual(calls, [])
            else:
                self.assertEqual(len(calls), 2 if relaunch else 1, execution.stdout+execution.stderr)
                if relaunch: self.assertEqual(calls[-1], str(launcher))
                self.assertLess(phases.index('window_visible'), phases.index('permission'))
                self.assertIn('INSTALLDIR="'+str(folder)+'"', (folder/'args.txt').read_text(encoding='utf-8-sig'))
                if scenario == 'normal': self.assertIn('installing', phases)
            if matching and exit_code == 1602: self.assertIn('Windows Installer returned 1602', execution.stdout)

    def test_exe_waits_for_installer_only_and_relaunches(self): self.simulate(kind='exe')
    def test_normal_close_installs_without_reopening(self): self.simulate(relaunch=False)
    def test_msi_installs_and_relaunches(self): self.simulate()
    def test_hash_mismatch_never_exits_app_or_launches(self): self.simulate(matching=False)
    def test_cancelled_install_reopens_existing_version(self): self.simulate(exit_code=1602)
    def test_busy_windows_installer_keeps_package_and_work(self): self.simulate(exit_code=1618)
    def test_reboot_required_counts_as_success(self): self.simulate(exit_code=3010)
    def test_uac_denied_is_visible_failure_and_reopens_existing_app(self): self.simulate(scenario='uac_cancelled')
    def test_duplicate_handoff_does_not_exit_app_or_overwrite_running_result(self): self.simulate(scenario='duplicate')
    def test_visible_window_failure_keeps_current_app_open(self): self.simulate(scenario='window_failure')
    def test_only_successful_staged_package_is_removed(self): self.simulate(staged=True)
    def test_failed_staged_package_is_kept(self): self.simulate(exit_code=1603,staged=True)

if __name__ == '__main__': unittest.main()
