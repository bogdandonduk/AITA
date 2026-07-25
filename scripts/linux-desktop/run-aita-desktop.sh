#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVER_URL=""
STOP_DAEMON=true

usage() {
  cat <<USAGE
Usage: $0 [--project-root PATH] [--local-server | --server-url URL] [--keep-daemon]

Launches the Compose desktop client from an interactive Linux graphical session.
Use --local-server while api.aita.kz DNS/Tunnel routing is being repaired.
USAGE
}

while (($#)); do
  case "$1" in
    --project-root)
      shift
      (($#)) || { echo "Missing value for --project-root" >&2; exit 2; }
      PROJECT_ROOT="$1"
      ;;
    --local-server) SERVER_URL="http://127.0.0.1:8080" ;;
    --server-url)
      shift
      (($#)) || { echo "Missing value for --server-url" >&2; exit 2; }
      SERVER_URL="$1"
      ;;
    --keep-daemon) STOP_DAEMON=false ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

((EUID != 0)) || { echo "Run the desktop client as your normal graphical user, not root/sudo." >&2; exit 1; }
[[ -d "$PROJECT_ROOT" ]] || { echo "Project directory not found: $PROJECT_ROOT" >&2; exit 1; }
PROJECT_ROOT="$(cd "$PROJECT_ROOT" && pwd)"
[[ -x "$PROJECT_ROOT/gradlew" ]] || { echo "Gradle wrapper not found under: $PROJECT_ROOT" >&2; exit 1; }

if [[ -z "${DISPLAY-}" ]]; then
  cat >&2 <<'ERROR'
AITA desktop cannot start because DISPLAY is missing.
Open a Terminal inside the logged-in Ubuntu desktop and run this script there.
A plain SSH shell, systemd service, or sudo session is not a graphical desktop session.
When using a Wayland desktop, ensure XWayland is installed and log out/in after installing it.
ERROR
  exit 1
fi

java_bin="$(command -v java || true)"
[[ -n "$java_bin" ]] || { echo "Java is missing. Install openjdk-21-jdk." >&2; exit 1; }
java_home="$($java_bin -XshowSettings:properties -version 2>&1 | awk -F'= ' '/^[[:space:]]*java.home =/ { print $2; exit }')"
java_version="$($java_bin -version 2>&1 | awk -F'"' '/version/ { print $2; exit }')"
[[ -n "$java_home" ]] || { echo "Could not determine java.home from $java_bin." >&2; exit 1; }
[[ "$java_version" == 21 || "$java_version" == 21.* ]] || {
  echo "AITA desktop requires Java 21; detected ${java_version:-unknown}." >&2
  exit 1
}

java_option_text="${JAVA_TOOL_OPTIONS-} ${_JAVA_OPTIONS-} ${JDK_JAVA_OPTIONS-}"
if [[ "$java_option_text" == *"-Djava.awt.headless=true"* ]]; then
  echo "A Java environment option forces headless mode. Remove -Djava.awt.headless=true before launching AITA desktop." >&2
  exit 1
fi

[[ -f "$java_home/lib/libawt_xawt.so" ]] || {
  cat >&2 <<ERROR
The selected Java 21 runtime is headless and lacks libawt_xawt.so:
  $java_home
Install the complete runtime and restart Gradle/IntelliJ:
  sudo apt install openjdk-21-jdk xwayland xdg-utils
ERROR
  exit 1
}

if [[ -n "$SERVER_URL" ]]; then
  SERVER_URL="${SERVER_URL%/}"
  case "$SERVER_URL" in
    http://127.0.0.1:*|http://localhost:*|https://*) ;;
    *) echo "Refusing unsafe desktop server URL: $SERVER_URL" >&2; exit 1 ;;
  esac
  export AITA_ENABLE_CLIENT_SERVER_URL_ENV_OVERRIDE=true
  export AITA_CLIENT_SERVER_URL="$SERVER_URL"
fi

if [[ "$SERVER_URL" == http://127.0.0.1:* || "$SERVER_URL" == http://localhost:* ]]; then
  command -v curl >/dev/null 2>&1 || {
    echo "curl is required to verify the local AITA server. Install it with: sudo apt install curl" >&2
    exit 1
  }
  curl --fail --silent --show-error --max-time 5 "$SERVER_URL/readyz" >/dev/null || {
    echo "Local AITA server is not ready at $SERVER_URL/readyz" >&2
    exit 1
  }
fi

cd "$PROJECT_ROOT"
export JAVA_HOME="$java_home"
export PATH="$JAVA_HOME/bin:$PATH"

if $STOP_DAEMON; then
  ./gradlew --stop >/dev/null 2>&1 || true
fi

printf 'AITA Linux desktop: DISPLAY=%s WAYLAND_DISPLAY=%s JAVA_HOME=%s SERVER=%s\n' \
  "${DISPLAY-}" "${WAYLAND_DISPLAY-}" "$JAVA_HOME" "${SERVER_URL:-bootstrap/default}"
exec ./gradlew --no-daemon -Dorg.gradle.java.home="$JAVA_HOME" :composeApp:run
