#!/usr/bin/env python3
"""Review or atomically import a prepared public catalogue. Never writes store/community inventory.
Production application requires the catalogue-v2 server migration and an encrypted backup.
"""
import argparse,datetime,fcntl,gzip,hashlib,json,os,pathlib,re,shutil,subprocess,sys,tempfile,uuid
from urllib.parse import urlparse,unquote

SOURCES={
 'Open Food Facts':('ODbL-1.0',r'https://world\.openfoodfacts\.org/product/[0-9]+'),
 'Open Beauty Facts':('ODbL-1.0',r'https://world\.openbeautyfacts\.org/product/[0-9]+'),
 'Open Pet Food Facts':('ODbL-1.0',r'https://world\.openpetfoodfacts\.org/product/[0-9]+'),
 'Universe-HTT':('LicenseRef-Universe-HTT-Free-Use',r'https://github\.com/papyrussolution/UhttBarcodeReference/releases/tag/20230913'),
 'USDA FoodData Central':('CC0-1.0',r'https://fdc\.nal\.usda\.gov/food-details/[0-9]+/nutrients'),
 'Wikidata':('CC0-1.0',r'https://www\.wikidata\.org/wiki/Q[0-9]+'),
}

def category_id(slug):
 return str(uuid.UUID(bytes=hashlib.md5(('aita:generic-goods-category:'+slug).encode()).digest(),version=3))

def valid_gtin(code):
 return isinstance(code,str) and bool(re.fullmatch(r'[0-9]{8}|[0-9]{12,14}',code)) and len(set(code))>1 and (sum(int(c)*(3 if i%2==0 else 1) for i,c in enumerate(code[-2::-1]))+int(code[-1]))%10==0

def validate(row,category_map):
 uuid.UUID(row['id']);codes=row.get('barcode')
 if not isinstance(codes,list) or len(codes)!=1 or not valid_gtin(codes[0]): raise ValueError('Invalid global barcode')
 code=codes[0];ean=('0'+code if len(code)==12 else code[1:] if len(code)==14 and code[0]=='0' else code)
 if (len(ean)==13 and ean.startswith(('2','02','04'))) or code.zfill(14).startswith(('0000000','0000002')):
  raise ValueError('Restricted-circulation barcode cannot be a global template')
 names=row.get('name')
 if not isinstance(names,list) or not names or len(names)>7:raise ValueError('Invalid names')
 for name in names:
  if name.get('language') not in ('main','en','ru','kk','ky','tg','uz') or not isinstance(name.get('value'),str) or not name['value'].strip() or len(name['value'])>500 or any(c in name['value'] for c in '\x00\r\n'):raise ValueError('Invalid product name')
 for field in ('supplierIds','manufacturerIds','typeIds'):
  if row.get(field):raise ValueError('Public import cannot invent suppliers, manufacturers or item types')
 cats=row.get('categoryIds') or []
 if len(cats)>20 or any(c not in category_map for c in cats):raise ValueError('Unknown category; refusing to invent it')
 source=row.get('catalogueSource') or {};spec=SOURCES.get(source.get('name'))
 if not spec or source.get('license')!=spec[0] or not re.fullmatch(spec[1],source.get('url','')):raise ValueError('Source attribution is missing or not approved')
 datetime.date.fromisoformat(source['retrievedOn'])
 result=dict(row);result['categoryIds']=[category_map[c] for c in cats] or None
 return result

def psql(env,sql):
 result=subprocess.run(['psql','-X','-qAt','-w','-v','ON_ERROR_STOP=1'],input=sql,text=True,capture_output=True,env=env,timeout=60)
 if result.returncode:raise RuntimeError('Database check failed: '+result.stderr[-2500:])
 return result.stdout.strip()

def category_map_for(schema,rows):
 result={}
 existing_ids={row['id'] for row in rows}
 for slug,category in schema.items():
  seed=category_id(slug)
  # Seed identity survives hierarchical display labels and translations.
  if seed in existing_ids:
   result[seed]=seed
   continue
  matches=[row['id'] for row in rows if any(n['language']=='en' and n['value'].strip().casefold()==category['en'].strip().casefold() for n in row['name'])]
  if len(matches)==1:result[seed]=matches[0]
  else:raise ValueError('Category missing or ambiguous: '+slug+'. Activate the prepared catalogue server release first.')
 return result

def copy_field(value):return str(value).replace('\\','\\\\').replace('\t','\\t').replace('\n','\\n').replace('\r','\\r')

MERGE_SQL=r'''
CREATE UNIQUE INDEX ON aita_goods_stage(code);
CREATE INDEX ON aita_goods_stage((lpad(code,14,'0')));
ANALYZE aita_goods_stage;
-- Existing public IDs and exact barcode strings survive; stock references never change.
UPDATE open_goods_catalogue old SET sort_name=s.sort_name, search_text=old.id::text || ' ' || old.code || ' ' || s.search_text,
 payload=jsonb_set(jsonb_set(s.payload::jsonb,'{id}',to_jsonb(old.id::text)),'{barcode}',jsonb_build_array(old.code))::text
FROM aita_goods_stage s WHERE lpad(old.code,14,'0')=lpad(s.code,14,'0')
 AND (old.payload::jsonb - 'id' - 'barcode') IS DISTINCT FROM (s.payload::jsonb - 'id' - 'barcode');
INSERT INTO open_goods_catalogue(id,code,sort_name,search_text,payload)
SELECT s.id,s.code,s.sort_name,s.id::text || ' ' || s.code || ' ' || s.search_text,s.payload FROM aita_goods_stage s
WHERE NOT EXISTS(SELECT 1 FROM open_goods_catalogue old WHERE lpad(old.code,14,'0')=lpad(s.code,14,'0'));
ANALYZE open_goods_catalogue;
'''

def import_rows(env,archive,manifest,mapping,log_path,apply):
 # COPY is streamed; even a multi-million-row import never builds a giant SQL string in memory.
 with log_path.open('w') as log:
  process=subprocess.Popen(['psql','-X','-qAt','-w','-v','ON_ERROR_STOP=1'],stdin=subprocess.PIPE,stdout=log,stderr=log,text=True,env=env)
  count=0
  try:
   out=process.stdin
   out.write("BEGIN; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='30min';\nSELECT pg_advisory_xact_lock(hashtextextended('aita:public-goods-import',0));\n")
   out.write('CREATE TEMP TABLE aita_goods_stage(id uuid,code text,sort_name text,search_text text,payload text) ON COMMIT DROP;\nCOPY aita_goods_stage FROM STDIN;\n')
   with gzip.open(archive,'rt',encoding='utf-8') as source:
    for line in source:
     if len(line)>65536:raise ValueError('Oversized product record')
     row=validate(json.loads(line),mapping);title=row['name'][0]['value'].lower();search=' '.join(n['value'] for n in row['name']).lower()
     values=[row['id'],row['barcode'][0],title,search,json.dumps(row,ensure_ascii=False,separators=(',',':'))]
     out.write('\t'.join(copy_field(v) for v in values)+'\n');count+=1
     if count%500000==0:print(f'CHECKED · {count:,} templates streamed; store stock remains untouched',flush=True)
   if count!=manifest['rows']:raise ValueError('Row count differs from the reviewed manifest')
   out.write('\\.\n'+MERGE_SQL)
   key='expanded-public-goods:'+manifest['sha256'];assert re.fullmatch(r'expanded-public-goods:[a-f0-9]{64}',key)
   out.write(f"INSERT INTO public_catalogue_imports(source_key,row_count) VALUES('{key}',{count}) ON CONFLICT(source_key) DO NOTHING;\n")
   out.write("SELECT json_build_object('catalogueRows',count(*)) FROM open_goods_catalogue;\n")
   out.write('COMMIT;\n' if apply else 'ROLLBACK;\n');out.close()
   if process.wait(timeout=3600):raise RuntimeError('Import rolled back. Read the saved import log; no partial import was committed.')
  except BaseException:
   # Closing COPY input on any validation error makes the backend roll back the transaction.
   if process.stdin and not process.stdin.closed:process.stdin.close()
   try:process.wait(timeout=20)
   except subprocess.TimeoutExpired:process.terminate();process.wait(timeout=20)
   raise
 return count

def production_environment():
 cfg={}
 for raw in pathlib.Path('/etc/aita/aita-prod.env').read_text().splitlines():
  line=raw.strip()
  if not line or line.startswith('#'):continue
  if line.startswith('export '):line=line[7:].lstrip()
  key,value=line.split('=',1);key=key.strip()
  if len(value)>=2 and value[0]==value[-1] and value[0] in "\"'":value=value[1:-1]
  if key in cfg:raise ValueError('Duplicate server environment key')
  cfg[key]=value
 target=urlparse(cfg['AITA_DB_URL'].removeprefix('jdbc:'))
 if target.scheme!='postgresql' or target.hostname not in ('127.0.0.1','localhost') or target.query:raise ValueError('Expected the configured local database')
 return {'PATH':'/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin','LANG':'C.UTF-8','PGHOST':target.hostname,
  'PGPORT':str(target.port or 5432),'PGDATABASE':unquote(target.path[1:]),'PGUSER':cfg['DB_USER'],'PGPASSWORD':cfg['DB_PASS'],
  'PGCONNECT_TIMEOUT':'10','PGAPPNAME':'aita-public-goods-import'}

def required_import_space(rows,archive_bytes):
 # The isolated 200k-row benchmark uses about 1.5 kB/row including indexes.
 # Reserve 3x that for COPY staging, index growth and WAL, plus a 5 GiB floor.
 return max(0,rows)*4500+max(0,archive_bytes)*2+5*1024**3

def check_import_space(env,manifest):
 clusters=json.loads(subprocess.check_output(['pg_lsclusters','--json'],text=True,timeout=10))
 matches=[c for c in clusters if str(c['port'])==env['PGPORT']]
 if len(matches)!=1:raise RuntimeError('Cannot identify the local PostgreSQL data volume; no import was started.')
 volume=pathlib.Path(matches[0]['pgdata']);free=shutil.disk_usage(volume).free
 required=required_import_space(manifest['rows'],manifest['bytes'])
 print(f'STORAGE · {free/1024**3:.1f} GiB free on the database volume; {required/1024**3:.1f} GiB reserved for this import and continued server operation.',flush=True)
 if free<required:raise RuntimeError('Not enough safe working space for this catalogue import. Keep the prepared archive, free temporary build/download space or expand the database volume, then retry. No database rows were changed.')

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--prepared',type=pathlib.Path,required=True);p.add_argument('--repo',type=pathlib.Path,default=pathlib.Path('/home/bogdan/IdeaProjects/AITA'));p.add_argument('--apply',action='store_true');a=p.parse_args()
 manifest=json.loads((a.prepared/'open-goods-notice.json').read_text());archive=a.prepared/'open-goods.jsonl.gz'
 if manifest['format']!='aita-open-goods-v2' or not 1<=manifest['rows']<=50_000_000:raise ValueError('Unsupported catalogue manifest')
 print('AITA · Verifying the complete catalogue archive…',flush=True)
 with archive.open('rb') as f:checksum=hashlib.file_digest(f,'sha256').hexdigest()
 if checksum!=manifest['sha256']:raise ValueError('Archive checksum mismatch; nothing was changed')
 if os.geteuid()!=0:raise RuntimeError('Production database checks require running this prepared script with sudo. No passwords belong in the command.')
 env=production_environment()
 mapping=category_map_for(manifest['categorySchema'],json.loads(psql(env,"SELECT coalesce(json_agg(json_build_object('id',id,'name',name)),'[]') FROM generic_goods_categories;")))
 migrated=psql(env,"SELECT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='137' AND success);")
 if migrated!='t':raise RuntimeError('The catalogue-v2 server release is not active. Keep the prepared files; import after the held release is approved and activated.')
 run=a.prepared/('import-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'));run.mkdir(mode=0o700)
 print(f"REVIEW · {manifest['rows']:,} global templates; {manifest['categorizedRows']:,} have source-based categories. Store goods, prices and quantities will not change.",flush=True)
 check_import_space(env,manifest)
 if not a.apply:
  print('READY · Archive, category mapping and server schema checked. Add --apply to back up and import.');return
 public_root=pathlib.Path('/var/lib/aita/catalogues');public_root.mkdir(parents=True,exist_ok=True)
 # Keep the database commit and the public source archive in the same import order.
 lease=(public_root/'import.lock').open('a')
 try:fcntl.flock(lease,fcntl.LOCK_EX|fcntl.LOCK_NB)
 except BlockingIOError:raise RuntimeError('Another catalogue import is running; wait for its saved result instead of starting a duplicate.')
 current=public_root/'current'
 if current.exists() and not current.is_symlink():raise RuntimeError('The public catalogue pointer is not a symlink. Resolve that path before importing; nothing was changed.')
 print('BACKUP · Creating the existing encrypted local database backup before import…',flush=True)
 with (run/'backup.log').open('w') as log:
  status=subprocess.run(['bash',str(a.repo/'scripts/linux-field-server/backup-aita-postgres.sh'),'--daily','--local-only'],stdout=log,stderr=subprocess.STDOUT,timeout=900)
 if status.returncode:raise RuntimeError('Backup failed; database import was not started. See the saved backup log.')
 version=public_root/checksum;version.mkdir(exist_ok=True)
 for filename in ('open-goods.jsonl.gz','open-goods-notice.json'):
  dest=version/filename;temporary=version/(filename+'.partial')
  shutil.copyfile(a.prepared/filename,temporary);temporary.chmod(0o644)
  with temporary.open('rb') as f:os.fsync(f.fileno())
  temporary.replace(dest)
 count=import_rows(env,archive,manifest,mapping,run/'database.log',True)
 # Public ODbL derivative and provenance are downloadable from the server's catalogue route.
 link=public_root/('current-'+str(os.getpid()));link.symlink_to(version.name,target_is_directory=True)
 try:link.replace(current)
 except OSError as error:
  (run/'result.json').write_text(json.dumps({'rows':count,'sha256':checksum,'databaseCommitted':True,'publicDirectory':str(version),'archivePointerUpdated':False},indent=2)+'\n')
  raise RuntimeError(f'Database import committed, but publishing its source archive pointer failed. Files are preserved at {version}; rerunning the same import is safe. See {run}/result.json.') from error
 receipt={'rows':count,'sha256':checksum,'finishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'publicDirectory':str(version)}
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n')
 print(f'COMPLETE · {count:,} global templates imported. Public source archive and attribution installed. No service was restarted. Log: {run}',flush=True)

if __name__=='__main__':
 try:main()
 except Exception as error:print('STOP · '+str(error),file=sys.stderr);sys.exit(1)
