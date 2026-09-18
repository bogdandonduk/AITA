#!/usr/bin/python3 -I
"""One-time owner-installed server release launcher. Only a pushed master commit is accepted.
Uses AITA's existing backup/build/readiness updater; grants no arbitrary sudo command.
Install: sudo python3 scripts/releases/server-release-access.py install --repo /home/bogdan/IdeaProjects/AITA --owner bogdan
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import pwd
import re
import shutil
import subprocess
import sys
import tempfile

BASE = Path('/usr/local/lib/aita-release')
CONFIG = Path('/etc/aita-release.json')
LAUNCHER = Path('/usr/local/sbin/aita-release-server')
SUDOERS = Path('/etc/sudoers.d/aita-release')
REMOTE_URLS = {'https://github.com/bogdandonduk/AITA.git', 'git@github.com:bogdandonduk/AITA.git'}


def require(ok, text):
    if not ok:
        raise RuntimeError(text)


def owner_command(config, *args, output=None):
    owner = pwd.getpwnam(config['owner'])
    command = ['/usr/sbin/runuser', '-u', owner.pw_name, '--', '/usr/bin/env', '-i',
        'HOME=' + owner.pw_dir, 'PATH=/usr/local/bin:/usr/bin:/bin', 'JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64',
        'GIT_TERMINAL_PROMPT=0', '/usr/bin/git', '-C', config['repo'], *args]
    result = subprocess.run(command, stdout=output or subprocess.PIPE, stderr=subprocess.PIPE, timeout=180)
    require(result.returncode == 0, 'Repository check/fetch failed as its owner; inspect GitHub authentication. No deploy started')
    return '' if output else result.stdout.decode().strip()


def validate_config(config):
    user = pwd.getpwnam(config['owner'])
    require(user.pw_uid > 0 and re.fullmatch(r'[a-z_][a-z0-9_-]*', user.pw_name), 'Invalid build owner')
    repo = Path(config['repo'])
    require(repo.is_absolute() and repo.is_dir() and repo.stat().st_uid == user.pw_uid, 'Repository must belong to the configured normal user')
    require(owner_command(config, 'remote', 'get-url', 'origin') in REMOTE_URLS, 'Only bogdandonduk/AITA origin is allowed')
    require(owner_command(config, 'symbolic-ref', '--short', 'HEAD') == 'master', 'Server deployment requires master checkout')
    require(not owner_command(config, 'status', '--porcelain'), 'Server checkout must be clean')


def install(args):
    config = {'owner': args.owner, 'repo': str(Path(args.repo).resolve())}
    validate_config(config)
    owner_command(config, 'fetch', '--prune', 'origin')
    require(owner_command(config, 'rev-parse', 'HEAD') == owner_command(config, 'rev-parse', 'origin/master'), 'Install the reviewed, pushed master version first')
    require(not BASE.is_symlink() and not LAUNCHER.is_symlink() and not CONFIG.is_symlink() and not SUDOERS.is_symlink(), 'Refusing symlinked installation paths')
    BASE.mkdir(mode=0o755, exist_ok=True)
    require(BASE.stat().st_uid == 0 and BASE.stat().st_mode & 0o022 == 0, 'Helper directory must be root-owned and not writable by others')
    ops = Path(config['repo']) / 'scripts/linux-field-server/aita-ops.py'
    shutil.copyfile(ops, BASE / 'ops-bootstrap.py'); (BASE / 'ops-bootstrap.py').chmod(0o644)
    shutil.copyfile(__file__, LAUNCHER); LAUNCHER.chmod(0o755)
    CONFIG.write_text(json.dumps(config) + '\n'); CONFIG.chmod(0o600)
    for path in (BASE / 'ops-bootstrap.py', LAUNCHER, CONFIG):
        os.chown(path, 0, 0)
    rule = f"{args.owner} ALL=(root) NOPASSWD: {LAUNCHER} *\n"
    with tempfile.NamedTemporaryFile(mode='w', prefix='aita-sudoers-', dir='/etc/sudoers.d', delete=False) as file:
        temporary = Path(file.name); file.write(rule)
    try:
        temporary.chmod(0o440)
        subprocess.run(['/usr/sbin/visudo', '-cf', str(temporary)], check=True)
        temporary.replace(SUDOERS)
    finally:
        temporary.unlink(missing_ok=True)
    print('READY: Only the fixed AITA release launcher can run without a password')
    print('It requires one exact SHA matching clean, pushed origin/master; builds still run as the normal user')
    print('Existing encrypted backup, controlled restart and health checks remain mandatory')
    print('No server was restarted by installation')


def launch(commit):
    require(re.fullmatch(r'[0-9a-f]{40}', commit), 'Provide exactly one full reviewed commit SHA')
    require(CONFIG.stat().st_uid == 0 and CONFIG.stat().st_mode & 0o077 == 0, 'Unsafe release configuration permissions')
    config = json.loads(CONFIG.read_text()); validate_config(config)
    owner_command(config, 'fetch', '--prune', 'origin')
    require(owner_command(config, 'rev-parse', 'origin/master') == commit and owner_command(config, 'rev-parse', 'HEAD') == commit,
        'Requested SHA must equal both clean local master and fetched origin/master')
    # Root owns the copied snapshot and launcher. No user-editable Python file executes as root.
    spec = importlib.util.spec_from_file_location('aita_ops_release_bootstrap', BASE / 'ops-bootstrap.py')
    ops = importlib.util.module_from_spec(spec); sys.modules[spec.name] = ops; spec.loader.exec_module(ops)
    with tempfile.TemporaryDirectory(prefix='aita-release-', dir='/var/tmp') as directory:
        archive = Path(directory) / 'source.tar'
        with archive.open('xb') as stream:
            owner_command(config, 'archive', '--format=tar', commit, output=stream)
        with archive.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        # The existing updater validates/extracts the archive, starts its managed systemd job,
        # builds as the owner, backs up and performs readiness checks before reporting success.
        args = argparse.Namespace(archive=str(archive), digest=digest, owner=config['owner'], commit=commit,
            java_home='/usr/lib/jvm/java-21-openjdk-amd64', ready_timeout=600, with_tests=True,
            require_offsite_backup=False, allow_local_backup=True, force=False)
        require(ops.start_job(args) == 0, 'Existing updater did not confirm launch; inspect status before retrying')


def main():
    require(os.geteuid() == 0, 'Installation/launch requires sudo; build processes never run as root')
    if len(sys.argv) >= 2 and sys.argv[1] == 'install':
        # The installed launcher cannot be repurposed for installation through its sudo grant.
        require(Path(__file__).resolve() != LAUNCHER, 'Installed launcher accepts only a commit SHA')
        parser = argparse.ArgumentParser(description=__doc__)
        parser.add_argument('install'); parser.add_argument('--repo', required=True); parser.add_argument('--owner', required=True)
        install(parser.parse_args())
    else:
        require(len(sys.argv) == 2, 'Provide exactly one full reviewed commit SHA')
        launch(sys.argv[1])


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('STOP: ' + str(error), file=sys.stderr)
        sys.exit(1)
