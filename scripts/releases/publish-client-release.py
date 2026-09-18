#!/usr/bin/env python3
"""Build a signed client feed. Installer files are copied first; channel.json is committed last.

Run --help or keygen/publish --help. Publishing is local only: no Git push, SSH, cloud
credentials, platform-store submission, or production installation is performed.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import time
from urllib.parse import urlsplit, parse_qs

MAX_METADATA = 262_144
MAX_INSTALLER = 2_147_483_648
FILE_KINDS = {"APK", "AAB", "MSI", "EXE", "PKG", "DMG", "DEB", "RPM"}
KINDS = {
    "ANDROID": {"APK", "AAB", "PLAY_STORE"}, "WINDOWS": {"MSI", "EXE"},
    "MACOS": {"PKG", "DMG", "APP_STORE"}, "LINUX": {"DEB", "RPM"},
    "IOS": {"APP_STORE", "TESTFLIGHT"}, "WEB": {"WEB_RELOAD"},
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def openssl(*args: str, data: bytes | None = None) -> bytes:
    result = subprocess.run(["openssl", *map(str, args)], input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, check=False)
    require(result.returncode == 0, "OpenSSL operation failed; check key format and permissions")
    return result.stdout


def read_json(path: Path) -> dict:
    require(path.is_file() and not path.is_symlink() and path.stat().st_size <= MAX_METADATA,
            "Missing, symlinked or oversized JSON input")
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), "JSON input must be an object")
    return value


def canonical_json(value: dict) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def public_url(value: str) -> bool:
    if not isinstance(value, str) or not 10 <= len(value) <= 2048 or any(c.isspace() or ord(c) < 32 or ord(c) == 127 for c in value) or "\\" in value:
        return False
    try:
        u = urlsplit(value); host = (u.hostname or "").lower().rstrip(".")
        return (u.scheme == "https" and u.port in (None, 443) and not u.username and not u.password
                and not u.fragment and "." in host and ":" not in host and any(c.isalpha() for c in host)
                and not host.startswith("0x") and not host.endswith((".localhost", ".local", ".internal")))
    except ValueError:
        return False


def integer(value, minimum: int, maximum: int, label: str) -> int:
    require(type(value) is int and minimum <= value <= maximum, "Invalid " + label)
    return value


def version(value: str) -> tuple[int, int, int]:
    require(isinstance(value, str) and re.fullmatch(r"[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}", value) is not None,
            "Version must be MAJOR.MINOR.PATCH")
    return tuple(map(int, value.split(".")))


def digest(path: Path) -> tuple[int, str]:
    require(path.is_file() and not path.is_symlink(), "Artifact must be a regular, non-symlinked file")
    require(0 < path.stat().st_size <= MAX_INSTALLER, "Artifact size outside allowed bounds")
    sha = hashlib.sha256(); count = 0
    with path.open("rb") as f:
        while True:
            block = f.read(1024 * 1024)
            if not block:
                break
            count += len(block); require(count <= MAX_INSTALLER, "Artifact exceeded maximum size")
            sha.update(block)
    return count, sha.hexdigest()


def public_key_from_private(path: Path) -> bytes:
    require(path.is_file() and not path.is_symlink(), "Private key is missing or symlinked")
    if os.name == "posix":
        require(path.stat().st_mode & 0o077 == 0, "Private key permissions must be 0600 or stricter")
    return openssl("pkey", "-in", str(path), "-pubout", "-outform", "DER")


def verify_envelope(value: dict, public_der: bytes, temporary: Path) -> dict:
    require(value.get("algorithm") == "RS256", "Unsupported signature algorithm")
    payload = base64.b64decode(value["payload"], validate=True)
    signature = base64.b64decode(value["signature"], validate=True)
    require(len(payload) <= 180_000 and 256 <= len(signature) <= 1024, "Invalid signature envelope size")
    (temporary / "public.der").write_bytes(public_der)
    (temporary / "signature").write_bytes(signature)
    openssl("dgst", "-sha256", "-verify", str(temporary / "public.der"), "-keyform", "DER",
            "-signature", str(temporary / "signature"), data=payload)
    result = json.loads(payload)
    require(isinstance(result, dict), "Signed payload must be an object")
    return result


def sync_directory(folder: Path) -> None:
    if os.name == "posix":
        fd = os.open(str(folder), os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)


def atomic_bytes(path: Path, content: bytes, mode: int = 0o644) -> None:
    require(not path.is_symlink(), "Refusing symlinked output")
    fd, name = tempfile.mkstemp(prefix=".aita-release-", dir=str(path.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as f:
            os.chmod(name, mode); f.write(content); f.flush(); os.fsync(f.fileno())
        os.replace(temporary, path)
        sync_directory(path.parent)
    finally:
        temporary.unlink(missing_ok=True)


def copy_artifact(source: Path, destination: Path, expected: tuple[int, str]) -> None:
    require(not destination.is_symlink(), "Refusing symlinked artifact output")
    if destination.exists():
        require(digest(destination) == expected, "Immutable artifact collision")
        return
    fd, name = tempfile.mkstemp(prefix=".aita-artifact-", dir=str(destination.parent))
    temporary = Path(name)
    try:
        with os.fdopen(fd, "wb") as out, source.open("rb") as inp:
            os.chmod(name, 0o644); count = 0; sha = hashlib.sha256()
            while True:
                block = inp.read(1024 * 1024)
                if not block:
                    break
                count += len(block); require(count <= expected[0], "Artifact changed during copying")
                out.write(block); sha.update(block)
            require((count, sha.hexdigest()) == expected, "Artifact changed during copying")
            out.flush(); os.fsync(out.fileno())
        os.replace(temporary, destination)
        sync_directory(destination.parent)
    finally:
        temporary.unlink(missing_ok=True)


def keygen(private: Path, public: Path) -> None:
    require(private.resolve() != public.resolve(), "Private and public paths must differ")
    repository = Path(__file__).resolve().parents[2]
    require(repository not in private.resolve().parents, "Keep the private signing key OUTSIDE the repository")
    require(not private.exists() and not public.exists(), "Key files already exist; this command never rotates keys")
    private.parent.mkdir(parents=True, exist_ok=True); public.parent.mkdir(parents=True, exist_ok=True)
    # OpenSSL emits only to an exclusive 0600 descriptor, never stdout/the terminal.
    raw = openssl("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072")
    fd = os.open(str(private), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(raw); f.flush(); os.fsync(f.fileno())
    public_der = public_key_from_private(private)
    with public.open("x", encoding="ascii") as f:
        f.write(base64.b64encode(public_der).decode("ascii") + "\n")
    print("Created private signing key and base64 public key. Back up the private key securely; never commit or upload it.")


def publish(spec_path: Path, info_path: Path, private: Path, catalog: Path, base_url: str, days: int = 90) -> Path:
    spec = read_json(spec_path); info = read_json(info_path)
    require(public_url(base_url) and not urlsplit(base_url).query, "Catalog URL must be public HTTPS without query/fragment")
    require(type(days) is int and 1 <= days <= 366, "Expiry must be 1..366 days")
    channel = spec.get("channel", "").upper(); require(channel in ("RELEASE", "TEST"), "Channel must be release or test")
    build = integer(spec.get("build"), 1, 2_100_000_000, "build")
    sequence = integer(spec.get("sequence"), 1, 9_007_199_254_740_991, "sequence")
    ver = spec.get("version"); version(ver)
    identifier = spec.get("id", "")
    require(isinstance(identifier,str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", identifier) is not None, "Invalid release ID")
    require(info.get("channel", "").upper() == channel and str(info.get("build")) == str(build) and info.get("version") == ver,
            "Release does not match the generated client-build.json; build the installer with these values first")
    require(info.get("distribution") in ("direct", "store"), "Missing distribution in build information")
    public_der = public_key_from_private(private)
    require(base64.b64encode(public_der).decode("ascii") == info.get("publicKey"),
            "Signing key does not match the public key embedded in the client build")
    require(info.get("feed", "").rstrip("/") == base_url.rstrip("/"), "Catalog URL differs from the one embedded in the client")
    notes = spec.get("notes", {})
    require(isinstance(notes, dict) and len(notes) <= 12 and all(re.fullmatch(r"[a-z]{2,3}",k) and isinstance(v,str) and len(v) <= 24_000 for k,v in notes.items()), "Invalid localized notes")
    entries = spec.get("artifacts", []); require(isinstance(entries, list) and 1 <= len(entries) <= 32, "Supply 1..32 artifacts")
    artifacts = []; download_only = []; copies = []; identities = set(); signing = {}
    for entry in entries:
        # A single channel can include APK + Play and desktop artifacts. Validate each
        # artifact's actual build-info sidecar instead of treating one distribution as global.
        artifact_info = info
        if entry.get("buildInfo"):
            info_file = Path(entry["buildInfo"])
            if not info_file.is_absolute():
                info_file = spec_path.parent / info_file
            artifact_info = read_json(info_file)
        require(artifact_info.get("channel", "").upper() == channel and
                str(artifact_info.get("build")) == str(build) and artifact_info.get("version") == ver,
                "Artifact build information has a different version, build or channel")
        require(artifact_info.get("publicKey") == info["publicKey"] and
                artifact_info.get("feed", "").rstrip("/") == base_url.rstrip("/"),
                "Artifact does not use the release's trust key and feed")
        require(artifact_info.get("distribution") in ("direct", "store"), "Invalid artifact distribution")
        if info.get("revision") and artifact_info.get("revision"):
            require(info["revision"] == artifact_info["revision"], "Artifacts must come from the same source revision")
        os_name = entry.get("os", "").upper(); kind = entry.get("kind", "").upper(); arch = entry.get("arch", "UNIVERSAL").upper()
        require(os_name in KINDS and kind in KINDS[os_name] and arch in ("ARM64", "X64", "UNIVERSAL"), "Invalid artifact target")
        identity = (os_name, arch, kind); require(identity not in identities, "Duplicate artifact target"); identities.add(identity)
        minimum = integer(entry.get("minimumOsMajor", 0), 0, 10000, "minimum OS version")
        item = dict(os=os_name, arch=arch, kind=kind, bytes=0, sha256="", minimumOsMajor=minimum)
        if kind in FILE_KINDS:
            require(artifact_info["distribution"] == "direct", "Store builds must not advertise direct installers")
            source = Path(entry.get("path", ""))
            if not source.is_absolute():
                source = spec_path.parent / source
            require(source.suffix.lower() == "." + kind.lower(), "Artifact extension does not match installer kind")
            size, sha = digest(source); filename = sha + "." + kind.lower()
            item.update(bytes=size, sha256=sha, url=base_url.rstrip("/") + "/artifacts/" + filename)
            copies.append((source, filename, (size, sha)))
        else:
            url = entry.get("url", ""); require(public_url(url), "Invalid external artifact URL")
            uri = urlsplit(url)
            if kind == "PLAY_STORE":
                require(artifact_info["distribution"] == "store" and uri.hostname == "play.google.com" and uri.path == "/store/apps/details" and parse_qs(uri.query).get("id") == ["kz.aita"], "Invalid Google Play link/distribution")
            if kind == "APP_STORE":
                require(uri.hostname == "apps.apple.com" and re.search(r"/id[0-9]+", uri.path), "Invalid App Store link")
            if kind == "TESTFLIGHT":
                require(channel == "TEST" and uri.hostname == "testflight.apple.com" and re.fullmatch(r"/join/[A-Za-z0-9]+", uri.path), "Invalid TestFlight link/channel")
            if kind == "APP_STORE" and os_name == "IOS":
                require(channel == "RELEASE", "Use TestFlight for iOS test-channel releases")
            item["url"] = url
        require(public_url(item["url"]), "Generated artifact URL is invalid")
        if "publisherSigned" in entry:
            require(type(entry["publisherSigned"]) is bool, "Invalid publisher signing status")
            signing[identity] = entry["publisherSigned"]
        (download_only if kind == "AAB" else artifacts).append(item)
    require(artifacts, "At least one normal updater artifact is required")
    catalog = catalog.expanduser().absolute()
    require(not catalog.is_symlink() and catalog.resolve() == catalog, "Catalog path must be canonical, not symlinked")
    catalog.mkdir(parents=True, exist_ok=True)
    # Cross-process lock protects high-water checking and the manifest commit. Never steal a lock.
    lock = catalog / ".publish.lock"
    fd = os.open(str(lock), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.close(fd)
    try:
        with tempfile.TemporaryDirectory(prefix=".aita-signing-", dir=str(catalog)) as tmp:
            temp = Path(tmp); destination = catalog / (channel.lower() + ".json")
            old = None
            if destination.exists():
                old = verify_envelope(read_json(destination), public_der, temp)
                require(old["channel"] == channel and sequence > old["sequence"] and build >= old["build"] and version(ver) >= version(old["version"]),
                        "Refusing a lower build/version or an older/equal metadata sequence")
                if build > old["build"] and any(a["os"] in ("WINDOWS", "MACOS", "LINUX") and a["kind"] in FILE_KINDS for a in artifacts):
                    require(version(ver) > version(old["version"]),
                            "Desktop package managers need a newer MAJOR.MINOR.PATCH for a new installer build")
                if build == old["build"]:
                    # Refresh expiry or add a later-built platform, but never replace a
                    # previously published binary or silently relabel this build.
                    require(identifier == old["id"] and ver == old["version"] and
                            all(previous in artifacts for previous in old["artifacts"]),
                            "Same-build refresh must preserve release identity and every existing artifact")
            download_files = [{k: item[k] for k in ("os", "arch", "kind", "url", "bytes", "sha256")}
                              for item in artifacts + download_only if item["kind"] in {"APK", "AAB", "EXE", "MSI"}]
            for download in download_files:
                identity = (download["os"], download["arch"], download["kind"])
                if identity in signing: download["publisherSigned"] = signing[identity]
            history = old.get("downloads", []) if old else []
            if old and not history:
                old_files = [{k: item[k] for k in ("os", "arch", "kind", "url", "bytes", "sha256")}
                             for item in old["artifacts"] if item["kind"] in {"APK", "EXE", "MSI"}]
                if old_files:
                    history = [dict(id=old["id"], version=old["version"], build=old["build"], notes=old.get("notes", {}), files=old_files)]
            for previous in history:
                if previous["build"] == build:
                    require(all(any(all(candidate.get(key) == value for key, value in item.items()) for candidate in download_files)
                                for item in previous["files"]), "Same-build refresh must preserve every download")
            downloads = ([dict(id=identifier, version=ver, build=build, notes=notes, files=download_files)] if download_files else [])
            downloads += [previous for previous in history if previous["build"] != build]
            downloads = downloads[:20]
            now = int(time.time() * 1000)
            release = dict(schema=1, channel=channel, sequence=sequence, id=identifier, version=ver, build=build,
                           publishedAtMillis=now, expiresAtMillis=now + days * 86_400_000, notes=notes, artifacts=artifacts,
                           downloads=downloads)
            payload = canonical_json(release)
            while len(payload) > 180_000 and len(downloads) > 1:
                downloads.pop(); payload = canonical_json(release)
            require(len(payload) <= 180_000, "Release payload too large")
            signature = openssl("dgst", "-sha256", "-sign", str(private), data=payload)
            envelope = dict(algorithm="RS256", payload=base64.b64encode(payload).decode("ascii"), signature=base64.b64encode(signature).decode("ascii"))
            encoded = canonical_json(envelope); require(len(encoded) <= MAX_METADATA, "Envelope too large")
            require(verify_envelope(envelope, public_der, temp) == release, "Signing self-check failed")
            folder = catalog / "artifacts"; require(not folder.is_symlink(), "Artifacts directory is symlinked")
            folder.mkdir(exist_ok=True)
            for source, name, expected in copies:
                copy_artifact(source, folder / name, expected)
            # Nothing above can expose partial metadata. Clients only see this final atomic commit.
            atomic_bytes(destination, encoded)
        return destination
    finally:
        lock.unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    key = sub.add_parser("keygen", help="Create a new OFFLINE signing key; never rotate an existing key")
    key.add_argument("--private-key", type=Path, required=True); key.add_argument("--public-key", type=Path, required=True)
    pub = sub.add_parser("publish", help="Validate, sign and atomically publish to a local release catalog")
    pub.add_argument("--spec", type=Path, required=True, help="JSON: channel, sequence, id, version, build, notes, artifacts (optional buildInfo per artifact)")
    pub.add_argument("--build-info", type=Path, required=True, help="Generated shared/build/generated/aitaClientBuild/client-build.json")
    pub.add_argument("--private-key", type=Path, required=True)
    pub.add_argument("--catalog", type=Path, required=True); pub.add_argument("--base-url", required=True)
    pub.add_argument("--expires-days", type=int, default=90)
    args = parser.parse_args()
    try:
        if args.command == "keygen": keygen(args.private_key, args.public_key)
        else: print("Published", publish(args.spec, args.build_info, args.private_key, args.catalog, args.base_url, args.expires_days))
    except (ValueError, OSError, KeyError, TypeError) as error:
        parser.exit(1, "Release not published: " + str(error) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
