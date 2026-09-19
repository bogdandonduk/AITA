"""Exercise the PACKAGED Windows printer entrypoint with an impossible queue. No page is sent."""
import argparse, pathlib, subprocess, tempfile, uuid, time, base64
p = argparse.ArgumentParser(); p.add_argument('--app-dir', required=True); a = p.parse_args()
launcher = pathlib.Path(a.app_dir).resolve() / 'AITA.exe'
assert launcher.is_file(), 'Packaged launcher missing'
for prefix in ["AITA-printer-", "AITA printer Кириллица '"]:
    with tempfile.TemporaryDirectory(prefix=prefix) as temporary:
        folder = pathlib.Path(temporary)
        data = folder / 'test bytes.raw'
        data.write_bytes(bytes([27, 64]))
        arguments = ['AITA-missing-ӘҚ-' + str(uuid.uuid4()), str(data)]
        encoded = [base64.b64encode(value.encode('utf-8')).decode('ascii') for value in arguments]
        started = time.monotonic()
        result = subprocess.run([str(launcher), '--aita-native-print', *encoded],
            capture_output=True, timeout=30)
        text = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        assert 'AITA_PRINT_ERROR:OpenPrinter failed (Win32=1801)' in text, text
        assert 'AITA_PRINT_SUBMITTING:' not in text and 'AITA_PRINT_JOB_STARTED:' not in text, text
        assert result.returncode != 0, text
        print('PASS: Packaged native bridge reached Windows, rejected nonexistent queue without printing; %.2fs' % (time.monotonic() - started))
