#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
ROOT="$(cd "$ROOT" && pwd)"
status=0

report() {
  printf 'AITA repository secret check: %s\n' "$*" >&2
  status=1
}

known_paths=(
  "aita-backup-private-key.txt"
  "aita-backup-age.key"
  "aita-prod.env"
  "rclone.conf"
  "cloudflare-tunnel-token.txt"
  ".dev.vars"
  "wrangler.local.jsonc"
  "cloudflare-api-token.txt"
)

for relative in "${known_paths[@]}"; do
  while IFS= read -r -d '' candidate; do
    report "sensitive file must not be stored in the repository: ${candidate#"$ROOT"/}"
  done < <(
    find "$ROOT" \
      \( -path "$ROOT/.git" -o -path "$ROOT/.gradle" -o -path '*/build' -o -path '*/node_modules' -o -path '*/.idea' \) -prune -o \
      -type f -name "$(basename "$relative")" -print0
  )
done

while IFS= read -r -d '' candidate; do
  report "machine-local Cloudflare environment file must not be stored in the repository: ${candidate#"$ROOT"/}"
done < <(
  find "$ROOT" \
    \( -path "$ROOT/.git" -o -path "$ROOT/.gradle" -o -path '*/build' -o -path '*/node_modules' -o -path '*/.idea' \) -prune -o \
    -type f \( -name '.dev.vars' -o -name '.dev.vars.*' -o -name 'wrangler.local.jsonc' -o -name '.env.local' \) -print0
)

# Build the marker in pieces so this checker does not match its own source.
age_marker='AGE-SECRET-''KEY-'
pem_marker='-----BEGIN .* PRIVATE ''KEY-----'

while IFS= read -r -d '' candidate; do
  if LC_ALL=C grep -Iq . "$candidate" 2>/dev/null &&
     LC_ALL=C grep -Eq "$age_marker|$pem_marker" "$candidate" 2>/dev/null; then
    report "private-key material detected in: ${candidate#"$ROOT"/}"
  fi
done < <(
  find "$ROOT" \
    \( -path "$ROOT/.git" -o -path "$ROOT/.gradle" -o -path '*/build' -o -path '*/node_modules' -o -path '*/.idea' \) -prune -o \
    -type f -size -5M -print0
)

if (( status != 0 )); then
  printf '%s\n' 'Remove the files from the working tree and Git history, rotate the exposed credentials, then run this check again.' >&2
  exit "$status"
fi

printf '%s\n' 'AITA repository secret check: PASS'
