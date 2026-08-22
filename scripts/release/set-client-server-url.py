#!/usr/bin/env python3
"""Compatibility wrapper for AITA's central public-endpoint synchronizer."""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SYNCHRONIZER = Path(__file__).with_name("set-aita-public-endpoints.py")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Set AITA's single workers.dev endpoint everywhere in the repository."
    )
    parser.add_argument("server_url", help="Canonical workers.dev HTTPS origin")
    parser.add_argument("--project-root", type=Path, default=ROOT)
    args = parser.parse_args()

    command = [
        sys.executable,
        str(SYNCHRONIZER),
        args.server_url,
        "--project-root",
        str(args.project_root.expanduser().resolve()),
    ]
    return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
