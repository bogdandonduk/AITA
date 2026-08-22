#!/usr/bin/env python3
"""Create the machine-local wrangler.jsonc for the AITA Workers VPC gateway."""

from __future__ import annotations

import argparse
import json
import os
import re
import tempfile
from pathlib import Path

UUID_RE = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
    r"[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)
ROOT = Path(__file__).resolve().parent
EXAMPLE = ROOT / "wrangler.jsonc.example"
TARGET = ROOT / "wrangler.jsonc"


def atomic_write(path: Path, text: str) -> None:
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
    os.chmod(temporary_path, 0o600)
    os.replace(temporary_path, path)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Write wrangler.jsonc using the existing AITA VPC Service ID."
    )
    parser.add_argument("service_id", help="Workers VPC Service UUID, not a Tunnel ID or token")
    parser.add_argument(
        "--account-id",
        default="",
        help="Optional Cloudflare account ID; OAuth login normally makes it unnecessary",
    )
    args = parser.parse_args()

    service_id = args.service_id.strip()
    if not UUID_RE.fullmatch(service_id):
        parser.error("service_id must be the UUID shown on Workers VPC → VPC Services")

    data = json.loads(EXAMPLE.read_text(encoding="utf-8"))
    services = data.get("vpc_services")
    if not isinstance(services, list) or len(services) != 1:
        parser.error("wrangler.jsonc.example must contain exactly one VPC service binding")
    services[0]["service_id"] = service_id

    account_id = args.account_id.strip()
    if account_id:
        if not re.fullmatch(r"[0-9a-fA-F]{32}", account_id):
            parser.error("account ID must be a 32-character hexadecimal Cloudflare account ID")
        data["account_id"] = account_id

    atomic_write(TARGET, json.dumps(data, indent=2) + "\n")
    print(f"Created {TARGET}")
    print("  Worker: aita-api")
    print("  Binding: AITA_ORIGIN")
    print(f"  VPC Service ID: {service_id}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
