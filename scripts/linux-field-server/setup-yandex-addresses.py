#!/usr/bin/env python3
"""Configure AITA's existing server-side Yandex integration without exposing keys."""
import datetime
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import stat
import tempfile
import urllib.error
import urllib.parse
import urllib.request

ENV = Path('/etc/aita/aita-prod.env')
SERVICES = {
 'AITA_YANDEX_GEOSUGGEST_API_KEY': ('Geosuggest', 'https://suggest-maps.yandex.ru/v1/suggest', {'text':'Астана Кабанбай батыра 1', 'lang':'ru', 'countries':'KZ', 'results':'1', 'attrs':'uri'}),
 'AITA_YANDEX_GEOCODER_API_KEY': ('Geocoder', 'https://geocode-maps.yandex.ru/v1/', {'geocode':'Астана Кабанбай батыра 1','format':'json','results':'1','lang':'ru_RU'}),
 'AITA_YANDEX_STATIC_MAPS_API_KEY': ('Static Maps', 'https://static-maps.yandex.ru/v1', {'ll':'71.4304,51.1282','size':'200,150','z':'12','lang':'ru_RU'}),
}
SIGNING = 'AITA_ADDRESS_MAP_SIGNING_SECRET'

def existing_value(text, key):
    matches = re.findall(r'^[ \t]*(?:export[ \t]+)?'+re.escape(key)+r'[ \t]*=[ \t]*(.*?)[ \t]*$', text, re.M)
    if len(matches)>1: raise RuntimeError('Duplicate '+key+' in server configuration; resolve it before setup.')
    if not matches: return ''
    parts = shlex.split(matches[0], comments=True)
    return parts[0] if len(parts)==1 else ''

def usable(value):
    return len(value)>=8 and not any(s in value.lower() for s in ('change_me','changeme','replace_with','placeholder','your_key','your_secret'))

def checked_key(value):
    if not re.fullmatch(r'[A-Za-z0-9_-]{8,200}',value):
        raise RuntimeError('The API key format is invalid. Paste only the key from the Yandex dashboard.')
    return value

def check_service(key, value):
    name, url, params = SERVICES[key]
    request = urllib.request.Request(url+'?'+urllib.parse.urlencode(dict(params, apikey=value)),headers={'User-Agent':'AITA-address-setup/1','Referer':'https://aita.kz/'})
    try:
        with urllib.request.urlopen(request,timeout=20) as response:
            body=response.read(2_000_001)
            if len(body)>2_000_000: raise RuntimeError(name+': response too large; no configuration saved.')
            if key.endswith('STATIC_MAPS_API_KEY'):
                valid=body.startswith(b'\x89PNG\r\n\x1a\n') or body.startswith(b'\xff\xd8\xff')
            else:
                data=json.loads(body)
                valid=isinstance(data,dict) and ('results' in data if key.endswith('GEOSUGGEST_API_KEY') else 'GeoObjectCollection' in data.get('response',{}))
            if not valid: raise RuntimeError(name+': unexpected response; no configuration saved.')
    except urllib.error.HTTPError as error:
        raise RuntimeError(name+': provider returned HTTP '+str(error.code)+'. Check product activation, plan, key restrictions and quota. New keys may need 15 minutes.') from None
    except (urllib.error.URLError,TimeoutError,OSError,json.JSONDecodeError):
        raise RuntimeError(name+': connection or response check failed. No keys are printed and no configuration has been changed.') from None
    print('READY: '+name+' accepted its key')

def updated_text(text, values):
    for key,value in values.items():
        existing_value(text,key)  # Reject ambiguous duplicate settings.
        assignment=key+'='+shlex.quote(value)
        pattern=r'^[ \t]*(?:export[ \t]+)?'+re.escape(key)+r'[ \t]*=.*$'
        if re.search(pattern,text,re.M): text=re.sub(pattern,lambda _:assignment,text,flags=re.M)
        else: text=text.rstrip('\n')+'\n'+assignment+'\n'
    return text

def save_configuration(path, before, values):
    info=path.lstat()
    if not stat.S_ISREG(info.st_mode): raise RuntimeError('Configuration must be a regular file, not a symlink.')
    if path.read_text()!=before: raise RuntimeError('Configuration changed during setup; run again to preserve the newer changes.')
    replacement=updated_text(before,values)
    stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup=path.with_name(path.name+'.before-addresses-'+stamp+'-'+secrets.token_hex(3))
    fd=os.open(backup,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as out:
        out.write(before);out.flush();os.fsync(out.fileno())
    fd,name=tempfile.mkstemp(prefix='.aita-addresses-',dir=path.parent)
    try:
        with os.fdopen(fd,'w') as out:
            os.fchmod(out.fileno(),stat.S_IMODE(info.st_mode)&0o640)
            os.fchown(out.fileno(),info.st_uid,info.st_gid)
            out.write(replacement);out.flush();os.fsync(out.fileno())
        if path.read_text()!=before: raise RuntimeError('Configuration changed during setup; newer configuration preserved.')
        os.replace(name,path)
        directory=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY)
        try: os.fsync(directory)
        finally: os.close(directory)
    finally:
        Path(name).unlink(missing_ok=True)
    return backup

def main():
    if os.geteuid()!=0: raise RuntimeError('Run this setup with sudo so it can update /etc/aita/aita-prod.env. It does not restart AITA.')
    if not os.isatty(0): raise RuntimeError('Run in your interactive Ubuntu terminal; API keys must not be passed as command arguments.')
    if ENV.is_symlink() or not ENV.is_file(): raise RuntimeError('Expected regular server configuration at /etc/aita/aita-prod.env.')
    text=ENV.read_text()
    print('AITA address setup · keys stay on this server, outside Git')
    print('Use Yandex Geosuggest, Geocoder and optional Static Maps keys with a plan allowing address-data storage.')
    allowed=input('Does your plan explicitly permit permanent address/coordinate storage in this app? [yes/NO]: ').strip().lower() == 'yes'
    values={'AITA_YANDEX_STORED_ADDRESS_LICENSE': 'permanent-storage-permitted' if allowed else ''}
    for key,(name,_,_) in SERVICES.items():
        old=existing_value(text,key) or existing_value(text,'AITA_YANDEX_MAPS_API_KEY')
        suffix=' [Enter keeps existing]' if usable(old) else (' [Enter skips map previews]' if 'STATIC_MAPS' in key else '')
        value=getpass.getpass(name+' API key'+suffix+': ').strip()
        if not value and usable(old): value=old
        if not value and 'STATIC_MAPS' in key: continue
        values[key]=checked_key(value)
    for key,value in values.items():
        if key in SERVICES: check_service(key,value)
    old_secret=existing_value(text,SIGNING)
    if 'AITA_YANDEX_STATIC_MAPS_API_KEY' in values and not (len(old_secret)>=32 and usable(old_secret)):
        values[SIGNING]=secrets.token_hex(64)
    backup=save_configuration(ENV,text,values)
    print('READY: Address keys verified and securely saved')
    print('Backup: '+str(backup))
    print('READY: Permanent-address integration '+('enabled for the next managed release' if allowed else 'held inactive; manual address entry remains available'))
    print('No service restarted. Tell the assistant setup succeeded.')

if __name__=='__main__':
    try: main()
    except (RuntimeError,PermissionError) as error:
        print('STOP: '+str(error));raise SystemExit(1)
    except (KeyboardInterrupt,EOFError):
        print('\nStopped before completion.');raise SystemExit(1)
