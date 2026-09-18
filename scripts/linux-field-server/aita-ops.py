#!/usr/bin/env python3
"""AITA Ubuntu operator entry point (Python 3.10+, standard library only).

    python3 scripts/linux-field-server/aita-ops.py update
    python3 scripts/linux-field-server/aita-ops.py logs [--history]
    python3 scripts/linux-field-server/aita-ops.py status
    python3 scripts/linux-field-server/aita-ops.py connection
    python3 scripts/linux-field-server/aita-ops.py backups [--watch] [--download]

The update frontend fetches Git interactively as the repository owner. A pinned
snapshot is then built as that user inside a detached, system-managed job. No
production environment is passed to Gradle. Root is used for installation only.
This is a single-origin controlled restart, NOT a zero-downtime deployment.
Updates temporarily require only an encrypted LOCAL backup; cloud access is not
attempted. Use update --require-offsite-backup to restore the strict cloud gate.
Scheduled backups and the standalone backups command retain their cloud checks.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import pwd
import re
import selectors
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
from typing import Iterator, Mapping, Sequence
from urllib.parse import urlsplit
import uuid

VERSION = "2026-09-11-connection-diagnostics-v4"
SERVICE = "aita-server.service"
UPDATE_UNIT = "aita-update.service"
ROOT = Path("/var/lib/aita-ops")
ENV_FILE = Path("/etc/aita/aita-prod.env")
INSTALLED = Path("/usr/local/lib/aita")
CURRENT_JAR = Path("/opt/aita/app/aita-server-all.jar")
DEFAULT_JAVA = Path("/usr/lib/jvm/java-21-openjdk-amd64")
SCRIPT_REL = Path("scripts/linux-field-server/aita-ops.py")
SAFE_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
ACTIVE_STATES = {"active", "activating", "reloading", "deactivating"}
SECRETS: list[str] = []


class OpsError(Exception):
    """An actionable operator error, never an environment dump."""


def redact(text: str) -> str:
    for value in sorted(SECRETS, key=len, reverse=True):
        if len(value) >= 5:
            text = text.replace(value, "<redacted>")
    text = re.sub(r"(https?://)[^\s/@]+(?::[^\s/@]+)?@", r"\1<redacted>@", text)
    text = re.sub(r"(?i)(Authorization\s*[:=]\s*Bearer\s+)\S+", r"\1<redacted>", text)
    text = re.sub(r"\b(?:gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)\b", "<redacted>", text)
    return text


def say(label: str, text: str) -> None:
    print(f"[{label}] {redact(text)}", flush=True)


def utc() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_json(path: Path, value: object, gid: int | None = None) -> None:
    fd, name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as stream:
            json.dump(value, stream, indent=2, ensure_ascii=True)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(name, 0o640 if gid is not None else 0o600)
        if gid is not None:
            os.chown(name, 0, gid)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def load_json(path: Path, default: object = None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return default


def require_tools(*names: str) -> None:
    missing = [name for name in names if shutil.which(name) is None]
    if missing:
        raise OpsError("Missing Ubuntu tools: " + ", ".join(missing) +
                       ". Install the corresponding packages first; no automatic OS upgrade is performed.")


def read_env(path: Path) -> dict[str, str]:
    """Match AITA's NAME=value file convention; NEVER evaluate shell input."""
    result: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        raise OpsError(f"Cannot read {path}. Check its existence and root:aita 0640 permissions.") from None
    for number, raw in enumerate(lines, 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export ") or line.startswith("export\t"):
            line = line[6:].lstrip()
        key, separator, value = line.partition("=")
        key = key.rstrip()
        if not separator or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key):
            raise OpsError(f"Invalid environment entry on line {number}; contents hidden. Use NAME=value.")
        if key in result:
            raise OpsError(f"Duplicate setting {key} on line {number}. Keep one entry per name.")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        result[key] = value
    return result


def protect_secrets(env: Mapping[str, str]) -> None:
    for key, value in env.items():
        if re.search(r"PASS|SECRET|TOKEN|PEPPER|(?:API|MASTER)_KEY|RCLONE_CONFIG_PASS", key, re.I):
            if value and value not in SECRETS:
                SECRETS.append(value)


def capture(argv: Sequence[str], *, timeout: int = 30, env=None, cwd=None,
            check: bool = True) -> subprocess.CompletedProcess:
    process = subprocess.Popen(list(map(str, argv)), cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                               text=True, errors="replace", start_new_session=True)
    try:
        stdout, stderr = process.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        raise OpsError(f"{Path(argv[0]).name} did not finish within {timeout}s. No automatic retry was made.") from None
    result = subprocess.CompletedProcess(argv, process.returncode, stdout, stderr)
    if check and result.returncode:
        detail = redact((result.stderr or result.stdout).strip())[-2200:]
        raise OpsError(f"{Path(argv[0]).name} failed (exit {result.returncode}). {detail}")
    return result


def stream_command(argv: Sequence[str], *, env=None, cwd=None, pass_fds=()) -> None:
    """Forward output without shell interpolation and keep long silent jobs legible."""
    process = subprocess.Popen(list(map(str, argv)), cwd=cwd, env=env,
                               stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, start_new_session=True, pass_fds=pass_fds)
    assert process.stdout is not None
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    pending = b""
    last_output = time.monotonic()
    try:
        while selector.get_map():
            events = selector.select(1)
            for key, _ in events:
                chunk = os.read(key.fd, 65536)
                if not chunk:
                    selector.unregister(key.fileobj)
                    continue
                pending += chunk
                while b"\n" in pending:
                    line, pending = pending.split(b"\n", 1)
                    print(redact(line.decode("utf-8", "replace")), flush=True)
                # Avoid unbounded memory if a command writes no newline.
                if len(pending) > 65536:
                    print(redact(pending.decode("utf-8", "replace")), flush=True)
                    pending = b""
                last_output = time.monotonic()
            if time.monotonic() - last_output >= 30:
                say("WAIT", "The current step is still running. Do not start another deployment.")
                last_output = time.monotonic()
        if pending:
            print(redact(pending.decode("utf-8", "replace")), flush=True)
        code = process.wait()
        if code:
            raise OpsError(f"{Path(argv[0]).name} ended with exit {code}; see the preceding output.")
    finally:
        selector.close()
        process.stdout.close()
        # Never kill a deployment or migration because its log viewer disconnected.


def properties(unit: str, *names: str) -> dict[str, str]:
    args = ["systemctl", "show", unit, "--no-pager"] + [f"--property={n}" for n in names]
    result = capture(args, check=False, timeout=10)
    return dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)


def positive_integer(value: object) -> int | None:
    text = str(value)
    if re.fullmatch(r"[0-9]{1,20}", text) and int(text) > 0:
        return int(text)
    return None


def server_snapshot() -> dict:
    """Read the backend, NOT the updater. No journal/env/command-line secrets."""
    names = ("LoadState", "ActiveState", "SubState", "MainPID", "InvocationID", "NRestarts",
             "ExecMainStartTimestamp", "ExecMainStartTimestampMonotonic",
             "ActiveEnterTimestamp", "ExecMainExitTimestamp", "Result")
    values = properties(SERVICE, *names)
    if not values.get("ActiveState"):
        raise OpsError("systemctl did not return the backend's state; its last start is unknown.")
    # Whitelist both the query and stored data. Never persist Environment/ExecStart.
    snapshot = {name: values[name] for name in names if name in values}
    snapshot.update(service=SERVICE, observed_at=utc())
    pid = positive_integer(values.get("MainPID"))
    # An exited process may retain a last-start timestamp, but it has no uptime.
    running = (pid is not None and values["ActiveState"] in ACTIVE_STATES) if "MainPID" in values else None
    snapshot["running"] = running
    snapshot["uptime_seconds"] = None
    start = positive_integer(values.get("ExecMainStartTimestampMonotonic"))
    now = time.monotonic_ns() // 1000  # Same CLOCK_MONOTONIC domain as systemd; microseconds.
    if running and start is not None and start <= now:
        snapshot["uptime_seconds"] = (now - start) // 1_000_000
    return snapshot


def format_uptime(seconds: int) -> str:
    days, remainder = divmod(seconds, 86400)
    hours, remainder = divmod(remainder, 3600)
    minutes, seconds = divmod(remainder, 60)
    return (f"{days}d " if days else "") + f"{hours:02d}h {minutes:02d}m {seconds:02d}s"


def report_server_state(label: str = "SERVER") -> dict:
    """Bounded, read-only diagnostics must not turn a deploy failure into success."""
    try:
        snapshot = server_snapshot()
    except (OpsError, OSError, subprocess.SubprocessError, ValueError) as error:
        message = redact(str(error))
        say(label, "UNAVAILABLE: " + message)
        return {"service": SERVICE, "observed_at": utc(), "error": message}
    say(label, f"{SERVICE}: {snapshot.get('ActiveState', 'unknown')}/"
        f"{snapshot.get('SubState', 'unknown')}; MainPID={snapshot.get('MainPID', 'unknown')}; "
        f"observed at {snapshot['observed_at']}")
    started = snapshot.get("ExecMainStartTimestamp", "")
    if started and started not in {"n/a", "0"}:
        description = "current main process" if snapshot["running"] else "last recorded main-process launch; not proof it is running"
        say(label + " START", f"{started} ({description}; systemd ExecMainStartTimestamp, host time zone).")
    else:
        say(label + " START", "Unknown: no main-process start timestamp was returned; not inferred from Git/update times.")
    seconds = snapshot["uptime_seconds"]
    if seconds is not None:
        say(label + " UPTIME", format_uptime(seconds) + " (monotonic elapsed time; excludes host suspend).")
    elif snapshot["running"] is False:
        say(label + " UPTIME", "Not running: no current backend main process.")
    else:
        say(label + " UPTIME", "Unavailable: a valid current main-process monotonic timestamp is required.")
    invocation = snapshot.get("InvocationID", "")
    if re.fullmatch(r"[a-fA-F0-9]{32}", invocation) and int(invocation, 16):
        say(label + " INVOCATION", invocation)
    restarts = snapshot.get("NRestarts", "unknown")
    say(label + " AUTO-RESTARTS", f"{restarts} (systemd automatic-restart counter, not a lifetime/manual-restart total).")
    return snapshot


def server_process_change(before: object, after: object) -> str:
    """Compare observed process identities; PID reuse alone cannot prove a restart."""
    if not isinstance(before, dict) or not isinstance(after, dict):
        return "unknown"
    if before.get("service") != SERVICE or after.get("service") != SERVICE:
        return "unknown"
    was_running, is_running = before.get("running"), after.get("running")
    if type(was_running) is not bool or type(is_running) is not bool:
        return "unknown"
    if not was_running or not is_running:
        return "started" if is_running else ("stopped" if was_running else "not-running")
    old_id, new_id = before.get("InvocationID", ""), after.get("InvocationID", "")
    if not all(isinstance(value, str) and re.fullmatch(r"[a-fA-F0-9]{32}", value) and int(value, 16)
               for value in (old_id, new_id)):
        return "unknown"
    if old_id.lower() != new_id.lower():
        return "changed"
    keys = ("MainPID", "ExecMainStartTimestampMonotonic")
    old_values = tuple(positive_integer(before.get(key)) for key in keys)
    new_values = tuple(positive_integer(after.get(key)) for key in keys)
    if None in old_values or None in new_values:
        return "unknown"
    return "unchanged" if old_values == new_values else "changed"


def report_process_change(before: object, after: object) -> str:
    change = server_process_change(before, after)
    messages = {
        "unchanged": "Same main PID, invocation and start timestamp before/after this job. No new backend launch was observed.",
        "changed": "A different backend process was observed after this job. See its actual systemd start timestamp above.",
        "started": "No main process was running at the first observation; one is running now.",
        "stopped": "A main process was running at the first observation; none is running now. Inspect service logs.",
        "not-running": "No running main process at either observation.",
        "unknown": "Insufficient systemd identity data to confirm a process change; no restart is inferred from the commit ID.",
    }
    say("SERVER PROCESS", messages[change])
    return change


def unit_busy(unit: str = UPDATE_UNIT) -> bool:
    return properties(unit, "ActiveState").get("ActiveState") in ACTIVE_STATES


def sudo_prefix() -> list[str]:
    return [] if os.geteuid() == 0 else ["sudo"]


def clean_user_env(user: pwd.struct_passwd, extra: Mapping[str, str] | None = None) -> dict[str, str]:
    result = {"HOME": user.pw_dir, "USER": user.pw_name, "LOGNAME": user.pw_name,
              "PATH": SAFE_PATH, "LANG": "C.UTF-8", "PYTHONUNBUFFERED": "1"}
    if extra:
        result.update(extra)
    return result


def user_command(user: pwd.struct_passwd, argv: Sequence[str], extra=None) -> list[str]:
    # env -i is deliberately AFTER runuser, so its HOME changes cannot interfere.
    return ["runuser", "-u", user.pw_name, "--", "env", "-i"] + [
        f"{key}={value}" for key, value in clean_user_env(user, extra).items()
    ] + list(map(str, argv))


def http_status(url: str, *, local: bool = False) -> str:
    args = ["curl", "-sS", "--output", "/dev/null", "--write-out", "%{http_code}",
            "--connect-timeout", "3", "--max-time", "8"]
    if local:
        args += ["--noproxy", "*"]
    result = capture(args + [url], timeout=12, check=False)
    return result.stdout.strip() if result.returncode == 0 else "unreachable"



# Read-only diagnostics deliberately do not read the production environment, tunnel token,
# service ExecStart, credentials, response cookies, arbitrary JSON or unfiltered journal lines.
DEFAULT_PUBLIC_ORIGIN = "https://aita-api.bogdan-donduk.workers.dev"
ORIGIN_ERROR_CODES = frozenset({
    "connection_refused", "connection_terminated", "connection_timeout", "connection_limit_reached",
    "destination_unavailable", "destination_not_found", "destination_ip_prohibited", "destination_ip_unroutable",
    "proxy_loop_detected", "dns_error", "dns_timeout", "tls_protocol_error", "tls_certificate_error",
    "http_request_error", "http_upgrade_failed", "http_request_denied", "http_protocol_error",
    "http_response_incomplete", "connection_read_timeout", "connection_write_timeout", "rate_limited",
    "proxy_internal_error", "origin_binding_missing", "origin_probe_timeout", "client_disconnected",
    "origin_connection_failed",
})


def public_origin_url(raw: str) -> str:
    parsed = urlsplit(raw.strip())
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or
            parsed.query or parsed.fragment or any(c.isspace() or ord(c) < 32 for c in raw)):
        raise OpsError("Public address must be HTTPS without credentials, query or whitespace.")
    if parsed.path.rstrip("/") not in {"", "/readyz", "/healthz", "/auth/capabilities"}:
        raise OpsError("Use the public HTTPS base address, not an API path.")
    # AITA_PUBLIC_HEALTH_URL was sometimes a complete /readyz URL. Never append /readyz twice.
    _ = parsed.port  # Validate port before a subprocess can see the address.
    return f"https://{parsed.netloc}"


def connection_response_summary(status: str, returncode: int, headers: str, body: str) -> dict:
    result = {"status": status if re.fullmatch(r"[1-5][0-9]{2}", status) else "unreachable"}
    if returncode:
        result["failure"] = {6: "dns", 7: "connection", 28: "timeout", 35: "tls", 60: "certificate",
                             63: "response_too_large"}.get(returncode, "curl_error")
    for line in headers.splitlines():
        name, _, value = line.partition(":")
        name, value = name.strip().lower(), value.strip()
        if name == "cf-ray" and re.fullmatch(r"[0-9a-fA-F]{8,32}-[A-Za-z]{3}", value):
            result["cf_ray"] = value
        elif name == "x-aita-origin-error" and value in ORIGIN_ERROR_CODES:
            result["origin_error"] = value
        elif name == "x-aita-gateway-version" and re.fullmatch(r"[A-Za-z0-9._-]{1,80}", value):
            result["gateway_version"] = value
        elif name == "content-type":
            mime = value.split(";", 1)[0].lower()
            if mime in {"application/json", "text/html", "text/plain"}: result["content_type"] = mime
    try:
        data = json.loads(body)
        if isinstance(data, dict):
            if data.get("error") == "origin_unavailable": result["origin_unavailable"] = True
            if data.get("code") in ORIGIN_ERROR_CODES: result["origin_error"] = data["code"]
            if data.get("gateway") == "aita-workers-vpc":
                result["aita_gateway"] = True
                if isinstance(data.get("originBindingConfigured"), bool): result["binding_configured"] = data["originBindingConfigured"]
                version = data.get("version")
                if isinstance(version, str) and re.fullmatch(r"[A-Za-z0-9._-]{1,80}", version): result["gateway_version"] = version
    except (ValueError, TypeError):
        pass
    return result


def connection_http_probe(url: str) -> dict:
    with tempfile.TemporaryDirectory(prefix="aita-connection-") as temp:
        header_path, body_path = Path(temp) / "headers", Path(temp) / "body"
        # --disable must be first: a per-user curlrc/proxy must not invisibly change this probe.
        cmd = ["curl", "--disable", "--silent", "--show-error", "--noproxy", "*",
               "--connect-timeout", "3", "--max-time", "8", "--max-filesize", "16384",
               "--dump-header", str(header_path), "--output", str(body_path),
               "--header", "Accept: application/json", "--header", "Cache-Control: no-cache",
               "--write-out", "%{http_code}", url]
        try:
            reply = capture(cmd, timeout=12, check=False)
        except (subprocess.TimeoutExpired, OpsError):
            return {"status": "unreachable", "failure": "probe_timeout"}
        def bounded_text(path):
            try:
                with path.open("rb") as f: return f.read(16384).decode("utf-8", errors="replace")
            except OSError: return ""
        return connection_response_summary(reply.stdout.strip(), reply.returncode,
                                           bounded_text(header_path), bounded_text(body_path))


def tunnel_journal_summary(text: str) -> dict:
    protocols = re.findall(r"\bprotocol[=:](quic|http2)\b", text, re.IGNORECASE)
    result = {"last_observed_protocol": protocols[-1].lower() if protocols else "unknown"}
    for name, pattern in {"registered_connections": r"Registered tunnel connection",
                          "errors": r"\b(?:ERR|ERROR)\b", "timeouts": r"\b(?:timeout|timed out)\b"}.items():
        result[name] = len(re.findall(pattern, text, re.IGNORECASE))
    return result


def connection_diagnosis(local: dict, edge: dict, ready: dict, capabilities: dict) -> str:
    if local.get("status") != "200":
        return "Local /readyz is not healthy. Inspect backend/readiness first; no restart was requested."
    if ready.get("status") == capabilities.get("status") == "200":
        return "Public HTTP is reachable at this instant. This does NOT prove a long-lived authenticated WebSocket or rule out an intermittent outage."
    origin_error = ready.get("origin_error") or capabilities.get("origin_error")
    if origin_error:
        return "Local backend is ready; the public gateway reports " + origin_error + ". Inspect the existing VPC service/tunnel, not the database or JAR."
    if edge.get("aita_gateway") is True:
        return "AITA Worker is reachable, but the origin API check failed. Check the existing AITA_ORIGIN binding, VPC target and cloudflared."
    return "Local backend is ready, but the public route is not verified. Check the printed public address, DNS/TLS, Worker and tunnel. No precise origin cause is established."


def connection_status(args: argparse.Namespace) -> int:
    public = public_origin_url(args.public_url)
    require_tools("curl", "systemctl")
    say("CONNECTION", "Read-only: no restart, tunnel reconfiguration, database write, backup upload or credential dump.")
    say("PUBLIC TARGET", public)
    proxy_present = any(os.environ.get(k) for k in ("HTTPS_PROXY", "HTTP_PROXY", "ALL_PROXY", "https_proxy", "http_proxy", "all_proxy"))
    say("PROBE ROUTE", "Direct HTTPS with certificate verification; curlrc and proxy variables ignored." +
        (" Proxy variables are present, but their values are not displayed." if proxy_present else ""))
    report_server_state()
    local = connection_http_probe("http://127.0.0.1:8080/readyz")
    edge = connection_http_probe(public + "/_edge/health")
    ready = connection_http_probe(public + "/readyz")
    capabilities = connection_http_probe(public + "/auth/capabilities")
    for name, result in (("LOCAL /readyz", local), ("EDGE /_edge/health", edge),
                         ("PUBLIC /readyz", ready), ("PUBLIC /auth/capabilities", capabilities)):
        say(name, json.dumps(result, sort_keys=True))
    tunnel = properties("cloudflared.service", "ActiveState", "SubState", "MainPID", "ExecMainStartTimestamp")
    say("TUNNEL", str(tunnel))
    if shutil.which("journalctl"):
        try:
            journal = capture(["journalctl", "--unit=cloudflared.service", "--since", "30 minutes ago",
                               "--lines=120", "--output=cat", "--no-pager"], check=False, timeout=8)
        except (subprocess.TimeoutExpired, OpsError):
            journal = subprocess.CompletedProcess([], 1, "", "")
        if journal.returncode == 0 and journal.stdout.strip() and "-- No entries --" not in journal.stdout:
            summary = tunnel_journal_summary(journal.stdout)
            say("TUNNEL JOURNAL", json.dumps(summary, sort_keys=True) + " (bounded recent sample, not a live connection test)")
            if summary["last_observed_protocol"] == "http2":
                say("WARNING", "Workers VPC requires QUIC. The journal last reports HTTP/2; check outbound UDP 7844 and the existing tunnel configuration. No protocol was changed.")
        else:
            say("TUNNEL JOURNAL", "Unavailable or empty for this user; no protocol is inferred.")
    say("DIAGNOSIS", connection_diagnosis(local, edge, ready, capabilities))
    return 0 if local.get("status") == ready.get("status") == capabilities.get("status") == "200" else 2


def validate_remote(remote: str) -> str:
    # Reject on-the-fly backend definitions, embedded credentials and option-like names.
    if "://" in remote or not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_ -]*:[^\r\n]*", remote):
        raise OpsError("AITA_BACKUP_RCLONE_REMOTE must be a configured remote:path (not a URL or token).")
    return remote.rstrip("/")


def rclone_environment(env: Mapping[str, str]) -> dict[str, str]:
    user = pwd.getpwnam("aita")
    # The service account's home/config, NOT root's or bogdan's login configuration.
    extra = {k: v for k, v in env.items() if k.startswith("RCLONE_") or k in {
        "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "no_proxy"}}
    extra["HOME"] = user.pw_dir
    if env.get("AITA_BACKUP_RCLONE_CONFIG"):
        extra["RCLONE_CONFIG"] = env["AITA_BACKUP_RCLONE_CONFIG"]
    return clean_user_env(user, extra)


def rclone_command(env: Mapping[str, str], *args: str) -> list[str]:
    # Credentials belong in the child's environment, never in argv/`ps` output.
    # Every caller must also pass rclone_environment(env) to the subprocess.
    return ["runuser", "--preserve-environment", "-u", "aita", "--", "rclone", *args,
            "--contimeout", "10s", "--timeout", "30s", "--retries", "1", "--low-level-retries", "2"]


@contextmanager
def file_lock(path: Path, *, shared: bool = False, wait: int = 0,
              create: bool = True) -> Iterator[int]:
    flags = os.O_RDWR | (os.O_CREAT if create else 0) | os.O_NOFOLLOW
    fd = os.open(path, flags, 0o600)
    deadline = time.monotonic() + wait
    next_notice = time.monotonic() + 30
    try:
        while True:
            try:
                fcntl.flock(fd, (fcntl.LOCK_SH if shared else fcntl.LOCK_EX) | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() >= deadline:
                    raise OpsError("Another backup/update owns the lock. Let it finish; do not kill it.") from None
                if time.monotonic() >= next_notice:
                    say("WAIT", "Waiting for the existing backup/check lock; no second writer is started.")
                    next_notice = time.monotonic() + 30
                time.sleep(1)
        yield fd
    finally:
        os.close(fd)


def git_value(repo: Path, *args: str) -> str:
    return capture(["git", "-C", str(repo), *args]).stdout.strip()


def check_worktree(repo: Path, branch: str) -> None:
    if Path(git_value(repo, "rev-parse", "--show-toplevel")).resolve() != repo:
        raise OpsError("This path is not the root of the intended Git checkout.")
    if git_value(repo, "branch", "--show-current") != branch:
        raise OpsError(f"The checkout must be on {branch}. No branch switch or reset was attempted.")
    flags = capture(["git", "-C", str(repo), "ls-files", "-v", "-z"]).stdout.split("\0")
    if any(item and (item[0].islower() or item[0] == "S") for item in flags):
        raise OpsError("Git has assume-unchanged/skip-worktree flags. Review them before deploying; "
                       "the updater will not silently ignore or discard these files.")
    status = git_value(repo, "-c", "core.fsmonitor=false", "status", "--porcelain", "--untracked-files=normal")
    if status:
        raise OpsError("The Ubuntu checkout has local changes/untracked files. Run git status --short, "
                       "preserve your work, then commit/stash deliberately. No reset or automatic stash was done.")


def validate_archive(archive: tarfile.TarFile) -> list[tarfile.TarInfo]:
    members = archive.getmembers()
    if len(members) > 50000 or sum(m.size for m in members) > 1024 ** 3:
        raise OpsError("The source archive is unexpectedly large; inspect the Git commit.")
    seen: set[str] = set()
    for member in members:
        path = PurePosixPath(member.name)
        if (not member.name or path.is_absolute() or ".." in path.parts or "\\" in member.name
                or any(ord(c) < 32 for c in member.name) or ".git" in path.parts
                or not (member.isfile() or member.isdir()) or member.name in seen):
            raise OpsError("Unsafe, linked, duplicate, or non-source entry in the Git snapshot.")
        seen.add(member.name)
    return members


def extract_source(archive_path: Path, destination: Path, owner: pwd.struct_passwd) -> dict[str, str]:
    hashes: dict[str, str] = {}
    destination.mkdir(mode=0o700)
    with tarfile.open(archive_path, "r:") as archive:
        for member in validate_archive(archive):
            target = destination / member.name
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                stream = archive.extractfile(member)
                if stream is None:
                    raise OpsError("Cannot read an archived source file.")
                with stream, target.open("xb") as output:
                    shutil.copyfileobj(stream, output)
                target.chmod(0o755 if member.mode & 0o111 else 0o644)
                hashes[member.name] = sha256(target)
    for path in [destination, *destination.rglob("*")]:
        os.chown(path, owner.pw_uid, owner.pw_gid)
        if path.is_dir():
            path.chmod(0o700)
    return hashes


def verify_source(source: Path, expected: Mapping[str, str]) -> None:
    for relative, digest in expected.items():
        path = source / relative
        if path.is_symlink() or not path.is_file() or sha256(path) != digest:
            raise OpsError(f"Pinned source changed during the build: {relative}. Deployment stopped.")


def latest_run() -> Path | None:
    state = load_json(ROOT / "latest.json", {})
    run_id = state.get("run_id", "") if isinstance(state, dict) else ""
    if not re.fullmatch(r"[0-9]{8}T[0-9]{6}Z-[a-f0-9]{12}", run_id):
        return None
    run = ROOT / "runs" / run_id
    return run if run.is_dir() else None


def show_server_logs(history: bool = False) -> None:
    report_server_state()
    say("LOGS", "Live AITA server logs. Ctrl+C closes this viewer only; AITA keeps running.")
    args = sudo_prefix() + ["journalctl", "-u", SERVICE, "--no-pager", "-o", "short-iso"]
    if history:
        # Ordinary pager; no tmux/alternate terminal configuration is installed.
        if shutil.which("less"):
            producer = subprocess.Popen(args + ["-n", "1000"], stdout=subprocess.PIPE)
            assert producer.stdout is not None
            try:
                subprocess.run(["less", "-R", "+G"], stdin=producer.stdout)
            finally:
                producer.stdout.close()
                producer.wait()
        else:
            subprocess.run(args + ["-n", "1000"])
    else:
        subprocess.run(args + ["-n", "60", "--follow"])


def follow_update(run: Path, *, server_logs: bool = True) -> int:
    log = run / "update.log"
    say("LOGS", f"Saved update log: {log}")
    say("SAFE TO DETACH", "Ctrl+C or an SSH disconnect stops this viewer, not the managed update job.")
    offset = 0
    while True:
        try:
            with log.open("r", encoding="utf-8", errors="replace") as stream:
                stream.seek(offset)
                text = stream.read()
                offset = stream.tell()
                if text:
                    print(text, end="", flush=True)
        except FileNotFoundError:
            pass
        state = load_json(run / "state.json", {})
        if state.get("result") in {"success", "warning", "failed"}:
            time.sleep(0.2)
            # Drain the last lines written immediately before the result file.
            with log.open("r", encoding="utf-8", errors="replace") as stream:
                stream.seek(offset)
                print(stream.read(), end="", flush=True)
            code = int(state.get("exit_code", 1))
            if server_logs and (state.get("server_touched") or state.get("result") in {"success", "warning"}):
                show_server_logs()
            return code
        if not unit_busy():
            say("STOP", "The update job is no longer running and has no final success record. "
                "Check this log and 'sudo journalctl -u aita-update.service'; do not assume deployment succeeded.")
            return 1
        time.sleep(1)


def update(args: argparse.Namespace) -> int:
    if os.geteuid() == 0:
        raise OpsError("Run update as bogdan/the checkout owner, without sudo. It will ask for sudo when needed.")
    require_tools("git", "sudo", "systemd-run", "systemctl", "curl", "tar")
    if not Path("/run/systemd/system").is_dir():
        raise OpsError("Run this on the actual systemd Ubuntu server, not your Mac or a build container.")
    if unit_busy():
        say("ALREADY RUNNING", "Attaching to the existing update; no second build or restart will be started.")
        run = latest_run()
        if run is None:
            raise OpsError("An update is running but its log pointer is not yet available. Retry logs in a moment.")
        if args.no_follow:
            say("RUNNING", f"Existing managed job continues. Log: {run / 'update.log'}")
            return 0
        return follow_update(run)
    repo = Path(args.repo).expanduser().resolve()
    branch = args.branch
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_./-]*", branch) or ".." in branch:
        raise OpsError("Invalid branch name.")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", args.remote):
        raise OpsError("Invalid Git remote name.")
    owner = pwd.getpwuid(os.getuid())
    state_dir = Path(owner.pw_dir) / ".local/state/aita"
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    with file_lock(state_dir / "operator-launch.lock"):
        check_worktree(repo, branch)
        remote_url = git_value(repo, "remote", "get-url", args.remote)
        if remote_url.startswith(("http://", "https://")) and urlsplit(remote_url).username:
            raise OpsError("The Git remote URL embeds credentials. Remove them and use a credential helper or SSH key.")
        say("LOCAL COMMIT", git_value(repo, "rev-parse", "--verify", "HEAD"))
        say("1/9 FETCH", f"Fetching {args.remote}/{branch}. The running server is untouched.")
        say("GITHUB", "For HTTPS, the Git 'Password' prompt expects your GitHub token, not your Ubuntu password.")
        result = subprocess.run(["git", "-C", str(repo), "fetch", "--prune", args.remote])
        if result.returncode:
            raise OpsError("Git fetch failed. Check GitHub credentials or DNS/Internet. No build or deployment started.")
        target = git_value(repo, "rev-parse", "--verify", f"refs/remotes/{args.remote}/{branch}")
        say("FETCHED COMMIT", f"{target} ({args.remote}/{branch}); compare this full ID with GitHub.")
        ancestor = capture(["git", "-C", str(repo), "merge-base", "--is-ancestor", "HEAD", target], check=False)
        if ancestor.returncode:
            raise OpsError("Local commits and the remote do not fast-forward cleanly. Review them; no reset was attempted.")
        capture(["git", "-C", str(repo), "merge", "--ff-only", target], timeout=120)
        check_worktree(repo, branch)
        if git_value(repo, "rev-parse", "HEAD") != target:
            raise OpsError("The checkout does not match the fetched commit. Stopping before deployment.")
        say("PINNED", f"Only committed files from {target} will be built; later edits cannot slip into this release.")
        with tempfile.TemporaryDirectory(prefix="update-snapshot-", dir=state_dir) as tmp:
            archive = Path(tmp) / "source.tar"
            with archive.open("wb") as stream:
                result = subprocess.run(["git", "-C", str(repo), "archive", "--format=tar", target],
                                        stdout=stream, stderr=subprocess.PIPE)
            if result.returncode:
                raise OpsError("Git could not export the selected commit. The running server is untouched.")
            with tarfile.open(archive) as tar:
                members = validate_archive(tar)
                if str(SCRIPT_REL) not in {m.name for m in members}:
                    raise OpsError("aita-ops.py is not committed in the fetched release. Commit/push the whole patch first.")
                # Use the newly fetched operator code even when the checkout updated this script.
                operator = tar.extractfile(str(SCRIPT_REL))
                assert operator is not None
                starter = Path(tmp) / "starter.py"
                starter.write_bytes(operator.read())
            say("PLAN", "Build/test while users stay online; refresh host helpers/assets; take an encrypted "
                "backup; controlled restart; verify local/public HTTP; follow logs.")
            say("DOWNTIME", "One Ktor process means a restart gap. Pending requests/WebSockets may reconnect. "
                "Do not deploy during critical checkout activity when uninterrupted service is required.")
            subprocess.run(["sudo", "-v"], check=True)
            launch = ["sudo", "python3", str(starter), "_start", "--archive", str(archive),
                      "--digest", sha256(archive), "--owner", owner.pw_name, "--commit", target,
                      "--ready-timeout", str(args.ready_timeout), "--java-home", args.java_home]
            if args.with_tests:
                launch.append("--with-tests")
            if args.require_offsite_backup:
                launch.append("--require-offsite-backup")
            elif args.allow_local_backup:
                launch.append("--allow-local-backup")
            if args.force:
                launch.append("--force")
            if subprocess.run(launch).returncode:
                raise OpsError("The launcher did not confirm success. Use status/logs before retrying; no deployment success is assumed.")
    run = latest_run()
    if run is None:
        raise OpsError("Cannot find the managed job's log. Inspect sudo journalctl -u aita-update.service.")
    if args.no_follow:
        say("STARTED", f"Managed update running. Inspect later with logs or status. Log: {run / 'update.log'}")
        return 0
    return follow_update(run)


def start_job(args: argparse.Namespace) -> int:
    if os.geteuid() != 0:
        raise OpsError("Internal job setup requires root.")
    owner = pwd.getpwnam(args.owner)
    if owner.pw_uid == 0:
        raise OpsError("Refusing to build as root.")
    if not re.fullmatch(r"[a-f0-9]{40,64}", args.commit):
        raise OpsError("Invalid pinned commit.")
    # mkdir/touch creation modes are filtered by the caller's umask. Keep the
    # worker's UMask=0077; explicitly set only the intended public traversal bits.
    for directory in (ROOT, ROOT / "runs"):
        directory.mkdir(mode=0o755, exist_ok=True)
        fd = os.open(directory, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            os.fchmod(fd, 0o755)
        finally:
            os.close(fd)
    with file_lock(ROOT / "launch.lock"):
        if unit_busy():
            raise OpsError("Another managed update started first. Use logs to follow it.")
        run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:12]
        run = ROOT / "runs" / run_id
        run.mkdir(mode=0o711)
        run.chmod(0o711)  # The build user must traverse this root-owned parent.
        stable_archive = run / "source.tar"
        shutil.copyfile(args.archive, stable_archive)
        stable_archive.chmod(0o600)
        if sha256(stable_archive) != args.digest:
            raise OpsError("The snapshot changed during transfer; nothing was started.")
        source = run / "source"
        hashes = extract_source(stable_archive, source, owner)
        stable_archive.unlink()
        # Privileged scripts stay root-owned even though Gradle's workspace is user-owned.
        tools = run / "tools"
        shutil.copytree(source / "scripts/linux-field-server", tools)
        for path in [tools, *tools.rglob("*")]:
            os.chown(path, 0, 0)
            path.chmod(0o755 if path.is_dir() or path.suffix in {".sh", ".py"} else 0o644)
        meta = {"run_id": run_id, "owner": owner.pw_name, "uid": owner.pw_uid, "gid": owner.pw_gid,
                "commit": args.commit, "created_at": utc(), "java_home": args.java_home,
                "ready_timeout": args.ready_timeout, "with_tests": args.with_tests,
                "allow_local_backup": not args.require_offsite_backup,
                "require_offsite_backup": args.require_offsite_backup, "force": args.force,
                "source_hashes": hashes}
        atomic_json(run / "meta.json", meta)
        atomic_json(run / "state.json", {"result": "starting", "phase": "preflight"}, owner.pw_gid)
        log = run / "update.log"
        fd = os.open(log, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        try:
            os.fchown(fd, 0, owner.pw_gid)
            os.fchmod(fd, 0o640)  # Exact root:owner-group log access even under 0077.
        finally:
            os.close(fd)
        atomic_json(ROOT / "latest.json", {"run_id": run_id}, 0)
        os.chmod(ROOT / "latest.json", 0o644)
        cmd = ["systemd-run", "--unit", UPDATE_UNIT, "--collect", "--service-type=exec",
               "--expand-environment=no", "--property=UMask=0077", "--property=Nice=10",
               "--property=IOSchedulingClass=best-effort", "--property=IOSchedulingPriority=7",
               f"--property=StandardOutput=append:{str(log).replace('%', '%%')}",
               "--property=StandardError=inherit", "--property=TimeoutStopSec=90",
               "/usr/bin/python3", "-u", str(tools / "aita-ops.py"), "_worker", "--run-dir", str(run)]
        try:
            result = capture(cmd, timeout=30, check=False)
        except OpsError:
            if not unit_busy():
                raise OpsError("Job launch confirmation timed out. Inspect status before retrying; no success is assumed.") from None
            say("STARTED", "systemd reports an active update despite delayed launch confirmation; follow its saved log.")
            return 0
        if result.returncode:
            atomic_json(run / "state.json", {"result": "failed", "phase": "job launch", "exit_code": 1,
                        "server_touched": False}, owner.pw_gid)
            raise OpsError("systemd could not start the update job: " + redact(result.stderr[-1500:]))
        say("DETACHED JOB", f"Started {UPDATE_UNIT} (the updater, NOT the backend). "
            "AITA has not been restarted by this launcher. An SSH disconnect will not stop the managed job.")
    return 0


def fault_advice(text: str, phase: str) -> str:
    lowered = text.lower()
    rules = [
        (("no space left", "disk quota", "insufficient free"), "The disk is short of space. Remove reviewed old build artifacts, not database files or backups."),
        (("unknownhostexception", "could not resolve", "temporary failure in name resolution", "name or service not known"), "Ubuntu could not reach a required hostname. Check outbound Internet/DNS; leave Cloudflare routing and database history alone."),
        (("unresolved reference", "compilation error", "compilation failed"), "The fetched Kotlin source does not compile. Keep the compiler errors; the old server was not stopped by the build."),
        (("there were failing tests", "failed (failures=", "failed (errors="), "A regression test failed. No test was silently skipped; review the named failing test before release."),
        (("cloud backup is not configured",), "Strict offsite protection is enabled, but its destination is missing. Configure it for aita, or run the normal update command for the temporary encrypted-local-only policy."),
        (("rate_limit_exceeded", "ratelimitexceeded", "quota exceeded for quota metric"), "The cloud API rejected the offsite check/upload. Normal update uses an encrypted local backup without cloud access; strict offsite deployment needs the cloud connection fixed first."),
        (("invalid_grant", "token has been expired", "failed to create file system", "didn't find section"), "rclone authentication/configuration failed for the aita service account. Its configuration is separate from your Mac, bogdan, and root."),
        (("checksum mismatch", "validate failed", "migration checksum", "flywayvalidateexception"), "Flyway detected a schema/history mismatch. Do not repair checksums, edit an applied migration, or roll back the JAR blindly."),
        (("permission denied", "accessdenied"), "A file/user permission check failed. Do not run Gradle as root or chmod everything to 777."),
        (("java 21 is required", "java is missing", "unsupported class file", "invalid source release"), "Use Java 21 for both the build and installed service. No Java upgrade was applied automatically."),
    ]
    for needles, advice in rules:
        if any(needle in lowered for needle in needles):
            return advice
    if phase == "restart":
        return ("The new process has not passed readiness. It may still be migrating or waiting for PostgreSQL. "
                "Read the current invocation log; do not start another deploy/backup or force a database rollback.")
    if phase == "backup":
        return "A fresh encrypted/offsite backup was not confirmed. The updater stopped before restarting AITA. Run backups --download."
    return f"Stopped during {phase}. Read the preceding error; no automatic database repair or rollback was attempted."


def atomic_install(source: Path, destination: Path, mode: int, *, uid: int = 0, gid: int = 0) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=f".{destination.name}.", dir=destination.parent)
    os.close(fd)
    try:
        shutil.copyfile(source, name)
        os.chmod(name, mode)
        os.chown(name, uid, gid)
        os.replace(name, destination)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def sync_helpers(tools: Path, backup: Path) -> None:
    names = ["aita-linux-common.sh", "backup-aita-postgres.sh", "healthcheck-aita.sh",
             "test-aita-readiness.sh", "restore-test-backup.sh"]
    INSTALLED.mkdir(mode=0o755, exist_ok=True)
    for name in names:
        capture(["bash", "-n", str(tools / name)])
    for name in names:
        target = INSTALLED / name
        if target.is_symlink():
            raise OpsError(f"Installed helper {name} is a symlink; review it before replacement.")
        if target.exists() and sha256(target) == sha256(tools / name):
            continue
        if target.exists():
            backup.mkdir(parents=True, exist_ok=True, mode=0o700)
            shutil.copy2(target, backup / name)
        atomic_install(tools / name, target, 0o755)
        say("HELPER", f"Updated {name}; service units, timers and production secrets were not replaced.")


def ensure_storage_path(path: Path, base: Path = Path("/srv/aita")) -> Path:
    path = path.expanduser()
    if not path.is_absolute() or path == base or base not in path.resolve().parents:
        raise OpsError(f"Unexpected production storage path {path}. Review it; no broad rsync/delete will be attempted.")
    return path


def remote_preflight(env: Mapping[str, str], *, allow_local: bool) -> str:
    # Short-circuit BEFORE parsing the old destination or invoking rclone. An
    # expired token, quota error, or missing rclone must not gate local-only updates.
    if allow_local:
        say("BACKUP POLICY", "LOCAL ONLY for this update: encrypted local backup remains mandatory. "
            "Cloud checks/uploads are skipped; offsite protection is NOT verified. "
            "A disk/machine loss can still lose the local backup. "
            "Drive settings and scheduled backup jobs are unchanged.")
        return ""
    say("BACKUP POLICY", "OFFSITE REQUIRED: cloud access and a verified encrypted offsite restore point "
        "must succeed before restarting AITA.")
    remote = env.get("AITA_BACKUP_RCLONE_REMOTE", "").strip()
    if not remote:
        raise OpsError("Cloud backup is NOT configured. Strict mode (--require-offsite-backup) needs "
                       "AITA_BACKUP_RCLONE_REMOTE and working rclone credentials for aita. "
                       "Use the normal update command for temporary encrypted-local-only protection.")
    remote = validate_remote(remote)
    require_tools("rclone")
    result = capture(rclone_command(env, "listremotes"), timeout=45, env=rclone_environment(env))
    if remote.split(":", 1)[0] + ":" not in result.stdout.splitlines():
        raise OpsError("The configured remote is missing from the aita service account's rclone configuration.")
    # Check account access, not a possibly new backup subdirectory. copyto creates
    # the configured destination when the first real encrypted backup is sent.
    capture(rclone_command(env, "lsf", remote.split(":", 1)[0] + ":", "--max-depth", "1", "--dirs-only"), timeout=60, env=rclone_environment(env))
    return remote


def preserve_predeploy_backup(env: Mapping[str, str], run: Path, remote: str, started: float) -> Path:
    directory = ensure_storage_path(Path(env.get("AITA_BACKUP_DIR", "/srv/aita/backups")))
    source = directory / "aita_latest.dump.age"
    lock = Path(env.get("AITA_BACKUP_LOCK_FILE", "/srv/aita/locks/postgres-backup.lock"))
    with file_lock(lock, shared=True, wait=600, create=False):
        if source.is_symlink() or not source.is_file() or source.stat().st_size == 0:
            raise OpsError("The backup job returned, but no encrypted aita_latest.dump.age exists.")
        # A pre-existing job can satisfy systemctl start --wait; allow only a recent snapshot.
        if source.stat().st_mtime < started - 900 or source.stat().st_mtime > time.time() + 60:
            raise OpsError("The latest backup is stale or dated in the future; it is not a safe pre-deploy restore point.")
        with source.open("rb") as stream:
            if not stream.read(80).startswith(b"age-encryption.org/v1"):
                raise OpsError("The .age file does not contain an age header. Refusing to treat it as encrypted backup.")
        target_dir = ensure_storage_path(directory / "deployments")
        user = pwd.getpwnam("aita")
        target_dir.mkdir(mode=0o750, exist_ok=True)
        os.chown(target_dir, user.pw_uid, user.pw_gid)
        target = target_dir / f"aita_{run.name}.dump.age"
        atomic_install(source, target, 0o600, uid=user.pw_uid, gid=user.pw_gid)
    if remote:
        stream_command(rclone_command(env, "copyto", str(target), remote + "/deployments/" + target.name,
                                      "--checksum", "--max-duration", "10m"), env=rclone_environment(env))
        verify_remote_file(env, target, remote + "/deployments", download=True, timeout=300)
    say("RESTORE POINT", f"Preserved {target}. It is not overwritten by the next five-minute backup.")
    return target


def worker(args: argparse.Namespace) -> int:
    if os.geteuid() != 0:
        raise OpsError("The managed worker requires root for installation, not for compilation.")
    run = Path(args.run_dir).resolve()
    if run.parent != (ROOT / "runs").resolve():
        raise OpsError("Invalid managed run directory.")
    meta = load_json(run / "meta.json")
    if not isinstance(meta, dict):
        raise OpsError("Managed job metadata is missing.")
    owner = pwd.getpwnam(meta["owner"])
    source, tools = run / "source", run / "tools"
    phase, touched, code = "preflight", False, 1
    server_before = None
    # A previous launcher can fetch this newer worker. Missing metadata therefore
    # uses the new temporary default, not the former mandatory-cloud policy.
    require_offsite = meta.get("require_offsite_backup", False)
    backup_policy = "offsite-required" if require_offsite else "local-only"
    lock_file = None
    def step(name: str, text: str) -> None:
        nonlocal phase
        phase = name
        atomic_json(run / "state.json", {"result": "running", "phase": phase, "at": utc(),
                    "server_touched": touched, "backup_policy": backup_policy}, owner.pw_gid)
        say("STEP " + name.upper(), text)
    try:
        if not isinstance(require_offsite, bool):
            raise OpsError("Invalid managed backup policy; expected a boolean require_offsite_backup.")
        # Same lock as bogdan's existing lower-level deploy script; FD ownership is
        # explicitly handed to that child, never released between build and switch.
        state_dir = Path(owner.pw_dir) / ".local/state/aita"
        state_dir.mkdir(parents=True, exist_ok=True)
        os.chown(state_dir, owner.pw_uid, owner.pw_gid)
        lock_path = state_dir / "deploy.lock"
        lock_file = os.open(lock_path, os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
        os.fchown(lock_file, owner.pw_uid, owner.pw_gid)
        try:
            fcntl.flock(lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise OpsError("Another low-level AITA deployment is running. This managed update stopped without changing the server.") from None
        say("RELEASE", f"AITA operator {VERSION}; pinned commit {meta['commit']}; build user {owner.pw_name}.")
        server_before = report_server_state("SERVER BEFORE")
        step("preflight", "Checking service, Java, database access, storage and the selected backup policy.")
        require_tools("bash", "runuser", "rsync", "unzip", "sha256sum", "flock", "curl", "ss", "pg_dump", "pg_restore", "age")
        env = read_env(ENV_FILE)
        protect_secrets(env)
        service = properties(SERVICE, "User", "Group", "ActiveState")
        if service.get("User") != "aita" or service.get("Group") != "aita":
            raise OpsError("The installed server service does not use User=aita / Group=aita. Inspect its unit before automation.")
        if env.get("AITA_HOST", "127.0.0.1") not in {"127.0.0.1", "localhost", "::1"} or env.get("AITA_PORT", "8080") != "8080":
            raise OpsError("This updater targets the existing loopback origin on port 8080; inspect customized routing first.")
        if env.get("AITA_BACKUP_ALLOW_PLAINTEXT", "false") == "true":
            raise OpsError("Disable plaintext backups and configure the age public recipient before managed deployment.")
        java = Path(meta["java_home"]) / "bin/java"
        result = capture([str(java), "-version"])
        if not re.search(r'version "21(?:\.|\")', result.stderr + result.stdout):
            raise OpsError("Java 21 is required. Install/configure openjdk-21-jdk; no OS changes were made.")
        for path in (run, Path("/opt/aita/app"), Path(env.get("AITA_BACKUP_DIR", "/srv/aita/backups"))):
            if shutil.disk_usage(path).free < 5 * 1024 ** 3:
                raise OpsError(f"Insufficient free disk space at {path}: keep at least 5 GiB available before deploying.")
        capture(["runuser", "-u", "aita", "--", "test", "-r", str(ENV_FILE)])
        stream_command(["bash", str(tools / "test-aita-readiness.sh"), "--service-preflight"],
                       env={**clean_user_env(pwd.getpwuid(0)), "JAVA_HOME": meta["java_home"],
                            "PGCONNECT_TIMEOUT": "10", "PGOPTIONS": "-c statement_timeout=10000 -c lock_timeout=2000"})
        if service.get("ActiveState") in ACTIVE_STATES and http_status("http://127.0.0.1:8080/readyz", local=True) != "200":
            raise OpsError("The existing Java service is running but not ready. It may still be migrating. "
                           "Use status/logs first; this updater will not interrupt an unexplained startup.")
        remote = remote_preflight(env, allow_local=not require_offsite)
        public_url = public_origin_url(env.get("AITA_PUBLIC_HEALTH_URL") or env.get("AITA_PUBLIC_SERVER_URL", ""))
        say("PUBLIC TARGET", public_url)
        last = load_json(ROOT / "last-success.json", {})
        if not meta["force"] and last.get("commit") == meta["commit"] and CURRENT_JAR.exists() and \
                sha256(CURRENT_JAR) == last.get("jar_sha256") and \
                sha256(ENV_FILE) == last.get("env_sha256") and \
                http_status("http://127.0.0.1:8080/readyz", local=True) == "200" and \
                http_status("http://127.0.0.1:8080/auth/capabilities", local=True) == "200":
            say("UNCHANGED", "This exact release is already installed and locally ready. No build or restart is needed.")
            say("VERIFIED INSTALLED COMMIT", f"{meta['commit']}; JAR SHA-256 {last['jar_sha256']}")
        else:
            step("tests", "Running offline deployment/operator regression checks before touching production files.")
            for test in ("test-deployment-scripts.py", "test-aita-ops.py"):
                stream_command(user_command(owner, ["python3", str(source / "scripts/linux-field-server/tests" / test)]))
            step("build", "Compiling the pinned source as the normal user; the old server stays online.")
            build = ["bash", str(tools / "build-aita-server.sh"), "--project-root", str(source)]
            if meta["with_tests"]:
                build.append("--with-tests")
            stream_command(user_command(owner, build, {"JAVA_HOME": meta["java_home"],
                           "AITA_BUILD_SOURCE_COMMIT": meta["commit"],
                           "PATH": str(Path(meta["java_home"]) / "bin") + ":" + SAFE_PATH}))
            verify_source(source, meta["source_hashes"])
            candidate = source / "server/build/libs/aita-server-all.jar"
            if not candidate.is_file() or candidate.is_symlink():
                raise OpsError("Build reported success but the candidate JAR is missing or linked. No deployment occurred.")
            candidate_hash = sha256(candidate)
            say("BUILT COMMIT", f"{meta['commit']}; candidate JAR SHA-256 {candidate_hash}")
            step("helpers", "Refreshing installed scripts atomically; preserving originals. No secret or unit-file replacement.")
            sync_helpers(tools, run / "previous-helpers")
            step("backup", "Taking an encrypted database backup while the old server is still serving users.")
            started = time.time()
            if require_offsite:
                stream_command(["systemctl", "start", "--wait", "aita-backup.service"])
            else:
                # Do not invoke the scheduled service here: its configured upload
                # may still fail/hang. Run the same root-owned helper as aita with
                # an invocation-only cloud skip. No env file or timer is rewritten.
                stream_command(user_command(pwd.getpwnam("aita"), [
                    "bash", str(tools / "backup-aita-postgres.sh"),
                    "--env-file", str(ENV_FILE), "--local-only"]))
            preserve_predeploy_backup(env, run, remote, started)
            step("assets", "Publishing matching assets with backups; production config and extra files are retained.")
            destination = ensure_storage_path(Path(env.get("AITA_ASSETS_ROOT") or
                                                   str(Path(env.get("AITA_SERVER_FILES_ROOT", "/srv/aita/server-files")) / "assets")))
            asset_source = source / "server/assets"
            if not asset_source.is_dir():
                raise OpsError("The pinned release has no server/assets directory.")
            destination.mkdir(parents=True, exist_ok=True)
            stream_command(["rsync", "-ac", "--safe-links", "--itemize-changes", "--chown=aita:aita", "--chmod=D750,F640",
                            "--backup", f"--backup-dir={run / 'previous-assets'}", str(asset_source) + "/", str(destination) + "/"])
            verify_source(source, meta["source_hashes"])
            step("waiting-backup", "Waiting for any active dump before switching; the existing server is not stopped yet.")
            backup_lock = Path(env.get("AITA_BACKUP_LOCK_FILE", "/srv/aita/locks/postgres-backup.lock"))
            # Prevent this host's scheduled pg_dump from competing with startup
            # DDL. Timers remain installed/active; their writers wait on the lock.
            with file_lock(backup_lock, wait=600, create=False):
                # Recheck the exact artifact after backup/assets work, before any restart.
                if candidate.is_symlink() or not candidate.is_file() or sha256(candidate) != candidate_hash:
                    raise OpsError("Candidate JAR changed after build verification. Deployment stopped.")
                touched = True
                step("restart", "Switching the JAR now. Requests may reconnect until migrations/startup finish; no blind rollback.")
                deploy_env = {**clean_user_env(owner), "AITA_DEPLOY_LOCK_FD": str(lock_file),
                              "AITA_BUILD_SOURCE_COMMIT": meta["commit"]}
                # The inherited descriptor keeps legacy and managed deployments mutually exclusive.
                cmd = ["bash", str(tools / "deploy-aita-server.sh"), "--project-root", str(source),
                       "--ready-timeout", str(meta["ready_timeout"])]
                stream_command(cmd, env=deploy_env, pass_fds=(lock_file,))
            jar_hash = sha256(CURRENT_JAR)
            if jar_hash != candidate_hash:
                raise OpsError("Installed JAR does not match the built candidate. Inspect the server; no automatic rollback was attempted.")
            say("DEPLOYED COMMIT", f"{meta['commit']}; installed JAR SHA-256 {jar_hash}; local readiness verified.")
            server_at_deploy = report_server_state("SERVER AFTER RESTART")
            atomic_json(ROOT / "last-success.json", {"commit": meta["commit"], "jar_sha256": jar_hash,
                        "run_id": run.name, "completed_at": utc(), "env_sha256": sha256(ENV_FILE),
                        "backup_policy": backup_policy, "server_at_deploy": server_at_deploy})
        step("public-check", "Checking the public Worker-to-origin path separately from local readiness.")
        failures = []
        for path in ("/readyz", "/auth/capabilities"):
            status = http_status(public_url.rstrip("/") + path)
            say("PUBLIC", f"{path}: {status}")
            if status != "200":
                failures.append(path)
        public_failures = bool(failures)
        if public_failures:
            say("WARNING", "Local deployment is ready, but public access failed. Check Worker/Tunnel routing; "
                "do not roll back the database or repeatedly deploy the same JAR. Run aita-ops.py connection for read-only edge/origin diagnostics.")
        step("backup-status", "Checking backup timers and local freshness" +
             (" and cloud contents." if require_offsite else "; cloud checks remain skipped for this update."))
        try:
            for finding in backup_snapshot(check_offsite=require_offsite):
                say(finding.level, finding.name + ": " + finding.detail)
                if finding.level == "FAIL":
                    failures.append("backup: " + finding.name)
        except (OpsError, OSError, KeyError) as error:
            failures.append("backup-status")
            say("WARNING", "Post-update backup checks could not complete: " + str(error))
        code = 2 if failures else 0
        if failures:
            say("OPERATIONAL WARNING", "The local backend is ready, but one or more public/backup checks need attention. "
                "Do not redeploy merely to clear this warning; use status/backups and the relevant log.")
        if not require_offsite:
            say("OFFSITE SKIPPED", "This update did not require or verify Google Drive. "
                "No cloud success is claimed. Use update --require-offsite-backup after migrating the backup account.")
        say("DONE", "Release installed/verified locally. Real email login, receipt printing and customer workflows still need their own checks.")
        # Only our isolated build copy is discarded, never the user's repository, DB, keys or backups.
        shutil.rmtree(source)
        result_name = "warning" if failures else "success"
    except (OpsError, OSError, subprocess.SubprocessError, KeyError, ValueError) as error:
        say("STOP", f"Phase={phase}; requested commit={meta['commit']}. {error}")
        try:
            text = (run / "update.log").read_text(errors="replace")[-50000:]
        except OSError:
            text = str(error)
        say("WHAT TO DO", fault_advice(text + str(error), phase))
        if touched:
            say("IMPORTANT", "No automatic JAR/database rollback or process kill was performed. Inspect the current server state before retrying.")
        else:
            say("NOT DEPLOYED", f"Commit {meta['commit']} was NOT installed by this job (stopped in {phase}).")
            say("SAFE", "Only means this job did not stop/restart AITA; it is NOT a successful deployment or health check. "
                "Earlier helper/asset/backup steps may have completed. Read STOP / WHAT TO DO above.")
            previous = load_json(ROOT / "last-success.json", {})
            if isinstance(previous, dict) and previous.get("commit"):
                say("LAST VERIFIED DEPLOYMENT", str(previous["commit"]) + " (historical record; use status to check current state)")
        result_name = "failed"
        code = 1
    finally:
        if lock_file is not None:
            os.close(lock_file)
    # Show the real backend even when tests/build/preflight failed before restart.
    # These observations are diagnostic, not a replacement for readiness checks.
    server_after = report_server_state("SERVER FINAL")
    process_change = report_process_change(server_before, server_after)
    atomic_json(run / "state.json", {"result": result_name, "phase": phase, "exit_code": code,
                "at": utc(), "server_touched": touched, "commit": meta["commit"],
                "requested_commit": meta["commit"],
                "deployment_verified": result_name in {"success", "warning"},
                "backup_policy": backup_policy, "server_before": server_before,
                "server_after": server_after, "process_change": process_change}, owner.pw_gid)
    return code


def hash_file(path: Path, algorithm: str) -> str:
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download_hash(env: Mapping[str, str], remote_file: str, timeout: int = 180) -> tuple[int, str]:
    digest, size = hashlib.sha256(), 0
    # Only encrypted bytes pass through this pipe. Nothing is restored or uploaded.
    with tempfile.TemporaryFile() as errors:
        p = subprocess.Popen(rclone_command(env, "cat", remote_file), stdin=subprocess.DEVNULL,
                             stdout=subprocess.PIPE, stderr=errors, start_new_session=True, env=rclone_environment(env))
        assert p.stdout is not None
        selector = selectors.DefaultSelector()
        selector.register(p.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + timeout
        try:
            while selector.get_map():
                if time.monotonic() >= deadline:
                    raise OpsError("Cloud download verification timed out; contents are NOT confirmed. Retry on a stable connection.")
                for key, _ in selector.select(1):
                    chunk = os.read(key.fd, 1024 * 1024)
                    if not chunk:
                        selector.unregister(key.fileobj)
                    else:
                        size += len(chunk)
                        digest.update(chunk)
            code = p.wait(timeout=max(1, int(deadline - time.monotonic())))
            if code:
                errors.seek(0)
                raise OpsError("Cloud download failed: " + redact(errors.read(2200).decode("utf-8", "replace")))
            return size, digest.hexdigest()
        finally:
            selector.close()
            p.stdout.close()
            if p.poll() is None:
                os.killpg(p.pid, signal.SIGTERM)
                try:
                    p.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(p.pid, signal.SIGKILL)
                    p.wait()


def matching_object(objects: object, name: str) -> dict:
    if not isinstance(objects, list):
        raise OpsError("Unexpected cloud directory response; contents are NOT confirmed.")
    matches = [item for item in objects if isinstance(item, dict)
               and item.get("Name") == name and not item.get("IsDir")]
    if not matches:
        raise OpsError(f"{name} is missing from the configured cloud folder. An old test file is not a database backup.")
    if len(matches) > 1:
        raise OpsError(f"The cloud contains duplicate copies named {name}. Review them; no automatic deletion/deduplication was done.")
    return matches[0]


def verify_remote_file(env: Mapping[str, str], local: Path, remote: str,
                       *, download: bool = False, timeout: int = 180) -> str:
    remote = validate_remote(remote)
    response = capture(rclone_command(env, "lsjson", remote, "--files-only", "--max-depth", "1", "--hash"), timeout=60, env=rclone_environment(env))
    try:
        item = matching_object(json.loads(response.stdout), local.name)
    except ValueError:
        raise OpsError("rclone returned invalid JSON; cloud contents are NOT confirmed.") from None
    if item.get("Size") != local.stat().st_size:
        raise OpsError("The cloud backup size differs from the local encrypted file. Upload is not verified.")
    if download:
        size, digest = download_hash(env, remote + "/" + local.name, timeout)
        if size != local.stat().st_size or digest != sha256(local):
            raise OpsError("Downloaded cloud bytes do NOT match the local encrypted backup.")
        return "Downloaded encrypted cloud bytes match local size and SHA-256."
    hashes = item.get("Hashes") or {}
    for algorithm in ("sha256", "sha1", "md5"):
        expected = hashes.get(algorithm) or hashes.get(algorithm.upper())
        if expected:
            if hash_file(local, algorithm).lower() != str(expected).lower():
                raise OpsError("Cloud and local backup checksums differ. Upload is not verified.")
            return f"Cloud size and {algorithm.upper()} match the local encrypted file (metadata check, not a restore)."
    raise OpsError("Size matches, but no comparable cloud checksum is available. Run backups --download for byte verification.")


@dataclass(frozen=True)
class Finding:
    level: str
    name: str
    detail: str


def freshness(path: Path, maximum: int, now: float | None = None) -> Finding:
    now = time.time() if now is None else now
    if not path.is_file() or path.stat().st_size == 0:
        return Finding("FAIL", path.name, "Missing or empty backup.")
    age = now - path.stat().st_mtime
    if age < -60:
        return Finding("FAIL", path.name, "Timestamp is in the future; check the server clock.")
    level = "PASS" if age <= maximum else "FAIL"
    return Finding(level, path.name, f"Age {max(0, int(age // 60))} minutes; limit {maximum // 60} minutes; "
                   f"{path.stat().st_size:,} bytes. File time is not a restore test.")


def backup_snapshot(download: bool = False, *, check_offsite: bool = True) -> list[Finding]:
    if download and not check_offsite:
        raise OpsError("Cloud download verification requires offsite checks.")
    env = read_env(ENV_FILE)
    protect_secrets(env)
    findings: list[Finding] = []
    for timer in ("aita-backup.timer", "aita-backup-daily.timer"):
        props = properties(timer, "ActiveState", "UnitFileState", "LastTriggerUSec")
        ok = props.get("ActiveState") == "active" and props.get("UnitFileState") == "enabled"
        findings.append(Finding("PASS" if ok else "FAIL", timer,
                                f"state={props.get('ActiveState', 'missing')}; enabled={props.get('UnitFileState', 'unknown')}; "
                                f"last trigger={props.get('LastTriggerUSec', 'unknown')}"))
    for unit in ("aita-backup.service", "aita-backup-daily.service"):
        props = properties(unit, "User", "ActiveState", "Result", "ExecMainStatus", "ExecMainExitTimestamp")
        if props.get("User") != "aita":
            findings.append(Finding("FAIL", unit, "Expected service User=aita; do not test rclone as a different account."))
        elif props.get("ActiveState") in ACTIVE_STATES:
            findings.append(Finding("WAIT", unit, "A backup is running; this is not itself an error."))
        elif props.get("Result") == "success" and props.get("ExecMainStatus") == "0":
            findings.append(Finding("PASS", unit, "Last process result succeeded; inactive is normal for a finished oneshot job."))
        else:
            findings.append(Finding("FAIL", unit, "Last run failed or is unknown; inspect journalctl for this unit."))
    directory = ensure_storage_path(Path(env.get("AITA_BACKUP_DIR", "/srv/aita/backups")))
    local = directory / "aita_latest.dump.age"
    findings.append(freshness(local, 900))
    daily_files = list((directory / "daily").glob("aita_*.dump.age"))
    if daily_files:
        daily = max(daily_files, key=lambda path: path.stat().st_mtime)
        findings.append(freshness(daily, 36 * 3600))
    else:
        findings.append(Finding("FAIL", "Daily archive", "No encrypted daily archive exists in the configured local folder."))
    remote = env.get("AITA_BACKUP_RCLONE_REMOTE", "").strip()
    if not check_offsite:
        findings.append(Finding("SKIPPED", "Offsite", "Cloud checks/uploads are disabled for this update only. "
                                "Offsite protection is NOT verified. Run backups --download separately to diagnose it."))
    elif not remote:
        findings.append(Finding("FAIL", "Offsite", "AITA_BACKUP_RCLONE_REMOTE is empty: backups remain on Ubuntu only."))
    elif not local.is_file():
        findings.append(Finding("FAIL", "Offsite", "No current local backup to compare with the cloud."))
    else:
        try:
            validate_remote(remote)
            require_tools("rclone")
            user = pwd.getpwnam("aita")
            cfg = env.get("AITA_BACKUP_RCLONE_CONFIG") or env.get("RCLONE_CONFIG") or str(Path(user.pw_dir) / ".config/rclone/rclone.conf")
            findings.append(Finding("INFO", "Cloud target", f"{remote}; run as aita; rclone config: {cfg}"))
            if Path(cfg).is_absolute() and any(Path(root) == Path(cfg) or Path(root) in Path(cfg).parents for root in ("/home", "/root")):
                findings.append(Finding("FAIL", "Service sandbox", "The installed backup units hide /home and /root. Store the service rclone config under /var/lib/aita, not a login user's home."))
            lock = Path(env.get("AITA_BACKUP_LOCK_FILE", "/srv/aita/locks/postgres-backup.lock"))
            if not lock.exists():
                raise OpsError("The backup lock is missing; cannot make a race-safe comparison. Run the configured backup job first.")
            # Shared lock stabilizes both latest file and this host's remote upload.
            # The writer waits (bounded) rather than failing if a deep check is in progress.
            with file_lock(lock, shared=True, create=False):
                with local.open("rb") as stream:
                    if not stream.read(80).startswith(b"age-encryption.org/v1"):
                        raise OpsError("The .age file has no age header. Encryption is NOT confirmed.")
                findings.append(Finding("PASS", "Encrypted format", "age header present; decryption/recovery still untested."))
                message = verify_remote_file(env, local, remote, download=download)
                findings.append(Finding("PASS", "Cloud latest", message))
                if daily_files:
                    # Latest daily should also exist offsite, not only the frequently overwritten file.
                    try:
                        message = verify_remote_file(env, daily, remote)
                        findings.append(Finding("PASS", "Cloud daily", message))
                    except OpsError as error:
                        findings.append(Finding("FAIL", "Cloud daily", str(error)))
        except (OpsError, OSError, KeyError) as error:
            level = "WAIT" if "owns the lock" in str(error) else "FAIL"
            findings.append(Finding(level, "Cloud verification", str(error)))
    findings.append(Finding("NOT TESTED", "Recoverability", "No database restored. A separate isolated restore with the off-server age private key is still required."))
    return findings


def backups(args: argparse.Namespace) -> int:
    if os.geteuid() != 0:
        cmd = ["sudo", "python3", str(Path(__file__).resolve()), "backups"]
        if args.watch:
            cmd += ["--watch", "--interval", str(args.interval)]
        if args.download:
            cmd.append("--download")
        if args.run_now:
            cmd.append("--run-now")
        return subprocess.call(cmd)
    require_tools("systemctl", "runuser")
    if args.run_now:
        if unit_busy():
            raise OpsError("An update is running. Do not start an additional manual backup during it; watch the existing job instead.")
        say("BACKUP NOW", "Starting the existing backup service once. This creates/uploads a backup; it does not restore or restart AITA.")
        stream_command(["systemctl", "start", "--wait", "aita-backup.service"])
    while True:
        say("BACKUPS " + utc(), "Read-only health/content check. No remote file deletion, database restore, or configuration change.")
        findings = backup_snapshot(args.download)
        for finding in findings:
            say(finding.level, finding.name + ": " + finding.detail)
        failures = sum(f.level == "FAIL" for f in findings)
        waits = sum(f.level == "WAIT" for f in findings)
        say("SUMMARY", f"{failures} problem(s), {waits} running/busy check(s). A cloud match is NOT a restore test or continuous replication.")
        if not args.watch:
            return 1 if failures else (2 if waits else 0)
        say("WATCH", f"Next check in {args.interval}s. Ctrl+C stops only this read-only monitor. It does not start a scheduled monitor.")
        time.sleep(args.interval)


def status() -> int:
    current_server = report_server_state()
    say("LOCAL", f"/readyz: {http_status('http://127.0.0.1:8080/readyz', local=True)}")
    previous = load_json(ROOT / "last-success.json", {})
    if isinstance(previous, dict) and previous.get("commit"):
        say("LAST VERIFIED COMMIT", str(previous["commit"]))
        say("DEPLOYMENT RECORDED AT", str(previous.get("completed_at", "unknown")) +
            " (deployment verification time, NOT the process launch time)")
        if previous.get("server_at_deploy"):
            change = server_process_change(previous["server_at_deploy"], current_server)
            say("DEPLOYMENT PROCESS MATCH", change +
                " compared with the process observed at that historical deployment; process identity alone does not prove which JAR it loaded.")
        try:
            installed_hash = sha256(CURRENT_JAR)
            say("INSTALLED JAR SHA-256", installed_hash)
            if installed_hash == previous.get("jar_sha256"):
                say("ARTIFACT MATCH", "Installed file matches that deployment. This does not independently identify a running process's in-memory JAR.")
            else:
                say("WARNING", "Installed JAR differs from the last verified deployment; its current commit is not established.")
        except OSError:
            say("WARNING", "Cannot read the installed JAR to check its deployment hash.")
    run = latest_run()
    if run:
        state = load_json(run / "state.json", {})
        if isinstance(state, dict):
            # Keep the two detailed snapshots in the saved record, not a huge dict
            # in the terminal. The fresh live snapshot is already shown above.
            summary = {key: state[key] for key in ("result", "phase", "exit_code", "at", "server_touched",
                       "commit", "requested_commit", "deployment_verified", "backup_policy", "process_change") if key in state}
            say("LAST UPDATE", str(summary))
        else:
            say("LAST UPDATE", "Record unreadable/invalid; no deployment result assumed.")
        say("LOG FILE", str(run / "update.log"))
    else:
        say("INFO", "No managed update record yet.")
    return 0


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", metavar="{update,logs,status,connection,backups}")
    p = sub.add_parser("update", help="fetch, build, encrypted backup, deploy, health checks and logs")
    p.add_argument("--repo", default=str(Path.home() / "IdeaProjects/AITA"))
    p.add_argument("--remote", default="origin")
    p.add_argument("--branch", default="master")
    p.add_argument("--java-home", default=str(DEFAULT_JAVA))
    p.add_argument("--ready-timeout", type=int, default=600)
    p.add_argument("--with-tests", action="store_true", help="also run shared/server Kotlin tests")
    policy = p.add_mutually_exclusive_group()
    policy.add_argument("--require-offsite-backup", action="store_true",
                        help="require working cloud access/upload/verification; default temporarily skips cloud and requires an encrypted LOCAL backup")
    policy.add_argument("--allow-local-backup", action="store_true",
                        help="compatibility alias for the default local-only update; configured cloud access is also skipped")
    p.add_argument("--force", action="store_true", help="rebuild/redeploy even if last successful commit and installed hash match")
    p.add_argument("--no-follow", action="store_true", help="return once the managed job starts; inspect later with logs/status")
    p = sub.add_parser("logs", help="reattach to update output; then view AITA server logs")
    p.add_argument("--history", action="store_true", help="scroll/search the saved update log with less (q to quit)")
    p.add_argument("--update-only", action="store_true", help="return the managed update result without following server logs")
    sub.add_parser("status", help="read current server and last managed update state")
    p = sub.add_parser("connection", help="read-only local, public Worker/origin and tunnel diagnostics")
    p.add_argument("--public-url", default=DEFAULT_PUBLIC_ORIGIN)
    p = sub.add_parser("backups", help="check timers, local freshness and service-user cloud contents")
    p.add_argument("--watch", action="store_true")
    p.add_argument("--interval", type=int, default=60)
    p.add_argument("--download", action="store_true", help="download encrypted latest bytes and compare SHA-256; no private key needed")
    p.add_argument("--run-now", action="store_true", help="explicitly start one real backup before checking")
    p = sub.add_parser("_start")
    for name in ("archive", "digest", "owner", "commit", "java-home"):
        p.add_argument("--" + name, required=True)
    p.add_argument("--ready-timeout", type=int, required=True)
    for name in ("with-tests", "force"):
        p.add_argument("--" + name, action="store_true")
    policy = p.add_mutually_exclusive_group()
    for name in ("require-offsite-backup", "allow-local-backup"):
        policy.add_argument("--" + name, action="store_true")
    p = sub.add_parser("_worker")
    p.add_argument("--run-dir", required=True)
    args = parser.parse_args(argv)
    if args.command is None:
        parser.print_help()
        return 0
    if getattr(args, "ready_timeout", 600) not in range(1, 901):
        raise OpsError("--ready-timeout must be 1..900 seconds. This is a readiness deadline, not a command to kill a migration.")
    if getattr(args, "interval", 60) < 30:
        raise OpsError("Use --interval 30 or more to avoid hammering Google Drive.")
    if args.command == "update":
        return update(args)
    if args.command == "_start":
        return start_job(args)
    if args.command == "_worker":
        return worker(args)
    if args.command == "backups":
        return backups(args)
    if args.command == "connection":
        return connection_status(args)
    if args.command == "status":
        return status()
    if args.command == "logs":
        run = latest_run()
        if args.update_only:
            if run is None:
                raise OpsError("No managed update has a saved result yet.")
            return follow_update(run, server_logs=False)
        if args.history and run:
            require_tools("less")
            return subprocess.call(["less", "-R", "+G", str(run / "update.log")])
        if unit_busy() and run:
            return follow_update(run)
        show_server_logs(history=args.history)
        return 0
    return 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        say("COMMAND CLOSED", "This terminal command stopped. Any already-launched managed job, AITA service and scheduled backups continue independently.")
        sys.exit(130)
    except (OpsError, OSError, subprocess.SubprocessError, tarfile.TarError, KeyError, ValueError) as error:
        say("STOP", str(error))
        sys.exit(1)
