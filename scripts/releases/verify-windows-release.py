#!/usr/bin/env python3
"""Verify native Windows installer identity, icon and Authenticode before publication."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import struct

UPGRADE_CODE = 'F100F3AF-CBA2-42E5-928D-8165D1A271A5'


def read_ico_frames(data):
    if len(data) < 6:
        raise RuntimeError('Truncated icon file')
    reserved, kind, count = struct.unpack_from('<HHH', data)
    if reserved or kind != 1 or not 1 <= count <= 256 or len(data) < 6 + 16 * count:
        raise RuntimeError('Invalid icon directory')
    frames = []
    for i in range(count):
        entry = data[6 + i * 16:22 + i * 16]
        length, offset = struct.unpack_from('<II', entry, 8)
        if not length or offset < 6 + 16 * count or offset + length > len(data):
            raise RuntimeError('Invalid icon frame bounds')
        frames.append((entry[:8], data[offset:offset + length]))
    return frames


def executable_icon_frames(path):
    # Read PE resources as data only. Rendering and re-saving HICONs can change alpha
    # pixels; checking original resources also verifies every embedded resolution.
    import ctypes as ct
    from ctypes import wintypes as wt
    kernel = ct.WinDLL('kernel32', use_last_error=True)
    callback_type = ct.WINFUNCTYPE(wt.BOOL, wt.HMODULE, ct.c_void_p, ct.c_void_p, ct.c_ssize_t)
    functions = {
        'LoadLibraryExW': ([wt.LPCWSTR, wt.HANDLE, wt.DWORD], wt.HMODULE),
        'FreeLibrary': ([wt.HMODULE], wt.BOOL),
        'EnumResourceNamesW': ([wt.HMODULE, ct.c_void_p, callback_type, ct.c_ssize_t], wt.BOOL),
        'FindResourceW': ([wt.HMODULE, ct.c_void_p, ct.c_void_p], wt.HANDLE),
        'SizeofResource': ([wt.HMODULE, wt.HANDLE], wt.DWORD),
        'LoadResource': ([wt.HMODULE, wt.HANDLE], wt.HANDLE),
        'LockResource': ([wt.HANDLE], ct.c_void_p),
    }
    for name, (arguments, result) in functions.items():
        function = getattr(kernel, name); function.argtypes = arguments; function.restype = result
    module = kernel.LoadLibraryExW(str(path.resolve()), None, 0x40 | 0x20)
    if not module:
        raise ct.WinError(ct.get_last_error())
    try:
        names = []
        @callback_type
        def collect(_, __, name, ___):
            names.append((name or 0) if not name or name <= 65535 else ct.wstring_at(name))
            return True
        if not kernel.EnumResourceNamesW(module, 14, collect, 0) or not names:
            raise RuntimeError('Executable has no icon group')
        def resource(kind, name):
            pointer = name if isinstance(name, int) else ct.cast(ct.c_wchar_p(name), ct.c_void_p)
            info = kernel.FindResourceW(module, pointer, kind)
            if not info:
                raise ct.WinError(ct.get_last_error())
            size = kernel.SizeofResource(module, info)
            address = kernel.LockResource(kernel.LoadResource(module, info))
            if not address or not 0 < size <= 16 * 1024 * 1024:
                raise RuntimeError('Invalid executable icon resource')
            return ct.string_at(address, size)
        group = resource(14, names[0])
        if len(group) < 6:
            raise RuntimeError('Truncated executable icon group')
        reserved, kind, count = struct.unpack_from('<HHH', group)
        if reserved or kind != 1 or not 1 <= count <= 256 or len(group) != 6 + 14 * count:
            raise RuntimeError('Invalid executable icon group')
        frames = []
        for i in range(count):
            entry = group[6 + i * 14:20 + i * 14]
            length, identity = struct.unpack_from('<IH', entry, 8)
            data = resource(3, identity)
            if len(data) != length:
                raise RuntimeError('Executable icon resource size mismatch')
            frames.append((entry[:8], data))
        return frames
    finally:
        kernel.FreeLibrary(module)


def verify_icon(path, expected_icon):
    expected = read_ico_frames(expected_icon.read_bytes())
    actual = executable_icon_frames(path)
    if actual != expected:
        raise RuntimeError('EXE must display the AITA icon at every embedded resolution')
    return dict(matchesAitaIcon=True, frames=len(actual),
                sizes=[{'width':meta[0] or 256, 'height':meta[1] or 256} for meta, _ in actual],
                sourceSha256=hashlib.sha256(expected_icon.read_bytes()).hexdigest())


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
  initialize=(Query "SELECT ``Sequence`` FROM ``InstallExecuteSequence`` WHERE ``Action``='InstallInitialize'");
  installFiles=(Query "SELECT ``Sequence`` FROM ``InstallExecuteSequence`` WHERE ``Action``='InstallFiles'");
  filesInUseDialog=(Query "SELECT ``Dialog`` FROM ``Dialog`` WHERE ``Dialog``='MsiRMFilesInUse'");
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
    if not (int(identity['initialize']) < int(identity['removeExisting']) < int(identity['installFiles'])) or identity['filesInUseDialog'] != 'MsiRMFilesInUse':
        raise RuntimeError('MSI must support rollback and a Restart Manager files-in-use dialog')
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
    p.add_argument('--icon', required=True, type=Path)
    p.add_argument('--production', choices=['true', 'false'], required=True)
    args = p.parse_args()
    production = args.production == 'true'
    artifacts = []
    paths = [x for x in args.directory.iterdir() if x.suffix.lower() in ('.exe', '.msi')]
    if len(paths) != 2 or {x.suffix.lower() for x in paths} != {'.exe', '.msi'}:
        raise RuntimeError('Expected exactly one EXE and one MSI')
    upgrade = verify_upgrade_identity(next(x for x in paths if x.suffix.lower() == '.msi'), args.version)
    icon = verify_icon(next(x for x in paths if x.suffix.lower() == '.exe'), args.icon)
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
                   production=production, artifacts=artifacts, upgrade=upgrade, icon=icon)
    (args.directory / 'windows-verification.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print('VERIFIED: Timestamped company signatures' if production else 'UNSIGNED PILOT: Authorized test-store installers; trusted Windows signing is deferred')


if __name__ == '__main__':
    main()
