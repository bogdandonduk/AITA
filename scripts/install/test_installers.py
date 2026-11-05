#!/usr/bin/env python3
"""Isolated installer trust/transport/package tests. Never installs or contacts production."""
import base64
import copy
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("aita_install", HERE / "aita-install.py")
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)
NOW = 1_800_000_000_000
CONTENT = b"test package bytes: this file is NEVER installed"
SHA = hashlib.sha256(CONTENT).hexdigest()


def release():
    return {"schema": 1, "channel": "RELEASE", "id": "v1.3.0-b35", "version": "1.3.0", "build": 35,
            "sequence": 35, "publishedAtMillis": NOW - 1000, "expiresAtMillis": NOW + 86400000,
            "artifacts": [{"os": "LINUX", "arch": "X64", "kind": "DEB", "minimumOsMajor": 0,
                           "bytes": len(CONTENT), "sha256": SHA,
                           "url": "https://aita-api.bogdan-donduk.workers.dev/client-updates/artifacts/" + SHA + ".deb"}]}


class Response(io.BytesIO):
    status = 200
    headers = {}


class TrustTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(prefix="aita-helper-tests-")
        cls.folder = Path(cls.temp.name)
        cls.private = cls.folder / "test-only-private.pem"
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(cls.private)], check=True, capture_output=True)
        cls.public = base64.b64encode(subprocess.check_output(["openssl", "pkey", "-in", str(cls.private), "-pubout", "-outform", "DER"])).decode()

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def envelope(self, payload=None):
        payload = json.dumps(release()).encode() if payload is None else payload
        signature = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", str(self.private)], input=payload)
        return json.dumps({"algorithm": "RS256", "payload": base64.b64encode(payload).decode(), "signature": base64.b64encode(signature).decode()}).encode()

    def test_signature_is_over_exact_bytes_and_rejects_tampering(self):
        raw = self.envelope()
        verified, digest = installer.verify_envelope(raw, self.public)
        self.assertEqual(verified, release())
        self.assertEqual(len(digest), 64)
        envelope = json.loads(raw)
        envelope["payload"] = base64.b64encode(json.dumps({**release(), "build": 36}).encode()).decode()
        with self.assertRaises(installer.InstallError):
            installer.verify_envelope(json.dumps(envelope).encode(), self.public)
        with self.assertRaises(installer.InstallError):
            installer.verify_envelope(raw)  # production key must not trust the test key

    def test_duplicate_fields_and_oversized_metadata_fail_closed(self):
        with self.assertRaises(installer.InstallError):
            installer.verify_envelope(self.envelope(b'{"schema":1,"schema":2}'), self.public)
        with self.assertRaises(installer.InstallError):
            installer.verify_envelope(b" " * (installer.MAX_FEED + 1), self.public)

    def test_expiry_future_date_and_bad_numeric_types(self):
        installer.validate_release(release(), NOW)
        for field, value in [("expiresAtMillis", NOW), ("publishedAtMillis", NOW + 600001), ("schema", True), ("sequence", True), ("build", 3.5), ("version", "1.2.3/../../bin")]:
            data = release(); data[field] = value
            with self.subTest(field=field), self.assertRaises(installer.InstallError):
                installer.validate_release(data, NOW)

    def test_replay_or_same_sequence_changed_payload_rejected(self):
        previous = {"sequence": 35, "build": 35, "version": "1.3.0", "payloadHash": "abc"}
        installer.validate_release(release(), NOW, previous, "abc")
        for field, value in [("sequence", 34), ("build", 34), ("version", "1.2.0")]:
            data = release(); data[field] = value
            with self.subTest(field=field), self.assertRaises(installer.InstallError):
                installer.validate_release(data, NOW, previous, "abc")
        with self.assertRaises(installer.InstallError):
            installer.validate_release(release(), NOW, previous, "changed")

    def test_exact_platform_architecture_and_package_selection(self):
        data = release()
        self.assertEqual(installer.select_artifact(data, "LINUX", "X64", "DEB")["sha256"], SHA)
        for target, arch, kind in [("LINUX", "ARM64", "DEB"), ("WINDOWS", "X64", "EXE"), ("ANDROID", "X64", "AAB")]:
            with self.subTest(target=target), self.assertRaises(installer.InstallError):
                installer.select_artifact(data, target, arch, kind)
        data["artifacts"][0]["minimumOsMajor"] = 100
        with self.assertRaises(installer.InstallError):
            installer.select_artifact(data, "LINUX", "X64", "DEB")

    def test_untrusted_urls_do_not_reach_network(self):
        for url in ["http://aita.kz/file.deb", "https://evil.example/file.deb", "https://aita.kz@evil.example/f.deb",
                    "https://aita.kz:8443/f.deb", "https://aita.kz/f.deb?q=1", "https://aita.kz/f.deb#x",
                    "https://aita.kz/a%2f..%2ff.deb", "https://aita.kz/../f.deb", "https://aita.kz/f.exe",
                    "https://aita.kz\\@evil.example/f.deb", "https://aita.kz/f.deb\n"]:
            with self.subTest(url=url), self.assertRaises(installer.InstallError):
                installer.valid_url(url, "DEB")

    def test_redirects_are_never_followed(self):
        with self.assertRaises(installer.InstallError):
            installer.NoRedirect().redirect_request(None, None, 302, "", {}, "https://evil.example/package.deb")


class LocalOperationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="aita-helper-local-")
        self.folder = Path(self.temp.name)
        self.artifact = release()["artifacts"][0]

    def tearDown(self):
        self.temp.cleanup()

    def test_download_verifies_and_reuses_file_without_network(self):
        path = installer.download(self.artifact, self.folder, lambda _: None, opener=lambda _: Response(CONTENT))
        self.assertEqual(path.read_bytes(), CONTENT)
        def forbidden(_):
            self.fail("Verified cached installer should not be downloaded again")
        self.assertEqual(installer.download(self.artifact, self.folder, lambda _: None, opener=forbidden), path)

    def test_bad_download_never_becomes_executable_installer(self):
        for raw in (CONTENT[:-1], CONTENT + b"x", b"x" * len(CONTENT)):
            with self.subTest(length=len(raw)), patch.object(installer.time, "sleep"), self.assertRaises(installer.InstallError):
                installer.download(self.artifact, self.folder, lambda _: None, opener=lambda _: Response(raw))
            self.assertEqual(list(self.folder.iterdir()), [])

    def test_interrupted_download_retries_with_clean_file(self):
        attempts = []
        def open_file(_):
            attempts.append(1)
            if len(attempts) == 1:
                raise urllib.error.URLError("test timeout")
            return Response(CONTENT)
        with patch.object(installer.time, "sleep"):
            path = installer.download(self.artifact, self.folder, lambda _: None, opener=open_file)
        self.assertEqual(len(attempts), 2)
        self.assertEqual(path.read_bytes(), CONTENT)
        self.assertFalse(list(self.folder.glob("*.partial")))

    def test_early_eof_retries_instead_of_leaving_unusable_download(self):
        responses = iter([Response(CONTENT[:8]), Response(CONTENT)])
        with patch.object(installer.time, "sleep"):
            path = installer.download(self.artifact, self.folder, lambda _: None, opener=lambda _: next(responses))
        self.assertEqual(path.read_bytes(), CONTENT)

    def test_private_state_and_concurrent_install_lock(self):
        folder = installer.private_directory(self.folder / "private")
        self.assertEqual(folder.stat().st_mode & 0o777, 0o700)
        with installer.installer_lock(folder):
            with self.assertRaises(installer.InstallError):
                with installer.installer_lock(folder):
                    self.fail("Second installer acquired lock")
        installer.atomic_json(folder / "state", {"build": 35})
        self.assertEqual(json.loads((folder / "state").read_text()), {"build": 35})

    def test_symlinked_parent_and_state_fail_closed(self):
        link = self.folder / "link"
        link.symlink_to(self.folder, target_is_directory=True)
        with self.assertRaises(installer.InstallError):
            installer.private_directory(link / "new")
        destination = self.folder / "target"
        destination.write_text("preserved")
        state = self.folder / "state"; state.symlink_to(destination)
        with self.assertRaises(installer.InstallError):
            installer.atomic_json(state, {"new": True})
        self.assertEqual(destination.read_text(), "preserved")

    def test_deb_command_preserves_data_and_uses_sudo_only_for_install(self):
        file = self.folder / "package.deb"
        metadata = {"Package": "aita", "Version": "1.3.0-1", "Architecture": "amd64"}
        with patch.object(installer.shutil, "which", side_effect=lambda name: "/usr/bin/" + name), \
                patch.object(installer, "command_output", side_effect=lambda args: metadata[args[-1]]), \
                patch.object(installer.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", "")):
            command = installer.linux_install_command(file, self.artifact, release())
        self.assertEqual(command, ["sudo", "/usr/bin/apt-get", "install", "--reinstall", "--no-remove", "--", str(file)])

    def test_deb_wrong_identity_and_downgrade_rejected(self):
        file = self.folder / "package.deb"
        for package in ("other-app", "aita"):
            metadata = {"Package": package, "Version": "1.3.0-1", "Architecture": "amd64"}
            returns = [subprocess.CompletedProcess([], 0, "2.0.0-1", ""), subprocess.CompletedProcess([], 1)]
            with patch.object(installer.shutil, "which", side_effect=lambda name: "/usr/bin/" + name), \
                    patch.object(installer, "command_output", side_effect=lambda args: metadata[args[-1]]), \
                    patch.object(installer.subprocess, "run", side_effect=returns), self.assertRaises(installer.InstallError):
                installer.linux_install_command(file, self.artifact, release())

    def test_android_requires_explicit_authorized_device(self):
        with self.assertRaises(installer.InstallError):
            installer.android_target(None)
        with self.assertRaises(installer.InstallError):
            installer.android_target("serial; rm -rf")
        with patch.object(installer.shutil, "which", return_value="/tools/adb"), \
                patch.object(installer, "command_output", side_effect=["device", "arm64-v8a", "35"]):
            base, arch, major = installer.android_target("phone-123")
        self.assertEqual((base, arch, major), (["/tools/adb", "-s", "phone-123"], "ARM64", 35))


class PackagingTests(unittest.TestCase):
    def test_exported_helpers_match_pinned_key_and_archive_checksums(self):
        import zipfile
        spec = importlib.util.spec_from_file_location("package_installers", HERE / "package-installers.py")
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(prefix="aita-helper-package-") as folder:
            root = Path(folder); archive = root / "helpers.zip"
            module.package(root / "web", archive)
            destination = root / "web" / "install"
            self.assertTrue((destination / "aita-install.ps1").read_bytes().startswith(b"\xef\xbb\xbf"))
            for line in (destination / "SHA256SUMS.txt").read_text().splitlines():
                expected, name = line.split("  ")
                self.assertEqual(hashlib.sha256((destination / name).read_bytes()).hexdigest(), expected)
            with zipfile.ZipFile(archive) as zipped:
                self.assertEqual(set(zipped.namelist()), {"aita-install/" + name for name in (*module.FILES, "SHA256SUMS.txt")})
                for name in zipped.namelist():
                    self.assertEqual(zipped.read(name), (destination / Path(name).name).read_bytes())
            with self.assertRaises(ValueError):
                module.package(archive=archive)

    def test_exporter_rejects_changed_trust_key(self):
        spec = importlib.util.spec_from_file_location("package_installers_key", HERE / "package-installers.py")
        module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        with patch.object(module.json, "loads", return_value={"spki": "unreviewed-key"}), self.assertRaises(ValueError):
            module.package()


if __name__ == "__main__":
    unittest.main(verbosity=2)
