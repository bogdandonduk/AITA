"""Permit a release tooling repair without silently changing pinned application source."""
import argparse
from pathlib import Path
import re
import subprocess


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True).strip()


def validate(source_root, tooling_root, revision, tooling_revision):
    if not all(re.fullmatch('[0-9a-f]{40}', sha) for sha in (revision, tooling_revision)):
        raise RuntimeError('Source and verification revisions must be full commit hashes')
    if git(source_root, 'rev-parse', 'HEAD') != revision or git(tooling_root, 'rev-parse', 'HEAD') != tooling_revision:
        raise RuntimeError('Checked out commits do not match the requested source and verification revisions')
    subprocess.run(['git', '-C', str(tooling_root), 'merge-base', '--is-ancestor', revision, tooling_revision], check=True)
    changed = git(tooling_root, 'diff', '--name-only', revision, tooling_revision, '--').splitlines()
    forbidden = [path for path in changed if not (
        path.startswith('scripts/releases/') or path == '.github/workflows/build-windows-release.yml')]
    if forbidden:
        raise RuntimeError('Verification repair changes application source: ' + ', '.join(forbidden))
    print(f'PINNED: Application {revision}; verification tools {tooling_revision}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, required=True)
    parser.add_argument('--tooling-root', type=Path, required=True)
    parser.add_argument('--revision', required=True)
    parser.add_argument('--tooling-revision', required=True)
    args = parser.parse_args()
    validate(args.source_root, args.tooling_root, args.revision, args.tooling_revision)
