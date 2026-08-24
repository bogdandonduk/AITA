#!/usr/bin/env bash
set -euo pipefail

if ! command -v openssl >/dev/null 2>&1; then
  echo "openssl is required." >&2
  exit 1
fi

key="$(openssl rand -base64 32 | tr -d '\n')"
printf 'AITA_INTEGRATION_MASTER_KEY_VERSION=1\n'
printf 'AITA_INTEGRATION_MASTER_KEY_B64=%s\n' "$key"
printf 'AITA_INTEGRATION_PREVIOUS_MASTER_KEYS=\n'
