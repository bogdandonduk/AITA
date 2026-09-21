#!/usr/bin/env python3
"""Exercise an unchanged signed release APK on an isolated Android emulator.
Requires a disposable AITA backend on loopback, an @example.invalid fixture account,
and AITA_TEST_LOGIN / AITA_TEST_PASSWORD. Never uses a production account.
"""
import argparse, hashlib, json, os, re, subprocess, tempfile, zipfile
from pathlib import Path

JAVA = r'''package kz.aita.verification;
import android.app.*;import android.content.*;import android.os.*;import java.lang.reflect.*;
public class LoginProbe extends Instrumentation {
 private Bundle args;
 public void onCreate(Bundle b){args=b;start();}
 public void callApplicationOnCreate(Application app){
  try {Class.forName("kz.aita.CommonMainKt",true,app.getClassLoader()).getMethod("setRuntimeClientServerUrlOverride",String.class).invoke(null,SERVER_URL);}
  catch(Exception e){throw new RuntimeException(e);}
  super.callApplicationOnCreate(app);
 }
 public void onStart(){ Bundle result=new Bundle(); try {
  android.util.Log.i("AitaReleaseProbe","Probe started");
  ClassLoader loader=getTargetContext().getClassLoader();
  Class<?> core=Class.forName("kz.aita.CommonMainKt",true,loader);
  Class<?> platform=Class.forName("kz.aita.AndroidMainKt",true,loader);
  for(int i=0;i<120 && platform.getMethod("getGetStoredUserAuthTokens").invoke(null)==null;i++)Thread.sleep(500);
  core.getMethod("setRuntimeClientServerUrlOverride",String.class).invoke(null,SERVER_URL);
  Intent intent=new Intent();intent.setClassName("kz.aita","kz.aita.android.MainActivity");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  android.util.Log.i("AitaReleaseProbe","Opening activity with isolated backend");
  startActivitySync(intent);Thread.sleep(3000);
  if(!"resume".equals(args.getString("mode"))){
   Class<?> login=Class.forName("kz.aita.UserAuthLogInDataModel",true,loader);
   Class<?> device=Class.forName("kz.aita.ClientDeviceInfoDataModel",true,loader);
   Object request=login.getConstructor(String.class,String.class,device).newInstance(TEST_LOGIN,TEST_PASSWORD,null);
   android.util.Log.i("AitaReleaseProbe","Submitting login");
   core.getMethod("logInUser",login,String.class).invoke(null,request,SERVER_URL);
  }
  Object accountState=core.getMethod("getUserAccountState").invoke(null);
  Object storesState=core.getMethod("getStoresState").invoke(null);
  Object account=null,stores=null;
  for(int i=0;i<240;i++){
   account=accountState.getClass().getMethod("getPayloadValue").invoke(accountState);
   stores=storesState.getClass().getMethod("getPayloadValue").invoke(storesState);
   if(account!=null && stores instanceof java.util.List && !((java.util.List<?>)stores).isEmpty())break;
   Thread.sleep(1000);
  }
  if(account==null || !(stores instanceof java.util.List) || ((java.util.List<?>)stores).isEmpty())throw new IllegalStateException("Authenticated store hydration did not complete; account="+(account!=null)+", stores="+(stores!=null));
  android.util.Log.i("AitaReleaseProbe","Account and stores hydrated");Thread.sleep(30000);
  result.putString("result","Release process hydrated authenticated account and stores and survived 30 seconds");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}'''

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--server-port', type=int, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-[0-9]+', args.serial) or not 10000 <= args.server_port <= 65535:
        parser.error('Use an emulator and a high loopback port for the disposable backend')
    login = os.environ.get('AITA_TEST_LOGIN', '')
    password = os.environ.get('AITA_TEST_PASSWORD', '')
    if not login.endswith('@example.invalid') or not password:
        parser.error('Set AITA_TEST_LOGIN to the disposable @example.invalid account and AITA_TEST_PASSWORD')
    apk = args.apk.resolve(strict=True)
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
    adb = [str(sdk / 'platform-tools/adb'), '-s', args.serial]
    if subprocess.check_output(adb + ['shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() != '1':
        raise RuntimeError('Refusing to clear or install on a physical device')
    args.output.mkdir(parents=True, exist_ok=True)
    config_path = Path.home() / '.config/aita/release-signing/android.json'
    if config_path.stat().st_mode & 0o077:
        raise RuntimeError('Android signing configuration must be private')
    keys = json.loads(config_path.read_text())
    env = dict(os.environ, **keys)
    version = lambda p: tuple(int(v) for v in re.findall(r'\d+', p.name))
    bt = max((p for p in (sdk / 'build-tools').iterdir() if (p / 'apksigner').is_file()), key=version)
    platform = max((p for p in (sdk / 'platforms').iterdir() if (p / 'android.jar').is_file()), key=version) / 'android.jar'
    with (args.output / 'runtime.log').open('w') as log, tempfile.TemporaryDirectory(prefix='aita-android-login-') as temp:
        root = Path(temp)
        def run(argv):
            subprocess.run(list(map(str, argv)), env=env, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=600)
        source = JAVA.replace('SERVER_URL', json.dumps('http://127.0.0.1:' + str(args.server_port)))
        source = source.replace('TEST_LOGIN', json.dumps(login)).replace('TEST_PASSWORD', json.dumps(password))
        (root / 'LoginProbe.java').write_text(source)
        (root / 'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="kz.aita.verification.release"><uses-sdk android:minSdkVersion="24" android:targetSdkVersion="36"/><application android:label="AITA isolated release verification"/><instrumentation android:name="kz.aita.verification.LoginProbe" android:targetPackage="kz.aita" android:functionalTest="true"/></manifest>')
        (root / 'classes').mkdir(); (root / 'dex').mkdir()
        run(['javac', '-source', '11', '-target', '11', '-classpath', platform, '-d', root / 'classes', root / 'LoginProbe.java'])
        run([bt / 'd8', '--min-api', '24', '--lib', platform, '--output', root / 'dex', root / 'classes/kz/aita/verification/LoginProbe.class'])
        run([bt / 'aapt2', 'link', '-I', platform, '--manifest', root / 'AndroidManifest.xml', '-o', root / 'unsigned.apk'])
        with zipfile.ZipFile(root / 'unsigned.apk', 'a') as package:
            package.write(root / 'dex/classes.dex', 'classes.dex')
        run([bt / 'zipalign', '-f', '4', root / 'unsigned.apk', root / 'aligned.apk'])
        run([bt / 'apksigner', 'sign', '--ks', keys['AITA_ANDROID_KEYSTORE_PATH'], '--ks-key-alias', keys['AITA_ANDROID_KEY_ALIAS'], '--ks-pass', 'env:AITA_ANDROID_KEYSTORE_PASSWORD', '--key-pass', 'env:AITA_ANDROID_KEY_PASSWORD', '--out', root / 'probe.apk', root / 'aligned.apk'])
        run(adb + ['reverse', 'tcp:' + str(args.server_port), 'tcp:' + str(args.server_port)])
        run(adb + ['install', '-r', '--no-incremental', str(apk)])
        run(adb + ['install', '-r', '--no-incremental', str(root / 'probe.apk')])
        run(adb + ['shell', 'pm', 'clear', 'kz.aita'])
        run(adb + ['logcat', '-c'])
        for mode in ['login', 'resume']:
            result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-e', 'mode', mode, 'kz.aita.verification.release/kz.aita.verification.LoginProbe'], text=True, capture_output=True, timeout=600)
            (args.output / (mode + '.log')).write_text(result.stdout + result.stderr)
            if result.returncode or 'INSTRUMENTATION_CODE: -1' not in result.stdout or 'hydrated authenticated account and stores' not in result.stdout:
                raise RuntimeError('Signed Android ' + mode + ' failed; see the retained instrumentation log')
        crash = subprocess.check_output(adb + ['logcat', '-d', '-b', 'crash'], text=True)
        (args.output / 'crash.log').write_text(crash)
        if 'Process: kz.aita,' in crash:
            raise RuntimeError('Android runtime recorded an AITA crash')
    result = dict(passed=True, apkSha256=hashlib.sha256(apk.read_bytes()).hexdigest(), serial=args.serial, freshLogin=True, savedAccountRestart=True)
    (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print('PASS: signed APK fresh login, store hydration and saved-account restart')

if __name__ == '__main__':
    main()
