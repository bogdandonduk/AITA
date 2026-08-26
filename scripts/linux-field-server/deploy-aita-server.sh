#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

aita_require_command flock
aita_require_command sha256sum
aita_require_command unzip

deploy_state_dir="${XDG_STATE_HOME:-$HOME/.local/state}/aita"
mkdir -p "$deploy_state_dir"
exec 9>"$deploy_state_dir/deploy.lock"
flock -n 9 || aita_die "Another AITA deployment is already running for $(id -un)"

project_root="$(pwd)"
build=false
build_with_tests=false
backup=false
rollback=false

while (($#)); do
  case "$1" in
    --project-root) project_root="${2:?Missing value for --project-root}"; shift ;;
    --build) build=true ;;
    --build-with-tests) build=true; build_with_tests=true ;;
    --backup) backup=true ;;
    --rollback) rollback=true ;;
    -h|--help)
      echo "Usage: $0 [--project-root PATH] [--build|--build-with-tests] [--backup] [--rollback]"
      exit 0
      ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

project_root="$(cd -- "$project_root" && pwd)"
sudo_cmd=()
if ((EUID != 0)); then
  aita_require_command sudo
  sudo_cmd=(sudo)
fi

current_jar=/opt/aita/app/aita-server-all.jar
previous_jar=/opt/aita/app/aita-server-all.previous.jar

verify_server_jar() {
  local jar="${1:?Server JAR path is required}"
  [[ -s "$jar" ]] || aita_die "Server fat JAR is missing or empty: $jar"
  local size
  size="$(stat -c '%s' "$jar")"
  ((size >= 1024 * 1024)) || aita_die "Server JAR is suspiciously small ($size bytes): $jar"
  unzip -tqq "$jar" || aita_die "Server JAR is not a valid ZIP/JAR: $jar"
  unzip -Z1 "$jar" | grep -qx 'kz/aita/server/ServerKt.class' ||
    aita_die "Server JAR does not contain kz/aita/server/ServerKt.class: $jar"
  unzip -p "$jar" META-INF/MANIFEST.MF | tr -d '\r' |
    grep -Eq '^Main-Class:[[:space:]]*kz\.aita\.server\.ServerKt[[:space:]]*$' ||
    aita_die "Server JAR manifest does not point to kz.aita.server.ServerKt: $jar"
}

if $rollback; then
  [[ -f "$previous_jar" ]] || aita_die "No previous JAR is available at $previous_jar"
  aita_info "WARNING: a JAR rollback does not reverse Flyway migrations. Confirm schema compatibility before continuing."
  read -r -p "Type ROLLBACK to continue: " confirmation
  [[ "$confirmation" == "ROLLBACK" ]] || aita_die "Rollback cancelled"
  "${sudo_cmd[@]}" systemctl stop aita-server.service
  "${sudo_cmd[@]}" cp -a "$current_jar" "${current_jar}.failed.$(date -u +%Y%m%dT%H%M%SZ)" 2>/dev/null || true
  "${sudo_cmd[@]}" install -o aita -g aita -m 0640 "$previous_jar" "$current_jar"
  "${sudo_cmd[@]}" systemctl start aita-server.service
else
  if $build; then
    build_args=(--project-root "$project_root")
    if $build_with_tests; then
      build_args+=(--with-tests)
    fi
    bash "$SCRIPT_DIR/build-aita-server.sh" "${build_args[@]}"
  fi

  source_jar="$project_root/server/build/libs/aita-server-all.jar"
  [[ -r "$source_jar" ]] || aita_die "Fat JAR is missing: $source_jar (run with --build)"
  verify_server_jar "$source_jar"

  if $backup; then
    "${sudo_cmd[@]}" systemctl start --wait aita-backup.service
  fi

  checksum="$(sha256sum "$source_jar" | awk '{print $1}')"
  stage="/opt/aita/app/.aita-server-all.jar.$$.new"
  "${sudo_cmd[@]}" install -o aita -g aita -m 0640 "$source_jar" "$stage"
  staged_checksum="$("${sudo_cmd[@]}" sha256sum "$stage" | awk '{print $1}')"
  [[ "$checksum" == "$staged_checksum" ]] || aita_die "Staged JAR checksum mismatch"
  "${sudo_cmd[@]}" unzip -tqq "$stage" || aita_die "Staged server JAR integrity check failed"

  "${sudo_cmd[@]}" systemctl stop aita-server.service || true
  if "${sudo_cmd[@]}" test -f "$current_jar"; then
    "${sudo_cmd[@]}" cp -a "$current_jar" "$previous_jar"
  fi
  "${sudo_cmd[@]}" mv -f "$stage" "$current_jar"
  "${sudo_cmd[@]}" chown aita:aita "$current_jar"
  "${sudo_cmd[@]}" chmod 0640 "$current_jar"
  "${sudo_cmd[@]}" systemctl start aita-server.service
fi

for _ in $(seq 1 60); do
  if curl --silent --fail --max-time 2 http://127.0.0.1:8080/readyz >/dev/null; then
    deployed_checksum="$("${sudo_cmd[@]}" sha256sum "$current_jar" 2>/dev/null | awk '{print $1}' || true)"
    aita_info "AITA is ready. Deployed SHA-256: $deployed_checksum"
    exit 0
  fi
  sleep 2
done

"${sudo_cmd[@]}" systemctl --no-pager --full status aita-server.service || true
"${sudo_cmd[@]}" journalctl -u aita-server.service -n 120 --no-pager || true
aita_die "AITA did not become ready within 120 seconds; no automatic rollback was attempted"
