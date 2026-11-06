#!/usr/bin/env python3
"""Run existing real-database integration tests in a new disposable local PostgreSQL cluster."""
from __future__ import annotations
import argparse
import getpass
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
DEFAULT_TESTS = [
    'kz.aita.server.auth.AccountDeletionDatabaseTest',
    'kz.aita.server.WorkerPlacementDatabaseTest',
    'kz.aita.server.SupplierProcurementDatabaseTest',
    'kz.aita.server.ReplenishmentDatabaseTest',
    'kz.aita.server.TransactionStockDatabaseTest',
    'kz.aita.server.StoreCommerceDatabaseTest',
    'kz.aita.server.RecordAuditDatesDatabaseTest',
    'kz.aita.server.marketplace.MarketplaceRepositoryDatabaseTest',
    'kz.aita.server.subscriptions.SubscriptionRepositoryDatabaseTest',
]
DB_KEYS = ['AITA_AUTH_TEST_DB_URL', 'AITA_STORE_ARCH_TEST_DB_URL', 'AITA_MARKET_TEST_DB_URL',
           'AITA_BILLING_TEST_DB_URL', 'AITA_RETURN_TEST_JDBC', 'AITA_APP_STATE_TEST_JDBC',
           'AITA_DIAGNOSTICS_TEST_JDBC', 'AITA_PEOPLE_TEST_JDBC', 'AITA_SUPPORT_TEST_DB_URL']


def isolated_database_name():
    # Worker/audit test guards deliberately permit only alphanumerics after aita_test_.
    return 'aita_test_guided' + secrets.token_hex(6)



def collect_results(xml_root, evidence, selected, started_at):
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    incomplete = []
    evidence.mkdir(exist_ok=True)
    for name in selected:
        source = xml_root / ('TEST-' + name + '.xml')
        if not source.is_file() or source.stat().st_mtime < started_at:
            incomplete.append(name + ': missing or stale XML')
            continue
        shutil.copy2(source, evidence / source.name)
        try:
            root = ET.parse(source).getroot()
            values = {key:int(root.attrib.get(key, '0')) for key in totals}
            if values['tests'] <= 0: incomplete.append(name + ': no tests executed')
            for key in totals: totals[key] += values[key]
        except (ET.ParseError, ValueError) as error:
            incomplete.append(name + ': invalid XML ' + str(error))
    complete = not incomplete and totals['tests'] > 0 and not any(totals[k] for k in ('failures', 'errors', 'skipped'))
    return totals, incomplete, complete


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pg-bin', type=Path, help='Directory containing initdb, pg_ctl, createdb (PostgreSQL 16+).')
    parser.add_argument('--test', action='append', choices=DEFAULT_TESTS, help='Repeat to select suites; defaults to all listed suites.')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if hasattr(os, 'geteuid') and os.geteuid() == 0:
        parser.error('Run as your normal development user, not root or sudo.')
    pg = args.pg_bin
    if not pg:
        discovered = shutil.which('initdb')
        if discovered: pg = Path(discovered).parent
        elif Path('/usr/lib/postgresql/16/bin/initdb').is_file(): pg = Path('/usr/lib/postgresql/16/bin')
    exe = '.exe' if os.name == 'nt' else ''
    if not pg or any(not (pg / (tool + exe)).is_file() for tool in ['initdb', 'pg_ctl', 'createdb']):
        parser.error('PostgreSQL tools are required. Ubuntu: install postgresql-16. Windows: install PostgreSQL and pass --pg-bin "C:\\Program Files\\PostgreSQL\\16\\bin".')
    output = (args.output or REPO / 'build/guided-backend' / (time.strftime('%Y%m%dT%H%M%S') + '-' + secrets.token_hex(3))).resolve()
    output.mkdir(parents=True, exist_ok=False)
    data = output / 'postgres'
    # PostgreSQL Unix sockets have a short platform path limit; worktree paths can be long.
    sockets = Path(tempfile.mkdtemp(prefix='aita-guided-pg-'))
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', 0)); port = probe.getsockname()[1]
    database = isolated_database_name()
    username = getpass.getuser()
    env = {k:v for k,v in os.environ.items() if not k.startswith(('AITA_', 'PG', 'AWS_', 'RESEND_', 'STRIPE_'))}
    url = f'jdbc:postgresql://127.0.0.1:{port}/{database}'
    fixed_names = {'AITA_RETURN_TEST_JDBC':'aita_return_test', 'AITA_APP_STATE_TEST_JDBC':'aita_app_state_test',
                   'AITA_DIAGNOSTICS_TEST_JDBC':'aita_diagnostics_test', 'AITA_PEOPLE_TEST_JDBC':'aita_people_test'}
    for key in DB_KEYS:
        env[key] = f'jdbc:postgresql://127.0.0.1:{port}/{fixed_names.get(key, database)}'
    for prefix in ['AITA_AUTH_TEST_DB','AITA_STORE_ARCH_TEST_DB','AITA_MARKET_TEST_DB','AITA_BILLING_TEST_DB','AITA_SUPPORT_TEST_DB']:
        env[prefix+'_USER'] = username; env[prefix+'_PASSWORD'] = ''
    env['AITA_ENV'] = 'test'
    selected = args.test or DEFAULT_TESTS
    print('AITA · disposable PostgreSQL integration', flush=True)
    print('New cluster: ' + str(data), flush=True)
    print('Loopback database: ' + url, flush=True)
    print('Production services, database directories and credentials are not used.', flush=True)
    start_attempted = False
    cleanup_failed = False
    code = 1
    try:
        with (output / 'postgres-setup.log').open('w') as log:
            subprocess.run([str(pg / ('initdb'+exe)), '-D', str(data), '-U', username, '--auth-local=trust', '--auth-host=trust', '--encoding=UTF8', '--no-locale'], env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
            options = f'-h 127.0.0.1 -p {port}'
            if os.name != 'nt': options += f' -k "{sockets}"'
            start_attempted = True
            subprocess.run([str(pg / ('pg_ctl'+exe)), '-D', str(data), '-l', str(output / 'postgres.log'), '-o', options, '-w', 'start'], env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
            for name in [database, *fixed_names.values()]:
                subprocess.run([str(pg / ('createdb'+exe)), '-h', '127.0.0.1', '-p', str(port), '-U', username, name], env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        command = [str(REPO / ('gradlew.bat' if os.name == 'nt' else 'gradlew')), ':server:test', '--rerun-tasks', '--no-daemon']
        for name in selected: command.extend(['--tests', name])
        print('Running real schema, transaction, worker, billing and deletion assertions. See integration.log.', flush=True)
        test_started_at = time.time()
        with (output / 'integration.log').open('w', encoding='utf8') as log:
            process = subprocess.Popen(command, cwd=REPO, env=env, stdout=log, stderr=subprocess.STDOUT,
                                       start_new_session=os.name != 'nt',
                                       creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == 'nt' else 0)
            try: code = process.wait()
            except KeyboardInterrupt:
                if os.name == 'nt': process.terminate()
                else: os.killpg(process.pid, signal.SIGTERM)
                try: process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    if os.name == 'nt': subprocess.run(['taskkill','/PID',str(process.pid),'/T','/F'],capture_output=True)
                    else: os.killpg(process.pid,signal.SIGKILL)
                    process.wait()
                code = 130
        xml_root = REPO / 'server/build/test-results/test'
        totals, incomplete, complete = collect_results(xml_root, output / 'junit', selected, test_started_at)
        if not complete:
            print('Selected suites failed, skipped tests, or lack fresh complete XML; this is not a complete pass.', flush=True)
            for issue in incomplete: print('  '+issue, flush=True)
            if code == 0: code = 1
        (output / 'report.json').write_text(json.dumps({'kind':'real-isolated-postgresql','exitCode':code,'suites':selected,'incompleteSuites':incomplete,**totals,'productionTouched':False,'clusterRemovedAfterRun':False},indent=2))
        print('Results: ' + json.dumps(totals), flush=True)
    except (subprocess.CalledProcessError, OSError) as error:
        print('STOPPED: the isolated test environment could not finish: '+str(error), file=sys.stderr)
        print('Read postgres-setup.log, postgres.log and integration.log in '+str(output), file=sys.stderr)
        code = 1
    except KeyboardInterrupt:
        print('Stopped by user. Closing only the disposable test environment.', file=sys.stderr)
        code = 130
    finally:
        if start_attempted:
            # A failed pg_ctl start can still leave a postmaster running. Check it even when
            # the start command never returned success; never delete a possibly live cluster.
            status = subprocess.run([str(pg / ('pg_ctl'+exe)), '-D', str(data), 'status'], env=env, capture_output=True)
            if status.returncode == 0:
                subprocess.run([str(pg / ('pg_ctl'+exe)), '-D', str(data), '-m', 'immediate', '-w', 'stop'], env=env, capture_output=True)
                status = subprocess.run([str(pg / ('pg_ctl'+exe)), '-D', str(data), 'status'], env=env, capture_output=True)
            cleanup_failed = status.returncode != 3
            if cleanup_failed:
                print('STOPPED: could not confirm PostgreSQL shutdown; preserving '+str(data)+' and socket directory '+str(sockets), file=sys.stderr)
        if not cleanup_failed:
            if data.exists(): shutil.rmtree(data)
            shutil.rmtree(sockets, ignore_errors=True)
            if (output / 'report.json').is_file():
                report=json.loads((output / 'report.json').read_text())
                report['clusterRemovedAfterRun']=True
                (output / 'report.json').write_text(json.dumps(report,indent=2))
            print('Disposable cluster stopped and removed. Logs: '+str(output), flush=True)
    return 1 if cleanup_failed else code


if __name__ == '__main__': sys.exit(main())
