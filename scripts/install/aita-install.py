#!/usr/bin/env python3
"""AITA verified Linux installer/updater and optional desktop Android ADB helper.

Download this file first, inspect it if desired, then run `python3 aita-install.py`.
No root invocation, cloud credentials, third-party Python packages, or shell piping.
"""
from __future__ import annotations

import argparse
import base64
import contextlib
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import ssl
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit

FEED = "https://aita-api.bogdan-donduk.workers.dev/client-updates/release.json"
PUBLIC_KEY = "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEA8jCMS4mrcZwVK3qHQmnfBd+Nr2clmJTgH6RcIcHhRcc9fGU3A9QfDrXougrPuQ3WDSfZQaoa6RKw/Yb6k2NqOG2ZRiqgKcxYpEsWYkgjpFfWP+JBEsJrFvjhEfy43QWYWaxDVV30se8LOrDHyfw/ajBqyninHcfPeVMqsoh5Jznc8dCQd3vBih85pTMbR2h9aUKw+rYkZhZMtbWoWhoXRyC+NIA8KHpEgKnCtPP355+cEi/GF+A9pPK8W3/b5uNiejFFR6nP/nqpg91d7zfxzYLFqbEUv9qxL9/wh0MSKODGYRx/PzK0zXxwumEsz1S0u9hEKLm9SCm8oAVgB7d77HJ4AbL5geetgW/xNaz8gk4f8B6V/RDxUvIRFDX5F4cw2DXIJQW1wQkk0gwSaN10XsEJ5RrpZ6bfOV9/SF2P6RZNmIhLGujVl2zZxJzjZJ7xim2PnrLkSJtpk8HeNrVY+WgO85H1yy+PU/vlV0ozE9KAlRerqfndIV7BZ+6bNGTxAgMBAAE="
TRUSTED_HOSTS = {"aita-api.bogdan-donduk.workers.dev", "api.aita.kz", "aita.kz"}
MAX_FEED = 262_144
MAX_INSTALLER = 2_147_483_648
FILE_KINDS = {"APK", "EXE", "MSI", "DEB", "RPM", "DMG", "PKG"}


class InstallError(Exception):
    """A concise, actionable user-facing error; never include HTTP bodies or secrets."""


def require(condition, message):
    if not condition:
        raise InstallError(message)


def integer(value, minimum, maximum):
    require(type(value) is int and minimum <= value <= maximum, "Invalid signed numeric metadata.")
    return value


def version(value):
    require(isinstance(value, str) and re.fullmatch(r"[0-9]{1,5}\.[0-9]{1,5}\.[0-9]{1,5}", value),
            "Invalid release version.")
    return tuple(map(int, value.split(".")))


def valid_url(value, kind=None):
    require(isinstance(value, str) and 10 <= len(value) <= 2048 and
            not any(c.isspace() or ord(c) < 32 or ord(c) == 127 for c in value) and "\\" not in value,
            "Unsafe download address.")
    try:
        u = urlsplit(value)
        okay = (u.scheme == "https" and u.hostname in TRUSTED_HOSTS and u.port in (None, 443)
                and not u.username and not u.password and not u.query and not u.fragment
                and not re.search(r"%(?:2f|5c|2e)", u.path, re.I) and "/../" not in u.path)
    except ValueError:
        okay = False
    require(okay, "The release points outside AITA's trusted HTTPS download hosts.")
    if kind:
        require(u.path.lower().endswith("." + kind.lower()), "The installer type does not match its address.")
    return value


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise InstallError("The download address redirected. Get a current installer script from aita.kz.")


def open_https(url):
    valid_url(url)
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=ssl.create_default_context()))
    return opener.open(urllib.request.Request(url, headers={"User-Agent": "AITA-Install/1", "Accept-Encoding": "identity"}), timeout=30)


def unique_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "Duplicate field in release metadata.")
            result[key] = value
        return result
    try:
        return json.loads(raw, object_pairs_hook=pairs, parse_constant=lambda _: (_ for _ in ()).throw(InstallError("Invalid JSON number.")))
    except (UnicodeError, ValueError, TypeError) as exc:
        raise InstallError("Unreadable release metadata. Retry with a stable connection.") from exc


def verify_envelope(raw, public_key=PUBLIC_KEY):
    require(len(raw) <= MAX_FEED, "Release metadata is too large.")
    envelope = unique_json(raw)
    require(isinstance(envelope, dict) and envelope.get("algorithm") == "RS256", "Unsupported release signature.")
    try:
        payload = base64.b64decode(envelope["payload"], validate=True)
        signature = base64.b64decode(envelope["signature"], validate=True)
        public = base64.b64decode(public_key, validate=True)
    except (KeyError, ValueError, TypeError) as exc:
        raise InstallError("Invalid release signature encoding.") from exc
    require(0 < len(payload) <= 180_000 and 256 <= len(signature) <= 1024, "Invalid release signature size.")
    openssl = shutil.which("openssl")
    require(openssl, "OpenSSL is required. Install your operating system's openssl package, then retry.")
    with tempfile.TemporaryDirectory(prefix="aita-verify-") as folder:
        key_path, sig_path = Path(folder) / "public.der", Path(folder) / "signature"
        key_path.write_bytes(public); sig_path.write_bytes(signature)
        checked = subprocess.run([openssl, "dgst", "-sha256", "-verify", str(key_path), "-keyform", "DER",
                                  "-signature", str(sig_path)], input=payload, capture_output=True, timeout=30)
    require(checked.returncode == 0, "The release signature could not be verified. Nothing was installed; retry later.")
    return unique_json(payload), hashlib.sha256(payload).hexdigest()


def validate_release(release, now=None, previous=None, payload_hash=None):
    now = int(time.time() * 1000) if now is None else now
    require(isinstance(release, dict) and type(release.get("schema")) is int and release.get("schema") == 1 and release.get("channel") == "RELEASE",
            "This is not an AITA production release.")
    integer(release.get("sequence"), 1, 9_007_199_254_740_991)
    integer(release.get("build"), 1, 2_100_000_000)
    version(release.get("version"))
    require(isinstance(release.get("id"), str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", release["id"]), "Invalid release identity.")
    published = integer(release.get("publishedAtMillis"), 1, 9_007_199_254_740_991)
    expires = integer(release.get("expiresAtMillis"), 1, 9_007_199_254_740_991)
    require(published <= now + 600_000 and now < expires and 0 < expires - published <= 366 * 86_400_000,
            "The release catalog is expired or your computer's clock is incorrect. Check the date and retry.")
    entries = release.get("artifacts")
    require(isinstance(entries, list) and 1 <= len(entries) <= 32, "The catalog has no valid installer list.")
    identities = set()
    for artifact in entries:
        require(isinstance(artifact, dict), "Invalid installer entry.")
        identity = (artifact.get("os"), artifact.get("arch", "UNIVERSAL"), artifact.get("kind"))
        require(all(isinstance(v, str) for v in identity) and identity not in identities, "Duplicate or invalid installer entry.")
        identities.add(identity)
    if previous:
        require(release["sequence"] >= integer(previous.get("sequence"), 1, 9_007_199_254_740_991)
                and release["build"] >= integer(previous.get("build"), 1, 2_100_000_000)
                and version(release["version"]) >= version(previous.get("version")),
                "An older release catalog was received. AITA will not downgrade your installation.")
        if release["sequence"] == previous["sequence"]:
            require(previous.get("payloadHash") == payload_hash, "The signed catalog changed without a new sequence. Retry later.")
    return release


def select_artifact(release, target_os, arch, kind, os_major=0):
    require(kind in {"APK", "EXE", "MSI", "DEB", "RPM"}, "This installer type is not supported by the helper.")
    candidates = [a for a in release["artifacts"] if a.get("os") == target_os and a.get("kind") == kind
                  and a.get("arch", "UNIVERSAL") in (arch, "UNIVERSAL")]
    candidates.sort(key=lambda a: a.get("arch", "UNIVERSAL") != arch)
    require(candidates, "No compatible installer is published for this device. Use AITA on the web at https://aita.kz.")
    artifact = candidates[0]
    require(integer(artifact.get("minimumOsMajor", 0), 0, 10000) <= os_major, "This release requires a newer operating system.")
    integer(artifact.get("bytes"), 1, MAX_INSTALLER)
    require(isinstance(artifact.get("sha256"), str) and re.fullmatch(r"[0-9a-f]{64}", artifact["sha256"]), "Invalid installer checksum.")
    valid_url(artifact.get("url"), kind)
    return artifact


def fetch_catalog():
    with open_https(FEED) as response:
        require(response.status == 200, "Release service is temporarily unavailable.")
        data = response.read(MAX_FEED + 1)
    require(len(data) <= MAX_FEED, "Release metadata is too large.")
    return verify_envelope(data)


def private_directory(path):
    # Reject symlinked parents, including pre-existing cache directories; never follow an attacker-controlled path.
    path = path.absolute()
    for current in list(reversed(path.parents)) + [path]:
        require(not current.is_symlink(), "The installer folder contains a symbolic link. Use a normal home directory.")
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    info = path.stat()
    require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid(), "The installer folder belongs to another user.")
    os.chmod(path, 0o700)
    return path


def atomic_json(path, value):
    require(not path.is_symlink(), "Unsafe installer state file.")
    fd, name = tempfile.mkstemp(prefix=".state-", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(value, f, separators=(",", ":")); f.flush(); os.fsync(f.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


@contextlib.contextmanager
def installer_lock(folder):
    import fcntl
    descriptor = os.open(folder / "install.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise InstallError("Another AITA installer is already running. Finish or close that window first.") from exc
        yield
    finally:
        os.close(descriptor)


def verify_file(path, artifact):
    require(not path.is_symlink() and path.is_file(), "The installer file is missing or unsafe.")
    h = hashlib.sha256(); count = 0
    with path.open("rb") as f:
        for data in iter(lambda: f.read(1024 * 1024), b""):
            count += len(data)
            require(count <= artifact["bytes"], "Installer size does not match the signed catalog.")
            h.update(data)
    require(count == artifact["bytes"] and h.hexdigest() == artifact["sha256"],
            "Installer integrity check failed. Nothing was installed; download again.")


def download(artifact, folder, report, opener=open_https):
    path = folder / (artifact["sha256"] + "." + artifact["kind"].lower())
    if path.exists():
        verify_file(path, artifact)
        report("Already downloaded; checksum verified.")
        return path
    require(shutil.disk_usage(folder).free >= artifact["bytes"] + 64 * 1024 * 1024,
            "Not enough free disk space for the installer. Free space, then retry.")
    for attempt in range(1, 4):
        temporary = folder / ("download-" + os.urandom(8).hex() + ".partial")
        try:
            with opener(artifact["url"]) as response, temporary.open("xb") as output:
                require(response.status == 200, "Download service returned an unexpected response.")
                announced = response.headers.get("Content-Length")
                require(announced is None or announced == str(artifact["bytes"]), "Download size differs from the signed catalog.")
                count = 0; last_bucket = -1
                while True:
                    data = response.read(256 * 1024)
                    if not data:
                        break
                    count += len(data)
                    require(count <= artifact["bytes"], "Download exceeds its signed size.")
                    output.write(data)
                    bucket = int(count * 100 / artifact["bytes"]) // 10
                    if bucket > last_bucket:
                        report(f"Downloading: {min(100, bucket * 10)}%")
                        last_bucket = bucket
                if count < artifact["bytes"]:
                    raise ConnectionError("Download ended before the signed byte count")
                output.flush(); os.fsync(output.fileno())
            verify_file(temporary, artifact)
            os.replace(temporary, path)
            return path
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError) as exc:
            if attempt == 3:
                raise InstallError("Download interrupted three times. Check your connection and rerun this script.") from exc
            report(f"Connection interrupted; retrying ({attempt}/3).")
            time.sleep(attempt)
        finally:
            temporary.unlink(missing_ok=True)
    raise InstallError("Download did not finish.")


def command_output(args, timeout=30):
    completed = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    require(completed.returncode == 0, "A required device/package check failed: " + Path(args[0]).name)
    return completed.stdout.strip()


def linux_target():
    require(sys.platform.startswith("linux"), "For Windows use aita-install.ps1. Apple installers are not published yet.")
    arch = {"x86_64": "X64", "amd64": "X64", "aarch64": "ARM64", "arm64": "ARM64"}.get(platform.machine().lower())
    require(arch, "This Linux CPU architecture has no supported native AITA installer. Use https://aita.kz.")
    # Prefer the package system owning the installed AITA, not an incidental secondary package manager.
    dpkg, rpm = shutil.which("dpkg-query"), shutil.which("rpm")
    if dpkg and subprocess.run([dpkg, "-W", "-f=${Status}", "aita"], capture_output=True).returncode == 0:
        return arch, "DEB"
    if rpm and subprocess.run([rpm, "-q", "aita"], capture_output=True).returncode == 0:
        return arch, "RPM"
    if shutil.which("apt-get") and shutil.which("dpkg-deb"):
        return arch, "DEB"
    require(rpm and (shutil.which("dnf") or shutil.which("zypper")), "This Linux distribution needs a DEB/apt or RPM/dnf/zypper package manager.")
    return arch, "RPM"


def linux_install_command(path, artifact, release):
    arch = artifact.get("arch", "UNIVERSAL")
    if artifact["kind"] == "DEB":
        tool = shutil.which("dpkg-deb")
        require(tool, "dpkg-deb is missing. Install the distribution's dpkg tools and retry.")
        package = command_output([tool, "-f", str(path), "Package"])
        package_version = command_output([tool, "-f", str(path), "Version"])
        package_arch = command_output([tool, "-f", str(path), "Architecture"])
        require(package == "aita" and package_version.split("-")[0] == release["version"]
                and package_arch == {"X64": "amd64", "ARM64": "arm64"}.get(arch), "The DEB package identity does not match the signed release.")
        installed = subprocess.run(["dpkg-query", "-W", "-f=${Version}", "aita"], capture_output=True, text=True)
        if installed.returncode == 0:
            require(subprocess.run(["dpkg", "--compare-versions", installed.stdout.strip(), "le", package_version]).returncode == 0,
                    "A newer AITA package is already installed; downgrade refused.")
        manager = shutil.which("apt-get")
        require(manager, "apt-get is missing.")
        return ["sudo", manager, "install", "--reinstall", "--no-remove", "--", str(path)]
    rpm = shutil.which("rpm")
    require(rpm, "RPM tools are missing.")
    package = command_output([rpm, "-qp", "--queryformat", "%{NAME}\n%{VERSION}\n%{ARCH}", str(path)]).splitlines()
    require(package == ["aita", release["version"], {"X64": "x86_64", "ARM64": "aarch64"}.get(arch)],
            "The RPM package identity does not match the signed release.")
    installed = subprocess.run([rpm, "-q", "--queryformat", "%{VERSION}", "aita"], capture_output=True, text=True)
    if installed.returncode == 0:
        require(version(installed.stdout.strip()) <= version(release["version"]), "A newer AITA package is already installed; downgrade refused.")
    manager = shutil.which("dnf") or shutil.which("zypper")
    require(manager, "Use a supported dnf or zypper distribution.")
    return ["sudo", manager, "install", "--", str(path)]


def android_target(serial):
    require(serial and re.fullmatch(r"[A-Za-z0-9_.:-]{1,120}", serial), "Supply --serial with the device ID from 'adb devices'.")
    adb = shutil.which("adb")
    require(adb, "Install Android SDK Platform Tools on this computer, connect/unlock your phone, and approve USB debugging first.")
    base = [adb, "-s", serial]
    require(command_output(base + ["get-state"]) == "device", "Unlock your phone and approve this computer's USB debugging connection.")
    abi = command_output(base + ["shell", "getprop", "ro.product.cpu.abi"])
    arch = {"arm64-v8a": "ARM64", "armeabi-v7a": "ARM32", "x86_64": "X64", "x86": "X86"}.get(abi)
    require(arch, "The connected phone's CPU is not supported.")
    sdk = command_output(base + ["shell", "getprop", "ro.build.version.sdk"])
    require(sdk.isdigit(), "Cannot read Android version from the connected phone.")
    return base, arch, int(sdk)


def running_aita():
    """Read exact executable names; never kill Java, another app, or a different user's process."""
    result = []
    if sys.platform.startswith("linux"):
        for entry in Path("/proc").iterdir():
            if not entry.name.isdigit():
                continue
            try:
                if entry.stat().st_uid == os.getuid() and Path(os.readlink(entry / "exe")).name == "AITA":
                    result.append(int(entry.name))
            except (OSError, ValueError):
                pass
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description="Verify and install/update AITA on Linux; optionally install its Android APK using desktop ADB.")
    parser.add_argument("--check", action="store_true", help="Verify catalog and show the compatible latest release; do not download/install.")
    parser.add_argument("--download-only", action="store_true", help="Verify and save the installer without starting it.")
    parser.add_argument("--android", action="store_true", help="Install to an explicitly selected Android device via desktop ADB.")
    parser.add_argument("--serial", help="Exact device ID from adb devices; required with --android.")
    args = parser.parse_args(argv)
    require(os.name == "posix", "On Windows use aita-install.ps1.")
    require(os.geteuid() != 0, "Run this script as your normal user, WITHOUT sudo. It asks for sudo only when installing the verified package.")
    require(not args.serial or args.android, "--serial is only used together with --android.")
    root = private_directory(Path.home() / ".local" / "state" / "aita-installer")
    log_path = root / (time.strftime("install-%Y%m%d-%H%M%S-") + os.urandom(4).hex() + ".log")
    with log_path.open("x", encoding="utf-8") as log:
        def report(message):
            print(message, flush=True); log.write(time.strftime("%H:%M:%S ") + message + "\n"); log.flush()
        report("AITA · verified installation and updates")
        report("Log: " + str(log_path))
        try:
            with installer_lock(root):
                if args.android:
                    base, arch, major = android_target(args.serial)
                    target, kind = "ANDROID", "APK"
                else:
                    arch, kind = linux_target()
                    target, major = "LINUX", 0
                report("[1/5] Checking the signed release catalog…")
                state_path = root / "trusted-release.json"
                previous = None
                require(not state_path.is_symlink(), "Unsafe saved release state.")
                if state_path.exists():
                    require(not state_path.is_symlink() and state_path.stat().st_size <= MAX_FEED, "Unsafe saved release state.")
                    previous = unique_json(state_path.read_bytes())
                    require(isinstance(previous, dict), "Saved release state is damaged. Contact support; do not disable verification.")
                release, payload_hash = fetch_catalog()
                validate_release(release, previous=previous, payload_hash=payload_hash)
                artifact = select_artifact(release, target, arch, kind, major)
                atomic_json(state_path, {"sequence": release["sequence"], "build": release["build"], "version": release["version"], "payloadHash": payload_hash})
                report(f"Verified: AITA {release['version']} · build {release['build']} · {target} {arch} {kind}")
                if args.check:
                    report("CHECKED: no installer was downloaded or started."); return 0
                report("[2/5] Downloading and checking every byte…")
                path = download(artifact, root, report)
                report("Verified installer: " + str(path))
                if args.download_only:
                    report("DOWNLOADED: installation was not started."); return 0
                report("[3/5] Preparing the normal operating-system installer…")
                command = base + ["install", "-r", str(path)] if args.android else linux_install_command(path, artifact, release)
                report("Save your work and close AITA. Existing account data and offline caches are preserved.")
                require(input("Install this verified release now? [y/N]: ").strip().lower() in ("y", "yes"), "Cancelled. The verified installer is retained for another attempt.")
                require(args.android or not running_aita(), "AITA is still running. Close its windows normally, then rerun this script.")
                verify_file(path, artifact)
                report("[4/5] Installing. Approve the normal OS prompt; progress appears below.")
                # Inherited terminal is intentional: sudo and device confirmation must stay visible.
                code = subprocess.call(command)
                require(code == 0, f"The system installer returned code {code}. No data was deleted. Resolve the displayed OS error and rerun; installer retained.")
                report("[5/5] The system installer reported success. Removing only this helper's verified installer.")
                path.unlink()
                report("INSTALLED: open AITA from your app menu. If the OS requested a restart, restart when convenient.")
                report("Your log remains at: " + str(log_path))
                return 0
        except (InstallError, OSError, urllib.error.URLError, subprocess.SubprocessError) as exc:
            message = str(exc) if isinstance(exc, InstallError) else "A network or operating-system operation failed. Check connectivity, permissions and available space, then retry."
            report("STOPPED: " + message)
            return 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (InstallError, KeyboardInterrupt, EOFError) as exc:
        print("STOPPED: " + (str(exc) if isinstance(exc, InstallError) else "Cancelled; no forced process termination."), file=sys.stderr)
        sys.exit(1)
