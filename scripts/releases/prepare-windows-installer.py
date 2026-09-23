"""Prepare the installed JDK's WiX template with transactional upgrades and files-in-use UI."""
import argparse
import os
import re
import subprocess
from pathlib import Path
import zipfile


def prepare(jdk, destination):
    module = jdk / 'jmods/jdk.jpackage.jmod'
    with zipfile.ZipFile(module) as archive:
        source = archive.read('classes/jdk/jpackage/internal/resources/main.wxs').decode('utf-8')
    old = '<RemoveExistingProducts Before="CostInitialize"/>'
    if source.count(old) != 1 or source.count('<UIRef Id="JpUI"/>') != 1:
        raise RuntimeError('JDK installer template changed; review upgrade sequencing before packaging')
    # InstallValidate can inspect old files before removal. The transaction can roll back
    # the old product if replacement fails. WiX's Restart Manager UI asks the user to close apps.
    source = source.replace(old, '<RemoveExistingProducts After="InstallInitialize"/>')
    source = source.replace('<UIRef Id="JpUI"/>', '<UIRef Id="JpUI"/>\n    <UI><DialogRef Id="MsiRMFilesInUse"/></UI>')
    destination.mkdir(parents=True, exist_ok=True)
    (destination / 'main.wxs').write_text(source, encoding='utf-8')
    print('READY: Transactional Windows upgrade with files-in-use dialog')


def package(jdk, root, version):
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version):
        raise RuntimeError('Invalid installer version')
    image = root / 'composeApp/build/compose/binaries/main/app/AITA'
    if not (image / 'AITA.exe').is_file():
        raise RuntimeError('Build and verify the Compose application image before packaging')
    resources = root / 'build/windows-installer-resources'
    prepare(jdk, resources)
    # Compose 1.9.2 appends its own --resource-dir after freeArgs, overriding ours.
    # Package its unchanged, verified app image directly with the same JDK and identity.
    for kind in ('msi', 'exe'):
        destination = root / ('composeApp/build/compose/binaries/main/' + kind)
        temporary = root / ('build/windows-package-temp/' + kind)
        command = [str(jdk / 'bin/jpackage.exe'), '--type', kind, '--app-image', str(image),
            '--name', 'AITA', '--app-version', version, '--vendor', 'AITA', '--description', 'AITA',
            '--win-dir-chooser', '--win-menu', '--win-menu-group', 'AITA',
            '--win-upgrade-uuid', 'f100f3af-cba2-42e5-928d-8165d1a271a5',
            '--icon', str(root / 'composeApp/src/jvmMain/resources/drawable/app_icon.ico'),
            '--resource-dir', str(resources), '--dest', str(destination), '--temp', str(temporary), '--verbose']
        print('PACKAGING: AITA ' + kind.upper() + ' with the verified upgrade template', flush=True)
        subprocess.run(command, check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', action='store_true')
    parser.add_argument('--version')
    args = parser.parse_args()
    jdk = Path(os.environ['JAVA_HOME'])
    if args.package:
        package(jdk, Path.cwd(), args.version or '')
    else:
        prepare(jdk, Path('build/windows-installer-resources'))
