#!/usr/bin/env python3
"""AITA verified release pass: Android APK/AAB, Pages web, Windows CI, existing server updater.
Run doctor first. Every invocation writes a redacted log and a machine-readable result under
build/releases. Signing material stays outside Git. No Worker deployment or automatic repair.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from datetime import datetime, timezone
import zipfile
import functools
import http.server
import threading

REPOSITORY = 'bogdandonduk/AITA'
ACCOUNT = 'eb83f32e0d274f3e81e020d4776f666e'
ROOT = Path(__file__).resolve().parents[2]
SIGNING = Path.home() / '.config/aita/release-signing'


def utc():
    return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def ordered_release_targets(targets):
    # Old clients remain compatible with the additive server protocol. New web clients
    # must not reach an older backend that would ignore their explicit return destination.
    return (["server"] if "server" in targets else []) + [t for t in targets if t not in {"server", "windows"}] + (["windows"] if "windows" in targets else [])


def read_release_notes(path=None):
    notes = json.loads(Path(path).read_text()) if path else {
        'en': 'AITA release. See the versioned release record for changes and verification.',
        'ru': 'Выпуск AITA. Изменения и проверки указаны в записи этой версии.',
        'kk': 'AITA шығарылымы. Өзгерістер мен тексерулер нұсқа жазбасында көрсетілген.',
        'ky': 'AITA чыгарылышы. Өзгөртүүлөр жана текшерүүлөр версия жазуусунда көрсөтүлгөн.',
        'tg': 'Нашри AITA. Тағйирот ва санҷишҳо дар сабти версия оварда шудаанд.',
        'uz': 'AITA nashri. O‘zgarishlar va tekshiruvlar versiya yozuvida ko‘rsatilgan.'}
    require(isinstance(notes, dict) and set(notes) == {'en', 'ru', 'kk', 'ky', 'tg', 'uz'} and
            all(isinstance(v, str) and 0 < len(v.strip()) <= 24000 for v in notes.values()),
            'Release notes must contain nonempty EN/RU/KK/KY/TG/UZ strings, at most 24000 characters each')
    return notes


def redact(text, secrets=()):
    for value in sorted((v for v in secrets if len(v) >= 6), key=len, reverse=True):
        text = text.replace(value, '[REDACTED]')
    text = re.sub(r'(?i)(Bearer\s+|(?:password|token|secret)\s*[=:]\s*)[^\s,;]+', r'\1[REDACTED]', text)
    text = re.sub(r'(https?://)[^/@\s]+@', r'\1[REDACTED]@', text)
    return re.sub(r'(?:gh[pousr]_|github_pat_)[A-Za-z0-9_]+', '[REDACTED]', text)


def capture(args, cwd=ROOT):
    result = subprocess.run(args, cwd=cwd, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    require(result.returncode == 0, f'{args[0]} failed ({result.returncode}): {redact(result.stderr[-1200:])}')
    return result.stdout.strip()


def version_tuple(version):
    require(re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', version), 'Version must be MAJOR.MINOR.PATCH')
    parts = tuple(map(int, version.split('.')))
    require(parts[0] <= 255 and parts[1] <= 255 and parts[2] <= 65535, 'Version exceeds Windows installer limits')
    return parts


def release_identity(version, build):
    version_tuple(version)
    require(1 <= build <= 2_100_000_000, 'Build must be 1..2100000000')
    return f'v{version}-b{build}'


def next_feed_sequence(catalog, build):
    """A later platform may finish the same build; metadata still needs a new sequence."""
    existing = catalog / 'release.json'
    if not existing.exists():
        return build
    envelope = json.loads(existing.read_text())
    current = json.loads(base64.b64decode(envelope['payload'], validate=True))['sequence']
    require(type(current) is int and 1 <= current < 9_007_199_254_740_991,
            'Existing updater sequence is invalid or exhausted; inspect the catalog before publishing')
    # The publisher independently verifies the existing signature, monotonicity and immutable
    # artifacts under its publication lock. Reading this number does not establish trust.
    return max(build, current + 1)


def draft_release_assets(tag, revision):
    # A draft can lack a Git tag; GitHub's releases/tags endpoint then returns 404.
    # gh resolves the draft for the authenticated owner and supplies its stable REST ID URL.
    view = json.loads(capture(['gh', 'release', 'view', tag, '--repo', REPOSITORY,
                              '--json', 'apiUrl,tagName,targetCommitish,isDraft']))
    require(view['isDraft'] and view['tagName'] == tag and view['targetCommitish'] == revision,
            'GitHub draft identity changed before asset verification')
    require(re.fullmatch(re.escape(f'https://api.github.com/repos/{REPOSITORY}/releases/') + '[0-9]+', view['apiUrl']),
            'Unexpected GitHub draft API URL')
    remote = json.loads(capture(['gh', 'api', view['apiUrl']]))
    require(remote['draft'] and remote['tag_name'] == tag and remote['target_commitish'] == revision,
            'GitHub draft identity changed while reading its assets')
    return remote['assets']


def load_signing():
    config = SIGNING / 'android.json'
    require(config.is_file() and not config.is_symlink() and config.stat().st_mode & 0o077 == 0,
            'Run scripts/releases/setup-android-signing.py first; signing configuration must be private (0600)')
    values = json.loads(config.read_text())
    required = ['AITA_ANDROID_KEYSTORE_PATH', 'AITA_ANDROID_KEYSTORE_PASSWORD', 'AITA_ANDROID_KEY_ALIAS', 'AITA_ANDROID_KEY_PASSWORD']
    require(all(isinstance(values.get(k), str) and values[k] for k in required), 'Android signing configuration is incomplete')
    key = Path(values['AITA_ANDROID_KEYSTORE_PATH'])
    require(key.is_file() and not key.is_symlink() and key.stat().st_mode & 0o077 == 0, 'Keystore must exist with private permissions')
    return {k: values[k] for k in required}


def advice(error):
    text = str(error).lower()
    for needles, answer in [
        (('keystore', 'signing configuration'), 'Preserve the existing key. Check its path/password locally; never generate a replacement for an installed production app.'),
        (('no space', 'disk full'), 'Free space by reviewing old build outputs. Keep databases, signing keys and backups.'),
        (('resolve host', 'dns', 'https'), 'Check the apex CNAME and Pages custom-domain status. Do not disable certificate verification.'),
        (('401', 'oauth', 'authentication'), 'For Cloudflare, run npx --yes wrangler@4.134.0 login --device; for GitHub, run gh auth status.'),
        (('sudo', 'server helper'), 'The server helper needs one-time installation by the Ubuntu owner. No blanket passwordless sudo is required.'),
        (('signature', 'certificate', 'windows signing'), 'Verify the installer checksum and signing configuration. Trusted Windows mode requires a company certificate; pilot mode still rejects broken signatures.'),
        (('compil', 'test'), 'Fix the named compiler/test failure and rerun verification before publishing.'),
        (('already exists', 'collision'), 'Published assets are immutable. Inspect the saved result and existing release; use a new build number if bytes changed.'),
    ]:
        if any(x in text for x in needles):
            return answer
    return 'Read the saved stage log. Completed external actions are recorded; inspect them before rerunning. No rollback, migration repair or key rotation was attempted.'


class Run:
    def __init__(self, args):
        self.args = args
        self.root = ROOT
        self.env = dict(os.environ)
        self.env.setdefault('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')
        self.env.setdefault('ANDROID_HOME', str(Path.home() / 'Android/Sdk'))
        self.env['PATH'] = str(Path(self.env['JAVA_HOME']) / 'bin') + os.pathsep + self.env.get('PATH', '')
        self.secrets = [v for k, v in self.env.items() if any(x in k.upper() for x in ('TOKEN', 'PASSWORD', 'SECRET'))]
        self.folder = ROOT / 'build/releases' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + str(os.getpid()))
        self.folder.mkdir(parents=True)
        self.log = (self.folder / 'release.log').open('x', buffering=1)
        self.state = {'startedAt': utc(), 'stages': {}, 'artifacts': [], 'log': str(self.folder / 'release.log')}
        self.assets = self.folder / 'assets'
        self.assets.mkdir()
        self.save()

    def save(self):
        path = self.folder / 'result.json'
        temporary = path.with_suffix('.tmp')
        temporary.write_text(json.dumps(self.state, indent=2) + '\n')
        temporary.replace(path)

    def say(self, label, message):
        line = f'[{utc()}] {label}: {redact(str(message), self.secrets)}'
        print(line, flush=True); self.log.write(line + '\n')

    def command(self, label, args):
        self.say('RUN', label)
        with subprocess.Popen(args, cwd=ROOT, env=self.env, text=True, stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT, bufsize=1) as child:
            for line in child.stdout:
                clean = redact(line.rstrip(), self.secrets)
                print(clean, flush=True); self.log.write(clean + '\n')
            code = child.wait()
        require(code == 0, f'{label} failed with exit code {code}')

    def stage(self, name, action):
        artifact_start = len(self.state['artifacts'])
        self.state['stages'][name] = {'status': 'running', 'startedAt': utc()}; self.save()
        self.say('START', name)
        try:
            action()
        except Exception as error:
            del self.state['artifacts'][artifact_start:]
            clean = redact(str(error), self.secrets)
            self.state['stages'][name].update(status='failed', error=clean, finishedAt=utc()); self.save()
            self.say('STOP', clean); self.say('NEXT STEP', advice(error))
            return False
        self.state['stages'][name].update(status='complete', finishedAt=utc()); self.save()
        self.say('DONE', name)
        return True

    def pin(self):
        require(not capture(['git', 'status', '--porcelain']), 'Commit the reviewed changes before a release pass; source must be clean')
        self.revision = capture(['git', 'rev-parse', 'HEAD'])
        remote = capture(['git', 'ls-remote', 'origin', 'refs/heads/master']).split()[0]
        require(self.revision == remote, 'Release source must exactly match pushed origin/master')
        self.tag = release_identity(self.args.version, self.args.build)
        self.state["notes"] = read_release_notes(getattr(self.args, "notes_file", None))
        for record in (ROOT / 'releases').glob('*/release.json'):
            old = json.loads(record.read_text())
            require(self.args.build > int(old['build']) and version_tuple(self.args.version) > version_tuple(old['version']),
                    'Use an increasing build and installer version; an existing release cannot be replaced')
        self.state.update(revision=self.revision, version=self.args.version, build=self.args.build, tag=self.tag)
        self.env.update(AITA_RELEASE_REVISION=self.revision, AITA_RELEASE_VERSION=self.args.version,
            AITA_RELEASE_BUILD=str(self.args.build), AITA_ANDROID_VERSION_NAME=self.args.version,
            AITA_ANDROID_VERSION_CODE=str(self.args.build), AITA_RELEASE_CHANNEL='release',
            AITA_RELEASE_DISTRIBUTION='direct', AITA_RELEASE_BUILT_AT=utc())
        public = SIGNING / 'update-public.txt'
        require(public.is_file(), 'Updater signing public key is missing; run setup-android-signing.py')
        self.env['AITA_UPDATE_PUBLIC_KEY'] = public.read_text().strip()
        self.env['AITA_UPDATE_FEED_BASE'] = 'https://aita-api.bogdan-donduk.workers.dev/client-updates'
        self.say('SOURCE', self.revision)

    def verify(self):
        self.command('Shared, UI and server regression checks', ['bash', './gradlew', ':shared:jvmTest', ':composeApp:jvmTest', ':server:test',
            '--no-daemon', '--no-watch-fs', '--max-workers=4', '--console=plain'])
        self.command('Release automation tests', [sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts/releases', '-p', 'test_*.py'])

    def unchanged(self):
        require(capture(['git', 'rev-parse', 'HEAD']) == self.revision and not capture(['git', 'status', '--porcelain']),
                'Source changed during the build. Verify and rebuild the new commit before publishing')

    def collect(self, path, name, platform, signature):
        require(path.is_file() and not path.is_symlink() and path.stat().st_size > 0, f'Missing artifact {path}')
        require(path.stat().st_size < 2_000_000_000, 'Artifact exceeds release asset limit')
        destination = self.assets / name
        require(not destination.exists(), 'Artifact name collision')
        shutil.copy2(path, destination)
        self.state['artifacts'].append({'name': name, 'sha256': sha(destination), 'bytes': destination.stat().st_size,
            'platform': platform, 'signature': signature,
            'url': f'https://github.com/{REPOSITORY}/releases/download/{self.tag}/{name}'})
        self.save()

    def android(self):
        keys = load_signing(); self.env.update(keys)
        self.secrets += [v for k, v in keys.items() if 'PASSWORD' in k]
        self.command('Build signed APK and AAB', ['bash', './gradlew', ':composeApp:assembleRelease', ':composeApp:bundleRelease',
            '--no-daemon', '--no-watch-fs', '--max-workers=4', '--console=plain'])
        apks = list((ROOT / 'composeApp/build/outputs/apk/release').glob('*.apk'))
        aabs = list((ROOT / 'composeApp/build/outputs/bundle/release').glob('*.aab'))
        require(len(apks) == len(aabs) == 1, 'Expected exactly one release APK and one AAB')
        signers = list((Path(self.env['ANDROID_HOME']) / 'build-tools').glob('*/apksigner'))
        require(signers, 'Android SDK apksigner is missing')
        signer = max(signers, key=lambda p: tuple(int(x) for x in re.findall(r'\d+', p.parent.name)))
        self.command('Verify APK signing integrity', [str(signer), 'verify', '--verbose', '--print-certs', str(apks[0])])
        cert = self.folder / 'android-signing.der'
        self.command('Export public Android certificate', ['keytool', '-exportcert', '-keystore', keys['AITA_ANDROID_KEYSTORE_PATH'],
            '-alias', keys['AITA_ANDROID_KEY_ALIAS'], '-storepass:env', 'AITA_ANDROID_KEYSTORE_PASSWORD', '-file', str(cert)])
        output = capture([str(signer), 'verify', '--print-certs', str(apks[0])])
        fingerprint = sha(cert)
        require(fingerprint in output.lower(), 'APK signer does not match the production keystore')
        self.command('Verify AAB against production signing identity', ['jarsigner', '-verify', '-strict', '-keystore', keys['AITA_ANDROID_KEYSTORE_PATH'],
            '-storepass:env', 'AITA_ANDROID_KEYSTORE_PASSWORD', str(aabs[0]), keys['AITA_ANDROID_KEY_ALIAS']])
        self.unchanged()
        for path in [apks[0], aabs[0]]:
            self.collect(path, f'AITA-{self.args.version}-{self.args.build}-android{path.suffix}', 'android', fingerprint)
        self.collect(ROOT / 'shared/build/generated/aitaClientBuild/client-build.json', 'android-client-build.json', 'android', None)

    def web(self):
        self.command('Build production web app', ['bash', 'scripts/linux-web/run-aita-wasm.sh', '--build-only', '--max-workers', '4'])
        dist = ROOT / 'composeApp/build/dist/wasmJs/productionExecutable'
        archive = self.folder / 'web.zip'
        with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as package:
            for path in sorted(dist.rglob('*')):
                if path.is_file():
                    require(not path.is_symlink() and path.stat().st_size <= 25 * 1024 * 1024, 'Web file exceeds Pages size limit')
                    package.write(path, path.relative_to(dist))
        self.collect(archive, f'AITA-{self.args.version}-{self.args.build}-web.zip', 'web', None)
        self.collect(ROOT / 'shared/build/generated/aitaClientBuild/client-build.json', 'web-client-build.json', 'web', None)
        self.browser_check(dist)
        self.env['CLOUDFLARE_ACCOUNT_ID'] = ACCOUNT
        self.unchanged()
        # --force disables Wrangler's agent-specific Pages-to-Workers delegation. This is a Pages upload.
        self.command('Publish Cloudflare Pages', ['npx', '--yes', 'wrangler@4.118.0', 'pages', 'deploy', str(dist),
            '--project-name', 'aita-web', '--branch', 'master', '--commit-hash', self.revision,
            '--commit-message', f'AITA {self.tag}', '--force'])
        self.state['webUploadCompleted'] = True; self.save()
        self.command('Check aita.kz HTTPS', ['curl', '--fail', '--show-error', '--silent', '--max-time', '30', '--output', '/dev/null', 'https://aita.kz/'])
        self.browser_check()
        self.say('LIVE', 'https://aita.kz/ (HTTPS, app startup, narrow/wide rendering and preference persistence verified)')

    def browser_check(self, dist=None):
        # Use the production origin for API CORS, while routing only static assets locally before upload.
        self.env.setdefault('NODE_PATH', '/tmp/aita-browser-check/node_modules')
        self.env['AITA_WEB_URL'] = 'https://aita.kz/'
        shots = self.folder / ('browser-before-upload' if dist else 'browser-live')
        shots.mkdir(exist_ok=True); self.env['AITA_ARTIFACTS'] = str(shots)
        server = None
        if dist:
            class Handler(http.server.SimpleHTTPRequestHandler):
                extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, '.wasm': 'application/wasm'}
                def log_message(self, *args): pass
            server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Handler, directory=str(dist)))
            threading.Thread(target=server.serve_forever, daemon=True).start()
            self.env['AITA_WEB_LOCAL_ORIGIN'] = f'http://127.0.0.1:{server.server_port}'
        try:
            self.command('Browser smoke check before upload' if dist else 'Browser smoke check on aita.kz',
                         ['node', 'scripts/linux-web/test/app-smoke.cjs'])
        finally:
            self.env.pop('AITA_WEB_LOCAL_ORIGIN', None)
            if server: server.shutdown(); server.server_close()

    def reuse_windows(self):
        run_id = self.args.windows_run
        require(isinstance(run_id, int) and run_id > 0, 'Windows run ID must be a positive integer')
        run = json.loads(capture(['gh', 'run', 'view', str(run_id), '--repo', REPOSITORY,
            '--json', 'headSha,workflowName,displayTitle,status,conclusion']))
        require(run['headSha'] == self.revision and run['workflowName'] == 'Build AITA Windows Release' and
                run['displayTitle'] == f'AITA Windows {self.tag}', 'Existing Windows run does not match this exact source and release identity')
        require(run['status'] in ('queued', 'in_progress') or run['conclusion'] == 'success',
                'Existing Windows run failed; inspect its diagnostics before starting a corrected build')
        self.state['windowsRun'] = run_id; self.save()
        self.say('REUSE', f'Windows run {run_id}; artifact identity, checksums and signing mode will be verified before collection')

    def dispatch_windows(self):
        self.command('Dispatch Windows release build', ['gh', 'workflow', 'run', 'build-windows-release.yml', '--repo', REPOSITORY,
            '--ref', 'master', '-f', f'production={str(self.args.windows_signing == "trusted").lower()}', '-f', f'version={self.args.version}', '-f', f'build={self.args.build}', '-f', f'revision={self.revision}'])
        deadline = time.monotonic() + 120
        run_id = None
        while time.monotonic() < deadline:
            runs = json.loads(capture(['gh', 'run', 'list', '--repo', REPOSITORY, '--workflow', 'build-windows-release.yml',
                '--commit', self.revision, '--limit', '20', '--json', 'databaseId,displayTitle,createdAt']))
            matching = [r for r in runs if r['displayTitle'] == f'AITA Windows {self.tag}' and r['createdAt'] >= self.state['startedAt']]
            if len(matching) == 1:
                run_id = matching[0]['databaseId']; break
            require(len(matching) < 2, 'Multiple Windows runs match; inspect Actions before continuing')
            time.sleep(5)
        require(run_id, 'Windows dispatch result unknown; inspect Actions before retrying')
        self.state['windowsRun'] = run_id; self.save()

    def windows(self):
        if not self.state.get('windowsRun'): self.dispatch_windows()
        run_id = self.state['windowsRun']
        self.command('Wait for Windows installers', ['gh', 'run', 'watch', str(run_id), '--repo', REPOSITORY, '--exit-status', '--interval', '30'])
        downloaded = self.folder / 'windows'
        self.command('Download verified Windows artifacts', ['gh', 'run', 'download', str(run_id), '--repo', REPOSITORY,
            '--name', f'AITA-Windows-{self.tag}' + ('' if self.args.windows_signing == 'trusted' else '-UNSIGNED-PILOT'), '--dir', str(downloaded)])
        receipt = json.loads((downloaded / 'windows-verification.json').read_text())
        require(receipt['revision'] == self.revision and receipt['version'] == self.args.version and int(receipt['build']) == self.args.build,
                'Windows build identity mismatch')
        trusted = self.args.windows_signing == 'trusted'
        require(receipt['production'] == trusted, 'Windows signing mode mismatch')
        self.state['windowsSigning'] = 'trusted Authenticode' if trusted else 'unsigned pilot, explicitly authorized'
        for entry in receipt['artifacts']:
            name = entry['name']
            require(Path(name).name == name and Path(name).suffix.lower() in ('.exe', '.msi'), 'Invalid Windows artifact name')
            path = downloaded / name
            signature_ok = entry['status'] == 'Valid' and bool(entry['timestamp']) if trusted else entry['status'] in ('NotSigned', 'Valid')
            require(signature_ok and sha(path) == entry['sha256'], 'Windows signature or checksum mismatch')
            self.collect(path, name, 'windows', entry['thumbprint'])
        require({Path(e['name']).suffix.lower() for e in receipt['artifacts']} == {'.exe', '.msi'}, 'Both EXE and MSI are required')
        self.collect(downloaded / 'windows-verification.json', 'windows-verification.json', 'windows', None)
        self.collect(downloaded / 'windows-client-build.json', 'windows-client-build.json', 'windows', None)

    def server(self):
        helper = Path('/usr/local/sbin/aita-release-server')
        require(helper.is_file(), 'Server helper not installed')
        self.command('Start existing managed server updater', ['sudo', '-n', str(helper), self.revision])
        self.command('Follow backup, controlled restart and readiness', [sys.executable, 'scripts/linux-field-server/aita-ops.py', 'logs', '--update-only'])
        latest = json.loads(Path('/var/lib/aita-ops/latest.json').read_text())
        folder = Path('/var/lib/aita-ops/runs') / latest['run_id']
        # The existing updater deliberately keeps meta.json root-only. Its root-owned final
        # state is readable by the build owner's group and includes the verified source SHA.
        result = json.loads((folder / 'state.json').read_text())
        require(result.get('commit') == self.revision and result.get('result') == 'success' and result.get('deployment_verified') is True,
                'Server update did not confirm this source revision; inspect updater log')
        self.state['serverRun'] = latest['run_id']; self.save()

    def updater_feed(self):
        catalog = Path('/var/lib/aita-client-releases')
        require(catalog.is_dir() and os.access(catalog, os.W_OK), 'Install the server helper to configure the public updater catalog first')
        require(self.state['stages'].get('server', {}).get('status') == 'complete', 'Verify this server update before publishing its public updater feed')
        entries = []
        for entry in self.state['artifacts']:
            path = self.assets / entry['name']; suffix = path.suffix.lower()
            if suffix not in ('.apk', '.aab', '.msi', '.exe'): continue
            platform = entry['platform']
            require(platform in ('android', 'windows') and sha(path) == entry['sha256'], 'Invalid updater artifact')
            entries.append(dict(os=platform.upper(), kind=suffix[1:].upper(), arch='UNIVERSAL' if platform == 'android' else 'X64',
                minimumOsMajor=24 if platform == 'android' else 10, path=str(path), buildInfo=str(self.assets / f'{platform}-client-build.json'),
                publisherSigned=platform == 'android' or self.state.get('windowsSigning') == 'trusted Authenticode'))
        require(entries, 'No native installers available for the updater feed')
        if self.state.get('webUploadCompleted'):
            entries.append(dict(os='WEB', kind='WEB_RELOAD', url='https://aita.kz/', buildInfo=str(self.assets / 'web-client-build.json')))
        spec = self.folder / 'updater-spec.json'
        spec.write_text(json.dumps(dict(channel='release', id=self.tag, sequence=next_feed_sequence(catalog, self.args.build),
            version=self.args.version, build=self.args.build, notes=self.state["notes"], artifacts=entries), indent=2))
        base = self.env['AITA_UPDATE_FEED_BASE']
        self.command('Publish signed updater metadata and immutable downloads', [sys.executable, 'scripts/releases/publish-client-release.py',
            'publish', '--spec', str(spec), '--build-info', entries[0]['buildInfo'], '--private-key', str(SIGNING / 'update-private.pem'),
            '--catalog', str(catalog), '--base-url', base])
        fetched = self.folder / 'public-release.json'
        self.command('Verify public updater feed over HTTPS', ['curl', '--fail', '--silent', '--show-error', '--max-time', '30',
            '--output', str(fetched), base + '/release.json'])
        require(sha(fetched) == sha(catalog / 'release.json'), 'Public updater feed is stale or differs from the signed publication')
        for entry in entries:
            if 'path' not in entry: continue
            path = Path(entry['path']); destination = self.folder / ('download-check' + path.suffix)
            self.command('Verify public installer download ' + path.suffix, ['curl', '--fail', '--silent', '--show-error', '--max-time', '300',
                '--output', str(destination), base + '/artifacts/' + sha(path) + path.suffix])
            require(sha(destination) == sha(path), 'Public installer download checksum mismatch')
            destination.unlink()
        self.state['updaterFeedPublished'] = True; self.save()

    def publish(self):
        self.unchanged()
        entries = self.state['artifacts']
        require(entries, 'No verified release artifacts to upload')
        require(all(sha(self.assets / e['name']) == e['sha256'] for e in entries), 'Artifact changed after verification')
        manifest = {'version': self.args.version, 'build': self.args.build, 'revision': self.revision,
            'tag': self.tag, 'createdAt': utc(), 'artifacts': entries, 'notes': self.state['notes'],
            'stages': {k: v for k, v in self.state['stages'].items() if k != 'github'}, 'visibility': 'private GitHub repository; downloads require repository access',
            'windowsSigning': self.state.get('windowsSigning'), 'updaterFeedPublished': self.state.get('updaterFeedPublished', False)}
        record = self.folder / 'release.json'; record.write_text(json.dumps(manifest, indent=2) + '\n')
        sums = self.folder / 'SHA256SUMS.txt'; sums.write_text(''.join(f"{e['sha256']}  {e['name']}\n" for e in entries))
        notes = self.folder / 'notes.txt'
        notes.write_text(f'AITA {self.args.version}, build {self.args.build}\nSource: {self.revision}\n\n'
            + self.state['notes']['en'] + '\n\n'
            + '\n'.join(f"{k}: {v['status']}" for k, v in self.state['stages'].items())
            + '\n\nSee release.json for signing verification, checksums and platform status.\n')
        # Create as draft: interrupted uploads cannot expose an incomplete release as complete.
        self.command('Create GitHub draft release', ['gh', 'release', 'create', self.tag, '--repo', REPOSITORY,
            '--target', self.revision, '--title', f'AITA {self.args.version} · build {self.args.build}', '--notes-file', str(notes), '--draft'])
        self.state['githubDraftCreated'] = self.tag; self.save()
        self.command('Upload immutable release assets', ['gh', 'release', 'upload', self.tag, '--repo', REPOSITORY,
            *[str(self.assets / e['name']) for e in entries], str(record), str(sums)])
        remote = draft_release_assets(self.tag, self.revision)
        received = {x['name']: x for x in remote}
        expected = entries + [dict(name=p.name, bytes=p.stat().st_size, sha256=sha(p)) for p in (record, sums)]
        require(set(received) == {e['name'] for e in expected} and all(received[e['name']]['size'] == e['bytes'] and
                received[e['name']].get('digest') == 'sha256:' + e['sha256'] for e in expected), 'Uploaded release asset verification failed')
        self.command('Publish verified GitHub release', ['gh', 'release', 'edit', self.tag, '--repo', REPOSITORY, '--draft=false'])
        destination = ROOT / 'releases' / f'{self.args.version}-build-{self.args.build}'
        destination.mkdir(parents=True, exist_ok=False)
        shutil.copy2(record, destination / 'release.json'); shutil.copy2(sums, destination / 'SHA256SUMS.txt')
        self.say('RECORD', f'{destination}: commit these public checksums and links; binaries stay in GitHub Releases')


def doctor(run):
    for tool in ('git', 'gh', 'java', 'keytool', 'jarsigner', 'python3', 'npx', 'curl'):
        run.say('READY' if shutil.which(tool) else 'MISSING', tool)
    run.say('READY' if (SIGNING / 'android.json').exists() else 'SETUP NEEDED', 'Android signing (APK and AAB)')
    run.say('READY' if Path('/usr/local/sbin/aita-release-server').exists() else 'SETUP NEEDED', 'Restricted server deployment helper')
    run.say('WINDOWS', 'GitHub Windows runner; unsigned pilot authorized, trusted signing optional until rollout')
    run.say('WEB', 'Cloudflare Pages aita-web; aita.kz CNAME -> aita-web.pages.dev; HTTPS checked after upload')
    run.say('DEFERRED', 'macOS and iOS at the owner’s request')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['doctor', 'run'])
    parser.add_argument('--version')
    parser.add_argument('--build', type=int)
    parser.add_argument('--targets', default='android,windows,web,server')
    parser.add_argument('--windows-signing', choices=['pilot', 'trusted'], default='pilot', help='Owner-authorized unsigned pilot now; trusted signing later')
    parser.add_argument('--windows-run', type=int, help='Reuse an existing Windows CI run for this exact source SHA, version and build')
    parser.add_argument('--notes-file', type=Path, help='JSON release notes in EN/RU/KK/KY/TG/UZ; pinned at the start of the pass')
    parser.add_argument('--publish', action='store_true', help='upload completed targets to GitHub Releases')
    args = parser.parse_args()
    run = Run(args)
    try:
        if args.command == 'doctor':
            doctor(run); return 0
        require(args.version and args.build, 'Supply --version and --build')
        targets = args.targets.split(',')
        require(len(targets) == len(set(targets)) and set(targets) <= {'android', 'windows', 'web', 'server'}, 'Unknown/duplicate release target')
        if not run.stage('source', run.pin) or not run.stage('verification', run.verify):
            return 1
        if 'windows' in targets:
            if not run.stage('windows-dispatch', run.reuse_windows if args.windows_run else run.dispatch_windows):
                return 1
        # The remote Windows runner builds while local Android/Pages work proceeds serially.
        ordered = ordered_release_targets(targets)
        results = []
        for target in ordered:
            if target == 'web' and 'server' in targets and run.state['stages'].get('server', {}).get('status') != 'complete':
                run.state['stages']['web'] = {'status': 'held', 'error': 'Compatible server deployment did not succeed'}
                run.save(); run.say('HELD', 'Web publication waits for the compatible server. Local native builds can still finish.')
                results.append(False)
                continue
            results.append(run.stage(target, getattr(run, target)))
        if args.publish and 'server' in targets:
            results.append(run.stage('updater-feed', run.updater_feed))
        if args.publish and all(run.state['stages'].get(t, {}).get('status') == 'complete' for t in targets):
            results.append(run.stage('github', run.publish))
        elif args.publish:
            run.say('HELD', 'GitHub publication waits for every requested client platform to pass. Verified local artifacts and logs are retained.')
        return 0 if all(results) else 1
    except Exception as error:
        run.say('STOP', str(error)); run.say('NEXT STEP', advice(error)); return 1
    finally:
        run.state['finishedAt'] = utc(); run.save()
        run.say('SAVED', run.folder / 'result.json'); run.log.close()


if __name__ == '__main__':
    sys.exit(main())
