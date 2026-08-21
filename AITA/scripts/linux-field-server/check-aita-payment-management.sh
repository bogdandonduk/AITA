#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="${1:-$(pwd)}"
ENV_FILE="${2:-/etc/aita/aita-prod.env}"
failures=0

fail() { printf 'FAIL: %s\n' "$1" >&2; failures=$((failures + 1)); }
pass() { printf 'PASS: %s\n' "$1"; }

[[ -f "$PROJECT_ROOT/settings.gradle.kts" ]] || fail "Project root is invalid"

if grep -RInE --exclude-dir=.git --exclude='*.zip' --exclude='*.log' \
  '(api\.aita\.kz|bootstrap\.aita\.kz)' "$PROJECT_ROOT" >/dev/null 2>&1; then
  fail "Retired API domains are present"
else
  pass "Retired API domains are absent"
fi

if grep -RInE --exclude-dir=.git --exclude='*.example' --exclude='*.template' \
  '(AITA_(KASPI|WEBKASSA)_[A-Z_]*(TOKEN|SECRET|PASSWORD|PRIVATE_KEY)=.+)' "$PROJECT_ROOT" >/dev/null 2>&1; then
  fail "A provider secret appears to be embedded in the repository"
else
  pass "No obvious provider secret is embedded"
fi

if [[ -f "$ENV_FILE" ]]; then
  mode="$(stat -c '%a' "$ENV_FILE")"
  [[ "$mode" == "640" || "$mode" == "600" ]] || fail "$ENV_FILE permissions are $mode; expected 640 or 600"
  grep -q '^AITA_INTEGRATION_MASTER_KEY_VERSION=' "$ENV_FILE" || fail "Integration master-key version is missing"
  grep -q '^AITA_INTEGRATION_MASTER_KEY_B64=' "$ENV_FILE" || fail "Integration master key is missing"
  pass "Production environment file inspected without printing secrets"
else
  printf 'WARN: %s is absent; skipping host-only checks\n' "$ENV_FILE"
fi

if (( failures > 0 )); then
  printf '%d payment-management safety check(s) failed.\n' "$failures" >&2
  exit 1
fi
printf 'All payment-management safety checks passed.\n'
