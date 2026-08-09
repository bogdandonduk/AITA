#!/usr/bin/env python3
"""Synchronize AITA's canonical public API/bootstrap URLs across the repository.

The workers.dev API is the canonical registrar-independent endpoint. The paid
aita.kz domain remains an ordered legacy fallback and optional vanity alias.
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
BOOTSTRAP_WRANGLER = Path("cloudflare/wrangler.aita-bootstrap.json")
API_WORKER_EXAMPLE = Path("cloudflare/aita-api-worker/wrangler.jsonc.example")
LINUX_DESKTOP = Path("scripts/linux-desktop/run-aita-desktop.sh")


def normalized_http_url(raw: str, *, workers_dev_required: bool = False) -> str:
    value = raw.strip()
    parsed = urlsplit(value)
    if parsed.scheme != "https" or not parsed.hostname:
        raise ValueError(f"Expected an absolute HTTPS URL, got {raw!r}")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("Public endpoint URLs must not contain credentials, query, or fragment")
    if workers_dev_required and not parsed.hostname.endswith(".workers.dev"):
        raise ValueError("The canonical domainless endpoint must use a workers.dev hostname")
    path = parsed.path.rstrip("/")
    return urlunsplit((parsed.scheme, parsed.netloc, path, "", ""))


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


def replace_once(text: str, pattern: str, replacement: str, description: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise RuntimeError(f"Could not uniquely update {description}; matched {count} times")
    return updated


def update_kotlin(path: Path, primary: str, legacy: str, legacy_bootstrap: str) -> None:
    text = path.read_text(encoding="utf-8")
    bootstrap_urls = ",".join(
        [
            f"{primary}/.well-known/aita-server.json",
            legacy_bootstrap,
            f"{legacy}/.well-known/aita-server.json",
        ]
    )
    values = {
        "DEFAULT_AITA_SERVER_URL": primary,
        "DEFAULT_AITA_FALLBACK_SERVER_URLS": legacy,
        "DEFAULT_AITA_BOOTSTRAP_URLS": bootstrap_urls,
    }
    for name, value in values.items():
        pattern = rf'^(private const val {re.escape(name)}\s*=\s*)"(?:\\.|[^"\\])*"\s*$'
        text = replace_once(text, pattern, lambda_match_replacement(value), name)
    atomic_write(path, text)


def lambda_match_replacement(value: str):
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return lambda match: f'{match.group(1)}"{escaped}"'


def update_global_json(path: Path, primary: str) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    data["payload"]["serverUrl"]["first"] = primary
    atomic_write(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def replace_env_value(text: str, key: str, value: str) -> str:
    return replace_once(text, rf'^{re.escape(key)}=.*$', f"{key}={value}", key)


def update_env(path: Path, primary: str, legacy: str) -> None:
    text = path.read_text(encoding="utf-8")
    origins = ",".join(
        [
            primary,
            legacy,
            "https://aita.kz",
            "https://www.aita.kz",
            "http://127.0.0.1:8080",
            "http://localhost:8080",
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


def update_bootstrap_worker(path: Path, primary: str, legacy: str) -> None:
    text = path.read_text(encoding="utf-8")
    text = replace_once(
        text,
        r'^(const DEFAULT_SERVER_URL\s*=\s*)"[^"]*";',
        lambda match: f'{match.group(1)}"{primary}";',
        "bootstrap DEFAULT_SERVER_URL",
    )
    text = replace_once(
        text,
        r'^(const DEFAULT_LEGACY_SERVER_URL\s*=\s*)"[^"]*";',
        lambda match: f'{match.group(1)}"{legacy}";',
        "bootstrap DEFAULT_LEGACY_SERVER_URL",
    )
    atomic_write(path, text)


def update_json_var(path: Path, key: str, value: str) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    data.setdefault("vars", {})[key] = value
    atomic_write(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")


def update_linux_desktop_help(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    text = text.replace(
        "Use --local-server while api.aita.kz DNS/Tunnel routing is being repaired.",
        "The default build uses the domain-independent workers.dev bootstrap; use --local-server only for origin troubleshooting.",
    )
    atomic_write(path, text)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Synchronize AITA's workers.dev primary endpoint and legacy domain fallbacks."
    )
    parser.add_argument("primary_worker_url", help="Canonical aita-api workers.dev HTTPS origin")
    parser.add_argument(
        "--legacy-server-url",
        default="https://api.aita.kz",
        help="Optional legacy API fallback",
    )
    parser.add_argument(
        "--legacy-bootstrap-url",
        default="https://bootstrap.aita.kz/.well-known/aita-server.json",
        help="Optional legacy bootstrap fallback",
    )
    parser.add_argument("--project-root", type=Path, default=ROOT)
    args = parser.parse_args()

    try:
        root = args.project_root.expanduser().resolve()
        primary = normalized_http_url(args.primary_worker_url, workers_dev_required=True)
        legacy = normalized_http_url(args.legacy_server_url)
        legacy_bootstrap = normalized_http_url(args.legacy_bootstrap_url)

        update_kotlin(root / COMMON_MAIN, primary, legacy, legacy_bootstrap)
        update_global_json(root / SERVER_GLOBAL, primary)
        update_env(root / LINUX_ENV, primary, legacy)
        update_env(root / WINDOWS_ENV, primary, legacy)
        update_powershell_default(root / WINDOWS_HEALTH, primary)
        update_powershell_default(root / WINDOWS_READINESS, primary)
        update_bootstrap_worker(root / BOOTSTRAP_WORKER, primary, legacy)
        update_json_var(root / BOOTSTRAP_WRANGLER, "AITA_CURRENT_SERVER_URL", primary)
        update_json_var(root / BOOTSTRAP_WRANGLER, "AITA_LEGACY_SERVER_URL", legacy)
        update_json_var(root / API_WORKER_EXAMPLE, "AITA_LEGACY_SERVER_URL", legacy)
        update_linux_desktop_help(root / LINUX_DESKTOP)
    except (KeyError, OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        parser.error(str(error))

    print("AITA public endpoint synchronization complete")
    print(f"  primary API/bootstrap: {primary}")
    print(f"  legacy API fallback:   {legacy}")
    print(f"  legacy bootstrap:      {legacy_bootstrap}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
