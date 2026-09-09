#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

env_file="${AITA_ENV_FILE:-/etc/aita/aita-prod.env}"
daily=false
local_only=false
while (($#)); do
  case "$1" in
    --daily) daily=true ;;
    --local-only) local_only=true ;;
    --env-file) env_file="${2:?Missing value for --env-file}"; shift ;;
    -h|--help)
      echo "Usage: $0 [--daily] [--env-file PATH] [--local-only]"
      echo "--local-only requires encryption and skips cloud upload for this invocation only; scheduled defaults/configuration are unchanged."
      exit 0 ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

# The deployment flag cannot be overwritten by a production environment entry.
readonly local_only
aita_load_env "$env_file"
aita_validate_production_env
aita_require_command pg_dump
aita_require_command pg_restore
aita_require_command flock

auto_backup_dir="${AITA_BACKUP_DIR:-/srv/aita/backups}"
install -d -m 0750 "$auto_backup_dir" "$auto_backup_dir/daily" "$auto_backup_dir/.tmp"
lock_file="${AITA_BACKUP_LOCK_FILE:-/srv/aita/locks/postgres-backup.lock}"
install -d -m 0750 "$(dirname "$lock_file")"
exec 9>"$lock_file"
# Briefly wait for a daily/latest run or a read-only cloud verification instead
# of treating a harmless overlap as a silently skipped successful backup.
lock_wait="${AITA_BACKUP_LOCK_WAIT_SECONDS:-300}"
[[ "$lock_wait" =~ ^[0-9]+$ ]] && ((lock_wait <= 900)) || aita_die "AITA_BACKUP_LOCK_WAIT_SECONDS must be 0..900"
aita_info "Waiting for the backup lock (at most ${lock_wait}s). The server stays online."
flock -w "$lock_wait" 9 || aita_die "Another backup/check still owns the lock; no new backup was created by this attempt"

aita_parse_jdbc_url "$AITA_DB_URL"
backup_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
plain_tmp="$auto_backup_dir/.tmp/aita_${backup_stamp}_$$.dump"
encrypted_tmp="$auto_backup_dir/.tmp/aita_${backup_stamp}_$$.dump.age"
publish_tmp=""
cleanup() {
  rm -f -- "$plain_tmp" "$encrypted_tmp"
  [[ -z "$publish_tmp" ]] || rm -f -- "$publish_tmp"
  unset PGPASSWORD
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

export PGPASSWORD="$DB_PASS"
export PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-10}"
aita_info "Creating a consistent PostgreSQL snapshot. Normal app reads/writes may continue."
pg_dump \
  --host "$AITA_PARSED_DB_HOST" \
  --port "$AITA_PARSED_DB_PORT" \
  --username "$DB_USER" \
  --dbname "$AITA_PARSED_DB_NAME" \
  --lock-wait-timeout=30s \
  --format custom \
  --compress 6 \
  --no-owner \
  --no-privileges \
  --file "$plain_tmp"

pg_restore --list "$plain_tmp" >/dev/null
[[ -s "$plain_tmp" ]] || aita_die "PostgreSQL produced an empty backup"

recipient="${AITA_BACKUP_AGE_RECIPIENT-}"
allow_plaintext="${AITA_BACKUP_ALLOW_PLAINTEXT:-false}"
if [[ -n "$recipient" ]] && ! aita_is_placeholder "$recipient"; then
  aita_require_command age
  age --recipient "$recipient" --output "$encrypted_tmp" "$plain_tmp"
  [[ -s "$encrypted_tmp" ]] || aita_die "age produced an empty encrypted backup"
  final_extension="dump.age"
  source_for_publish="$encrypted_tmp"
elif ! $local_only && [[ "$allow_plaintext" == "true" ]]; then
  final_extension="dump"
  source_for_publish="$plain_tmp"
else
  aita_die "AITA_BACKUP_AGE_RECIPIENT must be configured; plaintext backups are disabled"
fi

if $daily; then
  final_path="$auto_backup_dir/daily/aita_${backup_stamp}.${final_extension}"
else
  final_path="$auto_backup_dir/aita_latest.${final_extension}"
fi
publish_tmp="${final_path}.new.$$"
cp --reflink=auto --sparse=always "$source_for_publish" "$publish_tmp"
chmod 0600 "$publish_tmp"
mv -f "$publish_tmp" "$final_path"
publish_tmp=""
aita_info "Local backup saved: $final_path (this alone does not confirm cloud upload)"

remote="${AITA_BACKUP_RCLONE_REMOTE-}"
if $local_only; then
  aita_info "LOCAL ONLY: cloud upload skipped for this invocation. Remote settings and scheduled backups are unchanged; offsite protection is NOT verified."
elif [[ -n "$remote" ]]; then
  aita_require_command rclone
  if [[ -n "${AITA_BACKUP_RCLONE_CONFIG-}" ]]; then
    export RCLONE_CONFIG="$AITA_BACKUP_RCLONE_CONFIG"
  fi
  aita_info "Uploading the backup as $(id -un) using that account's rclone configuration."
  if ! rclone copyto "$final_path" "${remote%/}/$(basename "$final_path")" \
    --checksum --retries 3 --low-level-retries 5 --contimeout 10s --timeout 60s --max-duration 10m; then
    aita_die "Cloud upload failed. The encrypted LOCAL backup is preserved; offsite protection is NOT confirmed."
  fi
  aita_info "Cloud upload command succeeded (use aita-ops.py backups --download to verify remote bytes)."
else
  aita_info "WARNING: AITA_BACKUP_RCLONE_REMOTE is empty. This backup is LOCAL ONLY, not uploaded to Google Drive."
fi

retention_days="${AITA_BACKUP_RETENTION_DAYS:-30}"
if [[ "$retention_days" =~ ^[1-9][0-9]*$ ]]; then
  find "$auto_backup_dir/daily" -maxdepth 1 -type f -name 'aita_*.dump*' -mtime "+$retention_days" -delete
fi

aita_info "Backup completed: $final_path"
