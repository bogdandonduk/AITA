#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

AITA_SERVER_DEPLOY_SCRIPT_VERSION="2026-09-08-managed-update-v5"
project_root="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
build=false
build_with_tests=false
backup=false
rollback=false
verify_only=false
ready_timeout=120

while (($#)); do
  case "$1" in
    --project-root) project_root="${2:?Missing value for --project-root}"; shift ;;
    --build) build=true ;;
    --build-with-tests) build=true; build_with_tests=true ;;
    --backup) backup=true ;;
    --rollback) rollback=true ;;
    --verify-only) verify_only=true ;;
    --ready-timeout) ready_timeout="${2:?Missing value for --ready-timeout}"; shift ;;
    -h|--help)
      cat <<'HELP'
Usage: deploy-aita-server.sh [--project-root PATH] [--build|--build-with-tests]
                            [--backup] [--rollback] [--verify-only]
                            [--ready-timeout SECONDS]

--verify-only validates the built JAR against the checkout without touching
systemd, the installed artifact, or the database. --build can be used with it.
Normal deployment validates and stages the artifact before stopping AITA.
Success requires the new service process to own port 8080, answer /readyz, and
(if present in the JAR) answer /auth/capabilities. No email is sent by the check.
The readiness window defaults to 120 seconds; use up to 900 for a planned
migration. JAR rollback never reverses a database migration.
HELP
      exit 0
      ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

[[ "$ready_timeout" =~ ^[1-9][0-9]{0,2}$ ]] && ((ready_timeout <= 900)) ||
  aita_die "--ready-timeout must be between 1 and 900 seconds"
if $rollback && { $build || $verify_only; }; then
  aita_die "--rollback cannot be combined with --build or --verify-only"
fi

aita_require_command curl
aita_require_command flock
aita_require_command sha256sum
aita_require_command unzip
project_root="$(cd -- "$project_root" && pwd)"

deploy_state_dir="${XDG_STATE_HOME:-$HOME/.local/state}/aita"
mkdir -p "$deploy_state_dir"
if [[ -n "${AITA_DEPLOY_LOCK_FD-}" ]]; then
  # The managed operator already owns this exact lock throughout tests/build.
  # Validate the inherited descriptor rather than trusting an environment flag.
  [[ "$AITA_DEPLOY_LOCK_FD" =~ ^[0-9]+$ ]] || aita_die "Invalid inherited deployment lock descriptor"
  inherited_inode="$(stat -Lc '%d:%i' "/proc/$$/fd/$AITA_DEPLOY_LOCK_FD" 2>/dev/null || true)"
  expected_inode="$(stat -Lc '%d:%i' "$deploy_state_dir/deploy.lock" 2>/dev/null || true)"
  [[ -n "$expected_inode" && "$inherited_inode" == "$expected_inode" ]] ||
    aita_die "Inherited deployment lock does not match this account's deployment lock"
  flock -n "$AITA_DEPLOY_LOCK_FD" || aita_die "Inherited deployment lock could not be acquired"
else
  exec 9>"$deploy_state_dir/deploy.lock"
  flock -n 9 || aita_die "Another AITA deployment is already running for $(id -un)"
fi

current_jar=/opt/aita/app/aita-server-all.jar
previous_jar=/opt/aita/app/aita-server-all.previous.jar
stage=""
deployment_phase=validation
sudo_cmd=()

cleanup_staged_jar() {
  local status=$?
  if [[ -n "$stage" ]]; then
    "${sudo_cmd[@]}" rm -f -- "$stage" >/dev/null 2>&1 || true
  fi
  if ((status != 0)); then
    aita_info "DEPLOYMENT FAILED during $deployment_phase; no successful release was reported."
    case "$deployment_phase" in
      validation|backup|staging)
        aita_info "This deploy attempt did not replace the installed JAR or stop the server."
        ;;
      *) aita_info "Check systemd/journal output before retrying; no automatic database or JAR rollback was attempted." ;;
    esac
  fi
}
trap cleanup_staged_jar EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

show_service_failure() {
  "${sudo_cmd[@]}" systemctl --no-pager --full status aita-server.service || true
  local failure_invocation
  failure_invocation="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=InvocationID --value 2>/dev/null || true)"
  if [[ "$failure_invocation" =~ ^[0-9a-fA-F]{32}$ ]]; then
    "${sudo_cmd[@]}" journalctl -u aita-server.service "_SYSTEMD_INVOCATION_ID=$failure_invocation" -n 120 --no-pager || true
  else
    "${sudo_cmd[@]}" journalctl -u aita-server.service -n 120 --no-pager || true
  fi
  if "${sudo_cmd[@]}" test -f "$previous_jar"; then
    aita_info "Previous JAR is preserved at $previous_jar"
    aita_info "Manual rollback command: bash $SCRIPT_DIR/deploy-aita-server.sh --rollback"
    aita_info "A JAR rollback does not reverse Flyway migrations; check schema compatibility first."
  fi
}

aita_info "Deploy script profile: $AITA_SERVER_DEPLOY_SCRIPT_VERSION"
aita_info "Source checkout: $(aita_describe_checkout "$project_root")"

if ! $rollback; then
  if $build; then
    build_args=(--project-root "$project_root")
    if $build_with_tests; then
      build_args+=(--with-tests)
    fi
    bash "$SCRIPT_DIR/build-aita-server.sh" "${build_args[@]}"
  fi
  source_jar="$project_root/server/build/libs/aita-server-all.jar"
  aita_verify_server_jar "$source_jar" "$project_root"
  if $verify_only; then
    aita_info "VERIFY ONLY PASSED. No installed files, services, or database records were changed."
    exit 0
  fi
fi

aita_require_command systemctl
aita_require_command ss
if ((EUID != 0)); then
  aita_require_command sudo
  sudo_cmd=(sudo)
  sudo -v
fi

if $rollback; then
  source_jar="$previous_jar"
  "${sudo_cmd[@]}" bash -c 'source "$1"; aita_verify_server_jar "$2"' \
    _ "$SCRIPT_DIR/aita-linux-common.sh" "$source_jar"
  aita_info "WARNING: a JAR rollback does not reverse Flyway migrations. Confirm schema compatibility before continuing."
  read -r -p "Type ROLLBACK to continue: " confirmation
  [[ "$confirmation" == "ROLLBACK" ]] || aita_die "Rollback cancelled"
fi

# Support both the historical package and the current auth package, so a
# deliberately selected legacy rollback is not required to expose a new route.
source_entries="$("${sudo_cmd[@]}" unzip -Z1 "$source_jar")"
expect_auth=false
if grep -Fxq 'kz/aita/server/auth/AitaAdvancedAuthenticationKt.class' <<< "$source_entries" ||
   grep -Fxq 'kz/aita/server/AitaAdvancedAuthenticationKt.class' <<< "$source_entries"; then
  expect_auth=true
fi
checksum="$("${sudo_cmd[@]}" sha256sum "$source_jar" | awk '{print $1}')"
aita_info "Candidate JAR SHA-256: $checksum"

if $backup; then
  deployment_phase=backup
  "${sudo_cmd[@]}" systemctl start --wait aita-backup.service || aita_die "Backup failed; deployment stopped before changing the server"
fi

deployment_phase=staging
stage="/opt/aita/app/.aita-server-all.jar.$$.new"
"${sudo_cmd[@]}" install -o aita -g aita -m 0640 "$source_jar" "$stage"
staged_checksum="$("${sudo_cmd[@]}" sha256sum "$stage" | awk '{print $1}')"
[[ "$checksum" == "$staged_checksum" ]] || aita_die "Staged JAR checksum mismatch"
"${sudo_cmd[@]}" unzip -tqq "$stage" || aita_die "Staged server JAR integrity check failed"

# Preserve the old artifact before any downtime; do not ignore a failed stop and
# then mistake the still-running old server's /readyz for a successful release.
if "${sudo_cmd[@]}" test -f "$current_jar"; then
  if $rollback; then
    "${sudo_cmd[@]}" cp -a "$current_jar" "${current_jar}.failed.$(date -u +%Y%m%dT%H%M%SZ)"
  else
    current_checksum="$("${sudo_cmd[@]}" sha256sum "$current_jar" | awk '{print $1}')"
    if [[ "$current_checksum" != "$checksum" ]]; then
      "${sudo_cmd[@]}" cp -a "$current_jar" "$previous_jar"
    else
      aita_info "Candidate matches the installed JAR; preserving the existing previous-release JAR."
    fi
  fi
fi
before_invocation="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=InvocationID --value)"
before_pid="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=MainPID --value)"
aita_info "Previous service PID: ${before_pid:-unknown}"
deployment_phase=stopping
if ! "${sudo_cmd[@]}" systemctl stop aita-server.service; then
  show_service_failure
  aita_die "Could not stop the old service; the candidate JAR was NOT installed"
fi
stopped_pid="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=MainPID --value)"
[[ "$stopped_pid" == 0 ]] || aita_die "Old service still has MainPID=$stopped_pid; refusing to replace its JAR"

deployment_phase=installing
"${sudo_cmd[@]}" mv -f "$stage" "$current_jar"
stage=""
"${sudo_cmd[@]}" chown aita:aita "$current_jar"
"${sudo_cmd[@]}" chmod 0640 "$current_jar"
deployment_phase=starting
if ! "${sudo_cmd[@]}" systemctl start aita-server.service; then
  show_service_failure
  aita_die "AITA systemd service failed to start"
fi

deployment_phase=readiness
deadline=$((SECONDS + ready_timeout))
next_progress=$((SECONDS + 15))
last_ready_status=not_checked
last_auth_status=not_required
$expect_auth && last_auth_status=not_checked
while ((SECONDS < deadline)); do
  main_pid="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=MainPID --value)"
  invocation="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=InvocationID --value)"
  if [[ "$main_pid" =~ ^[1-9][0-9]*$ && -n "$invocation" && "$invocation" != "$before_invocation" ]] &&
     "${sudo_cmd[@]}" systemctl is-active --quiet aita-server.service; then
    listeners="$("${sudo_cmd[@]}" ss -H -ltnp '( sport = :8080 )')"
    if [[ "$listeners" == *"pid=$main_pid,"* ]]; then
      last_ready_status="$(curl --noproxy '*' --silent --output /dev/null --write-out '%{http_code}' \
        --connect-timeout 2 --max-time 2 http://127.0.0.1:8080/readyz || true)"
      if $expect_auth; then
        last_auth_status="$(curl --noproxy '*' --silent --output /dev/null --write-out '%{http_code}' \
          --connect-timeout 2 --max-time 2 http://127.0.0.1:8080/auth/capabilities || true)"
      fi
      if [[ "$last_ready_status" == 200 && ( "$last_auth_status" == 200 || "$last_auth_status" == not_required ) ]]; then
        # Do not report success across a concurrent restart or artifact change.
        after_invocation="$("${sudo_cmd[@]}" systemctl show aita-server.service --property=InvocationID --value)"
        if [[ "$invocation" == "$after_invocation" ]] &&
           "${sudo_cmd[@]}" systemctl is-active --quiet aita-server.service; then
          deployed_checksum="$("${sudo_cmd[@]}" sha256sum "$current_jar" | awk '{print $1}')"
          [[ "$deployed_checksum" == "$checksum" ]] || aita_die "Installed JAR changed during readiness verification"
          deployment_phase=complete
          aita_info "DEPLOYMENT COMPLETE. New service PID: $main_pid. /readyz: HTTP 200."
          if $expect_auth; then
            aita_info "/auth/capabilities: HTTP 200 (route verified; provider configuration and real email delivery still need their own test)."
          fi
          aita_info "Deployed SHA-256: $deployed_checksum"
          exit 0
        fi
      fi
    fi
  fi
  if ((SECONDS >= next_progress)); then
    aita_info "Waiting for startup: PID=${main_pid:-unknown}, /readyz=$last_ready_status, /auth/capabilities=$last_auth_status. Flyway may still be running; do not redeploy."
    next_progress=$((SECONDS + 15))
  fi
  sleep 1
done

aita_info "Readiness results: /readyz=$last_ready_status; /auth/capabilities=$last_auth_status"
show_service_failure
aita_die "AITA did not pass new-process readiness within the ${ready_timeout}s window; no automatic rollback was attempted because Flyway schema changes may make a blind JAR rollback unsafe"
