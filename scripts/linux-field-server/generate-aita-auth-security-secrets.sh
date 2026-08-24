#!/usr/bin/env bash
set -euo pipefail

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    printf 'Required command is missing: %s\n' "$1" >&2
    exit 1
  }
}

require_command openssl

random_hex_64() {
  openssl rand -hex 64
}

random_b64_32() {
  openssl rand -base64 32 | tr -d '\n'
}

cat <<EOF
# Paste these values into /etc/aita/aita-prod.env.
# Keep them stable, independent, and outside Git.
AITA_AUTH_CODE_PEPPER=$(random_hex_64)
AITA_ACCOUNT_SECURITY_MASTER_KEY_B64=$(random_b64_32)
EOF
