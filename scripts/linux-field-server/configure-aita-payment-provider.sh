#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/etc/aita/aita-prod.env"
JAR="/opt/aita/app/aita-server-all.jar"

usage() {
  cat <<'EOF'
Usage:
  configure-aita-payment-provider.sh [--env-file PATH] [--jar PATH] -- <import options>

Example for Webkassa (the token is prompted and never placed in shell history):
  sudo bash scripts/linux-field-server/configure-aita-payment-provider.sh -- \
    --store-id 00000000-0000-0000-0000-000000000000 \
    --provider webkassa \
    --environment production \
    --display-name "Main cashbox" \
    --setting cashboxNumber=123456 \
    --secret-key apiToken

Kaspi credential fields must remain disabled until the official merchant contract is available.
EOF
}

args=()
while (($#)); do
  case "$1" in
    --env-file) ENV_FILE="${2:?--env-file requires a path}"; shift 2 ;;
    --jar) JAR="${2:?--jar requires a path}"; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    --) shift; args=("$@"); break ;;
    *) printf 'Unknown wrapper option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ ${#args[@]} -gt 0 ]] || { usage >&2; exit 2; }
[[ -r "$ENV_FILE" ]] || { printf 'Cannot read %s\n' "$ENV_FILE" >&2; exit 1; }
[[ -s "$JAR" ]] || { printf 'Server JAR is missing: %s\n' "$JAR" >&2; exit 1; }

# Refuse any argument that looks like an inline secret assignment.
for arg in "${args[@]}"; do
  if [[ "$arg" =~ ^--(token|password|private-key|secret)$ ]] || \
     { [[ "$arg" == *=* ]] && [[ "$arg" =~ (token|secret|password|private[_-]?key) ]]; }; then
    printf 'Refusing a possible secret in command-line arguments. Use --secret-key and enter it at the prompt.\n' >&2
    exit 2
  fi
done

actor="os-admin:${SUDO_USER:-${USER:-unknown}}"
exec sudo -u aita env \
  AITA_PAYMENT_IMPORT_ACTOR="$actor" \
  AITA_PAYMENT_ENV_FILE="$ENV_FILE" \
  AITA_PAYMENT_SERVER_JAR="$JAR" \
  bash -c '
    set -euo pipefail
    set -a
    # shellcheck disable=SC1090
    source "$AITA_PAYMENT_ENV_FILE"
    set +a
    exec java -cp "$AITA_PAYMENT_SERVER_JAR" \
      kz.aita.server.payments.PaymentCredentialImportCli "$@"
  ' _ "${args[@]}"
