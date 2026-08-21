#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

project_root="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
run_tests=true
max_workers="${AITA_GRADLE_MAX_WORKERS:-4}"

while (($#)); do
  case "$1" in
    --project-root)
      project_root="${2:?Missing value for --project-root}"
      shift
      ;;
    --skip-tests)
      run_tests=false
      ;;
    --max-workers)
      max_workers="${2:?Missing value for --max-workers}"
      shift
      ;;
    -h|--help)
      cat <<'HELP'
Usage: build-aita-server.sh [--project-root PATH] [--skip-tests] [--max-workers N]

Builds only the JVM shared module and Ktor server. Android, iOS, desktop, and
Wasm targets are not configured, so an Ubuntu server does not need their SDKs.
The complete console output is saved under ~/.local/state/aita/build-logs/.

If Kotlin's incremental compiler cache is corrupt, the script performs one
safe retry after deleting only generated JVM compiler caches and switching to
in-process, non-incremental compilation.
HELP
      exit 0
      ;;
    *)
      aita_die "Unknown argument: $1"
      ;;
  esac
  shift
done

((EUID != 0)) || aita_die "Run this build as the normal project owner, not as root or through sudo"
[[ "$max_workers" =~ ^[1-9][0-9]*$ ]] || aita_die "--max-workers must be a positive integer"
project_root="$(cd -- "$project_root" && pwd)"
[[ -f "$project_root/gradlew" ]] || aita_die "Gradle wrapper is missing under $project_root"
[[ -f "$project_root/settings.gradle.kts" ]] || aita_die "AITA settings.gradle.kts is missing under $project_root"
[[ -f "$project_root/settings-server.gradle.kts" ]] || aita_die "AITA settings-server.gradle.kts is missing under $project_root"
[[ -w "$project_root" ]] || aita_die "Project root is not writable by $(id -un): $project_root"

aita_require_command sha256sum
aita_require_command tee
java_bin="$(aita_require_java21)"
if [[ -z "${JAVA_HOME-}" ]]; then
  resolved_java="$(readlink -f "$java_bin" 2>/dev/null || printf '%s' "$java_bin")"
  export JAVA_HOME="$(cd -- "$(dirname -- "$resolved_java")/.." && pwd)"
fi
export PATH="$JAVA_HOME/bin:$PATH"

log_dir="${XDG_STATE_HOME:-$HOME/.local/state}/aita/build-logs"
mkdir -p "$log_dir"
log_stamp="$(date -u +%Y%m%dT%H%M%SZ)"
log_file="$log_dir/server-build-$log_stamp.log"
jar_path="$project_root/server/build/libs/aita-server-all.jar"

gradle_tasks=()
if $run_tests; then
  gradle_tasks+=(":shared:jvmTest" ":server:test")
fi
gradle_tasks+=(":server:buildFatJar")

gradle_common=(
  bash ./gradlew
  --settings-file settings-server.gradle.kts
  -Paita.serverOnly=true
  -Porg.gradle.java.installations.auto-download=false
  "-Porg.gradle.java.installations.paths=$JAVA_HOME"
  "${gradle_tasks[@]}"
  --no-configuration-cache
  --no-daemon
  --no-watch-fs
  "--max-workers=$max_workers"
  --console=plain
  --stacktrace
)

diagnose_build_log() {
  local file="${1:?Build log is required}"
  if grep -Eqi 'SDK location not found|ANDROID_HOME|Android SDK' "$file"; then
    aita_info "Diagnosis: Gradle tried to configure Android. Confirm this bundle's settings.gradle.kts and shared/build.gradle.kts are both installed, and build only through this script."
  fi
  if grep -Eqi 'UnknownHostException|Could not resolve|Name or service not known|Temporary failure in name resolution' "$file"; then
    aita_info "Diagnosis: Ubuntu could not resolve or reach a Gradle/Maven host. Check DNS, date/time, proxy/firewall, and outbound HTTPS."
  fi
  if grep -Eqi 'Unsupported class file major version|Java 21 is required|invalid source release|toolchain' "$file"; then
    aita_info "Diagnosis: Java/toolchain mismatch. This script pins Gradle and JVM compilation to JAVA_HOME=$JAVA_HOME."
  fi
  if grep -Eqi 'Permission denied|AccessDeniedException|Operation not permitted' "$file"; then
    aita_info "Diagnosis: repository or Gradle-cache ownership is wrong. Build as the normal project owner; do not run Gradle with sudo."
  fi
  if grep -Eqi 'No space left on device|Disk quota exceeded' "$file"; then
    aita_info "Diagnosis: the build volume has insufficient free space."
  fi
}

run_logged_gradle() {
  local attempt_label="${1:?Attempt label is required}"
  shift
  local -a extra_args=("$@")

  {
    printf '\n===== AITA server build: %s =====\n' "$attempt_label"
    printf 'UTC: %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'User: %s\n' "$(id -un)"
    printf 'Project: %s\n' "$project_root"
    printf 'JAVA_HOME: %s\n' "$JAVA_HOME"
    "$JAVA_HOME/bin/java" -version
    printf 'Command:'
    printf ' %q' "${gradle_common[@]}" "${extra_args[@]}"
    printf '\n\n'
  } | tee -a "$log_file"

  set +e
  (
    cd "$project_root"
    "${gradle_common[@]}" "${extra_args[@]}"
  ) 2>&1 | tee -a "$log_file"
  local gradle_status=${PIPESTATUS[0]}
  set -e
  return "$gradle_status"
}

aita_info "Project root: $project_root"
aita_info "Java: $($JAVA_HOME/bin/java -version 2>&1 | head -n 1)"
aita_info "Building server-only graph with max-workers=$max_workers"
aita_info "Build log: $log_file"

if ! run_logged_gradle "normal"; then
  if grep -Eqi \
    'Could not close incremental caches|Storage for .+ is already registered|Daemon compilation failed|Could not connect to Kotlin compile daemon|Kotlin compile daemon.*failed' \
    "$log_file"; then
    aita_info "Detected a transient Kotlin daemon/incremental-cache failure; retrying once with generated compiler caches reset"
    rm -rf \
      "$project_root/shared/build/kotlin" \
      "$project_root/shared/build/classes/kotlin" \
      "$project_root/server/build/kotlin" \
      "$project_root/server/build/classes/kotlin"

    if ! run_logged_gradle \
      "cache-recovery" \
      -Pkotlin.incremental=false \
      -Pkotlin.compiler.execution.strategy=in-process; then
      diagnose_build_log "$log_file"
      aita_die "Server build failed after cache recovery. Full log: $log_file"
    fi
  else
    diagnose_build_log "$log_file"
    aita_die "Server build failed. Full log: $log_file"
  fi
fi

[[ -s "$jar_path" ]] || aita_die "Server fat JAR was not produced at $jar_path; full log: $log_file"
if command -v unzip >/dev/null 2>&1; then
  unzip -tqq "$jar_path" || aita_die "Server fat JAR integrity check failed; full log: $log_file"
fi

aita_info "Server fat JAR: $jar_path"
aita_info "Size: $(du -h "$jar_path" | awk '{print $1}')"
aita_info "SHA-256: $(sha256sum "$jar_path" | awk '{print $1}')"
aita_info "Build log retained at: $log_file"
