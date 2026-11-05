#!/usr/bin/env python3
"""Visible, isolated walkthroughs of the compiled AITA web app. Standard-library runner."""
from __future__ import annotations
import argparse
import http.server
import json
import mimetypes
import os
from pathlib import Path
import secrets
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.parse
import webbrowser

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
CATALOGUE = json.loads((HERE / 'catalogue.json').read_text())



def terminate_owned(process):
    """Close only this launcher's new process group, including surviving child processes."""
    if process.poll() is None:
        try:
            if os.name == 'nt': process.send_signal(signal.CTRL_BREAK_EVENT)
            else: process.terminate()
        except (ProcessLookupError, OSError): pass
        try: process.wait(timeout=12)
        except subprocess.TimeoutExpired:
            if os.name == 'nt': subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], capture_output=True)
            else:
                try: os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError: pass
            process.wait(timeout=5)
    if os.name != 'nt':
        # The leader may already have exited while a fixture child is still alive.
        # Every case was started with start_new_session=True, so this group is ours.
        try: os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError: return
        time.sleep(0.2)
        try: os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError: pass


class Controller:
    def __init__(self, scenarios, output):
        self.lock = threading.RLock()
        self.output = output
        self.paused = False
        self.stopped = False
        self.started = threading.Event()
        self.finished = False
        self.current = None
        self.step = ''
        self.events = []
        self.results = [{**s, 'status': 'pending'} for s in scenarios]

    def event(self, message):
        with self.lock:
            entry = {'time': time.strftime('%H:%M:%S'), **message}
            self.events.append(entry)
            self.events = self.events[-200:]
            with (self.output / 'events.jsonl').open('a', encoding='utf8') as stream:
                stream.write(json.dumps(entry, ensure_ascii=False) + '\n')

    def state(self):
        with self.lock:
            return dict(paused=self.paused, stopped=self.stopped, started=self.started.is_set(),
                        finished=self.finished, current=self.current, step=self.step,
                        results=[dict(item) for item in self.results], events=self.events[-30:])

    def save(self):
        report = {**self.state(), 'kind': 'compiled-ui-with-fixture-api',
                  'productionTouched': False, 'realEmailsSent': False,
                  'limitations': ['Fixture scenarios validate the compiled UI and requests, not the production database.',
                                  'Native Android/iOS/desktop drivers and installers are not exercised by this browser runner.',
                                  'No physical printing, payment capture, SMS or actual mail delivery occurs.',
                                  'Pending/skipped cases are not passes. Use backend.py for disposable PostgreSQL integration.']}
        (self.output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--scenario', action='append', help='Scenario id, repeatable. Default: journey. Use all for the catalogue.')
    parser.add_argument('--list', action='store_true')
    parser.add_argument('--dist', type=Path, default=REPO / 'composeApp/build/dist/wasmJs/productionExecutable')
    parser.add_argument('--headless', action='store_true', help='CI mode; default opens a visible isolated Chromium window.')
    parser.add_argument('--slow-ms', type=int, default=160)
    parser.add_argument('--start', action='store_true', help='Start immediately instead of waiting for dashboard Start.')
    parser.add_argument('--no-dashboard', action='store_true', help='Do not open the control page in your browser.')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.list:
        for item in CATALOGUE:
            print(f"{item['id']:23} {item['title']}")
        return 0
    ids = args.scenario or ['journey']
    unknown = set(ids) - {s['id'] for s in CATALOGUE} - {'all'}
    if unknown:
        parser.error('Unknown scenario(s): ' + ', '.join(sorted(unknown)))
    selected = CATALOGUE if 'all' in ids else [s for s in CATALOGUE if s['id'] in ids]
    dist = args.dist.resolve()
    if not (dist / 'index.html').is_file():
        parser.error(f'Compiled web app missing: {dist}\nBuild once: ./gradlew :composeApp:wasmJsBrowserDistribution')
    if not shutil.which('node'):
        parser.error('Node.js is required. Install the current Node.js LTS and retry.')
    dependencies = REPO / 'scripts/linux-web/test/node_modules'
    node_path = str(dependencies) + os.pathsep + os.environ.get('NODE_PATH', '')
    check = subprocess.run(['node', '-e', "require('playwright');require('playwright-core')"],
                           env={**os.environ, 'NODE_PATH': node_path}, capture_output=True)
    if check.returncode:
        parser.error('Playwright is missing. Run: npm ci --prefix scripts/linux-web/test\nThen: npx --prefix scripts/linux-web/test playwright install chromium')
    output = (args.output or REPO / 'build/guided-tests' / (time.strftime('%Y%m%dT%H%M%S') + '-' + secrets.token_hex(3))).resolve()
    output.mkdir(parents=True, exist_ok=False)
    control = Controller(selected, output)
    token = secrets.token_urlsafe(24)
    dashboard = (HERE / 'dashboard.html').read_text(encoding='utf8')

    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *a, **kw):
            super().__init__(*a, directory=str(dist), **kw)

        def log_message(self, *_):
            pass

        def do_GET(self):
            parsed = urllib.parse.urlparse(self.path)
            if parsed.path == '/control/' + token:
                return self.send_data(dashboard.encode(), 'text/html; charset=utf-8')
            if parsed.path == '/control/' + token + '/state':
                return self.send_data(json.dumps(control.state()).encode(), 'application/json')
            artifact_prefix = '/control/' + token + '/artifacts/'
            if parsed.path.startswith(artifact_prefix):
                relative = urllib.parse.unquote(parsed.path[len(artifact_prefix):])
                artifact = (output / relative).resolve()
                if not artifact.is_relative_to(output) or not artifact.is_file():
                    return self.send_error(404)
                return self.send_data(artifact.read_bytes(), mimetypes.guess_type(artifact)[0] or 'text/plain')
            if parsed.path.startswith('/control/'):
                return self.send_error(404)
            return super().do_GET()

        def send_data(self, data, mime):
            self.send_response(200)
            self.send_header('Content-Type', mime)
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_POST(self):
            path = urllib.parse.urlparse(self.path).path
            if path not in ['/control/' + token + '/command', '/control/' + token + '/step']:
                return self.send_error(404)
            origin = self.headers.get('Origin')
            if origin and origin != server_origin:
                return self.send_error(403)
            try: length = int(self.headers.get('Content-Length', '0'))
            except ValueError: return self.send_error(400)
            if length < 0: return self.send_error(400)
            if length > 8192: return self.send_error(413)
            try:
                data = json.loads(self.rfile.read(length))
            except (ValueError, UnicodeError):
                return self.send_error(400)
            with control.lock:
                if path.endswith('/command'):
                    action = data.get('action')
                    if action == 'start': control.started.set()
                    elif action == 'pause': control.paused = True
                    elif action == 'continue': control.paused = False
                    elif action == 'stop': control.stopped = True; control.started.set()
                    else: return self.send_error(400)
                    control.event({'command': action})
                else:
                    control.step = data.get('label', control.step)
                    if data.get('label') != getattr(control, '_last_label', None) or 'error' in data or 'browserError' in data:
                        control.event(data)
                        control._last_label = data.get('label')
                return self.send_data(json.dumps({'paused': control.paused, 'stopped': control.stopped}).encode(), 'application/json')

    mimetypes.add_type('application/wasm', '.wasm')
    mimetypes.add_type('text/javascript', '.js')
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server_origin = f'http://127.0.0.1:{server.server_port}'
    control_url = server_origin + '/control/' + token
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print('\nAITA · guided test studio', flush=True)
    print('Compiled app: ' + str(dist), flush=True)
    print('Control page: ' + control_url, flush=True)
    print('Evidence: ' + str(output), flush=True)
    print('All accounts/stores are disposable fixtures. External network is blocked. No production changes.', flush=True)
    if not args.no_dashboard:
        webbrowser.open(control_url)
    if args.start or args.headless or args.no_dashboard:
        control.started.set()
    process = None
    try:
        while not control.started.wait(0.2): pass
        for result in control.results:
            if control.stopped:
                result['status'] = 'skipped'; result['reason'] = 'Stopped before scenario'; continue
            while control.paused and not control.stopped: time.sleep(0.2)
            if control.stopped:
                result['status'] = 'skipped'; continue
            scenario = result['id']
            directory = output / scenario
            directory.mkdir()
            result.update(status='running', startedAt=time.time(), evidence=str(directory))
            control.current = scenario
            print('\n▶ ' + result['title'], flush=True)
            control.event({'scenario': scenario, 'status': 'running'})
            # Remove auth/provider variables inherited from a developer shell. No real credentials needed.
            env = {k:v for k,v in os.environ.items() if not k.startswith(('AITA_', 'CLOUDFLARE_', 'AWS_', 'STRIPE_', 'RESEND_'))}
            env.update(NODE_PATH=node_path, NODE_OPTIONS='--require ' + json.dumps(str(HERE / 'preload.cjs')),
                       AITA_WEB_DIST=str(dist), AITA_ARTIFACTS=str(directory), AITA_WEB_TEST_URL=server_origin,
                       AITA_WEB_TEST_OUTPUT=str(directory),
                       AITA_WEB_LOCAL_ORIGIN=server_origin, AITA_GUIDED_CONTROL=control_url,
                       AITA_GUIDED_HEADLESS='1' if args.headless else '0', AITA_GUIDED_SLOW_MS=str(max(0, args.slow_ms)))
            for key, value in result.get('environment', {}).items():
                if not key.startswith('AITA_TEST_') or not isinstance(value, str):
                    raise ValueError('Only string AITA_TEST_* scenario switches are supported')
                env[key] = value
            with (directory / 'console.log').open('w', encoding='utf8') as log:
                process = subprocess.Popen(['node', str((HERE / result['file']).resolve())], cwd=REPO, env=env,
                                           stdout=log, stderr=subprocess.STDOUT,
                                           start_new_session=os.name != 'nt', creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0)
                deadline = time.monotonic() + result.get('timeout', 600)
                while process.poll() is None:
                    if control.paused: deadline += 0.2
                    if control.stopped or time.monotonic() > deadline:
                        terminate_owned(process)
                    time.sleep(0.2)
                terminate_owned(process)
                result.update(status='passed' if process.returncode == 0 else 'stopped' if control.stopped else 'failed',
                              exitCode=process.returncode, durationSeconds=round(time.time()-result['startedAt'], 1))
            result['screenshots'] = [str(p.relative_to(output)).replace(os.sep, '/') for p in sorted(directory.rglob('*.png'))]
            print(('✓ ' if result['status']=='passed' else '✗ ') + result['title'] + ' · ' + result['status'], flush=True)
            control.event({'scenario':scenario,'status':result['status']})
            control.save()
            process = None
    except KeyboardInterrupt:
        control.stopped = True
        if process: terminate_owned(process)
        for result in control.results:
            if result['status']=='running': result['status']='stopped'
            elif result['status']=='pending': result['status']='skipped'
    finally:
        control.finished = True
        control.save()
        # Snapshot remains readable after the localhost controller exits.
        static = dashboard.replace('/*REPORT*/null', json.dumps(control.state()).replace('</', '<\\/'))
        (output / 'report.html').write_text(static, encoding='utf8')
        server.shutdown()
    print('\nReport: ' + str(output / 'report.html'), flush=True)
    print('Fixture accounts/stores and isolated browser contexts are discarded. Production was not contacted.', flush=True)
    return 0 if all(r['status']=='passed' for r in control.results) else 1


if __name__ == '__main__':
    sys.exit(main())
