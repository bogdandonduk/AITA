#!/usr/bin/env python3
"""Rebuild AITA's real shared/client/server interfaces after applying a source update.

Run without sudo, with IDE builds stopped. --reset-generated removes ONLY the
listed repository-local generated directories, never the global Gradle download
cache or application/database data. --dry-run validates and prints the plan.
"""
from __future__ import annotations

import argparse
import os
import re
from pathlib import Path
import shlex
import shutil
import subprocess
import sys
import zipfile
from typing import Mapping, Sequence, TextIO

MODULES = ("shared", "composeApp", "server")
GENERATED_PATHS = (".gradle", ".kotlin", "build", "shared/build", "composeApp/build", "server/build")
REQUIRED_FILES = (
    "settings.gradle.kts", "build.gradle.kts", "gradlew", "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    *(f"{module}/build.gradle.kts" for module in MODULES),
    "shared/src/commonMain/kotlin/kz/aita/MarketplaceModels.kt",
    "shared/src/commonMain/kotlin/kz/aita/MarketProductProfile.kt",
    "shared/src/commonMain/kotlin/kz/aita/MarketOfferDetail.kt",
    "shared/src/commonMain/kotlin/kz/aita/auth/AdvancedAuthenticationModels.kt",
    "shared/src/commonMain/kotlin/kz/aita/auth/ContactVerificationModels.kt",
    "shared/src/commonMain/kotlin/kz/aita/JsonCacheStorage.kt",
    "shared/src/commonMain/sqldelight/kz/aita/app_database.sq",
    "shared/src/jvmTest/kotlin/kz/aita/JsonCacheQueryBindingTest.kt",
    "composeApp/src/commonMain/kotlin/kz/aita/AuthenticationEmailUi.kt",
    "composeApp/src/commonMain/kotlin/kz/aita/ContactEmailConfirmation.kt",
    "server/src/main/kotlin/kz/aita/server/marketplace/MarketplaceRepository.kt",
)
COMPILE_TASKS = (
    ":shared:compileCommonMainKotlinMetadata",
    ":shared:compileKotlinJvm", ":shared:compileTestKotlinJvm", ":shared:jvmJar",
    ":composeApp:compileCommonMainKotlinMetadata",
    ":composeApp:compileKotlinJvm", ":composeApp:compileTestKotlinJvm",
    ":server:compileKotlin", ":server:compileTestKotlin",
)
# These are pure linkage/cache tests. Never run deployment or opt-in live DB tests.
LINKAGE_TASKS = (
    ":shared:jvmTest", "--tests", "kz.aita.JsonCacheQueryBindingTest",
    "--tests", "kz.aita.SharedProtocolLinkageTest",
    "--tests", "kz.aita.LegacyEventMessagesTest",
    ":composeApp:jvmTest", "--tests", "kz.aita.SharedProtocolClientLinkageTest",
    ":server:test", "--tests", "kz.aita.server.SharedProtocolServerLinkageTest",
)


class RebuildError(RuntimeError):
    pass


def is_within(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def checked_path(root: Path, relative: str) -> Path:
    """Refuse symlinked components instead of cleaning a different directory."""
    path = root
    for component in Path(relative).parts:
        if component in ("..", "."):
            raise RebuildError(f"Unsafe relative path: {relative}")
        path = path / component
        if path.is_symlink():
            raise RebuildError(f"Refusing symlinked repository path: {path}")
    if not is_within(path.resolve(), root):
        raise RebuildError(f"Path escapes repository: {path}")
    return path


def validate_root(root: Path) -> None:
    if root == Path(root.anchor) or root == Path.home().resolve():
        raise RebuildError("Refusing a filesystem root or your home directory.")
    missing = [name for name in REQUIRED_FILES if not checked_path(root, name).is_file()]
    if missing:
        raise RebuildError("Incomplete AITA source tree; restore the full bundle first:\n  " + "\n  ".join(missing))
    if (root / "AITA" / "settings.gradle.kts").exists():
        raise RebuildError("Nested AITA/AITA project detected. Use one canonical repository root.")


# Source-level guardrails for previously observed partial/conflicted patch applications.
# These are deliberately NOT a Kotlin type checker or a substitute for Gradle.
SOURCE_CONSISTENCY_FILES = (
    'shared/src/commonMain/kotlin/kz/aita/CommonMain.kt',
    'shared/src/commonMain/kotlin/kz/aita/MarketProductProfile.kt',
    'shared/src/commonMain/kotlin/kz/aita/MarketplaceModels.kt',
    'shared/src/commonMain/kotlin/kz/aita/MarketOfferDetail.kt',
    'shared/src/commonMain/kotlin/kz/aita/auth/AdvancedAuthenticationModels.kt',
    'shared/src/commonMain/kotlin/kz/aita/JsonCacheStorage.kt',
    'shared/src/commonMain/sqldelight/kz/aita/app_database.sq',
    'composeApp/src/commonMain/kotlin/kz/aita/AitaLoadingMotion.kt',
    'composeApp/src/commonMain/kotlin/kz/aita/CommonMainComposeWidgetsConfig.kt',
    'composeApp/src/commonMain/kotlin/kz/aita/CommonMainComposeMenuA.kt',
    'server/src/main/resources/db/migration/V112__marketplace_product_profiles_parent_storefronts.sql',
)


def kotlin_code_only(text: str) -> str:
    """Mask comments/literals, preserving offsets and newlines for structural checks."""
    out = list(text)
    i = 0
    while i < len(text):
        start = i
        if text.startswith('//', i):
            end = text.find('\n', i + 2)
            i = len(text) if end < 0 else end
        elif text.startswith('/*', i):
            depth = 1
            i += 2
            while i < len(text) and depth:
                if text.startswith('/*', i):
                    depth += 1; i += 2
                elif text.startswith('*/', i):
                    depth -= 1; i += 2
                else:
                    i += 1
        elif text.startswith('"""', i):
            end = text.find('"""', i + 3)
            i = len(text) if end < 0 else end + 3
        elif text[i] in ('"', "'"):
            quote = text[i]; i += 1
            while i < len(text):
                if text[i] == '\\':
                    i += 2
                elif text[i] == quote:
                    i += 1; break
                else:
                    i += 1
        else:
            i += 1; continue
        for j in range(start, min(i, len(text))):
            if text[j] != '\n':
                out[j] = ' '
    return ''.join(out)


def declaration_parameters(code: str, kind: str, name: str) -> str | None:
    pattern = (r'\bclass\s+' + re.escape(name) + r'\s*\(') if kind == 'class' else (
        r'\bfun\s+(?:AppConfiguration\.)?' + re.escape(name) + r'\s*\(')
    found = re.search(pattern, code)
    if found is None:
        return None
    start = found.end(); depth = 1
    for end in range(start, len(code)):
        depth += (code[end] == '(') - (code[end] == ')')
        if depth == 0:
            return code[start:end]
    return None


def source_consistency_errors(root: Path) -> list[str]:
    errors: list[str] = []
    code: dict[str, str] = {}
    for relative in SOURCE_CONSISTENCY_FILES:
        path = checked_path(root, relative)
        if not path.is_file():
            errors.append(f'Missing required integration source: {relative}')
        else:
            text = path.read_text(encoding='utf-8')
            code[relative] = kotlin_code_only(text) if path.suffix == '.kt' else text
    if errors:
        return errors

    duplicate_model_names = ('MarketProductDetails', 'MarketProductAttribute', 'StockMarketplaceProfile',
                             'MarketBranchAvailability', 'MarketListing', 'MarketStorefront', 'MarketOffer',
                             'AitaAuthFlowDataModel', 'AitaAuthenticationSettingsDataModel')
    shared_files = sorted((root / 'shared/src/commonMain/kotlin').rglob('*.kt'))
    models: dict[str, list[str]] = {name: [] for name in duplicate_model_names}
    for module in MODULES:
        for path in sorted((root / module / 'src').rglob('*.kt')):
            relative = path.relative_to(root).as_posix()
            # Never follow a source-tree symlink while preparing a destructive generated-only cleanup.
            checked_path(root, relative)
            masked = code.get(relative)
            if masked is None:
                masked = kotlin_code_only(path.read_text(encoding='utf-8'))
            if re.search(r'(?m)^\s*(?:<<<<<<<[^\n]*|=======[ \t]*|>>>>>>>[^\n]*)$', masked):
                errors.append(f'Unresolved conflict markers: {relative}')
            if module == 'shared' and path in shared_files:
                for name in duplicate_model_names:
                    models[name].extend([relative] * len(re.findall(r'\bclass\s+' + name + r'\b', masked)))
            if module == 'composeApp' and '/commonMain/' in relative:
                if re.search(r'\bAitaLoadingLayout\b', masked):
                    errors.append(f'Obsolete loading-layout API: {relative}')
                if re.search(r'\bfillMaxWidthIfTextPresent\s*=', masked):
                    errors.append(f'Obsolete action-button argument: {relative}')
    for name, locations in models.items():
        if len(locations) != 1:
            errors.append(f'{name} must have exactly one shared declaration (found {len(locations)}): ' + ', '.join(locations))

    shared = 'shared/src/commonMain/kotlin/kz/aita/'
    ui = 'composeApp/src/commonMain/kotlin/kz/aita/'
    for relative, kind, name, fields in (
        (shared+'CommonMain.kt', 'class', 'GoodsItemDataModel', ('marketplaceProfile',)),
        (shared+'auth/AdvancedAuthenticationModels.kt', 'class', 'AitaAuthFlowDataModel', ('flowId', 'nextStep')),
        (shared+'auth/AdvancedAuthenticationModels.kt', 'class', 'AitaAuthenticationSettingsDataModel', ('authenticatorEnabled', 'securityRevision')),
        (shared+'MarketplaceModels.kt', 'class', 'MarketListing', ('product',)),
        (shared+'MarketplaceModels.kt', 'class', 'MarketOffer', ('product',)),
        (shared+'MarketplaceModels.kt', 'class', 'MarketStorefront', ('shareBranchAvailability',)),
        (shared+'MarketplaceModels.kt', 'class', 'MarketListingUpdate', ('replaceProduct',)),
        (shared+'MarketOfferDetail.kt', 'class', 'MarketOfferDetailResult', ('branchAvailability', 'branchAvailabilityTruncated')),
        (ui+'CommonMainComposeWidgetsConfig.kt', 'fun', 'aitaFormTextField', ('passwordRevealed', 'onPasswordRevealedChange')),
        (ui+'CommonMainComposeWidgetsConfig.kt', 'fun', 'GoodsItemInStockWidget', ('shelfActionsEnabled',)),
        (ui+'CommonMainComposeMenuA.kt', 'fun', 'MessageText', ('loadingLayout',)),
    ):
        params = declaration_parameters(code[relative], kind, name)
        for field in fields:
            count = len(re.findall(r'\b' + field + r'\s*:', params or ''))
            if count != 1:
                errors.append(f'{name}.{field}: expected exactly one parameter, found {count} ({relative})')

    sql = code['shared/src/commonMain/sqldelight/kz/aita/app_database.sq']
    calls = code[shared+'JsonCacheStorage.kt']
    for parameter in ('sliceOffset', 'sliceLength', 'cacheKey', 'prefix', 'manifestKey', 'keepPrefix'):
        if not re.search(r':' + parameter + r'\b', sql) or not re.search(r'\b' + parameter + r'\s*=', calls):
            errors.append(f'SQLDelight source/caller binding is incomplete: {parameter}')
    for parameter in ('sliceOffset', 'sliceLength'):
        if not re.search(r'CAST\s*\(\s*:' + parameter + r'\s+AS\s+INTEGER\s*\)', sql, re.I):
            errors.append(f'Missing numeric SQL cast for {parameter}')

    versions: dict[tuple[int, ...], list[str]] = {}
    for path in sorted((root / 'server/src/main/resources/db/migration').glob('V*.sql')):
        match = re.match(r'V(\d+(?:[._]\d+)*)__', path.name)
        if match is None:
            errors.append(f'Invalid versioned migration filename: {path.name}'); continue
        parts = tuple(int(part) for part in re.split(r'[._]', match[1]))
        while len(parts) > 1 and parts[-1] == 0:
            parts = parts[:-1]
        versions.setdefault(parts, []).append(path.name)
    for names in versions.values():
        if len(names) > 1:
            errors.append('Duplicate migration version: ' + ', '.join(names))
    return errors


def validate_source_consistency(root: Path) -> None:
    errors = source_consistency_errors(root)
    if errors:
        raise RebuildError('Mixed or incomplete AITA source. No cleanup/build was performed.\n  ' +
                           '\n  '.join(errors[:40]) +
                           ('\n  (additional issues omitted)' if len(errors) > 40 else ''))


def cleanup_plan(root: Path, env: Mapping[str, str]) -> list[Path]:
    gradle_home = Path(env.get("GRADLE_USER_HOME") or Path.home() / ".gradle").expanduser().resolve()
    plan: list[Path] = []
    for relative in GENERATED_PATHS:
        path = checked_path(root, relative)
        if is_within(gradle_home, path.resolve()):
            raise RebuildError(f"Refusing to remove Gradle user-home/downloads at {gradle_home}.")
        if path.exists() and not path.is_dir():
            raise RebuildError(f"Expected a generated directory, found a file: {path}")
        if path.exists():
            plan.append(path)
    return plan


def wrapper_command(root: Path) -> list[str]:
    # ZIP extraction need not preserve gradlew's executable bit.
    if os.name == "nt":
        return [os.environ.get("COMSPEC", "cmd.exe"), "/d", "/c", str(root / "gradlew.bat")]
    shell = shutil.which("bash")
    if shell is None:
        raise RebuildError("Bash is required to run this repository's gradlew script.")
    return [shell, str(root / "gradlew")]


def build_command(root: Path, *, compile_only: bool, android: bool, offline: bool) -> list[str]:
    command = wrapper_command(root) + [
        "--no-daemon", "--no-configuration-cache", "--no-build-cache", "--rerun-tasks",
        "--console=plain", "--stacktrace", "--no-parallel",
        "-Pkotlin.incremental=false", "-Paita.serverOnly=false", "-Paita.webOnly=false",
    ]
    if offline:
        command.append("--offline")
    command.extend(COMPILE_TASKS)
    if android:
        command.extend((":shared:compileDebugKotlinAndroid", ":composeApp:compileDebugKotlinAndroid"))
    if not compile_only:
        command.extend(LINKAGE_TASKS)
    return command


def run(command: Sequence[str], root: Path, log: TextIO | None = None) -> int:
    print("+ " + shlex.join(command), flush=True)
    with subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, errors="replace") as process:
        assert process.stdout is not None
        try:
            for line in process.stdout:
                print(line, end="", flush=True)
                if log is not None:
                    log.write(line)
                    log.flush()
            return process.wait()
        except KeyboardInterrupt:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise



# Postconditions inspect Gradle's actual outputs, not copies of production declarations.
# This catches a missing/stale shared artifact behind a downstream unresolved-reference cascade.
REQUIRED_SHARED_CLASSES = (
    "kz/aita/auth/AitaAuthFlowDataModel.class",
    "kz/aita/auth/AitaAuthFlowDataModel$$serializer.class",
    "kz/aita/auth/AitaAuthenticationSettingsDataModel.class",
    "kz/aita/auth/AitaAuthenticationSettingsDataModel$$serializer.class",
    "kz/aita/auth/AitaAuthNextStep.class",
    "kz/aita/auth/AdvancedAuthenticationModelsKt.class",
    "kz/aita/AppDatabase.class",
)
GENERATED_QUERY_PARAMETERS = {
    "selectKvSlice": {"sliceOffset": "Long", "sliceLength": "Long", "cacheKey": "String"},
    "deleteKvPrefixExcept": {"prefix": "String", "manifestKey": "String", "keepPrefix": "String"},
}


def generated_query_errors(source: str) -> list[str]:
    """Check every generated overload; nullable and kotlin-qualified bindings are valid."""
    code = kotlin_code_only(source)
    errors: list[str] = []
    for method, expected in GENERATED_QUERY_PARAMETERS.items():
        declarations = list(re.finditer(r"\bfun\s+(?:<[^>]+>\s*)?" + method + r"\s*\(", code))
        if not declarations:
            errors.append(f"Missing generated query method: {method}")
            continue
        for declaration in declarations:
            start = declaration.end()
            end = start
            depth = 1
            while end < len(code) and depth:
                depth += (code[end] == '(') - (code[end] == ')')
                end += 1
            params = code[start:end - 1] if depth == 0 else ''
            for name, type_name in expected.items():
                pattern = (r"\b" + name + r"\s*:\s*(?:kotlin\.)?" + type_name +
                           r"\??\s*(?=,|$|=)")
                if not re.search(pattern, params):
                    errors.append(f"Generated {method}: expected {name}: {type_name} (optionally nullable)")
    return errors


def verify_compiled_outputs(root: Path) -> list[str]:
    """Reject an exit-zero wrapper without the expected shared JAR and regenerated queries.

    This is an artifact-presence/interface check after compilation, not a bytecode
    verifier, serializer round-trip test, UI test, or guarantee about IntelliJ's index.
    """
    libs = checked_path(root, "shared/build/libs")
    candidates = sorted(p for p in libs.glob("shared-jvm*.jar")
                        if not p.stem.endswith(("-sources", "-javadoc")))
    if len(candidates) != 1:
        raise RebuildError("Expected one shared/build/libs/shared-jvm*.jar after :shared:jvmJar, "
                           f"found {len(candidates)}. Run again with --reset-generated; "
                           "check the first Gradle failure instead of importing duplicate models.")
    jar = checked_path(root, candidates[0].relative_to(root).as_posix())
    try:
        with zipfile.ZipFile(jar) as archive:
            entries = archive.namelist()
            missing = [entry for entry in REQUIRED_SHARED_CLASSES if entries.count(entry) != 1]
            if missing:
                raise RebuildError("Shared JAR is missing or duplicates required classes/serializers:\n  " +
                                   "\n  ".join(missing))
            metadata = [entry for entry in entries
                        if entry.startswith("META-INF/") and entry.endswith(".kotlin_module")]
            if not metadata or not all(archive.getinfo(entry).file_size > 0 for entry in metadata):
                raise RebuildError("Shared JAR has no non-empty Kotlin module metadata.")
            for entry in REQUIRED_SHARED_CLASSES:
                with archive.open(entry) as compiled:
                    if compiled.read(4) != b"\xca\xfe\xba\xbe":
                        raise RebuildError(f"Invalid compiled class in shared JAR: {entry}")
    except (zipfile.BadZipFile, RuntimeError, OSError) as error:
        if isinstance(error, RebuildError):
            raise
        raise RebuildError(f"Cannot read compiled shared JAR: {error}") from error

    generated = checked_path(root, "shared/build/generated/sqldelight")
    query_files: list[Path] = []
    for path in sorted(generated.rglob("*Queries.kt")):
        path = checked_path(root, path.relative_to(root).as_posix())
        text = path.read_text(encoding="utf-8")
        masked = kotlin_code_only(text)
        if not any(re.search(r"\b" + method + r"\s*\(", masked)
                   for method in GENERATED_QUERY_PARAMETERS):
            continue
        query_files.append(path)
        errors = generated_query_errors(text)
        if errors:
            raise RebuildError(f"Stale/incompatible generated query interface in {path.relative_to(root)}:\n  " +
                               "\n  ".join(errors))
    if not query_files:
        raise RebuildError("No generated SQLDelight cache-query interface found under "
                           "shared/build/generated/sqldelight. Run --reset-generated.")
    return [f"Shared artifact checked: {jar.relative_to(root)}",
            "Authentication classes, generated serializers, and Kotlin module metadata are present.",
            f"Named SQLDelight cache bindings checked in {len(query_files)} generated interface(s)."]


def execute(root: Path, args: argparse.Namespace, env: Mapping[str, str]) -> int:
    validate_root(root)
    validate_source_consistency(root)
    plan = cleanup_plan(root, env) if args.reset_generated else []
    # Validate the log path before any changes too: do not follow a build/ or log symlink.
    log_path = checked_path(root, "build/aita-build-repair.log")
    command = build_command(root, compile_only=args.compile_only, android=args.android, offline=args.offline)
    print(f"AITA source root: {root}")
    print("Checks: common metadata; JVM shared/client/server production AND test compilation.")
    if not args.compile_only:
        print("Then: generated SQLite query bindings and selected real shared-API/legacy-message tests.")
    if args.reset_generated:
        print("Stop IDE builds before proceeding. Generated-only cleanup:")
        for path in plan:
            print(f"  {path.relative_to(root)}")
    if args.dry_run:
        if args.reset_generated:
            print("Would first run: " + shlex.join(wrapper_command(root) + ["--stop"]))
        print("Would run: " + shlex.join(command))
        print("Would then inspect the actual shared JAR and generated SQLDelight bindings.")
        print("Dry run only. No build, cleanup, or success certification was performed.")
        return 0
    if args.reset_generated:
        # Failure to start the pinned wrapper must not destroy even the old generated outputs.
        result = run(wrapper_command(root) + ["--stop"], root)
        if result:
            print("Gradle could not stop/start correctly; no generated directories were removed.", file=sys.stderr)
            return result
        # Revalidate immediately before removal; never trust only the initial plan.
        validate_root(root)
        validate_source_consistency(root)
        plan = cleanup_plan(root, env)
        for path in plan:
            shutil.rmtree(path)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    with log_path.open("w", encoding="utf-8") as log:
        log.write("AITA real compiler/linkage verification\n" + shlex.join(command) + "\n")
        result = run(command, root, log)
        if not result:
            try:
                for message in verify_compiled_outputs(root):
                    print(message, flush=True)
                    log.write(message + "\n")
            except (RebuildError, OSError) as error:
                result = 2
                message = f"Compiled-output verification FAILED: {error}"
                print(message, file=sys.stderr)
                log.write(message + "\n")
    if result:
        print(f"Build verification FAILED (exit {result}). No success is claimed.\nCompiler output: {log_path}", file=sys.stderr)
        return result
    print("Selected Gradle compilation" + ("" if args.compile_only else " and linkage tests") + " PASSED.")
    print("This does not verify rendered UI, live services, PostgreSQL integration, iOS, or a production deployment.")
    print(f"Compiler output: {log_path}")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reset-generated", action="store_true", help="Stop Gradle and remove only listed repository build caches before compiling.")
    parser.add_argument("--dry-run", action="store_true", help="Validate and show the plan without changing files or launching Gradle.")
    parser.add_argument("--compile-only", action="store_true", help="Compile all JVM production/test sources, without executing selected tests.")
    parser.add_argument("--android", action="store_true", help="Also compile Android debug source sets; needs an Android SDK.")
    parser.add_argument("--offline", action="store_true", help="Ask Gradle to use already-downloaded dependencies; it does not supply missing downloads.")
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[2]
    try:
        return execute(root, args, os.environ)
    except KeyboardInterrupt:
        print("Cancelled; verification is incomplete.", file=sys.stderr)
        return 130
    except (RebuildError, OSError) as error:
        print(f"Cannot verify AITA: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
