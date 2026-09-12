#!/usr/bin/env python3
"""Prepare reviewed, auditable company-employment SQL. Never connects to a database.

Apply only after V103, through a trusted PostgreSQL administration connection.
Store owners have no right to employ company staff. The operator ID is attribution,
not an authentication bypass: the database role executing SQL is recorded too.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import sys
import uuid
from typing import Sequence


def identifier(value: str) -> str:
    if not re.fullmatch(r"[a-z][a-z0-9_.-]{1,63}", value):
        raise ValueError("IDs need 2–64 lowercase ASCII letters/digits/dot/underscore/hyphen")
    return value


def user_id(value: str) -> str:
    parsed = str(uuid.UUID(value))
    if parsed != value.lower():
        raise ValueError("Use a complete canonical UUID")
    return parsed


def timestamp(value: str | None) -> int | None:
    if value is None:
        return None
    moment = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if moment.tzinfo is None:
        raise ValueError("Dates must include a timezone")
    delta = moment.astimezone(timezone.utc) - datetime(1970, 1, 1, tzinfo=timezone.utc)
    millis = (delta.days * 86400 + delta.seconds) * 1000 + delta.microseconds // 1000
    if not 0 <= millis < 9_223_372_036_854_775_807:
        raise ValueError("Date is outside the supported range")
    return millis


def text(value: str, label: str, maximum: int = 240) -> str:
    clean = value.strip()
    if not clean or len(clean) > maximum or any(ord(c) < 32 for c in clean):
        raise ValueError(f"{label} must contain 1–{maximum} printable characters")
    return clean


def literal(value: str) -> str:
    if "\0" in value:
        raise ValueError("NUL is not allowed in SQL text")
    return "'" + value.replace("'", "''") + "'"


RELEASE_ORPHANED_WORK = """
    -- Resolve stale assignment without granting another employee access implicitly.
    WITH released AS (
        UPDATE support_tickets t SET assigned_agent_user_id=NULL,revision=revision+1,updated_at_millis=at_ms
        WHERE t.assigned_agent_user_id=affected_user AND NOT EXISTS
            (SELECT 1 FROM company_support_agent_access a WHERE a.user_id=affected_user)
        RETURNING t.id,t.status
    ) INSERT INTO support_ticket_events(ticket_id,actor_user_id,employment_id,event_type,
        previous_assignee_user_id,next_assignee_user_id,previous_status,next_status,occurred_at_millis)
    SELECT id,actor,employment,'employment_access_removed',affected_user,NULL,status,status,at_ms FROM released;
"""


def build_sql(args: argparse.Namespace) -> str:
    if args.command == "inspect":
        who = user_id(args.user_id)
        return ("BEGIN READ ONLY;\nSELECT e.id,e.user_id,e.employee_number,e.status,e.starts_at_millis,e.ends_at_millis,\n"
                "e.created_at,e.updated_at,a.id AS assignment_id,a.job_id,a.is_active,a.starts_at_millis AS job_starts,\n"
                "a.ends_at_millis AS job_ends FROM company_employments e LEFT JOIN company_job_assignments a\n"
                f"ON a.employment_id=e.id WHERE e.user_id={literal(who)}::uuid ORDER BY e.created_at,a.created_at;\nCOMMIT;\n")
    actor = user_id(args.operator_user_id)
    reason = text(args.reason, "Reason", 500)
    data: dict[str, object] = {"action": args.command}
    if args.command in ("employ", "assign"):
        data["user_id" if args.command == "employ" else "employment_id"] = user_id(
            args.user_id if args.command == "employ" else args.employment_id)
        data["jobs"] = [identifier(v) for v in args.job]
        if len(set(data["jobs"])) != len(data["jobs"]) or len(data["jobs"]) > 20:
            raise ValueError("Specify 1–20 distinct jobs")
        start, end = timestamp(args.starts_at), timestamp(args.ends_at)
        if start is not None and end is not None and end <= start:
            raise ValueError("The end must follow the start")
        data.update(starts_at=start, ends_at=end)
        if args.command == "employ":
            data["employee_number"] = text(args.employee_number, "Employee number", 64) if args.employee_number else None
    elif args.command == "status":
        data.update(employment_id=user_id(args.employment_id), status=args.status)
    elif args.command == "unassign":
        data["assignment_id"] = user_id(args.assignment_id)
    elif args.command == "job":
        data.update(job_id=identifier(args.job_id), department_id=identifier(args.department),
                    title={"en": text(args.title_en, "English title"), "ru": text(args.title_ru, "Russian title"),
                           "kk": text(args.title_kk, "Kazakh title")},
                    capabilities=[identifier(v) for v in args.capability])
        if len(set(data["capabilities"])) != len(data["capabilities"]):
            raise ValueError("Duplicate capability")
    else:
        raise ValueError("Unknown command")
    # User-provided text is serialized outside the dollar-quoted program, not interpolated into it.
    prefix = ("BEGIN;\nSET LOCAL standard_conforming_strings=on;\n"
              "SELECT pg_advisory_xact_lock(hashtextextended('aita-company-authority-v1',0));\n"
              f"SELECT set_config('aita.operator_user_id',{literal(actor)},TRUE);\n"
              f"SELECT set_config('aita.operator_reason',{literal(reason)},TRUE);\n"
              f"SELECT set_config('aita.company_command',{literal(json.dumps(data, ensure_ascii=True))},TRUE);\n")
    program = r"""
DO $aita_company$
DECLARE
    cfg JSONB := current_setting('aita.company_command')::jsonb;
    actor UUID := current_setting('aita.operator_user_id')::uuid;
    at_ms BIGINT := floor(extract(epoch FROM clock_timestamp())*1000)::bigint;
    start_ms BIGINT;
    end_ms BIGINT;
    employment UUID;
    affected_user UUID;
    job TEXT;
    previous_status TEXT;
BEGIN
    IF NOT EXISTS(SELECT 1 FROM users WHERE id=actor AND is_active) THEN
        RAISE EXCEPTION 'Operator account does not exist or is inactive';
    END IF;
    IF cfg->>'action' IN ('employ','assign') THEN
        start_ms := coalesce((cfg->>'starts_at')::bigint,at_ms);
        end_ms := (cfg->>'ends_at')::bigint;
        IF end_ms IS NOT NULL AND end_ms<=start_ms THEN RAISE EXCEPTION 'Invalid employment/assignment period'; END IF;
        IF cfg->>'action'='employ' THEN
            affected_user := (cfg->>'user_id')::uuid;
            IF NOT EXISTS(SELECT 1 FROM users WHERE id=affected_user AND is_active) THEN
                RAISE EXCEPTION 'Employee account does not exist or is inactive';
            END IF;
            INSERT INTO company_employments(user_id,employee_number,starts_at_millis,ends_at_millis)
            VALUES(affected_user,cfg->>'employee_number',start_ms,end_ms) RETURNING id INTO employment;
        ELSE
            employment := (cfg->>'employment_id')::uuid;
            SELECT user_id,status INTO affected_user,previous_status FROM company_employments WHERE id=employment FOR UPDATE;
            IF NOT FOUND OR previous_status='ended' THEN RAISE EXCEPTION 'Employment is missing or ended'; END IF;
        END IF;
        FOR job IN SELECT jsonb_array_elements_text(cfg->'jobs') LOOP
            IF NOT EXISTS(SELECT 1 FROM company_jobs j LEFT JOIN company_departments d ON d.id=j.department_id
                WHERE j.id=job AND j.is_active AND (d.id IS NULL OR d.is_active)) THEN
                RAISE EXCEPTION 'Job is missing or disabled';
            END IF;
            IF EXISTS(SELECT 1 FROM company_job_assignments WHERE employment_id=employment AND job_id=job AND is_active
                AND starts_at_millis<coalesce(end_ms,9223372036854775807)
                AND coalesce(ends_at_millis,9223372036854775807)>start_ms) THEN
                RAISE EXCEPTION 'This employment already has an overlapping assignment';
            END IF;
            INSERT INTO company_job_assignments(employment_id,job_id,starts_at_millis,ends_at_millis)
            VALUES(employment,job,start_ms,end_ms);
        END LOOP;
    ELSIF cfg->>'action'='status' THEN
        employment := (cfg->>'employment_id')::uuid;
        SELECT user_id,status INTO affected_user,previous_status FROM company_employments WHERE id=employment FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'Employment does not exist'; END IF;
        IF previous_status='ended' AND cfg->>'status'<>'ended' THEN
            RAISE EXCEPTION 'Ended employment cannot resume; create a new employment record';
        END IF;
        UPDATE company_employments SET status=cfg->>'status',updated_at=clock_timestamp()
        WHERE id=employment AND status IS DISTINCT FROM cfg->>'status';
    ELSIF cfg->>'action'='unassign' THEN
        UPDATE company_job_assignments SET is_active=FALSE WHERE id=(cfg->>'assignment_id')::uuid
        RETURNING employment_id INTO employment;
        IF NOT FOUND THEN RAISE EXCEPTION 'Assignment does not exist'; END IF;
        SELECT user_id INTO affected_user FROM company_employments WHERE id=employment;
    ELSIF cfg->>'action'='job' THEN
        INSERT INTO company_jobs(id,department_id,title) VALUES(cfg->>'job_id',cfg->>'department_id',cfg->'title');
        INSERT INTO company_job_capabilities(job_id,capability_id)
        SELECT cfg->>'job_id',jsonb_array_elements_text(cfg->'capabilities');
    ELSE RAISE EXCEPTION 'Unknown company command';
    END IF;
"""
    return prefix + program + RELEASE_ORPHANED_WORK + "\nEND $aita_company$;\nCOMMIT;\n"


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)
    employ = commands.add_parser("employ", help="Create a new employment and job assignments")
    employ.add_argument("--user-id", required=True)
    employ.add_argument("--employee-number")
    assign = commands.add_parser("assign", help="Assign another job without replacing existing jobs")
    assign.add_argument("--employment-id", required=True)
    for cmd in (employ, assign):
        cmd.add_argument("--job", action="append", required=True)
        cmd.add_argument("--starts-at")
        cmd.add_argument("--ends-at")
    status = commands.add_parser("status", help="Suspend, resume or end existing employment")
    status.add_argument("--employment-id", required=True)
    status.add_argument("--status", choices=("active", "suspended", "ended"), required=True)
    unassign = commands.add_parser("unassign", help="Revoke one job assignment")
    unassign.add_argument("--assignment-id", required=True)
    job = commands.add_parser("job", help="Create a company job using registered server capabilities")
    job.add_argument("--job-id", required=True)
    job.add_argument("--department", default="support")
    for lang in ("en", "ru", "kk"):
        job.add_argument("--title-" + lang, required=True)
    job.add_argument("--capability", action="append", required=True)
    for cmd in (employ, assign, status, unassign, job):
        cmd.add_argument("--operator-user-id", required=True)
        cmd.add_argument("--reason", required=True)
        cmd.add_argument("--sql", required=True, type=Path)
    inspect = commands.add_parser("inspect", help="Prepare a read-only employment/assignment listing")
    inspect.add_argument("--user-id", required=True)
    inspect.add_argument("--sql", required=True, type=Path)
    return root


def main(argv: Sequence[str] | None = None) -> int:
    cli = parser()
    args = cli.parse_args(argv)
    try:
        sql = build_sql(args)
        fd = os.open(args.sql, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "w", encoding="utf-8") as file:
            file.write(sql)
    except (ValueError, OSError) as error:
        cli.error(str(error))
    print(f"Prepared {args.sql}. Review it; no database was contacted or changed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
