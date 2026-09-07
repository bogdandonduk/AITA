#!/usr/bin/env bash

set -Eeuo pipefail

aita_die() {
  printf 'AITA: %s\n' "$*" >&2
  exit 1
}

aita_info() {
  printf 'AITA: %s\n' "$*"
}

aita_require_command() {
  command -v "$1" >/dev/null 2>&1 || aita_die "Required command is missing: $1"
}

aita_load_env() {
  local env_file="${1:?Environment file path is required}"
  [[ -r "$env_file" ]] || aita_die "Environment file is not readable: $env_file"

  local raw line key value line_number=0
  local -A seen=()
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    line_number=$((line_number + 1))
    line="${raw%$'\r'}"
    line="${line#${line%%[![:space:]]*}}"
    line="${line%${line##*[![:space:]]}}"
    [[ -z "$line" || "${line:0:1}" == "#" ]] && continue

    if [[ "$line" == export[[:space:]]* ]]; then
      line="${line#export}"
      line="${line#${line%%[![:space:]]*}}"
    fi

    [[ "$line" == *=* ]] || aita_die "Malformed environment line $line_number in $env_file (expected NAME=value; contents hidden)"
    key="${line%%=*}"
    value="${line#*=}"
    key="${key%${key##*[![:space:]]}}"

    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || aita_die "Invalid environment variable name on line $line_number in $env_file (contents hidden)"
    [[ -z "${seen[$key]+x}" ]] || aita_die "Duplicate environment variable in $env_file: $key"
    seen[$key]=1

    if [[ ${#value} -ge 2 ]]; then
      if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
        value="${value:1:${#value}-2}"
      elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
        value="${value:1:${#value}-2}"
      fi
    fi

    export "$key=$value"
  done < "$env_file"
}

aita_required_value() {
  local name="${1:?Variable name is required}"
  local value="${!name-}"
  [[ -n "$value" ]] || aita_die "$name must be configured"
  printf '%s' "$value"
}

aita_is_placeholder() {
  local value="${1,,}"
  [[ -z "$value" || "$value" == *change_me* || "$value" == *changeme* || "$value" == *replace_with* || "$value" == *placeholder* || "$value" == *your_secret* ]]
}

aita_validate_production_env() {
  local environment="${AITA_ENV-}"
  [[ "$environment" == "production" || "$environment" == "prod" ]] || aita_die "AITA_ENV must be production"

  local jwt_secret refresh_pepper
  jwt_secret="$(aita_required_value AITA_JWT_SECRET)"
  refresh_pepper="$(aita_required_value AITA_REFRESH_PEPPER)"
  ((${#jwt_secret} >= 64)) || aita_die "AITA_JWT_SECRET must contain at least 64 characters"
  ((${#refresh_pepper} >= 64)) || aita_die "AITA_REFRESH_PEPPER must contain at least 64 characters"
  ! aita_is_placeholder "$jwt_secret" || aita_die "AITA_JWT_SECRET still contains a placeholder"
  ! aita_is_placeholder "$refresh_pepper" || aita_die "AITA_REFRESH_PEPPER still contains a placeholder"
  [[ "$jwt_secret" != "$refresh_pepper" ]] || aita_die "AITA_JWT_SECRET and AITA_REFRESH_PEPPER must be independently generated"

  aita_required_value AITA_JWT_ISSUER >/dev/null
  aita_required_value AITA_JWT_AUDIENCE >/dev/null
  aita_required_value AITA_PUBLIC_SERVER_URL >/dev/null
  aita_required_value AITA_DB_URL >/dev/null
  aita_required_value DB_USER >/dev/null
  aita_required_value DB_PASS >/dev/null

  [[ "${AITA_PUBLIC_SERVER_URL}" == https://* ]] || aita_die "AITA_PUBLIC_SERVER_URL must use HTTPS"
  [[ "${AITA_DB_URL}" == jdbc:postgresql://* ]] || aita_die "AITA_DB_URL must be a PostgreSQL JDBC URL"

  local host="${AITA_HOST:-127.0.0.1}"
  case "$host" in
    127.0.0.1|localhost|::1) ;;
    *) aita_die "Production AITA_HOST must be local-only (127.0.0.1, localhost, or ::1) when Cloudflare Tunnel is used" ;;
  esac

  [[ "${AITA_FLYWAY_CLEAN_DISABLED:-true}" == "true" ]] || aita_die "AITA_FLYWAY_CLEAN_DISABLED must stay true in production"
  [[ "${AITA_SCHEMA_AUTO_REPAIR:-false}" == "false" ]] || aita_die "AITA_SCHEMA_AUTO_REPAIR must stay false in production"
}

aita_require_java21() {
  local java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
  if [[ -n "${JAVA_HOME-}" && -x "$JAVA_HOME/bin/java" ]]; then
    java_bin="$JAVA_HOME/bin/java"
  else
    java_bin="$(command -v java || true)"
  fi
  [[ -n "$java_bin" && -x "$java_bin" ]] || aita_die "Java is missing"

  local version
  version="$("$java_bin" -version 2>&1 | awk -F'"' '/version/ && !found { print $2; found=1 }')"
  [[ "$version" == 21.* || "$version" == "21" ]] || aita_die "Java 21 is required; detected ${version:-unknown}"
  printf '%s' "$java_bin"
}

aita_parse_jdbc_url() {
  local url="${1:?JDBC URL is required}"
  if [[ "$url" =~ ^jdbc:postgresql://([^/:?#]+)(:([0-9]+))?/([^?]+) ]]; then
    AITA_PARSED_DB_HOST="${BASH_REMATCH[1]}"
    AITA_PARSED_DB_PORT="${BASH_REMATCH[3]:-5432}"
    AITA_PARSED_DB_NAME="${BASH_REMATCH[4]}"
  else
    aita_die "Unsupported PostgreSQL JDBC URL: $url"
  fi
}

aita_curl_status() {
  local url="${1:?URL is required}"
  local timeout="${2:-10}"
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
    --connect-timeout "$timeout" --max-time "$timeout" "$url"
}


# Capture producers to completion before testing membership. In a pipefail shell,
# `unzip -Z1 ... | grep -q ...` can report failure for a valid large JAR: grep
# exits on its first match and unzip then receives SIGPIPE (exit status 141).
aita_verify_server_jar() {
  local jar="${1:?Server JAR path is required}"
  local project_root="${2:-}"
  local size listing manifest main_class entry count
  aita_require_command unzip
  aita_require_command stat
  aita_require_command sha256sum

  [[ -s "$jar" ]] || aita_die "Server fat JAR is missing or empty: $jar"
  size="$(stat -c '%s' "$jar")" || aita_die "Cannot inspect server JAR: $jar"
  ((size >= 1024 * 1024)) || aita_die "Server JAR is suspiciously small ($size bytes): $jar"
  unzip -tqq "$jar" || aita_die "Server JAR integrity check failed: $jar"
  listing="$(unzip -Z1 "$jar")" || aita_die "Cannot list server JAR entries: $jar"

  for entry in kz/aita/server/ServerKt.class META-INF/MANIFEST.MF application.yaml; do
    count="$(grep -Fxc -- "$entry" <<< "$listing" || true)"
    [[ "$count" == 1 ]] || aita_die "Server JAR must contain exactly one $entry (found $count): $jar"
  done
  manifest="$(unzip -p "$jar" META-INF/MANIFEST.MF)" || aita_die "Cannot read server JAR manifest: $jar"
  manifest="${manifest//$'\r'/}"
  # Only the manifest's main section supplies the executable entrypoint. Reject
  # duplicates instead of accepting a matching line in a later named section.
  main_class="$(awk '
    /^$/ { ended=1 }
    !ended && /^Main-Class:/ {
      sub(/^Main-Class:[[:space:]]*/, ""); sub(/[[:space:]]*$/, ""); print
    }
  ' <<< "$manifest")"
  [[ "$main_class" == kz.aita.server.ServerKt ]] ||
    aita_die "Server JAR manifest does not point unambiguously to kz.aita.server.ServerKt: $jar"

  # For deployment from source, catch an old packaged resource tree before
  # stopping the running server. This inspects bytes in the JAR, NOT the live DB.
  if [[ -n "$project_root" ]]; then
    local resources="$project_root/server/src/main/resources"
    local source_file source_hash packed_hash migrations=0
    [[ -f "$resources/application.yaml" && -d "$resources/db/migration" ]] ||
      aita_die "Server source resources are missing under $project_root"
    local -a source_files=("$resources/application.yaml")
    [[ ! -f "$resources/logback.xml" ]] || source_files+=("$resources/logback.xml")
    while IFS= read -r -d '' source_file; do
      source_files+=("$source_file")
      migrations=$((migrations + 1))
    done < <(find "$resources/db/migration" -type f -name '*.sql' -print0)
    ((migrations > 0)) || aita_die "No Flyway SQL migrations found under $resources/db/migration"

    for source_file in "${source_files[@]}"; do
      entry="${source_file#"$resources/"}"
      count="$(grep -Fxc -- "$entry" <<< "$listing" || true)"
      [[ "$count" == 1 ]] || aita_die "Source resource is missing or duplicated in the JAR: $entry (rebuild before deploying)"
      source_hash="$(sha256sum "$source_file")" || aita_die "Cannot read source resource: $source_file"
      packed_hash="$(unzip -p "$jar" "$entry" | sha256sum)" || aita_die "Cannot read packaged resource: $entry"
      [[ "${source_hash%% *}" == "${packed_hash%% *}" ]] ||
        aita_die "Packaged resource differs from the checkout: $entry (rebuild before deploying; do not repair Flyway checksums)"
    done

    local auth_source="$project_root/server/src/main/kotlin/kz/aita/server/auth/AitaAdvancedAuthentication.kt"
    if [[ -f "$auth_source" ]]; then
      local auth_package
      auth_package="$(awk '$1 == "package" && !found { gsub(/;/, "", $2); print $2; found=1 }' "$auth_source")"
      [[ "$auth_package" =~ ^[A-Za-z_][A-Za-z0-9_.]*$ ]] || aita_die "Cannot determine the authentication source package"
      entry="${auth_package//.//}/AitaAdvancedAuthenticationKt.class"
      grep -Fxq -- "$entry" <<< "$listing" ||
        aita_die "Authentication source exists but its compiled route class is missing: $entry (rebuild before deploying)"
    fi
    aita_info "JAR verified: entrypoint, configuration, and $migrations source migration(s) match. Live DB migration is checked at server startup."
  else
    aita_info "JAR verified: integrity, entrypoint, and application configuration"
  fi
}

# This is a local checkout description, not a claim that GitHub was fetched or
# that the running service already uses this revision. Never print remote URLs
# or environment values here: either may contain credentials.
aita_describe_checkout() {
  local project_root="${1:?Project root is required}" revision status
  if command -v git >/dev/null 2>&1 &&
     revision="$(git -C "$project_root" rev-parse --verify HEAD 2>/dev/null)"; then
    printf '%s' "$revision"
    if status="$(git -C "$project_root" status --porcelain --untracked-files=normal 2>/dev/null)"; then
      [[ -z "$status" ]] || printf ' (uncommitted changes present)'
    else
      printf ' (working-tree status unavailable)'
    fi
    printf '\n'
  else
    printf 'source export without readable Git metadata\n'
  fi
}
