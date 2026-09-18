#!/usr/bin/env python3
"""Create the first production signing identity outside Git; never replace an existing key.
Passwords travel through environment/stdin, never command arguments or console output.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path.home() / ".config/aita/release-signing")
    parser.add_argument("--github", action="store_true", help="install existing/new Android secrets in bogdandonduk/AITA")
    args = parser.parse_args()
    os.umask(0o077)
    root = Path(__file__).resolve().parents[2]
    folder = args.directory.expanduser().resolve()
    if folder == root or root in folder.parents:
        raise SystemExit("Choose a private directory outside the repository")
    folder.mkdir(parents=True, exist_ok=True, mode=0o700)
    if folder.stat().st_mode & 0o077:
        raise SystemExit("Signing directory must have permission 0700")
    config = folder / "android.json"
    keystore = folder / "aita-production.jks"
    if config.exists():
        if config.is_symlink() or config.stat().st_mode & 0o077:
            raise SystemExit("Android configuration must be a regular private file (0600)")
        values = json.loads(config.read_text())
        print("KEEP: Existing Android production identity preserved")
    else:
        if keystore.exists() or (folder / "update-private.pem").exists():
            raise SystemExit("Partial signing setup found. Preserve it and investigate; no key was replaced")
        values = {
            "AITA_ANDROID_KEYSTORE_PATH": str(keystore),
            "AITA_ANDROID_KEYSTORE_PASSWORD": secrets.token_urlsafe(36),
            "AITA_ANDROID_KEY_ALIAS": "aita-production",
            "AITA_ANDROID_KEY_PASSWORD": secrets.token_urlsafe(36),
        }
        env = {**os.environ, **values}
        print("CREATE: Android RSA-4096 production signing key; passwords will not be displayed", flush=True)
        subprocess.run(["keytool", "-genkeypair", "-storetype", "JKS", "-keystore", str(keystore),
            "-alias", values["AITA_ANDROID_KEY_ALIAS"], "-keyalg", "RSA", "-keysize", "4096",
            "-sigalg", "SHA256withRSA", "-validity", "10000", "-dname", "CN=AITA, OU=Android, O=AITA",
            "-storepass:env", "AITA_ANDROID_KEYSTORE_PASSWORD", "-keypass:env", "AITA_ANDROID_KEY_PASSWORD"],
            env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        with config.open("x") as file:
            json.dump(values, file, indent=2); file.write("\n")
        subprocess.run(["python3", str(root / "scripts/releases/publish-client-release.py"), "keygen",
            "--private-key", str(folder / "update-private.pem"), "--public-key", str(folder / "update-public.txt")], check=True)
    if not keystore.is_file() or keystore.is_symlink() or keystore.stat().st_mode & 0o077:
        raise SystemExit("Keystore is missing or not private; stopped")
    subprocess.run(["keytool", "-list", "-keystore", str(keystore), "-alias", values["AITA_ANDROID_KEY_ALIAS"],
        "-storepass:env", "AITA_ANDROID_KEYSTORE_PASSWORD"], env={**os.environ, **values}, check=True)
    if args.github:
        public = (folder / "update-public.txt").read_text().strip()
        inputs = {k: v for k, v in values.items() if k != "AITA_ANDROID_KEYSTORE_PATH"}
        inputs["AITA_ANDROID_KEYSTORE_BASE64"] = base64.b64encode(keystore.read_bytes()).decode()
        for name, value in inputs.items():
            subprocess.run(["gh", "secret", "set", name, "--repo", "bogdandonduk/AITA"], input=value,
                text=True, check=True, stdout=subprocess.DEVNULL)
            print("SAVED: GitHub secret " + name)
        for name, value in {"AITA_UPDATE_PUBLIC_KEY": public,
            "AITA_UPDATE_FEED_BASE": "https://aita-api.bogdan-donduk.workers.dev/client-updates"}.items():
            subprocess.run(["gh", "variable", "set", name, "--repo", "bogdandonduk/AITA"], input=value,
                text=True, check=True, stdout=subprocess.DEVNULL)
    print(f"READY: Private signing files are in {folder}")
    print("BACK UP: Keep an encrypted copy of this entire directory off this Ubuntu machine")
    print("Never commit these files. Losing the Android key prevents updates to existing installs")
    print("The updater public key is prepared; publishing a signed update feed is a separate step")


if __name__ == "__main__":
    main()
