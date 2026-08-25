#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

env_file="${AITA_ENV_FILE:-/etc/aita/aita-prod.env}"
service_preflight=false
check_endpoints=false
while (($#)); do
  case "$1" in
    --service-preflight) service_preflight=true ;;
    --check-endpoints) check_endpoints=true ;;
    --env-file) env_file="${2:?Missing value for --env-file}"; shift ;;
    -h|--help) echo "Usage: $0 [--service-preflight] [--check-endpoints] [--env-file PATH]"; exit 0 ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

failures=0
warns=0
pass() { printf 'PASS %s\n' "$*"; }
warn() { printf 'WARN %s\n' "$*" >&2; warns=$((warns + 1)); }
fail() { printf 'FAIL %s\n' "$*" >&2; failures=$((failures + 1)); }

if [[ -r "$env_file" ]]; then
  pass "environment file is readable: $env_file"
else
  fail "environment file is missing or unreadable: $env_file"
fi

if ((failures == 0)); then
  if (aita_load_env "$env_file" && aita_validate_production_env); then
    aita_load_env "$env_file"
    pass "production environment passes security validation"
  else
    fail "production environment validation failed"
  fi
fi

if java_bin="$(aita_require_java21 2>/dev/null)"; then
  pass "Java 21 is available: $java_bin"
else
  fail "Java 21 is unavailable"
fi

jar=/opt/aita/app/aita-server-all.jar
[[ -s "$jar" ]] && pass "deployed fat JAR exists" || fail "deployed fat JAR is missing: $jar"
[[ -d "${AITA_SERVER_FILES_ROOT:-/srv/aita/server-files}" ]] && pass "server-files directory exists" || fail "server-files directory is missing"
[[ -d "${AITA_LOG_DIR:-/var/log/aita}" ]] && pass "log directory exists" || fail "log directory is missing"

if command -v pg_isready >/dev/null && command -v psql >/dev/null && [[ -n "${AITA_DB_URL-}" ]]; then
  aita_parse_jdbc_url "$AITA_DB_URL"
  if pg_isready -q -h "$AITA_PARSED_DB_HOST" -p "$AITA_PARSED_DB_PORT" -d "$AITA_PARSED_DB_NAME"; then
    pass "PostgreSQL accepts connections"
  else
    fail "PostgreSQL is not ready"
  fi
  if [[ -n "${DB_USER-}" && -n "${DB_PASS-}" ]]; then
    if PGPASSWORD="$DB_PASS" psql -X -v ON_ERROR_STOP=1 -qAt \
      -h "$AITA_PARSED_DB_HOST" -p "$AITA_PARSED_DB_PORT" -U "$DB_USER" -d "$AITA_PARSED_DB_NAME" \
      -c 'select 1' 2>/dev/null | grep -qx 1; then
      pass "AITA database credentials work"
    else
      fail "AITA database credentials do not work"
    fi
  fi
else
  fail "PostgreSQL client commands are missing"
fi

if $service_preflight; then
  ((failures == 0)) || exit 1
  exit 0
fi

has_real_address_value() {
  local value="${1-}"
  [[ -n "$value" ]] && ! aita_is_placeholder "$value"
}

address_shared_key="${AITA_YANDEX_MAPS_API_KEY-}"
address_suggest_key="${AITA_YANDEX_GEOSUGGEST_API_KEY:-$address_shared_key}"
address_geocoder_key="${AITA_YANDEX_GEOCODER_API_KEY:-$address_shared_key}"
address_static_key="${AITA_YANDEX_STATIC_MAPS_API_KEY:-$address_shared_key}"
address_signing_secret="${AITA_ADDRESS_MAP_SIGNING_SECRET-}"

if has_real_address_value "$address_suggest_key"; then
  pass "Yandex address suggestions are configured"
else
  warn "Yandex Geosuggest key is missing; verified address selection will be unavailable"
fi
if has_real_address_value "$address_geocoder_key"; then
  pass "Yandex address resolution is configured"
else
  warn "Yandex Geocoder key is missing; stores cannot save newly verified addresses"
fi
if has_real_address_value "$address_static_key" &&
   has_real_address_value "$address_signing_secret" &&
   ((${#address_signing_secret} >= 32)); then
  pass "signed Yandex map previews are configured"
else
  warn "Static Maps key or a 32+ character address-map signing secret is missing; map previews will be unavailable"
fi

backup_remote="${AITA_BACKUP_RCLONE_REMOTE-}"
if [[ -z "$backup_remote" ]]; then
  warn "AITA_BACKUP_RCLONE_REMOTE is empty; encrypted backups currently remain local only"
elif ! command -v rclone >/dev/null 2>&1; then
  warn "AITA_BACKUP_RCLONE_REMOTE is set but rclone is not installed"
else
  backup_remote_name="${backup_remote%%:*}"
  if [[ -z "$backup_remote_name" || "$backup_remote" != *:* ]]; then
    warn "AITA_BACKUP_RCLONE_REMOTE is malformed: expected remote:path"
  elif ((EUID == 0)) && command -v runuser >/dev/null 2>&1; then
    if runuser -u aita -- env HOME=/var/lib/aita rclone listremotes 2>/dev/null |
       grep -qx "${backup_remote_name}:"; then
      pass "rclone backup remote is available to the aita service user: ${backup_remote_name}:"
    else
      warn "rclone remote ${backup_remote_name}: is not configured for the aita service user"
    fi
  else
    pass "rclone backup destination is declared: $backup_remote"
  fi
fi

if systemctl is-enabled --quiet aita-server.service 2>/dev/null; then pass "aita-server.service is enabled"; else warn "aita-server.service is not enabled"; fi
if systemctl is-active --quiet cloudflared.service 2>/dev/null; then pass "cloudflared.service is active"; else warn "cloudflared.service is not active yet"; fi

if [[ -r /proc/mdstat ]] && grep -q '^md' /proc/mdstat; then
  if grep -Eq '\[[0-9]+/[0-9]+\].*\[[U]+\]' /proc/mdstat; then pass "software RAID appears assembled"; else warn "software RAID exists; inspect /proc/mdstat manually"; fi
else
  warn "no Linux software RAID array detected"
fi

root_free_kb="$(df -Pk / | awk 'NR==2 {print $4}')"
((root_free_kb >= 10 * 1024 * 1024)) && pass "root filesystem has at least 10 GiB free" || warn "root filesystem has less than 10 GiB free"

if command -v ufw >/dev/null; then
  ufw status | head -1 | grep -q 'Status: active' && pass "UFW is active" || warn "UFW is not active"
fi

if $check_endpoints; then
  if "$SCRIPT_DIR/healthcheck-aita.sh" --env-file "$env_file"; then pass "local and public endpoints are healthy"; else fail "one or more endpoints failed"; fi
fi

printf '\nReadiness summary: %d failure(s), %d warning(s)\n' "$failures" "$warns"
((failures == 0))
