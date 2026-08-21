#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
failures=0

fail() { printf 'FAIL: %s\n' "$*" >&2; failures=$((failures + 1)); }
pass() { printf 'PASS: %s\n' "$*"; }

if [[ ! -d "$PROJECT_ROOT/server" ]]; then
  fail "Project root does not contain server/: $PROJECT_ROOT"
  exit 2
fi

tracked_text() {
  find "$PROJECT_ROOT" \
    -type f \
    \( -name '*.kt' -o -name '*.kts' -o -name '*.json' -o -name '*.jsonc' -o -name '*.yaml' -o -name '*.yml' -o -name '*.toml' -o -name '*.sql' -o -name '*.sh' \) \
    ! -path '*/build/*' ! -path '*/.gradle/*' ! -path '*/.git/*' ! -path '*/node_modules/*' -print0
}

if tracked_text | xargs -0 grep -nE 'api\.aita\.kz|bootstrap\.aita\.kz' >/tmp/aita-payment-retired-domain.$$ 2>/dev/null; then
  cat /tmp/aita-payment-retired-domain.$$ >&2
  fail "Retired public endpoint remains in source"
else
  pass "Only the canonical Worker endpoint is used"
fi
rm -f /tmp/aita-payment-retired-domain.$$

# Search assignments and JSON/YAML values, avoiding harmless model field declarations.
if tracked_text | xargs -0 grep -nEi '(kaspi|webkassa).*(token|secret|password|api[-_]?key)[[:space:]]*[:=][[:space:]]*["'"'][^"'"']{12,}["'"']' >/tmp/aita-payment-secrets.$$ 2>/dev/null; then
  cat /tmp/aita-payment-secrets.$$ >&2
  fail "Possible provider credential embedded in source"
else
  pass "No obvious Kaspi/Webkassa credential literals"
fi
rm -f /tmp/aita-payment-secrets.$$

if tracked_text | xargs -0 grep -nEi '(authorization|token|secret|password|api[-_]?key).*(println|printStackTrace|logger\.|log\.)' >/tmp/aita-payment-secret-logs.$$ 2>/dev/null; then
  cat /tmp/aita-payment-secret-logs.$$ >&2
  fail "Potential secret logging pattern requires review"
else
  pass "No obvious payment-secret logging pattern"
fi
rm -f /tmp/aita-payment-secret-logs.$$

if grep -RInE 'amount(Minor)?[[:space:]]*:[[:space:]]*(Double|Float)' \
  "$PROJECT_ROOT/shared/src" "$PROJECT_ROOT/server/src" \
  --include='*.kt' --exclude-dir=build >/tmp/aita-payment-float-money.$$ 2>/dev/null; then
  cat /tmp/aita-payment-float-money.$$ >&2
  fail "Floating-point payment amount found"
else
  pass "Payment amounts use integer minor units"
fi
rm -f /tmp/aita-payment-float-money.$$

if [[ -f /etc/aita/aita-prod.env ]]; then
  mode="$(stat -c '%a' /etc/aita/aita-prod.env)"
  [[ "$mode" == "640" || "$mode" == "600" ]] || fail "/etc/aita/aita-prod.env mode is $mode; expected 640 or 600"
  if grep -q '^AITA_INTEGRATION_MASTER_KEY_B64=.' /etc/aita/aita-prod.env; then
    pass "Integration master key is configured"
  else
    fail "AITA_INTEGRATION_MASTER_KEY_B64 is missing"
  fi
else
  printf 'WARN: /etc/aita/aita-prod.env is absent; repository-only checks completed.\n' >&2
fi

if (( failures > 0 )); then
  printf '%d payment safety check(s) failed.\n' "$failures" >&2
  exit 1
fi
printf 'All AITA payment safety checks passed.\n'
