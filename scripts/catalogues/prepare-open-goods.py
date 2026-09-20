#!/usr/bin/env python3
"""Input rows: code, product_name, brands, countries_tags, categories_tags, quantity, last_modified_t.
Use only the official export and keep the pinned revision file with input rows.
"""
import pathlib,json,uuid,gzip,re,collections,hashlib
import argparse
parser=argparse.ArgumentParser(description="Prepare an attributed, checksum-validated open goods snapshot from official OFF export rows. No live database or network access.")
parser.add_argument('--input',type=pathlib.Path,required=True,help='Directory containing food-products.json / beauty-products.json and off-revision.txt')
parser.add_argument('--output',type=pathlib.Path,required=True,help='Output catalogue resource directory')
parser.add_argument('--retrieved-on',required=True,help='YYYY-MM-DD source retrieval date')
args=parser.parse_args()
import datetime
datetime.date.fromisoformat(args.retrieved_on)
root=args.input;out=args.output
revision=(root/'off-revision.txt').read_text().strip()
if not re.fullmatch('[0-9a-f]{40}',revision):raise ValueError('Expected a pinned official export revision')
out.mkdir(parents=True,exist_ok=True)
def good(code):
 if not re.fullmatch(r'\d{8}|\d{12,14}',code):return False
 if len(code)==13 and code.startswith(('2','02')):return False
 return sum(int(c)*(3 if i%2==0 else 1) for i,c in enumerate(code[-2::-1]))%10 == (10-int(code[-1]))%10 and len(set(code))>1
rows={};stats=collections.Counter()
for kind in ['food','beauty']:
 if not (root/(kind+'-products.json')).exists():continue
 for code,names,brand,countries,categories,quantity,modified in json.load(open(root/(kind+'-products.json'))):
  if not good(code):stats['invalid_gtin']+=1;continue
  localized={n['lang']:re.sub(r'\s+',' ',n['text']).strip() for n in names if n.get('text') and n['lang'] in ('main','en','ru','kk','ky','tg','uz')}
  localized={l:v for l,v in localized.items() if v and len(v)<=420 and '<' not in v and '>' not in v}
  if not localized:stats['missing_name']+=1;continue
  chosen=localized.get('ru',localized.get('en',localized.get('main',next(iter(localized.values())))))
  localized={'main':chosen,**{l:v for l,v in localized.items() if l!='main'}}
  for l,v in list(localized.items()):
   # Preserve pack size and brand supplied by the source, without translating or inferring facts.
   if brand and len(brand)<80 and brand.lower() not in v.lower():v=f'{brand} · {v}'
   if quantity and len(quantity)<40 and quantity.lower() not in v.lower():v=f'{v} · {quantity}'
   localized[l]=v[:500]
  source='Open Food Facts' if kind=='food' else 'Open Beauty Facts';domain='openfoodfacts' if kind=='food' else 'openbeautyfacts'
  row={'id':str(uuid.uuid5(uuid.NAMESPACE_URL,f'https://world.{domain}.org/product/{code}')),'barcode':[code],'name':[{'language':l,'value':v} for l,v in localized.items()],'typeIds':None,'categoryIds':None,'supplierIds':None,'manufacturerIds':None,'catalogueSource':{'name':source,'url':f'https://world.{domain}.org/product/{code}','license':'ODbL-1.0','licenseUrl':'https://opendatacommons.org/licenses/odbl/1-0/','retrievedOn':args.retrieved_on,'countryCodes':[c for tag,c in [('en:kazakhstan','KZ'),('en:tajikistan','TJ'),('en:uzbekistan','UZ'),('en:kyrgyzstan','KG'),('en:russia','RU')] if tag in (countries or [])]}}
  rows.setdefault(code,row);stats[kind]+=1
if not rows:raise ValueError('No valid products; refusing to write an empty catalogue')
payload=''.join(json.dumps(rows[k],ensure_ascii=False,separators=(',',':'))+'\n' for k in sorted(rows)).encode()
(out/'open-goods.jsonl.gz').write_bytes(gzip.compress(payload,mtime=0))
manifest={'retrievedOn':args.retrieved_on,'source':'https://huggingface.co/datasets/openfoodfacts/product-database','revision':revision,'license':'ODbL-1.0','contentsLicense':'Database Contents License 1.0','attribution':'© Open Food Facts contributors; © Open Beauty Facts contributors','rows':len(rows),'sha256':hashlib.sha256(payload).hexdigest(),'inputSha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in root.glob('*-products.json')},'filters':dict(stats),'countryCounts':dict(collections.Counter(c for r in rows.values() for c in r['catalogueSource']['countryCodes']))}
(out/'open-goods-notice.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(manifest)
