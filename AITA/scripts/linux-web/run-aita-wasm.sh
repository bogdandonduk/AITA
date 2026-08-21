#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../linux-field-server/aita-linux-common.sh"

project_root="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
bind_address="127.0.0.1"
port="8090"
max_workers="${AITA_GRADLE_MAX_WORKERS:-4}"
build=true
build_only=false

while (($#)); do
  case "$1" in
    --project-root)
      project_root="${2:?Missing value for --project-root}"
      shift
      ;;
    --bind)
      bind_address="${2:?Missing value for --bind}"
      shift
      ;;
    --port)
      port="${2:?Missing value for --port}"
      shift
      ;;
    --max-workers)
      max_workers="${2:?Missing value for --max-workers}"
      shift
      ;;
    --skip-build)
      build=false
      ;;
    --build-only)
      build_only=true
      ;;
    -h|--help)
      cat <<'HELP'
Usage: run-aita-wasm.sh [options]

Options:
  --project-root PATH  AITA repository root
  --bind ADDRESS       Preview bind address (default: 127.0.0.1)
  --port PORT          Preview port (default: 8090)
  --max-workers N      Gradle worker limit (default: 4)
  --skip-build         Serve the existing production distribution
  --build-only         Build and verify the distribution, then exit

The default loopback binding is intentional. Reach it remotely over the existing
Tailscale SSH connection with:
  ssh -N -L 8090:127.0.0.1:8090 aita-ubuntu
Then open http://localhost:8090 on the controlling computer.
HELP
      exit 0
      ;;
    *)
      aita_die "Unknown argument: $1"
      ;;
  esac
  shift
done

((EUID != 0)) || aita_die "Run the web build as the normal project owner, not as root or through sudo"
[[ "$port" =~ ^[0-9]+$ ]] && ((port >= 1 && port <= 65535)) || aita_die "--port must be between 1 and 65535"
[[ "$max_workers" =~ ^[1-9][0-9]*$ ]] || aita_die "--max-workers must be a positive integer"
project_root="$(cd -- "$project_root" && pwd)"
[[ -f "$project_root/gradlew" ]] || aita_die "Gradle wrapper is missing under $project_root"
[[ -w "$project_root" ]] || aita_die "Project root is not writable by $(id -un): $project_root"
aita_require_command python3
aita_require_command tee

java_bin="$(aita_require_java21)"
if [[ -z "${JAVA_HOME-}" ]]; then
  resolved_java="$(readlink -f "$java_bin" 2>/dev/null || printf '%s' "$java_bin")"
  export JAVA_HOME="$(cd -- "$(dirname -- "$resolved_java")/.." && pwd)"
fi
export PATH="$JAVA_HOME/bin:$PATH"

dist_dir="$project_root/composeApp/build/dist/wasmJs/productionExecutable"
log_dir="${XDG_STATE_HOME:-$HOME/.local/state}/aita/build-logs"
mkdir -p "$log_dir"
log_file="$log_dir/wasm-build-$(date -u +%Y%m%dT%H%M%SZ).log"

if $build; then
  aita_info "Building the Wasm-only graph; Android, iOS, desktop, and server modules are excluded"
  aita_info "Build log: $log_file"

  set +e
  (
    cd "$project_root"
    bash ./gradlew \
      -Paita.webOnly=true \
      -Porg.gradle.java.installations.auto-download=false \
      "-Porg.gradle.java.installations.paths=$JAVA_HOME" \
      :composeApp:wasmJsBrowserDistribution \
      --no-configuration-cache \
      --no-daemon \
      --no-watch-fs \
      "--max-workers=$max_workers" \
      --console=plain \
      --stacktrace
  ) 2>&1 | tee "$log_file"
  gradle_status=${PIPESTATUS[0]}
  set -e

  ((gradle_status == 0)) || aita_die "Wasm build failed. Full log: $log_file"
fi

for required_file in index.html composeApp.js styles.css favicon.svg favicon.ico favicon-32.png apple-touch-icon.png; do
  [[ -s "$dist_dir/$required_file" ]] || aita_die "Wasm distribution is missing: $dist_dir/$required_file"
done
find "$dist_dir" -maxdepth 1 -type f -name '*.wasm' -size +0c -print -quit | grep -q . \
  || aita_die "No non-empty WebAssembly binary was found under $dist_dir"

aita_info "Wasm distribution ready: $dist_dir"
$build && aita_info "Build log retained at: $log_file"
if $build_only; then
  exit 0
fi

if [[ "$bind_address" == "0.0.0.0" || "$bind_address" == "::" ]]; then
  aita_info "WARNING: the preview is being exposed on every host interface; prefer 127.0.0.1 plus SSH port forwarding"
fi

aita_info "Serving AITA Wasm at http://$bind_address:$port"
if [[ "$bind_address" == "127.0.0.1" || "$bind_address" == "localhost" ]]; then
  aita_info "Remote access: ssh -N -L $port:127.0.0.1:$port aita-ubuntu"
  aita_info "Then open: http://localhost:$port"
fi

exec python3 - "$bind_address" "$port" "$dist_dir" <<'PY'
from __future__ import annotations

import functools
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

bind_address = sys.argv[1]
port = int(sys.argv[2])
directory = Path(sys.argv[3]).resolve()


class AitaWasmHandler(SimpleHTTPRequestHandler):
    extensions_map = {
        **SimpleHTTPRequestHandler.extensions_map,
        ".wasm": "application/wasm",
        ".js": "text/javascript; charset=utf-8",
        ".mjs": "text/javascript; charset=utf-8",
        ".json": "application/json; charset=utf-8",
        ".svg": "image/svg+xml",
    }

    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store, max-age=0")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        super().end_headers()


handler = functools.partial(AitaWasmHandler, directory=str(directory))
server = ThreadingHTTPServer((bind_address, port), handler)
try:
    server.serve_forever()
except KeyboardInterrupt:
    pass
finally:
    server.server_close()
PY
