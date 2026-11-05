import json
from pathlib import Path
import unittest
import tempfile
import time
import os
import subprocess
import sys
from backend import isolated_database_name, collect_results
from run import terminate_owned

HERE = Path(__file__).resolve().parent


class StudioContractTest(unittest.TestCase):
    def test_database_name_matches_strict_existing_suite_guards(self):
        name = isolated_database_name()
        self.assertRegex('jdbc:postgresql://127.0.0.1:49321/' + name,
                         r'^jdbc:postgresql://127\.0\.0\.1:[0-9]+/aita_test_[a-z0-9]+$')
        self.assertNotEqual(name, isolated_database_name())

    def test_catalogue_has_existing_isolated_scripts_and_unique_ids(self):
        scenarios = json.loads((HERE / 'catalogue.json').read_text())
        self.assertEqual(len(scenarios), len({s['id'] for s in scenarios}))
        for item in scenarios:
            with self.subTest(item=item['id']):
                self.assertTrue((HERE / item['file']).resolve().is_file())
                self.assertEqual(item['kind'], 'fixture-ui')
                self.assertTrue(item['areas'])
                for key, value in item.get('environment', {}).items():
                    self.assertTrue(key.startswith('AITA_TEST_'))
                    self.assertIsInstance(value, str)
                self.assertNotIn('live-session', item['file'])
                self.assertNotEqual(Path(item['file']).name, 'app-smoke.cjs')

    def test_backend_report_requires_every_fresh_nonempty_successful_suite(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            xml = root / 'TEST-example.xml'
            xml.write_text('<testsuite tests="3" failures="0" errors="0" skipped="0"/>')
            totals, missing, complete = collect_results(root, root / 'evidence', ['example'], time.time()-1)
            self.assertTrue(complete)
            self.assertEqual(totals['tests'], 3)
            self.assertFalse(collect_results(root, root / 'evidence', ['example', 'missing'], time.time()-1)[2])
            self.assertFalse(collect_results(root, root / 'evidence', ['example'], time.time()+1)[2])
            for attributes in ['tests="0"', 'tests="3" failures="1"', 'tests="3" errors="1"', 'tests="3" skipped="1"']:
                xml.write_text('<testsuite '+attributes+'/>')
                self.assertFalse(collect_results(root, root / 'evidence', ['example'], time.time()-1)[2])

    @unittest.skipIf(os.name == 'nt', 'Unix process-group contract; Windows uses its own CTRL_BREAK group')
    def test_cleanup_reaps_child_after_fixture_leader_exits(self):
        child = subprocess.Popen([sys.executable, '-c',
            'import subprocess,sys; p=subprocess.Popen([sys.executable,"-c","import time; time.sleep(60)"]); print(p.pid,flush=True)'],
            start_new_session=True, stdout=subprocess.PIPE, text=True)
        descendant = int(child.stdout.readline())
        child.wait(timeout=5)
        terminate_owned(child)
        state = Path('/proc') / str(descendant) / 'stat'
        for _ in range(20):
            if not state.exists() or state.read_text().split()[2] == 'Z': break
            time.sleep(.1)
        self.assertTrue(not state.exists() or state.read_text().split()[2] == 'Z')
        child.stdout.close()

    def test_static_report_has_single_replaceable_data_slot(self):
        html = (HERE / 'dashboard.html').read_text()
        self.assertEqual(html.count('/*REPORT*/null'), 1)
        for action in ['start', 'pause', 'continue', 'stop']:
            self.assertIn('data-action="' + action + '"', html)


if __name__ == '__main__': unittest.main()
