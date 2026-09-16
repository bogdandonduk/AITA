#!/usr/bin/env python3
"""Run a frozen AITA source snapshot so another build cannot replace its live JVM classpath.

No source checkout, Git index, application data or global dependency cache is deleted.
Snapshots are intentionally retained: deleting one while its app runs recreates the bug.
--packaged runs a native app image, not a system-wide installer. --verify never launches AITA.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
from typing import Sequence

REQUIRED = (
    "settings.gradle.kts", "gradlew", "gradle/wrapper/gradle-wrapper.jar",
    "composeApp/build.gradle.kts", "shared/build.gradle.kts", "server/build.gradle.kts",
    "shared/src/commonMain/kotlin/kz/aita/ConnectionRetryWakeup.kt",
    "shared/src/commonMain/kotlin/kz/aita/ProfilePhoto.kt",
    "composeApp/src/commonMain/kotlin/kz/aita/UserProfilePhoto.kt",
    "composeApp/src/commonMain/kotlin/kz/aita/AccountPresentation.kt",
    "composeApp/src/commonMain/kotlin/kz/aita/TwoFactorMethodDropdown.kt",
    "server/src/main/kotlin/kz/aita/server/profile/ProfilePhotoRoutes.kt",
    "composeApp/src/jvmMain/resources/drawable/app_icon.icns",
    "composeApp/src/jvmMain/resources/drawable/app_icon.ico",
    "composeApp/src/jvmMain/resources/drawable/app_icon.png",
)
EXCLUDED_DIRECTORIES = {".git", ".gradle", ".kotlin", ".idea", "build", "node_modules", "__pycache__", ".venv"}
SECRET_EXTENSIONS = {".keystore", ".jks", ".p12", ".pfx", ".pem", ".key"}


class SnapshotError(RuntimeError):
    pass


def included(relative: str) -> bool:
    path = Path(relative)
    if path.is_absolute() or not path.parts or any(p in {"..", "."} for p in path.parts):
        raise SnapshotError("Unsafe source path")
    return not (any(p in EXCLUDED_DIRECTORIES for p in path.parts) or
                path.name in {"local.properties", ".DS_Store"} or
                path.name == ".env" or path.name.startswith(".env.") or
                path.suffix.lower() in SECRET_EXTENSIONS)


def source_paths(root: Path) -> list[str]:
    if (root / ".git").exists():
        unresolved = subprocess.check_output(["git", "-C", str(root), "ls-files", "--unmerged", "-z"])
        if unresolved:
            raise SnapshotError("Resolve the Git merge before taking a source snapshot.")
        # Working files, including added-but-untracked sources, not merely the last commit.
        raw = subprocess.check_output(["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "-z"])
        paths = {p for p in raw.decode().split("\0") if p}
    else:
        paths = set()
        for folder, directories, files in os.walk(root, followlinks=False):
            directories[:] = [d for d in directories if d not in EXCLUDED_DIRECTORIES]
            paths.update((Path(folder) / name).relative_to(root).as_posix() for name in files)
    result = sorted(p for p in paths if included(p) and (root / p).exists())
    missing = [p for p in REQUIRED if p not in result]
    if missing:
        raise SnapshotError("Incomplete project; missing sources:\n  " + "\n  ".join(missing))
    return result


def safe_source(root: Path, relative: str) -> Path:
    path = root
    for part in Path(relative).parts:
        path /= part
        if path.is_symlink():
            raise SnapshotError(f"Do not snapshot symlinked source paths: {relative}")
    if not path.is_file() or not path.resolve().is_relative_to(root):
        raise SnapshotError(f"Source is not an ordinary in-project file: {relative}")
    return path


def fingerprint(root: Path, paths: Sequence[str]) -> dict[str, str]:
    return {p: hashlib.sha256(safe_source(root, p).read_bytes()).hexdigest() for p in paths}


def copy_snapshot(root: Path, destination: Path, paths: Sequence[str]) -> None:
    before = fingerprint(root, paths)
    destination.mkdir(parents=True, exist_ok=False)
    for relative in paths:
        source = safe_source(root, relative)
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        if hashlib.sha256(target.read_bytes()).hexdigest() != before[relative]:
            raise SnapshotError(f"Source changed while copying: {relative}. Retry with editor saves finished.")
    if source_paths(root) != list(paths) or fingerprint(root, paths) != before:
        raise SnapshotError("Source changed while taking the snapshot. No build was started; retry.")
    # Carry only SDK location, never private values from the developer's local.properties.
    local = root / "local.properties"
    if local.is_file() and not local.is_symlink():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("sdk.dir="):
                (destination / "local.properties").write_text(line + "\n", encoding="utf-8")
                break


def gradle_command(packaged: bool = False, verify: bool = False) -> list[str]:
    executable = ["cmd", "/d", "/c", "gradlew.bat"] if os.name == "nt" else ["bash", "./gradlew"]
    tasks = ([":shared:compileKotlinJvm", ":shared:jvmJar", ":composeApp:compileKotlinJvm", ":server:compileKotlin",
              ":shared:compileTestKotlinJvm", ":composeApp:compileTestKotlinJvm", ":server:compileTestKotlin"]
             if verify else [":composeApp:runDistributable" if packaged else ":composeApp:run"])
    return executable + tasks + ["--no-daemon", "--no-configuration-cache", "--console=plain", "--stacktrace"]


def execute(root: Path, runs: Path, *, dry_run: bool = False, packaged: bool = False, verify: bool = False) -> int:
    root, runs = root.resolve(strict=True), runs.resolve()
    if root == Path(root.anchor) or root == Path.home().resolve() or runs.is_relative_to(root):
        raise SnapshotError("Use a project root and a snapshot directory outside that project.")
    paths = source_paths(root)
    # Validate also for a dry run; no files are changed or builds launched in dry-run mode.
    for path in paths:
        safe_source(root, path)
    print(f"Source snapshot: {len(paths)} files; destination parent: {runs}")
    print("Action:", "verify compilation only" if verify else "run native application image" if packaged else "run desktop JVM")
    if dry_run:
        return 0
    runs.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix=datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-"), dir=runs))
    if os.name != "nt":
        run.chmod(stat.S_IRWXU)
    target = run / "AITA"
    copy_snapshot(root, target, paths)
    environment = os.environ.copy()
    if sys.platform == "darwin" and not environment.get("JAVA_HOME"):
        environment["JAVA_HOME"] = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
    print(f"Frozen run: {target}\nDo not edit or delete this snapshot while its application is running.", flush=True)
    logfile = run / "gradle.log"
    with logfile.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(gradle_command(packaged, verify), cwd=target, env=environment,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
        assert process.stdout is not None
        for line in process.stdout:
            sys.stdout.write(line); log.write(line); log.flush()
        result = process.wait()
    print(f"Gradle exit code: {result}; log: {logfile}. Snapshot retained.")
    return result


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--runs-dir", type=Path, help="Private snapshot directory outside the source checkout")
    parser.add_argument("--dry-run", action="store_true", help="Check sources without copying or building")
    actions = parser.add_mutually_exclusive_group()
    actions.add_argument("--packaged", action="store_true", help="Run a native application image with its bundle identity; no installation")
    actions.add_argument("--verify", action="store_true", help="Compile JVM production/test sources without launching the app")
    options = parser.parse_args(argv)
    try:
        return execute(options.root, options.runs_dir or options.root.resolve().parent / "AITA_dev_runs",
                       dry_run=options.dry_run, packaged=options.packaged, verify=options.verify)
    except (SnapshotError, OSError, subprocess.CalledProcessError) as error:
        print(f"AITA snapshot was not launched: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
