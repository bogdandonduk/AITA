#!/usr/bin/env python3
"""Export reviewed helpers + guide into <web-dist>/install and/or a release ZIP.

Example: python3 scripts/install/package-installers.py --web-root /path/to/web-dist --zip /path/to/aita-install-helpers.zip
This performs local packaging only, never publishing or installing anything.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shutil
import zipfile

HERE = Path(__file__).resolve().parent
FILES = ("aita-install.py", "aita-install.ps1", "index.html")


def package(web_root=None, archive=None):
    root = HERE.parents[1]
    public = json.loads((root / "composeApp/src/wasmJsMain/resources/downloads-public-key.json").read_text())["spki"]
    # Refuse stale helper keys after an intentional key rotation.
    py_key = re.search(r'^PUBLIC_KEY = "([A-Za-z0-9+/=]+)"$', (HERE / "aita-install.py").read_text(), re.M)[1]
    ps_key = re.search(r"^\$script:AitaPublicKey = '([A-Za-z0-9+/=]+)'$", (HERE / "aita-install.ps1").read_text(), re.M)[1]
    if py_key != public or ps_key != public:
        raise ValueError("Installer helper trust keys differ from the application; review key rotation first")
    data = {name: (HERE / name).read_bytes() for name in FILES}
    # UTF-8 BOM is needed for Windows PowerShell 5.1 if future localized messages use non-ASCII characters.
    if not data["aita-install.ps1"].startswith(b"\xef\xbb\xbf"):
        data["aita-install.ps1"] = b"\xef\xbb\xbf" + data["aita-install.ps1"]
    data["SHA256SUMS.txt"] = "".join(hashlib.sha256(content).hexdigest() + "  " + name + "\n" for name, content in data.items()).encode()
    if web_root is not None:
        destination = web_root / "install"
        if destination.is_symlink():
            raise ValueError("Refusing symlinked output directory")
        destination.mkdir(parents=True, exist_ok=True)
        for name, content in data.items():
            output = destination / name
            if output.is_symlink():
                raise ValueError("Refusing symlinked output file")
            output.write_bytes(content)
        print("READY: local web install guide at " + str(destination / "index.html"))
    if archive is not None:
        if archive.exists() or archive.is_symlink():
            raise ValueError("ZIP already exists; choose a new output path")
        archive.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive, "x", zipfile.ZIP_DEFLATED) as output:
            for name, content in data.items():
                info = zipfile.ZipInfo("aita-install/" + name, (2026, 1, 1, 0, 0, 0))
                info.external_attr = 0o644 << 16
                output.writestr(info, content, compress_type=zipfile.ZIP_DEFLATED)
        print("READY: local installer helper archive " + str(archive))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--web-root", type=Path)
    parser.add_argument("--zip", dest="archive", type=Path)
    args = parser.parse_args()
    if not (args.web_root or args.archive):
        parser.error("specify --web-root and/or --zip")
    package(args.web_root, args.archive)
