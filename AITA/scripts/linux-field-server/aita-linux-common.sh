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

  local raw line key value
  local -A seen=()
  while IFS= read -r raw || [[ -n "$raw" ]]; do
    line="${raw%$'\r'}"
    line="${line#${line%%[![:space:]]*}}"
    line="${line%${line##*[![:space:]]}}"
    [[ -z "$line" || "${line:0:1}" == "#" ]] && continue

    if [[ "$line" == export[[:space:]]* ]]; then
      line="${line#export}"
      line="${line#${line%%[![:space:]]*}}"
    fi

    [[ "$line" == *=* ]] || aita_die "Malformed environment line in $env_file: $raw"
    key="${line%%=*}"
    value="${line#*=}"
    key="${key%${key##*[![:space:]]}}"

    [[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || aita_die "Invalid environment variable name in $env_file: $key"
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
  version="$($java_bin -version 2>&1 | awk -F'"' '/version/ { print $2; exit }')"
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
