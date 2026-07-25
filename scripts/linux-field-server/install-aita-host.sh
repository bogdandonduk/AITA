#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/aita-linux-common.sh"

install_packages=false
while (($#)); do
  case "$1" in
    --install-packages) install_packages=true ;;
    -h|--help)
      echo "Usage: sudo $0 [--install-packages]"
      exit 0
      ;;
    *) aita_die "Unknown argument: $1" ;;
  esac
  shift
done

((EUID == 0)) || aita_die "Run this installer with sudo"

if $install_packages; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y \
    openjdk-21-jdk-headless curl ca-certificates jq rsync unzip \
    postgresql-client age rclone smartmontools ufw
fi

if ! getent group aita >/dev/null; then
  groupadd --system aita
fi
if ! id aita >/dev/null 2>&1; then
  useradd --system --gid aita --home-dir /var/lib/aita --create-home --shell /usr/sbin/nologin aita
fi

install -d -o root -g aita -m 0750 /etc/aita
install -d -o root -g root -m 0755 /opt/aita
install -d -o aita -g aita -m 0750 /opt/aita/app
install -d -o aita -g aita -m 0750 /var/lib/aita /var/lib/aita/tmp
install -d -o aita -g aita -m 0750 /var/log/aita /var/backups/aita
install -d -o aita -g aita -m 0750 /srv/aita /srv/aita/server-files /srv/aita/backups /srv/aita/locks

install -d -o root -g root -m 0755 /usr/local/lib/aita
for script in \
  aita-linux-common.sh \
  backup-aita-postgres.sh \
  healthcheck-aita.sh \
  test-aita-readiness.sh \
  restore-test-backup.sh; do
  install -o root -g root -m 0755 "$SCRIPT_DIR/$script" "/usr/local/lib/aita/$script"
done

install -o root -g aita -m 0640 "$SCRIPT_DIR/aita-prod.env.example" /etc/aita/aita-prod.env.example
if [[ ! -e /etc/aita/aita-prod.env ]]; then
  install -o root -g aita -m 0640 "$SCRIPT_DIR/aita-prod.env.example" /etc/aita/aita-prod.env
  aita_info "Created /etc/aita/aita-prod.env with placeholders; edit it before starting the server"
fi

for unit in "$SCRIPT_DIR"/systemd/*.service "$SCRIPT_DIR"/systemd/*.timer; do
  install -o root -g root -m 0644 "$unit" "/etc/systemd/system/$(basename "$unit")"
done

systemctl daemon-reload
systemctl enable aita-server.service aita-backup.timer aita-backup-daily.timer aita-healthcheck.timer

aita_info "Host layout and systemd units are installed"
aita_info "Next: edit /etc/aita/aita-prod.env, deploy the fat JAR, then run test-aita-readiness.sh"
