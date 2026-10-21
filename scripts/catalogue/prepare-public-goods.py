#!/usr/bin/env python3
"""Deterministic, disk-backed preparation of licensed public barcode templates.
No network, production credentials, store data or prices are used. Outputs are reviewable.
"""
import argparse,collections,csv,datetime,gzip,hashlib,json,pathlib,re,sqlite3,unicodedata,uuid

SOURCES={
 'uhtt':('Universe-HTT','LicenseRef-Universe-HTT-Free-Use','https://habr.com/ru/articles/420585/','https://github.com/papyrussolution/UhttBarcodeReference/releases/tag/20230913'),
 'food':('Open Food Facts','ODbL-1.0','https://opendatacommons.org/licenses/odbl/1-0/','https://world.openfoodfacts.org/product/'),
 'beauty':('Open Beauty Facts','ODbL-1.0','https://opendatacommons.org/licenses/odbl/1-0/','https://world.openbeautyfacts.org/product/'),
 'petfood':('Open Pet Food Facts','ODbL-1.0','https://opendatacommons.org/licenses/odbl/1-0/','https://world.openpetfoodfacts.org/product/'),
 'usda':('USDA FoodData Central','CC0-1.0','https://creativecommons.org/publicdomain/zero/1.0/','https://fdc.nal.usda.gov/food-details/'),
 'wikidata':('Wikidata','CC0-1.0','https://creativecommons.org/publicdomain/zero/1.0/','https://www.wikidata.org/wiki/'),
}

def text(value):
 if not isinstance(value,str):return ''
 return re.sub(r'\s+',' ',''.join(c for c in unicodedata.normalize('NFC',value) if c not in '\x00\u200b\ufeff')).strip()

def gtin(raw):
 code=text(raw)
 if not re.fullmatch(r'[0-9]{8}|[0-9]{12,14}',code) or len(set(code))<2:return None
 if sum(int(c)*(3 if i%2==0 else 1) for i,c in enumerate(code[-2::-1]))%10 != (10-int(code[-1]))%10:return None
 # Restricted-circulation identifiers are not global product identities.
 ean13=code[-13:] if len(code)==14 and code.startswith('0') else '0'+code if len(code)==12 else code
 if len(ean13)==13 and ean13.startswith(('2','02','04')):return None
 # RCN-8 identifiers can also arrive left-padded to GTIN-12/13/14.
 if code.zfill(14).startswith(('0000000','0000002')):return None
 return ean13

def normal(value):
 value=re.sub(r'^[a-z]{2}:','',text(value).lower()).replace('(folder)','')
 return re.sub(r'[^\w]+',' ',value,flags=re.UNICODE).strip()

def category_id(slug):
 return str(uuid.UUID(bytes=hashlib.md5(('aita:generic-goods-category:'+slug).encode()).digest(),version=3))

class Classifier:
 def __init__(self,schema):
  self.schema=schema;self.exact={};self.cache={}
  for slug,c in schema.items():
   for label in [c['en'],c['ru'],c['en'].split('/')[-1],c['ru'].split('/')[-1]]:
    self.exact.setdefault(normal(label),set()).add(slug)
  # Explicit category synonyms, not loose substring guesses from a product's title.
  aliases={
   'food':'foods|продукты питания',
   'bakery_bread':'breads|breads and buns|bread products|хлебобулочные изделия|хлеб',
   'bakery_cookies':'biscuits|cookies|cookies and biscuits|печенье|печенье и крекеры',
   'bakery_cakes':'cakes|pies|торты|торт|пирожные',
   'bakery_buns':'buns|булочки',
   'dairy_milk':'milks|dairy milk|молоко|молоко и молочные продукты',
   'dairy_cheese':'cheeses|сыр|сыры',
   'dairy_yogurt':'yogurts|йогурт|йогурты',
   'dairy':'dairies|dairy products|молочные продукты',
   'beverages_water':'waters|bottled waters|минеральная вода|вода питьевая|вода минеральная',
   'beverages_juice':'juices|fruit juices|fruit juices and nectars|сок|соки',
   'beverages_tea':'teas|tea|чай',
   'beverages_coffee':'coffees|coffee|кофе',
   'beverages_soda':'sodas|carbonated drinks|soft drinks|газированные напитки',
   'beverages':'drinks|non alcoholic beverages|beverages and beverages preparations|напитки|напитки безалкогольные',
   'snacks_chips':'chips|crisps|salty snacks|чипсы',
   'snacks_nuts':'nuts|орехи',
   'snacks':'snacks|закуски',
   'sweets_chocolate':'chocolates|chocolate products|шоколад',
   'sweets_candy':'candies|sweets|конфеты|карамель',
   'sweets':'confectionery|кондитерские изделия',
   'grocery_pasta':'pastas|pasta|noodles|макаронные изделия|макароны',
   'grocery_rice':'rices|rice|рис',
   'grocery_grains':'cereals|cereal grains|cereals and their products|крупы',
   'grocery_sauces':'sauces|salad dressings and mayonnaise|соусы|майонез|соус',
   'grocery_oil':'vegetable oils|oils|oils edible|растительное масло|масло растительное',
   'grocery_spices':'herbs spices extracts|spices|seasonings|специи|приправы',
   'grocery_sugar':'sugars|sweeteners|сахар',
   'grocery_salt':'salts|salt|соль',
   'grocery_flour':'flours|flour|мука',
   'grocery_canned':'canned foods|canned food|консервы',
   'grocery_breakfast':'breakfast cereals|cereal|сухие завтраки',
   'grocery_staples_broths':'broths|prepared soups|soups|супы|суп',
   'frozen':'frozen foods|замороженные продукты',
   'meat':'meats|meat products|мясо|мясопродукты',
   'meat_sausages':'sausages|колбасы|колбаса',
   'seafood':'seafood|fishes|fish|рыба и морепродукты|рыба',
   'food_fruits':'fruits|фрукты',
   'food_vegetables':'vegetables|овощи',
   'pet_food_cats':'cat foods|cat food|корм для кошек',
   'pet_food_dogs':'dog foods|dog food|корм для собак',
   'pet':'pet foods|pets|pet food|товары для животных|зоотовары',
   'beauty':'beauty products|косметика',
   'beauty_fragrance':'perfumes|fragrances|парфюмерия|туалетная вода|духи',
   'beauty_nails':'nail polish|лак для ногтей|маникюр и педикюр',
   'beauty_makeup_lips':'lipsticks|помада губная',
   'beauty_hair_colour':'hair dyes|краска для волос',
   'personal_care_hair':'shampoos|shampoo|hair care|шампунь|уход за волосами',
   'personal_care_soap':'soaps|soap|мыло',
   'personal_care_oral_toothpaste':'toothpastes|зубная паста|паста зубная',
   'personal_care_oral_mouthwash':'mouthwashes|ополаскиватель для рта',
   'personal_care':'hygiene|гигиена|личная гигиена',
   'health_medicines':'лекарственные средства|лекарство|medicines|pharmaceutical products',
   'health_nutrition':'dietary supplements|food supplements|витамины|бады',
   'books':'books|книги|книга|полиграфия',
   'electronics_audio':'media|медиа',
   'toys':'toys|игрушки|игрушка',
   'toys_building':'building toys|construction toys|конструктор',
   'toys_dolls':'dolls|кукла',
   'toys_plush':'plush toys|мягкая игрушка|игрушка мягкая',
   'stationery':'stationery|канцелярские товары',
   'clothing':'clothing|одежда и обувь|одежда',
   'footwear':'footwear|обувь',
   'household':'household goods|хозтовары|хозяйственные товары|бытовая химия',
   'tools':'tools|инструменты|инструмент',
   'garden':'garden|растениеводство|сад и огород',
   'craft':'товары для творчества|товары для художников|живопись и рисования',
   'party':'товары для праздников|украшение новогоднее',
   'regulated_alcohol':'alcoholic beverages|wines|beers|wine|beer|алкогольные напитки|вино|пиво|водка',
   'regulated':'табакокурение|сигареты',
   'electrical':'электрооборудование|электротехника',
   'electronics':'электроника|техника|electronics',
   'auto':'автотовары|автозапчасти',
   'furniture':'мебель', 'plumbing':'сантехника', 'bags':'сумки|багаж', 'sports':'спорт|спортивные товары',
  }
  missing=[]
  for slug,labels in aliases.items():
   if slug not in schema:missing.append(slug);continue
   for label in labels.split('|'):self.exact[normal(label)]={slug}
  if missing:print('Unmapped synonym targets (ignored):',missing,flush=True)
 def classify(self,values,kind):
  key=(tuple(values),kind)
  if key in self.cache:return self.cache[key]
  choices=[]
  for index,value in enumerate(values):
   tokens=[normal(v) for v in value.split('/')]
   for depth,token in enumerate(tokens):
    matches=self.exact.get(token,set())
    if len(matches)==1:
     slug=next(iter(matches));choices.append((depth,len(self.chain(slug)),index,slug))
  if choices:slug=max(choices)[3]
  else:slug={'food':'food','beauty':'beauty','petfood':'pet','usda':'food'}.get(kind)
  result=self.chain(slug) if slug else []
  if len(self.cache)<250000:self.cache[key]=result
  return result
 def chain(self,slug):
  values=[];seen=set()
  while slug:
   if slug in seen or slug not in self.schema:raise ValueError('Invalid category hierarchy')
   seen.add(slug);values.append(slug);slug=self.schema[slug]['parent']
  return values[::-1]

class Prepare:
 def __init__(self,root,out,date):
  self.root=root;self.out=out;self.date=date;self.stats=collections.Counter()
  self.classifier=Classifier(json.loads((root/'category-schema.json').read_text()))
  self.db=sqlite3.connect(out/'catalogue-stage.sqlite');self.db.execute('PRAGMA journal_mode=WAL');self.db.execute('PRAGMA synchronous=NORMAL')
  self.db.execute('CREATE TABLE IF NOT EXISTS products(gtin TEXT PRIMARY KEY, score INTEGER NOT NULL, origin TEXT NOT NULL, data TEXT NOT NULL) WITHOUT ROWID')
  self.db.execute('CREATE TABLE IF NOT EXISTS completed(source TEXT PRIMARY KEY, digest TEXT NOT NULL, stats TEXT NOT NULL)')
  self.pending=[]
 def accept(self,kind,raw,names,categories,brand='',quantity='',countries=None,url=None,modified=''):
  self.stats[kind+':read']+=1
  code=gtin(raw)
  if not code:self.stats[kind+':invalid_gtin']+=1;return
  clean={lang:text(v) for lang,v in names.items() if text(v) and len(text(v))<=420 and not any(c in text(v) for c in '<>')}
  if not clean:self.stats[kind+':missing_name']+=1;return
  chosen=clean.get('ru',clean.get('en',clean.get('main',next(iter(clean.values())))))
  if chosen.lower() in ('unknown','test','default','product','нет','неизвестно','без названия') or not any(c.isalpha() for c in chosen):
   self.stats[kind+':unusable_name']+=1;return
  if 'main' not in clean:clean['main']=chosen
  else:clean['main']=chosen
  brand=text(brand);quantity=text(quantity)
  for lang,value in list(clean.items()):
   if 1<len(brand)<80 and brand.casefold() not in value.casefold():value=brand+' · '+value
   if 0<len(quantity)<40 and quantity.casefold() not in value.casefold():value+=' · '+quantity
   clean[lang]=value[:500]
  cats=self.classifier.classify(categories,kind)
  name,license,license_url,base=SOURCES[kind]
  source_url=url or (base+str(raw) if kind in ('food','beauty','petfood') else base)
  row={'id':str(uuid.uuid5(uuid.NAMESPACE_URL,'https://aita.kz/catalogue/gtin/'+code.zfill(14))),
   'barcode':[code],'name':[{'language':l,'value':clean[l]} for l in ['main','ru','en','kk','ky','tg','uz'] if l in clean],
   'categoryIds':[category_id(s) for s in cats] or None,'typeIds':None,'supplierIds':None,'manufacturerIds':None,
   'catalogueSource':{'name':name,'url':source_url,'license':license,'licenseUrl':license_url,'retrievedOn':self.date,'countryCodes':countries or []}}
  # Choose a complete source record. Never silently splice names/categories from conflicting products.
  score=10*bool(cats)+15*('ru' in clean)+min(len(clean),5)+{'uhtt':0,'food':20,'beauty':20,'petfood':20,'usda':15,'wikidata':20}[kind]
  if len(chosen)<5:score-=10
  self.pending.append((code.zfill(14),score,kind,json.dumps(row,ensure_ascii=False,separators=(',',':'))))
  self.stats[kind+':accepted']+=1
  if len(self.pending)>=5000:self.flush()
 def flush(self):
  self.db.executemany('INSERT INTO products VALUES(?,?,?,?) ON CONFLICT(gtin) DO UPDATE SET score=excluded.score,origin=excluded.origin,data=excluded.data WHERE excluded.score>products.score',self.pending)
  self.pending=[];self.db.commit()
 def source(self,kind,files,fn):
  if not all(p.exists() for p in files):print('WAITING',kind,flush=True);return
  digest=hashlib.sha256(''.join(hashlib.file_digest(p.open('rb'),'sha256').hexdigest() for p in files).encode()).hexdigest()
  old=self.db.execute('SELECT digest,stats FROM completed WHERE source=?',(kind,)).fetchone()
  if old:
   if old[0]!=digest:raise ValueError('Input changed: use a fresh output directory')
   self.stats.update(json.loads(old[1]));print('ALREADY PREPARED',kind,flush=True);return
  before=self.stats.copy();fn();self.flush()
  delta=self.stats-before;self.db.execute('INSERT INTO completed VALUES(?,?,?)',(kind,digest,json.dumps(dict(delta))));self.db.commit()
  print('PREPARED',kind,dict(delta),flush=True)
 def uhtt(self):
  with (self.root/'uhtt.csv').open(encoding='utf-8-sig') as f:
   assert next(f).rstrip('\r\n').split('\t')==['ID','UPCEAN','Name','CategoryID','CategoryName','BrandID','BrandName']
   for i,line in enumerate(f):
    p=line.rstrip('\r\n').split('\t')
    if len(p)!=7:self.stats['uhtt:malformed']+=1;continue
    title=text(p[2]);names={'main':title}
    if re.search('[А-Яа-яЁё]',title):names['ru']=title
    elif title.isascii():names['en']=title
    self.accept('uhtt',p[1],names,[p[4]],p[6])
    if i and i%500000==0:print('UHTT rows reviewed',i,flush=True)
 def off(self,kind):
  with gzip.open(self.root/(kind+'-source.jsonl.gz'),'rt',encoding='utf8') as f:
   for i,line in enumerate(f):
    p=json.loads(line)
    if p.get('obsolete'):self.stats[kind+':obsolete']+=1;continue
    names={n['lang']:n.get('text','') for n in p.get('product_name') or [] if n.get('lang') in ('main','ru','en','kk','ky','tg','uz')}
    if not names:
     names={'main':next((n['text'] for n in p.get('product_name') or [] if n.get('text')),'')}
    countries=[c for tag,c in [('en:kazakhstan','KZ'),('en:kyrgyzstan','KG'),('en:tajikistan','TJ'),('en:uzbekistan','UZ'),('en:russia','RU')] if tag in (p.get('countries_tags') or [])]
    self.accept(kind,p.get('code',''),names,p.get('categories_tags') or [],p.get('brands') or '',p.get('quantity') or '',countries)
    if i and i%500000==0:print(kind,'rows reviewed',i,flush=True)
 def usda(self):
  # Joining on disk avoids loading the million-row government export in Python memory.
  self.db.execute('CREATE TABLE IF NOT EXISTS usda_names(id TEXT PRIMARY KEY,name TEXT NOT NULL) WITHOUT ROWID')
  if not self.db.execute('SELECT 1 FROM usda_names LIMIT 1').fetchone():
   pending=[]
   with (self.root/'usda-food.csv').open() as f:
    for row in csv.DictReader(f):
     if row['data_type']!='branded_food':continue
     pending.append((row['fdc_id'],row['description']))
     if len(pending)>=10000:self.db.executemany('INSERT OR REPLACE INTO usda_names VALUES(?,?)',pending);pending=[]
   self.db.executemany('INSERT OR REPLACE INTO usda_names VALUES(?,?)',pending);self.db.commit()
  with (self.root/'usda-branded_food.csv').open() as f:
   for i,p in enumerate(csv.DictReader(f)):
    if p.get('discontinued_date'):self.stats['usda:discontinued']+=1;continue
    name=self.db.execute('SELECT name FROM usda_names WHERE id=?',(p['fdc_id'],)).fetchone()
    if not name:continue
    self.accept('usda',p['gtin_upc'],{'en':name[0]},[p['branded_food_category']],p.get('brand_name') or '',p.get('package_weight') or '',
     [{'United States':'US','New Zealand':'NZ'}.get(p['market_country'],p['market_country'])] if p['market_country'] in ('United States','New Zealand') else [],
     SOURCES['usda'][3]+p['fdc_id']+'/nutrients')
    if i and i%500000==0:print('USDA rows reviewed',i,flush=True)
 def wikidata(self):
  rows={}
  for b in json.loads((self.root/'wikidata-gtin.json').read_text())['results']['bindings']:
   key=(b['item']['value'],b['code']['value']);p=rows.setdefault(key,{'names':{},'cats':set()})
   p['names'][b['lang']['value']]=b['name']['value']
   if 'typeName' in b:p['cats'].add(b['typeName']['value'])
  for (url,code),p in rows.items():self.accept('wikidata',code,p['names'],sorted(p['cats']),url=re.sub(r'^https?://www\.wikidata\.org/entity/', 'https://www.wikidata.org/wiki/', url))
 def export(self):
  self.flush();counts=collections.Counter();countries=collections.Counter();excluded=collections.Counter();categorized=0;total=0
  dest=self.out/'open-goods.jsonl.gz';digest=hashlib.sha256()
  with (self.out/'open-goods.jsonl.gz.partial').open('wb') as out,gzip.GzipFile(fileobj=out,mode='wb',filename='',mtime=0,compresslevel=6) as packed:
   for origin,value in self.db.execute('SELECT origin,data FROM products ORDER BY gtin'):
    p=json.loads(value)
    # Apply current identity rules even when resuming a previously prepared stage.
    if gtin(p['barcode'][0]) is None:
     excluded[origin+':excluded_at_export']+=1;continue
    data=(value+'\n').encode();packed.write(data);digest.update(data);counts[origin]+=1;total+=1
    categorized+=bool(p['categoryIds']);countries.update(p['catalogueSource']['countryCodes'])
  (self.out/'open-goods.jsonl.gz.partial').replace(dest)
  manifest={'format':'aita-open-goods-v2','preparedOn':self.date,'rows':total,'categorizedRows':categorized,
   'license':'ODbL-1.0','licenseUrl':'https://opendatacommons.org/licenses/odbl/1-0/',
   'contentsLicense':'Database Contents License 1.0','contentsLicenseUrl':'https://opendatacommons.org/licenses/dbcl/1-0/',
   'uncategorizedRows':total-categorized,'sources':{k:{'name':v[0],'license':v[1],'licenseUrl':v[2],'source':v[3],'rows':counts[k]} for k,v in SOURCES.items() if counts[k]},
   'countries':dict(countries),'filters':{**{key:value for (stats,) in self.db.execute('SELECT stats FROM completed') for key,value in json.loads(stats).items()},**dict(excluded)},'sha256':hashlib.file_digest(dest.open('rb'),'sha256').hexdigest(),
   'uncompressedSha256':digest.hexdigest(),'bytes':dest.stat().st_size,
   'completedInputs':dict(self.db.execute('SELECT source,digest FROM completed')),
   'categorySchema':self.classifier.schema,
   'note':'Public reusable templates only; no inventory, prices, stock, verified availability or automatic translations. Source attribution is retained per row. Uncertain categories remain unset. ODbL source datasets remain separately attributable.'}
  (self.out/'open-goods-notice.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
  print('READY',total,'templates;',categorized,'categorized;',dict(counts),flush=True)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=pathlib.Path,required=True);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--retrieved-on',required=True);p.add_argument('--sources',nargs='+',default=list(SOURCES));p.add_argument('--no-export',action='store_true');a=p.parse_args();datetime.date.fromisoformat(a.retrieved_on);a.output.mkdir(parents=True,exist_ok=True)
 prep=Prepare(a.input,a.output,a.retrieved_on)
 for s in a.sources:
  if s=='uhtt':prep.source(s,[a.input/'uhtt.csv'],prep.uhtt)
  elif s=='usda':prep.source(s,[a.input/'usda-food.csv',a.input/'usda-branded_food.csv'],prep.usda)
  elif s=='wikidata':prep.source(s,[a.input/'wikidata-gtin.json'],prep.wikidata)
  else:prep.source(s,[a.input/(s+'-source.jsonl.gz')],lambda s=s:prep.off(s))
 if not a.no_export:prep.export()
 prep.db.close()
if __name__=='__main__':main()
