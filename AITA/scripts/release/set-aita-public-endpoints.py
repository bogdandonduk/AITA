#!/usr/bin/env python3
"""Synchronize AITA's single canonical workers.dev API endpoint.

Production clients are intentionally anchored to one registrar-independent
Cloudflare Worker. Localhost/LAN addresses remain available only through
explicit development overrides; no paid-domain API or bootstrap fallback is
written by this script.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[2]
COMMON_MAIN = Path("shared/src/commonMain/kotlin/kz/aita/CommonMain.kt")
SERVER_GLOBAL = Path("server/config/app/global.json")
LINUX_ENV = Path("scripts/linux-field-server/aita-prod.env.example")
WINDOWS_ENV = Path("scripts/windows-field-server/aita-prod.env.example")
WINDOWS_HEALTH = Path("scripts/windows-field-server/Watch-AitaHealth.ps1")
WINDOWS_READINESS = Path("scripts/windows-field-server/Test-AitaWindowsReadiness.ps1")
BOOTSTRAP_WORKER = Path("cloudflare/aita-bootstrap-worker.js")
API_WORKER = Path("cloudflare/aita-api-worker/src/index.js")
BOOTSTRAP_WRANGLER = Path("cloudflare/wrangler.aita-bootstrap.json")
API_WORKER_EXAMPLE = Path("cloudflare/aita-api-worker/wrangler.jsonc.example")
LINUX_DESKTOP = Path("scripts/linux-desktop/run-aita-desktop.sh")


def normalized_workers_dev_url(raw: str) -> str:
    value = raw.strip()
    parsed = urlsplit(value)
    hostname = (parsed.hostname or "").lower()
    if parsed.scheme != "https" or not hostname:
        raise ValueError(f"Expected an absolute HTTPS URL, got {raw!r}")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Public endpoint URLs must not contain credentials, query, or fragment")
    if not hostname.endswith(".workers.dev"):
        raise ValueError("The canonical AITA endpoint must use a workers.dev hostname")
    if parsed.port not in (None, 443):
        raise ValueError("The canonical workers.dev endpoint must use the default HTTPS port")
    path = parsed.path.rstrip("/")
    if path:
        raise ValueError("The canonical workers.dev endpoint must not contain a path")
    return urlunsplit((parsed.scheme, parsed.netloc, "", "", ""))


def atomic_write(path: Path, text: str) -> None:
    mode = path.stat().st_mode if path.exists() else 0o644
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="",
        dir=path.parent,
        prefix=f".{path.name}.",
        suffix=".tmp",
        delete=False,
    ) as temporary:
        temporary.write(text)
        temporary_path = Path(temporary.name)
    os.chmod(temporary_path, mode)
    os.replace(temporary_path, path)


def replace_once(text: str, pattern: str, replacement, description: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(f"Could not uniquely update {description}; matched {count} times")
    return updated


def kotlin_string_replacement(value: str):
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return lambda match: f'{match.group(1)}"{escaped}"'


def update_kotlin(path: Path, primary: str) -> None:
    text = path.read_text(encoding="utf-8")
    hostname = urlsplit(primary).hostname or ""
    values = {
        "DEFAULT_AITA_SERVER_URL": primary,
        "CANONICAL_AITA_PUBLIC_SERVER_HOST": hostname,
        "DEFAULT_AITA_FALLBACK_SERVER_URLS": "",
        # Direct-only production mode avoids an extra discovery request. The Worker still exposes
        # /.well-known/aita-server.json for old builds and diagnostics.
        "DEFAULT_AITA_BOOTSTRAP_URLS": "",
    }
    for name, value in values.items():
        pattern = rf'^(private const val {re.escape(name)}\s*=\s*)"(?:\\.|[^"\\])*"\s*$'
        text = replace_once(text, pattern, kotlin_string_replacement(value), name)
    atomic_write(path, text)


def update_global_json(path: Path, primary: str) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    data["payload"]["serverUrl"]["first"] = primary
    atomic_write(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def replace_env_value(text: str, key: str, value: str) -> str:
    return replace_once(text, rf'^{re.escape(key)}=.*$', f"{key}={value}", key)


def update_env(path: Path, primary: str) -> None:
    text = path.read_text(encoding="utf-8")
    origins = ",".join(
        [
            primary,
            "https://aita.kz",
            "https://www.aita.kz",
            "http://127.0.0.1:8080",
            "http://localhost:8080",
            "http://127.0.0.1:8090",
            "http://localhost:8090",
        ]
    )
    text = replace_env_value(text, "AITA_PUBLIC_SERVER_URL", primary)
    text = replace_env_value(text, "AITA_CORS_ALLOWED_ORIGINS", origins)
    if re.search(r"^AITA_PUBLIC_HEALTH_URL=", text, flags=re.MULTILINE):
        text = replace_env_value(text, "AITA_PUBLIC_HEALTH_URL", primary)
    if re.search(r"^AITA_DYNAMIC_GLOBAL_CONFIG_SERVER_URL_REWRITE=", text, flags=re.MULTILINE):
        text = replace_env_value(text, "AITA_DYNAMIC_GLOBAL_CONFIG_SERVER_URL_REWRITE", "true")
    atomic_write(path, text)


def update_powershell_default(path: Path, primary: str) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r"(\[string\]\$PublicUrl\s*=\s*)'[^']*'",
        lambda match: f"{match.group(1)}'{primary}'",
        f"PublicUrl in {path.name}",
    )
    atomic_write(path, text)


def update_javascript_constant(path: Path, name: str, primary: str) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_once(
        text,
        rf'^(const {re.escape(name)}\s*=\s*)"[^"]*";',
        lambda match: f'{match.group(1)}"{primary}";',
        f"{name} in {path.name}",
    )
    atomic_write(path, text)


def update_bootstrap_worker(path: Path, primary: str) -> None:
    update_javascript_constant(path, "DEFAULT_SERVER_URL", primary)


def update_json_vars(
    path: Path,
    updates: dict[str, str],
    removals: frozenset[str] = frozenset(),
    *,
    drop_custom_routes: bool = False,
) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    variables = data.setdefault("vars", {})
    for key in removals:
        variables.pop(key, None)
    variables.update(updates)
    if drop_custom_routes:
        data.pop("routes", None)
        data["preview_urls"] = False
    atomic_write(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def update_linux_desktop_help(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    replacements = {
        "using CommonMain.kt, optional bootstrap resolver, and /config/global global.json.":
            "using the single workers.dev production gateway and /config/global global.json.",
        "The default build uses the domain-independent workers.dev bootstrap; use --local-server only for origin troubleshooting.":
            "The default build uses the single domain-independent workers.dev gateway directly; use --local-server only for origin troubleshooting.",
        '${SERVER_URL:-bootstrap/default}': '${SERVER_URL:-workers.dev/default}',
    }
    for old, new in replacements.items():
        text = text.replace(old, new)
    atomic_write(path, text)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Synchronize AITA's single canonical workers.dev endpoint."
    )
    parser.add_argument("primary_worker_url", help="Canonical aita-api workers.dev HTTPS origin")
    parser.add_argument("--project-root", type=Path, default=ROOT)
    args = parser.parse_args()

    try:
        root = args.project_root.expanduser().resolve()
        primary = normalized_workers_dev_url(args.primary_worker_url)

        update_kotlin(root / COMMON_MAIN, primary)
        update_global_json(root / SERVER_GLOBAL, primary)
        update_env(root / LINUX_ENV, primary)
        update_env(root / WINDOWS_ENV, primary)
        update_powershell_default(root / WINDOWS_HEALTH, primary)
        update_powershell_default(root / WINDOWS_READINESS, primary)
        update_bootstrap_worker(root / BOOTSTRAP_WORKER, primary)
        update_javascript_constant(root / API_WORKER, "CANONICAL_PUBLIC_ORIGIN", primary)
        update_json_vars(
            root / BOOTSTRAP_WRANGLER,
            {},
            frozenset({"AITA_CURRENT_SERVER_URL", "AITA_LEGACY_SERVER_URL"}),
            drop_custom_routes=True,
        )
        update_json_vars(
            root / API_WORKER_EXAMPLE,
            {},
            frozenset({"AITA_LEGACY_SERVER_URL"}),
        )
        update_linux_desktop_help(root / LINUX_DESKTOP)
    except (KeyError, OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))

    print("AITA public endpoint synchronization complete")
    print(f"  canonical API: {primary}")
    print("  automatic API fallbacks: disabled")
    print("  automatic bootstrap requests: disabled")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
