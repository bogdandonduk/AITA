"""Prepare the installed JDK's WiX template with transactional upgrades and files-in-use UI."""
import os
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


if __name__ == '__main__':
    prepare(Path(os.environ['JAVA_HOME']), Path('build/windows-installer-resources'))
