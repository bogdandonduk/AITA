import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import zipfile
spec = importlib.util.spec_from_file_location('windows_template', Path(__file__).with_name('prepare-windows-installer.py'))
module = importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
class TemplateTests(unittest.TestCase):
    def test_direct_packaging_keeps_image_identity_and_one_effective_resource_directory(self):
        with TemporaryDirectory(prefix='AITA packaging ') as temp:
            root=Path(temp);jdk=root/'jdk';(jdk/'jmods').mkdir(parents=True)
            with zipfile.ZipFile(jdk/'jmods/jdk.jpackage.jmod','w') as archive:
                archive.writestr('classes/jdk/jpackage/internal/resources/main.wxs','<RemoveExistingProducts Before="CostInitialize"/><UIRef Id="JpUI"/>')
            image=root/'composeApp/build/compose/binaries/main/app/AITA';image.mkdir(parents=True);(image/'AITA.exe').write_bytes(b'unchanged verified application')
            with patch.object(module.subprocess,'run') as run:
                module.package(jdk,root,'1.1.7')
            self.assertEqual(run.call_count,2)
            for call,kind in zip(run.call_args_list,('msi','exe')):
                args=call.args[0];self.assertEqual(args.count('--resource-dir'),1)
                self.assertEqual(args[args.index('--app-image')+1],str(image))
                self.assertEqual(args[args.index('--type')+1],kind)
                self.assertEqual(args[args.index('--win-upgrade-uuid')+1],'f100f3af-cba2-42e5-928d-8165d1a271a5')
                template=(Path(args[args.index('--resource-dir')+1])/'main.wxs').read_text()
                self.assertIn('After="InstallInitialize"',template);self.assertIn('MsiRMFilesInUse',template)
            self.assertEqual((image/'AITA.exe').read_bytes(),b'unchanged verified application')
    def test_changed_jdk_template_requires_review(self):
        with TemporaryDirectory() as temp:
            root=Path(temp);(root/'jmods').mkdir()
            with zipfile.ZipFile(root/'jmods/jdk.jpackage.jmod','w') as archive:
                archive.writestr('classes/jdk/jpackage/internal/resources/main.wxs','changed template')
            with self.assertRaisesRegex(RuntimeError,'template changed'):module.prepare(root,root/'output')
