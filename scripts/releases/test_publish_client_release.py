#!/usr/bin/env python3
"""Publisher integration tests use disposable test keys and inert artifact bytes only."""
import base64
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("publisher", Path(__file__).with_name("publish-client-release.py"))
publisher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(publisher)


class PublishClientReleaseTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.keys = tempfile.TemporaryDirectory(prefix="aita-test-keys-")
        cls.key = Path(cls.keys.name).resolve() / "private.pem"
        cls.pub = Path(cls.keys.name).resolve() / "public.txt"
        publisher.keygen(cls.key, cls.pub)

    @classmethod
    def tearDownClass(cls):
        cls.keys.cleanup()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="aita-publisher-test-")
        self.root = Path(self.tmp.name).resolve()
        self.catalog = self.root / "catalog"
        self.artifact = self.root / "app.pkg"
        self.artifact.write_bytes(b"inert regression fixture, not an installer")
        self.base = "https://updates.example.org/client-updates"
        self.spec = self.root / "release.json"
        self.info = self.root / "client-build.json"
        self.data = dict(channel="release", sequence=2, id="release-2", version="1.0.2", build=2,
                         notes={"en": "Fixes", "ru": "Исправления"},
                         artifacts=[dict(os="MACOS", arch="ARM64", kind="PKG", path="app.pkg")])
        self.build = dict(channel="release", version="1.0.2", build="2", distribution="direct",
                          publicKey=self.pub.read_text().strip(), feed=self.base)
        self.save()

    def tearDown(self):
        self.tmp.cleanup()

    def save(self):
        self.spec.write_text(json.dumps(self.data), encoding="utf-8")
        self.info.write_text(json.dumps(self.build), encoding="utf-8")

    def publish(self):
        return publisher.publish(self.spec, self.info, self.key, self.catalog, self.base)

    def test_valid_publish_self_verifies_and_installer_hash_matches(self):
        destination = self.publish()
        with tempfile.TemporaryDirectory() as tmp:
            release = publisher.verify_envelope(publisher.read_json(destination), base64.b64decode(self.build["publicKey"]), Path(tmp))
        artifact = release["artifacts"][0]
        self.assertEqual(artifact["sha256"], publisher.digest(self.artifact)[1])
        self.assertTrue((self.catalog / "artifacts" / artifact["url"].split("/")[-1]).is_file())
        self.assertFalse((self.catalog / ".publish.lock").exists())

    def test_equal_or_lower_build_cannot_replace_release(self):
        old = self.publish().read_bytes()
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(old, (self.catalog / "release.json").read_bytes())

    def test_higher_sequence_refreshes_expiry_without_replacing_installer(self):
        old = self.publish().read_bytes()
        self.data["sequence"] += 1; self.save()
        self.assertNotEqual(old, self.publish().read_bytes())

    def test_same_build_cannot_replace_previously_published_bytes(self):
        old = self.publish().read_bytes()
        self.artifact.write_bytes(b"different inert bytes")
        self.data["sequence"] += 1; self.save()
        with self.assertRaises(ValueError): self.publish()
        self.assertEqual(old, (self.catalog / "release.json").read_bytes())

    def test_new_desktop_build_needs_a_new_package_version(self):
        self.publish(); self.data.update(build=3,sequence=3); self.build["build"]="3"; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_later_platform_can_be_added_to_same_build(self):
        self.publish()
        (self.root / "app.msi").write_bytes(b"inert Windows fixture")
        self.data["sequence"] += 1
        self.data["artifacts"].append(dict(os="WINDOWS", arch="X64", kind="MSI", path="app.msi"))
        self.save(); self.publish()
        self.assertEqual(2, len(list((self.catalog / "artifacts").iterdir())))

    def test_mixed_direct_and_store_distributions_validate_each_build_info(self):
        store = dict(self.build, distribution="store")
        (self.root / "play-build.json").write_text(json.dumps(store))
        self.data["artifacts"].append(dict(os="ANDROID",kind="PLAY_STORE",url="https://play.google.com/store/apps/details?id=kz.aita",buildInfo="play-build.json"))
        self.save(); self.publish()
        store["build"] = "99"
        (self.root / "play-build.json").write_text(json.dumps(store))
        self.data["sequence"] += 1; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_invalid_build_identity_does_not_publish(self):
        self.build["build"] = "1"; self.save()
        with self.assertRaises(ValueError): self.publish()
        self.assertFalse((self.catalog / "release.json").exists())

    def test_other_embedded_public_key_is_rejected(self):
        self.build["publicKey"] = "wrong"; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_wrong_feed_is_rejected(self):
        self.build["feed"] = "https://other.example.org/client-updates"; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_copy_failure_preserves_previous_manifest(self):
        old = self.publish().read_bytes()
        self.data.update(build=3, sequence=3, version="1.0.3"); self.build.update(build="3", version="1.0.3"); self.save()
        with patch.object(publisher, "copy_artifact", side_effect=OSError("injected failure")):
            with self.assertRaises(OSError): self.publish()
        self.assertEqual(old, (self.catalog / "release.json").read_bytes())
        self.assertFalse((self.catalog / ".publish.lock").exists())

    def test_invalid_store_link_cannot_be_signed(self):
        self.build["distribution"] = "store"
        self.data["artifacts"] = [dict(os="ANDROID",kind="PLAY_STORE",url="https://evil.example.org/store/apps/details?id=kz.aita")]
        self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_test_channel_is_independent(self):
        self.publish()
        self.build["channel"] = self.data["channel"] = "test"
        self.data["artifacts"] = [dict(os="IOS",kind="TESTFLIGHT",url="https://testflight.apple.com/join/Test1234")]
        self.save(); self.publish()
        self.assertTrue((self.catalog / "release.json").is_file()); self.assertTrue((self.catalog / "test.json").is_file())

    def test_symlinked_artifact_is_rejected(self):
        link = self.root / "alias.pkg"; link.symlink_to(self.artifact)
        self.data["artifacts"][0]["path"] = "alias.pkg"; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_duplicate_target_is_rejected(self):
        self.data["artifacts"] *= 2; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_keygen_will_not_rotate_existing_key(self):
        before = self.key.read_bytes()
        with self.assertRaises(ValueError): publisher.keygen(self.key,self.pub)
        self.assertEqual(before,self.key.read_bytes())

    def test_publish_lock_is_not_stolen(self):
        self.catalog.mkdir(); lock=self.catalog/".publish.lock"; lock.write_text("another publisher")
        with self.assertRaises(FileExistsError): self.publish()
        self.assertEqual("another publisher",lock.read_text())

    def test_store_build_does_not_advertise_direct_installer(self):
        self.build["distribution"] = "store"; self.save()
        with self.assertRaises(ValueError): self.publish()

    def test_metadata_and_url_limits(self):
        for url in ("http://example.org", "https://127.0.0.1/app", "https://u:p@example.org/app", "https://x.local/app", "https://example.org/app#fragment"):
            self.assertFalse(publisher.public_url(url))
        self.data["notes"] = {"en":"x"*24001};self.save()
        with self.assertRaises(ValueError): self.publish()


if __name__ == "__main__": unittest.main()
