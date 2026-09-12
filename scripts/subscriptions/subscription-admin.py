#!/usr/bin/env python3
"""Prepare subscription administration SQL without connecting to any database.

Examples:
  python3 scripts/subscriptions/subscription-admin.py promo --type lifetime --region KZ --sql /tmp/lifetime.sql
  python3 scripts/subscriptions/subscription-admin.py promo --type timed --duration-days 30 --sql /tmp/month.sql
  python3 scripts/subscriptions/subscription-admin.py promo --type discount --discount-percent 25 --region KZ --sql /tmp/discount.sql
  python3 scripts/subscriptions/subscription-admin.py price --region KZ --currency KZT --price-minor 799000 --sql /tmp/kz-price.sql

Review the SQL, then run it explicitly with psql against the intended database after V101.
No database credentials, plaintext code, or SQL execution are built into this program.
A generated high-entropy code is printed once; SQL stores only its normalized SHA-256 hash.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import os
from pathlib import Path
import re
import secrets
import sys
import uuid
from typing import Sequence

MAX_MINOR = 1_000_000_000_000
MAX_DURATION_MS = 3_153_600_000_000


def normalize_code(raw: str) -> str:
    code = raw.strip()
    if not re.fullmatch(r"[A-Za-z0-9_-]{6,96}", code):
        raise ValueError("Codes need 6–96 ASCII letters, digits, hyphens or underscores")
    return code.upper()


def exact_scaled(raw: str, factor: int, maximum: int, label: str) -> int:
    try:
        value = Decimal(raw) * factor
        if not value.is_finite() or value != value.to_integral_value() or not 1 <= value <= maximum:
            raise ValueError(f"Invalid {label}: use a positive, exact supported amount")
        return int(value)
    except InvalidOperation as error:
        raise ValueError(f"Invalid {label}") from error


def normalized_tag(raw: str | None, length: int, label: str) -> str | None:
    if raw is None:
        return None
    if not re.fullmatch(rf"[A-Za-z]{{{length}}}", raw):
        raise ValueError(f"{label} must contain exactly {length} ASCII letters")
    return raw.upper()


def timestamp(raw: str | None) -> int | None:
    if raw is None:
        return None
    try:
        value = datetime.fromisoformat(raw.replace("Z", "+00:00"))
        if value.tzinfo is None:
            raise ValueError("Validity dates must include a timezone (for example +00:00)")
        delta = value.astimezone(timezone.utc) - datetime(1970, 1, 1, tzinfo=timezone.utc)
        millis = (delta.days * 86400 + delta.seconds) * 1000 + delta.microseconds // 1000
        if millis < 0:
            raise ValueError("Validity dates cannot precede 1970")
        return millis
    except (ValueError, OverflowError) as error:
        raise ValueError(f"Invalid validity date: {error}") from error


def sql_literal(value: str | int | None) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, int):
        return str(value)
    return "'" + value.replace("'", "''") + "'"


def promo_sql(args: argparse.Namespace, raw_code: str) -> str:
    code = normalize_code(raw_code)
    region = normalized_tag(args.region, 2, "Region")
    currency = normalized_tag(args.currency, 3, "Currency")
    duration = exact_scaled(args.duration_days, 86_400_000, MAX_DURATION_MS, "duration") if args.duration_days else None
    percentage = exact_scaled(args.discount_percent, 100, 10_000, "percentage") if args.discount_percent else None
    fixed = args.discount_minor
    if fixed is not None and not 1 <= fixed <= MAX_MINOR:
        raise ValueError("Fixed discount must be 1–1,000,000,000,000 minor units")
    if args.type == "lifetime" and any(v is not None for v in (duration, percentage, fixed)):
        raise ValueError("Lifetime access cannot contain a duration or discount")
    if args.type == "timed" and (duration is None or percentage is not None or fixed is not None):
        raise ValueError("Timed access needs --duration-days and no discount")
    if args.type == "discount" and (duration is not None or (percentage is None) == (fixed is None)):
        raise ValueError("Discount codes need exactly one discount type and no duration")
    if fixed is not None and currency is None:
        raise ValueError("A fixed discount requires an explicit --currency")
    if not 1 <= args.max_redemptions <= 9_223_372_036_854_775_807:
        raise ValueError("Maximum redemptions must fit a positive database BIGINT")
    valid_from = timestamp(args.valid_from) or 0
    valid_until = timestamp(args.valid_until)
    if valid_until is not None and valid_until <= valid_from:
        raise ValueError("Validity end must follow validity start")
    store_id = str(uuid.UUID(args.store_id)) if args.store_id else None
    owner_id = str(uuid.UUID(args.owner_id)) if args.owner_id else None
    columns = ["id", "code_hash", "kind", "plan_id", "duration_millis", "discount_basis_points", "discount_minor",
               "currency_code", "region_code", "bound_store_id", "bound_owner_id", "valid_from_millis", "valid_until_millis", "max_redemptions"]
    values = [str(uuid.uuid4()), hashlib.sha256(code.encode("ascii")).hexdigest(), args.type, "basic", duration,
              percentage, fixed, currency, region, store_id, owner_id, valid_from, valid_until, args.max_redemptions]
    return "BEGIN;\nINSERT INTO subscription_promocodes (\n    " + ", ".join(columns) + ")\nVALUES (\n    " + ", ".join(map(sql_literal, values)) + ");\nCOMMIT;\n"


def price_sql(args: argparse.Namespace) -> str:
    region = normalized_tag(args.region, 2, "Region")
    currency = normalized_tag(args.currency, 3, "Currency")
    if not 0 <= args.price_minor <= MAX_MINOR:
        raise ValueError("Price must be 0–1,000,000,000,000 minor units (AITA uses 1/100 currency units)")
    if not 1 <= args.period_count <= 120:
        raise ValueError("Period count must be 1–120")
    return ("BEGIN;\nINSERT INTO store_subscription_plan_prices\n"
            "    (plan_id, region_code, currency_code, price_minor, period_unit, period_count)\nVALUES "
            f"('basic', {sql_literal(region)}, {sql_literal(currency)}, {args.price_minor}, {sql_literal(args.period)}, {args.period_count})\n"
            "ON CONFLICT (plan_id, region_code) DO UPDATE SET\n"
            "    currency_code = EXCLUDED.currency_code, price_minor = EXCLUDED.price_minor,\n"
            "    period_unit = EXCLUDED.period_unit, period_count = EXCLUDED.period_count, is_active = TRUE;\n"
            "-- The V101 trigger updates checkout price_version. Existing agreed renewal snapshots do not change.\nCOMMIT;\n")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = result.add_subparsers(dest="command", required=True)
    promo = commands.add_parser("promo", help="Create a promo code and an INSERT script")
    promo.add_argument("--type", choices=("lifetime", "timed", "discount"), required=True)
    promo.add_argument("--duration-days")
    promo.add_argument("--discount-percent")
    promo.add_argument("--discount-minor", type=int)
    promo.add_argument("--currency")
    promo.add_argument("--region")
    promo.add_argument("--store-id")
    promo.add_argument("--owner-id")
    promo.add_argument("--max-redemptions", type=int, default=1)
    promo.add_argument("--valid-from", help="Inclusive ISO-8601 timestamp with timezone")
    promo.add_argument("--valid-until", help="Exclusive ISO-8601 timestamp with timezone")
    promo.add_argument("--code-env", help="Optional environment variable containing a custom code; not a command-line secret")
    promo.add_argument("--sql", type=Path, required=True, help="New SQL file; existing files are never overwritten")
    price = commands.add_parser("price", help="Configure an explicit regional Basic price")
    price.add_argument("--region", required=True)
    price.add_argument("--currency", required=True)
    price.add_argument("--price-minor", type=int, required=True)
    price.add_argument("--period", choices=("month", "year"), default="month")
    price.add_argument("--period-count", type=int, default=1)
    price.add_argument("--sql", type=Path, required=True)
    return result


def main(argv: Sequence[str] | None = None) -> int:
    arguments = parser()
    args = arguments.parse_args(argv)
    try:
        code = None
        if args.command == "promo":
            if args.code_env:
                if args.code_env not in os.environ:
                    raise ValueError("The requested code environment variable is absent")
                code = normalize_code(os.environ[args.code_env])
            else:
                code = "AITA-" + secrets.token_hex(20).upper()  # 160 bits of secret entropy
            sql = promo_sql(args, code)
        else:
            sql = price_sql(args)
        # Exclusive create and 0600 even when the caller's umask is permissive; never overwrite a secret/script.
        fd = os.open(args.sql, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as output:
            output.write(sql)
        print(f"Prepared SQL only (not executed): {args.sql}")
        if code is not None:
            print(f"Save this promo code securely; it is NOT in the SQL file:\n{code}", file=sys.stderr)
        return 0
    except (ValueError, OSError) as error:
        arguments.exit(2, f"Error: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
