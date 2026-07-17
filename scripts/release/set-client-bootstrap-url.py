#!/usr/bin/env python3
"""Set the ordered AITA bootstrap resolver URLs in CommonMain.kt."""

from __future__ import annotations

import argparse
import os
import re
import tempfile
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

CONSTANT_NAME = "DEFAULT_AITA_BOOTSTRAP_URLS"
RELATIVE_TARGET = Path("shared/src/commonMain/kotlin/kz/aita/CommonMain.kt")


def project_root_from_script() -> Path:
    return Path(__file__).resolve().parents[2]


def normalize_http_url(raw_value: str) -> str:
    value = raw_value.strip()
    parsed = urlsplit(value)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError(f"Invalid bootstrap URL {raw_value!r}: expected absolute http(s) URL")
    if parsed.username or parsed.password:
        raise ValueError(f"Invalid bootstrap URL {raw_value!r}: credentials are not allowed")
    if parsed.query or parsed.fragment:
        raise ValueError(f"Invalid bootstrap URL {raw_value!r}: query and fragment are not allowed")

    path = parsed.path.rstrip("/")
    return urlunsplit((parsed.scheme, parsed.netloc, path, "", ""))


def flatten_url_arguments(raw_values: list[str]) -> list[str]:
    flattened: list[str] = []
    for raw_value in raw_values:
        flattened.extend(part for part in re.split(r"[,\s]+", raw_value) if part)
    return flattened


def distinct_normalized_urls(raw_values: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for raw_value in flatten_url_arguments(raw_values):
        normalized = normalize_http_url(raw_value)
        if normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    if not result:
        raise ValueError("At least one bootstrap URL is required")
    return result


def kotlin_string(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"')


def atomic_write(path: Path, text: str) -> None:
    mode = path.stat().st_mode
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


def replace_constant(target: Path, value: str) -> bool:
    original = target.read_text(encoding="utf-8")
    pattern = re.compile(
        rf'(?m)^(\s*private\s+const\s+val\s+{re.escape(CONSTANT_NAME)}\s*=\s*)"(?:\\.|[^"\\])*"(\s*)$'
    )
    replacement_value = kotlin_string(value)
    updated, count = pattern.subn(
        lambda match: f'{match.group(1)}"{replacement_value}"{match.group(2)}',
        original,
    )
    if count != 1:
        raise RuntimeError(
            f"Expected exactly one {CONSTANT_NAME} declaration in {target}, found {count}"
        )
    if updated == original:
        return False
    atomic_write(target, updated)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Set DEFAULT_AITA_BOOTSTRAP_URLS in the shared KMP client."
    )
    parser.add_argument(
        "bootstrap_urls",
        nargs="+",
        help="One or more absolute http(s) bootstrap URLs; commas are also accepted",
    )
    parser.add_argument(
        "--project-root",
        type=Path,
        default=project_root_from_script(),
        help="AITA project root (defaults to the root containing this script)",
    )
    args = parser.parse_args()

    try:
        normalized_urls = distinct_normalized_urls(args.bootstrap_urls)
        target = args.project_root.expanduser().resolve() / RELATIVE_TARGET
        if not target.is_file():
            raise FileNotFoundError(f"CommonMain.kt was not found at {target}")
        joined = ",".join(normalized_urls)
        changed = replace_constant(target, joined)
    except (OSError, RuntimeError, ValueError) as error:
        parser.error(str(error))

    state = "updated" if changed else "already set"
    print(f"{CONSTANT_NAME} {state}: {','.join(normalized_urls)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
