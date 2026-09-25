#!/usr/bin/env python3
"""Transport Windows CI results through private drafts, independently of Actions storage.

Drafts are never published by this helper. The normal release coordinator checks every
platform and the package verifier receipt before publishing a customer release.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

REPO = 'bogdandonduk/AITA'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True).strip()


def identity(run_id, attempt, revision):
    require(str(run_id).isdigit() and int(run_id) > 0 and str(attempt).isdigit() and int(attempt) > 0,
            'Invalid Windows run identity')
    require(re.fullmatch('[0-9a-f]{40}', revision), 'Invalid source revision')
    return f'windows-build-{run_id}-{attempt}'


def validate_draft(draft, tag, revision):
    require(draft['isDraft'] is True and draft['tagName'] == tag and draft['targetCommitish'] == revision,
            'Windows transport must be a private draft for the exact source')


def draft_view(tag, revision):
    value = json.loads(gh('release', 'view', tag, '--repo', REPO,
                         '--json', 'isDraft,tagName,targetCommitish,apiUrl'))
    validate_draft(value, tag, revision)
    require(re.fullmatch(re.escape(f'https://api.github.com/repos/{REPO}/releases/') + '[0-9]+', value['apiUrl']),
            'Unexpected draft API location')
    remote = json.loads(gh('api', value['apiUrl']))
    validate_draft(dict(isDraft=remote['draft'], tagName=remote['tag_name'], targetCommitish=remote['target_commitish']), tag, revision)
    value['assets'] = remote['assets']
    return value


def upload(args):
    tag = identity(args.run, args.attempt, args.revision)
    # List drafts rather than treating an arbitrary network error as "not found".
    known = json.loads(gh('release', 'list', '--repo', REPO, '--limit', '100', '--json', 'tagName'))
    if not any(r['tagName'] == tag for r in known):
        gh('release', 'create', tag, '--repo', REPO, '--target', args.revision, '--draft',
           '--title', f'Windows build {args.run}, attempt {args.attempt} · internal verification',
           '--notes', 'Internal CI transport. Not an installable release. Publication is controlled by the release coordinator.')
    value = draft_view(tag, args.revision)
    name = 'windows-diagnostics.zip' if args.diagnostics else 'windows-package.zip'
    require(not any(a['name'] == name for a in value['assets']), 'Immutable Windows transport asset already exists')
    roots = [Path(p) for p in args.paths]
    files = []
    for root in roots:
        if root.is_file():
            files.append((root, root.as_posix() if args.diagnostics else root.name))
        elif root.is_dir():
            files.extend((p, p.as_posix() if args.diagnostics else p.relative_to(root).as_posix())
                         for p in sorted(root.rglob('*')) if p.is_file())
    require(files, 'No Windows artifacts to preserve')
    require(all(not p.is_symlink() and not Path(n).is_absolute() and '..' not in Path(n).parts for p, n in files),
            'Unsafe Windows artifact path')
    require(len({n for _, n in files}) == len(files), 'Duplicate Windows artifact name')
    with tempfile.TemporaryDirectory() as folder:
        archive = Path(folder) / name
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as package:
            for path, name_in_zip in files:
                package.write(path, name_in_zip)
        gh('release', 'upload', tag, str(archive), '--repo', REPO)
        remote = draft_view(tag, args.revision)
        asset = next(a for a in remote['assets'] if a['name'] == name)
        require(asset['size'] == archive.stat().st_size and
                asset.get('digest') == 'sha256:' + hashlib.sha256(archive.read_bytes()).hexdigest(),
                'Windows transport upload checksum mismatch')
    print(f'PRESERVED: {name} in private draft {tag}; no customer release published')


def extract_package(archive, destination):
    with zipfile.ZipFile(archive) as package:
        entries = package.infolist()
        names = [e.filename for e in entries]
        require(len(names) == len(set(names)) and 4 <= len(names) <= 8, 'Unexpected Windows package entries')
        require(all(re.fullmatch(r'[A-Za-z0-9_.-]+', n) and Path(n).suffix.lower() in ('.exe', '.msi', '.json', '.txt') for n in names),
                'Unsafe Windows package entry')
        require(all(e.file_size <= 1_000_000_000 and e.external_attr >> 28 != 0xA for e in entries) and
                sum(e.file_size for e in entries) < 2_000_000_000, 'Unsafe Windows package size or link')
        require({'windows-verification.json', 'windows-client-build.json'} <= set(names), 'Windows verification receipts missing')
        require(any(n.endswith('.exe') for n in names) and any(n.endswith('.msi') for n in names), 'Windows installers missing')
        destination.mkdir(parents=True, exist_ok=False)
        package.extractall(destination)


def download(args):
    run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{args.run}'))
    require(run['conclusion'] == 'success' and run['name'] == 'Build AITA Windows Release', 'Windows workflow did not pass')
    tag = identity(args.run, run['run_attempt'], args.revision)
    value = draft_view(tag, args.revision)
    asset = next(a for a in value['assets'] if a['name'] == 'windows-package.zip')
    with tempfile.TemporaryDirectory() as folder:
        gh('release', 'download', tag, '--repo', REPO, '--pattern', asset['name'], '--dir', folder)
        archive = Path(folder) / asset['name']
        require(asset['size'] == archive.stat().st_size and
                asset.get('digest') == 'sha256:' + hashlib.sha256(archive.read_bytes()).hexdigest(),
                'Windows transport download checksum mismatch')
        extract_package(archive, args.destination)
    print(f'RECEIVED: exact-source Windows artifacts from {tag}; package checks still required')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['upload', 'download'])
    parser.add_argument('--run', required=True)
    parser.add_argument('--attempt', default=os.environ.get('GITHUB_RUN_ATTEMPT', '1'))
    parser.add_argument('--revision', required=True)
    parser.add_argument('--diagnostics', action='store_true')
    parser.add_argument('--destination', type=Path)
    parser.add_argument('--path', dest='paths', action='append', default=[])
    args = parser.parse_args()
    (upload if args.command == 'upload' else download)(args)
