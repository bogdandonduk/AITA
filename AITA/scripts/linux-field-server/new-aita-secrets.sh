#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  echo "Usage: $0 [--count N]" >&2
  exit 2
}

count=2
case "$#" in
  0) ;;
  2)
    [[ "$1" == "--count" ]] || usage
    count="$2"
    ;;
  *) usage ;;
esac

[[ "$count" =~ ^[1-9][0-9]*$ ]] || usage
command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }
umask 077
for ((i = 1; i <= count; i++)); do
  openssl rand -hex 64
done
