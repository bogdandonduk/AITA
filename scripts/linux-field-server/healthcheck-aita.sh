#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

env_file="${AITA_ENV_FILE:-/etc/aita/aita-prod.env}"
local_only=false
while (($#)); do
  case "$1" in
    --local-only) local_only=true ;;
    --env-file) env_file="${2:?Missing value for --env-file}"; shift ;;
    -h|--help) echo "Usage: $0 [--local-only] [--env-file PATH]"; exit 0 ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

aita_load_env "$env_file"
aita_require_command curl
port="${AITA_PORT:-8080}"
local_base="http://127.0.0.1:$port"
public_base="${AITA_PUBLIC_HEALTH_URL:-${AITA_PUBLIC_SERVER_URL:-}}"
failures=0

check_endpoint() {
  local label="$1" url="$2" expected="${3:-200}"
  local started elapsed status
  started="$(date +%s%3N)"
  status="$(aita_curl_status "$url" 12 || true)"
  elapsed=$(( $(date +%s%3N) - started ))
  if [[ "$status" == "$expected" ]]; then
    printf 'OK   %-24s HTTP %s in %sms\n' "$label" "$status" "$elapsed"
  else
    printf 'FAIL %-24s HTTP %s in %sms (%s)\n' "$label" "${status:-none}" "$elapsed" "$url" >&2
    failures=$((failures + 1))
  fi
}

check_endpoint "local health" "$local_base/healthz"
check_endpoint "local readiness" "$local_base/readyz"

if ! $local_only && [[ -n "$public_base" ]]; then
  public_base="${public_base%/}"
  check_endpoint "public health" "$public_base/healthz"
  check_endpoint "public readiness" "$public_base/readyz"
  check_endpoint "public bootstrap" "$public_base/.well-known/aita-server.json"
fi

((failures == 0)) || exit 1
