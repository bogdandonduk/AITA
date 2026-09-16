#!/usr/bin/env python3
import copy,importlib.util,json,struct,tempfile,unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('help_publisher',Path(__file__).with_name('publish-help.py'))
pub=importlib.util.module_from_spec(spec);spec.loader.exec_module(pub)
ROOT=Path(__file__).resolve().parents[2]
class HelpBookTest(unittest.TestCase):
    def setUp(self):
        self.book=json.loads((ROOT/'server/src/main/resources/help/tutorials.json').read_text())
    def test_complete_bundled_book_matches_client(self):
        pub.validate(self.book)
        self.assertEqual((ROOT/'server/src/main/resources/help/tutorials.json').read_bytes(),(ROOT/'composeApp/src/commonMain/composeResources/files/assets/help/tutorials.json').read_bytes())
        self.assertGreaterEqual(len(self.book['tutorials']),90)
        self.assertGreaterEqual(sum(len(x['steps']) for x in self.book['tutorials']),360)
    def test_existing_faq_translations_and_audiences_are_preserved(self):
        retained=[f for f in self.book['faqs'] if f['id'].startswith('faq.existing-') or f['id'] in {'faq.reset','faq.email-code','faq.support-secrets','faq.store-offline'}]
        self.assertEqual(51,len(retained))
        for f in retained:
            self.assertEqual({'en','ru','kk','ky','tg','uz'},set(f['question']))
            self.assertEqual({'en','ru','kk','ky','tg','uz'},set(f['answer']))
        self.assertNotIn('faq.existing-900',{f['id'] for f in self.book['faqs']})
        self.assertEqual(['STORE'],next(f for f in retained if f['id']=='faq.existing-860')['modes'])
        self.assertEqual(4,len(next(f for f in retained if f['id']=='faq.existing-894')['modes']))
    def test_bundled_long_form_locales_complete(self):
        for a in self.book['tutorials']:
            for field in [a['title'],a['introduction'],*(s['text'] for s in a['steps'])]:
                self.assertTrue(field.get('en'));self.assertTrue(field.get('ru'))
    def test_store_actions_never_leak_into_other_books(self):
        for a in self.book['tutorials']:
            if a['id'].startswith(('sale.','stock.','store.','market.','devices.')): self.assertEqual(['STORE'],a['modes'])
            if a['id'].startswith('buyer.'):self.assertEqual(['BUYER'],a['modes'])
            if a['id'].startswith('supplier.'):self.assertEqual(['SUPPLIER'],a['modes'])
    def test_no_placeholder_screenshots(self):
        self.assertTrue(all(not s['screenshots'] for t in self.book['tutorials'] for s in t['steps']))
    def test_invalid_ids_modes_and_duplicate_steps_rejected(self):
        for transform in [lambda b:b['tutorials'][0].update(id='../bad'),lambda b:b['tutorials'][0].update(modes=[]),lambda b:b['tutorials'][0]['steps'].append(b['tutorials'][0]['steps'][0])]:
            b=copy.deepcopy(self.book);transform(b)
            with self.assertRaises(ValueError):pub.validate(b)
    def test_wrong_scalar_types_fail_validation_cleanly(self):
        self.book['schema']=True
        with self.assertRaises(ValueError):pub.validate(self.book)
        self.book['schema']=1;self.book['tutorials'][0]['title']['en']=42
        with self.assertRaises(ValueError):pub.validate(self.book)
    def test_reads_are_bounded_even_after_size_metadata_was_observed(self):
        with tempfile.TemporaryDirectory() as tmp:
            file=Path(tmp)/'input';file.write_bytes(b'x'*20)
            with self.assertRaises(ValueError):pub.bounded_read(file,10)
            self.assertEqual(b'x'*20,pub.bounded_read(file,20))
    def test_cross_mode_faq_links_rejected(self):
        self.book['faqs'][0]['tutorialId']='sale.pay'
        with self.assertRaises(ValueError):pub.validate(self.book)
    def test_no_unsafe_asset_paths(self):
        for name in ['../private.png','https://example.org/x.png','data:image/png,x','file.png']:
            self.book['tutorials'][0]['steps'][0]['screenshots']=[dict(asset=name,alt={'en':'Screen'},width=5,height=5)]
            with self.assertRaises(ValueError):pub.validate(self.book)
    def test_publish_text_then_reject_same_revision(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();source=root/'source.json';source.write_text(json.dumps(self.book))
            result=pub.publish(source,root/'out'); before=result.read_bytes()
            with self.assertRaises(ValueError):pub.publish(source,root/'out')
            self.assertEqual(before,result.read_bytes())
    def test_screenshot_is_hashed_and_manifest_committed_last(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();images=root/'images';images.mkdir()
            (images/'step.png').write_bytes(b'\x89PNG\r\n\x1a\n'+b'\x00\x00\x00\rIHDR'+struct.pack('>II',120,200))
            self.book['tutorials'][0]['steps'][0]['screenshots']=[dict(asset='step.png',alt={'en':'An inert image header fixture'},width=1,height=1)]
            source=root/'source.json';source.write_text(json.dumps(self.book))
            with self.assertRaises(ValueError):pub.publish(source,root/'out',images)
            result=pub.publish(source,root/'out',images,True);b=json.loads(result.read_text());image=b['tutorials'][0]['steps'][0]['screenshots'][0]
            self.assertEqual((120,200),(image['width'],image['height']))
            self.assertTrue((root/'out/screenshots'/image['asset']).is_file())
    def test_a_running_publisher_lock_is_not_stolen(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve();source=root/'source.json';source.write_text(json.dumps(self.book));out=root/'out';out.mkdir();lock=out/'.publish.lock';lock.write_text('busy')
            with self.assertRaises(FileExistsError):pub.publish(source,out)
            self.assertEqual('busy',lock.read_text())
    def test_new_account_default_only_affects_new_rows(self):
        sql=(ROOT/'server/src/main/resources/db/migration/V113__account_app_mode_preference.sql').read_text()
        self.assertIn('ADD COLUMN app_mode_id INTEGER;',sql)
        self.assertIn('ALTER COLUMN app_mode_id SET DEFAULT 1;',sql)
        self.assertNotIn('UPDATE users',sql)
    def test_account_profile_and_appearance_saves_do_not_write_mode(self):
        s=(ROOT/'server/src/main/kotlin/kz/aita/server/Server.kt').read_text()
        section=s[s.index('put("/preferences/update")'):s.index('put("/update")',s.index('put("/preferences/update")'))]
        self.assertNotIn('Users.appModeId',section)
        self.assertIn('it[Users.appModeId] = DEFAULT_NEW_ACCOUNT_APP_MODE',s)
        self.assertIn('put("/app-mode")',s)
if __name__=='__main__':unittest.main()
