#!/usr/bin/env python3
"""Keep automatic client bootstrap requests disabled in direct-gateway mode."""

from __future__ import annotations

import argparse
import os
import re
import tempfile
from pathlib import Path

CONSTANT_NAME = "DEFAULT_AITA_BOOTSTRAP_URLS"
RELATIVE_TARGET = Path("shared/src/commonMain/kotlin/kz/aita/CommonMain.kt")
ROOT = Path(__file__).resolve().parents[2]


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


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Disable extra bootstrap requests; AITA uses its canonical workers.dev URL directly."
    )
    parser.add_argument(
        "bootstrap_urls",
        nargs="*",
        help="Deprecated; bootstrap URL lists are no longer accepted",
    )
    parser.add_argument("--project-root", type=Path, default=ROOT)
    args = parser.parse_args()

    if args.bootstrap_urls:
        parser.error(
            "AITA direct-gateway mode does not accept bootstrap fallbacks. "
            "Use set-aita-public-endpoints.py to change the canonical workers.dev endpoint."
        )

    target = args.project_root.expanduser().resolve() / RELATIVE_TARGET
    try:
        original = target.read_text(encoding="utf-8")
        pattern = re.compile(
            rf'(?m)^(\s*private\s+const\s+val\s+{CONSTANT_NAME}\s*=\s*)"(?:\\.|[^"\\])*"(\s*)$'
        )
        updated, count = pattern.subn(lambda match: f'{match.group(1)}""{match.group(2)}', original)
        if count != 1:
            raise RuntimeError(f"Expected exactly one {CONSTANT_NAME} declaration, found {count}")
        if updated != original:
            atomic_write(target, updated)
            state = "disabled"
        else:
            state = "already disabled"
    except (OSError, RuntimeError) as error:
        parser.error(str(error))

    print(f"{CONSTANT_NAME} {state}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
