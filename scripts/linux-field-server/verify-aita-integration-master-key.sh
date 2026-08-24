#!/usr/bin/env bash
set -Eeuo pipefail
ENV_FILE="${1:-/etc/aita/aita-prod.env}"
[[ -r "$ENV_FILE" ]] || { echo "Cannot read $ENV_FILE" >&2; exit 2; }

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

version="${AITA_INTEGRATION_MASTER_KEY_VERSION:-}"
key="${AITA_INTEGRATION_MASTER_KEY_B64:-}"
[[ "$version" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid AITA_INTEGRATION_MASTER_KEY_VERSION' >&2; exit 1; }
[[ -n "$key" ]] || { echo 'AITA_INTEGRATION_MASTER_KEY_B64 is missing' >&2; exit 1; }

bytes="$(printf '%s' "$key" | base64 --decode 2>/dev/null | wc -c | tr -d ' ')"
[[ "$bytes" == "32" ]] || { echo "Integration master key decodes to $bytes bytes; expected 32" >&2; exit 1; }

if [[ -n "${AITA_INTEGRATION_PREVIOUS_MASTER_KEYS:-}" ]]; then
  IFS=',' read -ra previous <<<"$AITA_INTEGRATION_PREVIOUS_MASTER_KEYS"
  for entry in "${previous[@]}"; do
    [[ "$entry" =~ ^[1-9][0-9]*: ]] || { echo 'Invalid previous-key entry (expected version:base64)' >&2; exit 1; }
    previous_key="${entry#*:}"
    previous_bytes="$(printf '%s' "$previous_key" | base64 --decode 2>/dev/null | wc -c | tr -d ' ')"
    [[ "$previous_bytes" == "32" ]] || { echo 'A previous integration key is not 32 bytes' >&2; exit 1; }
  done
fi

echo "Integration master key version $version is structurally valid."
