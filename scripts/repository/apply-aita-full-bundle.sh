#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
    cat <<'USAGE'
Apply a complete AITA repository ZIP to an existing Git working tree without
creating AITA/AITA, while preserving .git and local.properties.

Usage:
  bash scripts/repository/apply-aita-full-bundle.sh BUNDLE.zip [REPOSITORY]

Examples:
  bash scripts/repository/apply-aita-full-bundle.sh \
    "$HOME/Downloads/AITA_FULL_NEXT.zip" \
    "$HOME/IdeaProjects/AITA"

The script creates a timestamped full backup beside the repository, extracts
into a staging directory, replaces the working tree with rsync --delete,
clears Git assume-unchanged/skip-worktree flags, byte-verifies the result, and
prints the actual Git diff. The backup is kept until you delete it manually.
USAGE
}

die() {
    printf 'AITA bundle apply: ERROR: %s\n' "$*" >&2
    exit 1
}

info() {
    printf 'AITA bundle apply: %s\n' "$*"
}

command -v git >/dev/null 2>&1 || die "git is required"
command -v unzip >/dev/null 2>&1 || die "unzip is required"
command -v rsync >/dev/null 2>&1 || die "rsync is required"
command -v shasum >/dev/null 2>&1 || command -v sha256sum >/dev/null 2>&1 || die "shasum or sha256sum is required"

case "${1:-}" in
    -h|--help)
        usage
        exit 0
        ;;
esac

bundle_input=${1:-}
repo_input=${2:-}
[[ -n "$bundle_input" ]] || { usage >&2; exit 2; }
[[ -f "$bundle_input" ]] || die "bundle does not exist: $bundle_input"

bundle_dir=$(cd "$(dirname "$bundle_input")" && pwd -P)
bundle="$bundle_dir/$(basename "$bundle_input")"

if [[ -z "$repo_input" ]]; then
    repo_input=$(git rev-parse --show-toplevel 2>/dev/null || true)
fi
if [[ -z "$repo_input" ]]; then
    repo_input="$HOME/IdeaProjects/AITA"
fi
[[ -d "$repo_input" ]] || die "repository directory does not exist: $repo_input"
repo=$(cd "$repo_input" && pwd -P)

[[ -f "$repo/settings.gradle.kts" ]] || die "not an AITA repository root: $repo"
[[ -e "$repo/.git" ]] || die "the target has no .git metadata: $repo"

git_root=$(git -C "$repo" rev-parse --show-toplevel 2>/dev/null || true)
[[ "$git_root" == "$repo" ]] || die "Git root is '$git_root', not '$repo'. Refusing to update the wrong tree."

# Read the complete listing: grep -q on a live unzip pipe can mask an unsafe
# path when pipefail observes SIGPIPE after the first match.
bundle_listing="$(unzip -Z1 "$bundle")" || die "cannot read bundle directory"
if grep -Eq '(^/|(^|/)\.\.(/|$))' <<< "$bundle_listing"; then
    die "bundle contains an unsafe absolute or parent-traversal path"
fi
unzip -tqq "$bundle" || die "bundle integrity check failed"

repo_parent=$(dirname "$repo")
repo_name=$(basename "$repo")
stamp=$(date -u +%Y%m%dT%H%M%SZ)
stage=$(mktemp -d "$repo_parent/.aita-bundle-stage.XXXXXX")
backup="$repo_parent/${repo_name}-before-${stamp}"
installed=0

cleanup() {
    local exit_code=$?
    rm -rf "$stage"
    if [[ $exit_code -ne 0 && $installed -eq 1 && -d "$backup" ]]; then
        info "installation failed; restoring the previous repository from $backup"
        rm -rf "$repo"
        mv "$backup" "$repo"
    fi
    exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

mkdir -p "$stage/extracted"
unzip -q "$bundle" -d "$stage/extracted"

source_root=""
if [[ -f "$stage/extracted/AITA/settings.gradle.kts" ]]; then
    source_root="$stage/extracted/AITA"
else
    candidate_count=0
    while IFS= read -r candidate; do
        source_root=$(dirname "$candidate")
        candidate_count=$((candidate_count + 1))
    done < <(find "$stage/extracted" -type f -name settings.gradle.kts -print)
    [[ $candidate_count -eq 1 ]] || die "expected exactly one AITA settings.gradle.kts, found $candidate_count"
fi

[[ -f "$source_root/composeApp/build.gradle.kts" ]] || die "bundle is missing composeApp/build.gradle.kts"
[[ -f "$source_root/shared/build.gradle.kts" ]] || die "bundle is missing shared/build.gradle.kts"
[[ -f "$source_root/server/build.gradle.kts" ]] || die "bundle is missing server/build.gradle.kts"
[[ ! -e "$source_root/.git" ]] || die "bundle unexpectedly contains .git metadata"

# Some exports lose executable bits. Normalize the staging tree BEFORE copying
# and verifying it; otherwise our own chmod makes verification fail and restores
# the old tree, which looks like Git ignored the new files.
[[ ! -f "$source_root/gradlew" ]] || chmod +x "$source_root/gradlew"
if [[ -d "$source_root/scripts" ]]; then
    find "$source_root/scripts" -type f -name '*.sh' -exec chmod +x {} +
fi

if command -v shasum >/dev/null 2>&1; then
    bundle_sha=$(shasum -a 256 "$bundle" | awk '{print $1}')
else
    bundle_sha=$(sha256sum "$bundle" | awk '{print $1}')
fi
source_files=$(find "$source_root" -type f | wc -l | tr -d ' ')

info "bundle: $bundle"
info "SHA-256: $bundle_sha"
info "source files: $source_files"
info "target repository: $repo"
info "backup: $backup"

[[ ! -e "$backup" ]] || die "backup path already exists: $backup"
cp -a "$repo" "$backup"
installed=1

# Preserve only machine-local Git metadata and Android SDK location. Everything
# else is replaced exactly, and stale files are deleted from the active tree.
# --checksum matters: ZIP exports can preserve size AND timestamp for changed
# bytes, which rsync's default quick check would incorrectly skip.
rsync -a --checksum --delete \
    --exclude='/.git' \
    --exclude='/local.properties' \
    "$source_root/" "$repo/"

# A file can be byte-different yet hidden from status when it carries one of
# these index flags. Remove them only from paths that actually have a flag.
while IFS= read -r -d '' entry; do
    flag=${entry%% *}
    path=${entry#? }
    case "$flag" in
        [a-zS])
            # Git treats these as separate index operations; supplying both
            # switches in one command does not reliably clear skip-worktree.
            git -C "$repo" update-index --no-assume-unchanged -- "$path" || true
            git -C "$repo" update-index --no-skip-worktree -- "$path" || true
            ;;
    esac
done < <(git -C "$repo" ls-files -v -z)

git -C "$repo" update-index --really-refresh >/dev/null 2>&1 || true

# A checksum dry run must be empty. This verifies bytes, file presence, and
# deletion of stale source files without relying on timestamps or IDE caches.
verification=$(rsync -naci --delete \
    --exclude='/.git' \
    --exclude='/local.properties' \
    "$source_root/" "$repo/")
[[ -z "$verification" ]] || {
    printf '%s\n' "$verification" >&2
    die "the active repository does not exactly match the extracted bundle"
}

active_root=$(git -C "$repo" rev-parse --show-toplevel)
[[ "$active_root" == "$repo" ]] || die "post-install Git root mismatch: $active_root"

installed=2
trap - EXIT INT TERM HUP
rm -rf "$stage"

info "bundle installed and byte-verified"
info "Git status follows:"
status=$(git -C "$repo" status --short)
if [[ -n "$status" ]]; then
    printf '%s\n' "$status"
    info "Git diff summary:"
    git -C "$repo" diff --stat || true
else
    info "Git is clean because the bundle's tracked bytes already match the current index/HEAD."
    info "This is not an IntelliJ cache problem."
fi

info "keep the backup until AITA compiles and launches: $backup"
