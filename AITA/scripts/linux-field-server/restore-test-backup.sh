#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

((EUID == 0)) || aita_die "Run restore testing with sudo"
env_file="${AITA_ENV_FILE:-/etc/aita/aita-prod.env}"
identity=""
backup=""
test_db="aita_restore_test"
while (($#)); do
  case "$1" in
    --identity) identity="${2:?Missing value for --identity}"; shift ;;
    --backup) backup="${2:?Missing value for --backup}"; shift ;;
    --test-db) test_db="${2:?Missing value for --test-db}"; shift ;;
    --env-file) env_file="${2:?Missing value for --env-file}"; shift ;;
    -h|--help) echo "Usage: sudo $0 --identity AGE_PRIVATE_KEY [--backup FILE] [--test-db NAME]"; exit 0 ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

[[ "$test_db" != "aita_prod" ]] || aita_die "Refusing to restore into aita_prod"
[[ -r "$identity" ]] || aita_die "Age private identity is missing: $identity"
aita_load_env "$env_file"
aita_validate_production_env
aita_require_command age
aita_require_command pg_restore
aita_require_command psql
aita_require_command runuser

backup_dir="${AITA_BACKUP_DIR:-/srv/aita/backups}"
backup="${backup:-$backup_dir/aita_latest.dump.age}"
[[ -r "$backup" ]] || aita_die "Backup is missing: $backup"

tmp="$(mktemp /var/tmp/aita-restore-test.XXXXXX.dump)"
trap 'rm -f -- "$tmp"; unset PGPASSWORD' EXIT INT TERM HUP
age --decrypt --identity "$identity" --output "$tmp" "$backup"
pg_restore --list "$tmp" >/dev/null

aita_parse_jdbc_url "$AITA_DB_URL"
runuser -u postgres -- dropdb --if-exists "$test_db"
runuser -u postgres -- createdb --owner "$DB_USER" "$test_db"

export PGPASSWORD="$DB_PASS"
pg_restore \
  --host "$AITA_PARSED_DB_HOST" \
  --port "$AITA_PARSED_DB_PORT" \
  --username "$DB_USER" \
  --dbname "$test_db" \
  --no-owner --no-privileges --exit-on-error "$tmp"

table_count="$(psql -X -qAt -h "$AITA_PARSED_DB_HOST" -p "$AITA_PARSED_DB_PORT" -U "$DB_USER" -d "$test_db" -c "select count(*) from pg_catalog.pg_tables where schemaname = 'public';")"
[[ "$table_count" =~ ^[1-9][0-9]*$ ]] || aita_die "Restore completed but no public tables were found"
aita_info "Restore test succeeded in database $test_db with $table_count public table(s)"
