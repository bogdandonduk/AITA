#!/usr/bin/env python3
"""Exercise pure Windows PowerShell helpers with ephemeral, non-production fixtures.

Use --powershell /path/to/pwsh (Linux) or --powershell powershell.exe (Windows).
Requires OpenSSL for isolated fixture signing, never uses production private keys.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--powershell", default=shutil.which("powershell.exe") or shutil.which("pwsh"))
    args = parser.parse_args()
    if not args.powershell:
        parser.error("Install PowerShell or pass --powershell. This test never runs an installer.")
    here = Path(__file__).resolve().parent
    with tempfile.TemporaryDirectory(prefix="aita-ps-tests-") as temporary:
        folder = Path(temporary)
        private = folder / "test-only-private.pem"
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(private)], check=True, capture_output=True)
        public = subprocess.check_output(["openssl", "pkey", "-in", str(private), "-pubout", "-outform", "DER"])
        (folder / "public.txt").write_text(base64.b64encode(public).decode())
        content = b"fake test fixture; never execute"
        (folder / "installer.exe").write_bytes(content)
        release = {"schema": 1, "channel": "RELEASE", "id": "v1.3.0-b35", "version": "1.3.0", "build": 35, "sequence": 35,
                   "publishedAtMillis": 1799999999000, "expiresAtMillis": 1800086400000,
                   "artifacts": [{"os": "WINDOWS", "arch": "X64", "kind": "EXE", "minimumOsMajor": 10, "bytes": len(content),
                                  "sha256": hashlib.sha256(content).hexdigest(), "url": "https://aita.kz/installer.exe"}]}
        payload = json.dumps(release).encode()
        signature = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", str(private)], input=payload)
        (folder / "envelope.json").write_text(json.dumps({"algorithm": "RS256", "payload": base64.b64encode(payload).decode(), "signature": base64.b64encode(signature).decode()}))
        subprocess.run([args.powershell, "-NoProfile", "-NonInteractive", "-File", str(here / "test-installers.ps1"), "-FixtureDirectory", str(folder)], check=True)


if __name__ == "__main__":
    main()
