#!/usr/bin/env python3
"""Verify Authenticode on native Windows, then emit the production publication receipt."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

UPGRADE_CODE = 'F100F3AF-CBA2-42E5-928D-8165D1A271A5'


def verify_upgrade_identity(path, version):
    script = r'''$ErrorActionPreference='Stop'
$installer = New-Object -ComObject WindowsInstaller.Installer
$db = $installer.OpenDatabase($env:AITA_VERIFY_FILE, 0)
function Query($sql) {
  $view = $db.OpenView($sql); [void]$view.Execute(); $record = $view.Fetch()
  if ($null -eq $record) { [void]$view.Close(); return '' }
  $value = $record.StringData(1); [void]$view.Close(); return $value
}
@{upgradeCode=(Query "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='UpgradeCode'");
  version=(Query "SELECT ``Value`` FROM ``Property`` WHERE ``Property``='ProductVersion'");
  removeExisting=(Query "SELECT ``Sequence`` FROM ``InstallExecuteSequence`` WHERE ``Action``='RemoveExistingProducts'");
  relatedProducts=(Query 'SELECT `UpgradeCode` FROM `Upgrade`') } | ConvertTo-Json -Compress
'''
    output = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command', script],
        env={**os.environ, 'AITA_VERIFY_FILE': str(path.resolve())}, text=True, capture_output=True)
    if output.returncode:
        raise RuntimeError('Could not inspect MSI upgrade identity: ' + output.stderr.strip())
    identity = json.loads(output.stdout)
    if (identity['upgradeCode'].strip('{}').upper() != UPGRADE_CODE or identity['version'] != version or
            not identity['removeExisting'] or identity['relatedProducts'].strip('{}').upper() != UPGRADE_CODE):
        raise RuntimeError('MSI must preserve AITA identity and remove related older versions during an upgrade')
    return identity


def verify_signature(path, production, expected):
    env = {**os.environ, 'AITA_VERIFY_FILE': str(path.resolve())}
    command = "$s=Get-AuthenticodeSignature -LiteralPath $env:AITA_VERIFY_FILE; @{status=$s.Status.ToString(); thumbprint=$s.SignerCertificate.Thumbprint; timestamp=$s.TimeStamperCertificate.Thumbprint} | ConvertTo-Json -Compress"
    result = subprocess.run(['pwsh', '-NoProfile', '-NonInteractive', '-Command', command], env=env,
                            text=True, capture_output=True, check=True)
    signature = json.loads(result.stdout)
    if production and (signature['status'] != 'Valid' or not signature['timestamp'] or
                       not expected or signature['thumbprint'].upper() != expected.upper()):
        raise RuntimeError('Installer must have a valid timestamped signature from the configured company certificate')
    if not production and signature['status'] not in ('NotSigned', 'Valid'):
        raise RuntimeError('Pilot installer has a broken signature')
    return signature


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--directory', required=True, type=Path)
    p.add_argument('--version', required=True)
    p.add_argument('--build', required=True, type=int)
    p.add_argument('--revision', required=True)
    p.add_argument('--production', choices=['true', 'false'], required=True)
    args = p.parse_args()
    production = args.production == 'true'
    artifacts = []
    paths = [x for x in args.directory.iterdir() if x.suffix.lower() in ('.exe', '.msi')]
    if len(paths) != 2 or {x.suffix.lower() for x in paths} != {'.exe', '.msi'}:
        raise RuntimeError('Expected exactly one EXE and one MSI')
    upgrade = verify_upgrade_identity(next(x for x in paths if x.suffix.lower() == '.msi'), args.version)
    for path in paths:
        signature = verify_signature(path, production, os.environ.get('AITA_WINDOWS_SIGNER_THUMBPRINT', ''))
        name = f'AITA-{args.version}-{args.build}-windows{path.suffix.lower()}'
        destination = path.with_name(name)
        if path != destination:
            if destination.exists():
                raise RuntimeError('Refusing to replace an existing installer')
            path.rename(destination)
        with destination.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        artifacts.append(dict(name=name, sha256=digest, **signature))
    receipt = dict(version=args.version, build=args.build, revision=args.revision,
                   verificationRevision=os.environ.get('AITA_WORKFLOW_REVISION', args.revision),
                   production=production, artifacts=artifacts, upgrade=upgrade)
    (args.directory / 'windows-verification.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print('VERIFIED: Timestamped company signatures' if production else 'UNSIGNED PILOT: Authorized test-store installers; trusted Windows signing is deferred')


if __name__ == '__main__':
    main()
