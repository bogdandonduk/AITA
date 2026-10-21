import gzip,hashlib,importlib.util,json,os,pathlib,subprocess,tempfile,unittest,uuid
ROOT=pathlib.Path(__file__).parent

def load(name,file):
 spec=importlib.util.spec_from_file_location(name,ROOT/file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
prep=load('prepare_goods','prepare-public-goods.py');imp=load('import_goods','import-public-goods.py')
SCHEMA=json.loads((ROOT/'category-schema.json').read_text())

def item(code='1234567890128',name='Проверочный товар',id=None):
 return {'id':id or str(uuid.uuid4()),'barcode':[code],'name':[{'language':'ru','value':name}],
  'categoryIds':[prep.category_id('food')], 'catalogueSource':{'name':'Open Food Facts','license':'ODbL-1.0','licenseUrl':'https://opendatacommons.org/licenses/odbl/1-0/','retrievedOn':'2026-10-21','url':'https://world.openfoodfacts.org/product/'+code}}

class PreparationTests(unittest.TestCase):
 def test_normalizes_equivalent_barcodes_and_rejects_invalid_or_local_weight_ids(self):
  self.assertEqual(prep.gtin('012345678905'),'0012345678905');self.assertEqual(prep.gtin('00012345678905'),'0012345678905')
  for code in ['00000000','1234567890129','2000000000015','0200000000008','12x4567890128','1234567']:
   self.assertIsNone(prep.gtin(code),code)
 def test_restricted_short_and_upc_codes_are_rejected_in_every_padded_form(self):
  def checked(prefix):
   return prefix+str((-sum(int(c)*(3 if i%2==0 else 1) for i,c in enumerate(prefix[::-1])))%10)
  mapping={prep.category_id('food'):prep.category_id('food')}
  for prefix in ['0123456','2250376','41234567890','041234567890']:
   raw=checked(prefix)
   for length in [len(raw),12,13,14]:
    code=raw.zfill(length)
    self.assertTrue(imp.valid_gtin(code),code)
    self.assertIsNone(prep.gtin(code),code)
    with self.assertRaises(ValueError):imp.validate(item(code),mapping)
  # Do not reject globally assigned short codes or a GTIN-14 packaging indicator.
  for code in [checked('1234567'),checked('2345678901234')]:
   self.assertIsNotNone(prep.gtin(code));imp.validate(item(code),mapping)
 def test_categories_are_source_based_and_preserve_ancestors(self):
  c=prep.Classifier(SCHEMA)
  self.assertEqual(c.classify(['en:cat-foods'],'petfood'),c.chain('pet_food_cats'))
  self.assertEqual(c.classify(['невнятная категория'],'uhtt'),[])
  self.assertEqual(c.classify(['невнятная категория'],'food'),c.chain('food'))
 def test_source_record_deduplication_keeps_complete_provenance(self):
  with tempfile.TemporaryDirectory() as td:
   root=pathlib.Path(td);(root/'category-schema.json').write_text(json.dumps(SCHEMA));p=prep.Prepare(root,root,'2026-10-21')
   p.accept('uhtt','012345678905',{'en':'Old product'},['toys'])
   p.accept('food','0012345678905',{'ru':'Новый продукт'},['en:milks'])
   p.flush();rows=p.db.execute('SELECT data FROM products').fetchall();self.assertEqual(len(rows),1)
   row=json.loads(rows[0][0]);self.assertEqual(row['catalogueSource']['name'],'Open Food Facts')
   self.assertIn('Новый продукт',[n['value'] for n in row['name']]);p.db.close()
 def test_legacy_category_id_is_mapped_without_renaming_category(self):
  actual=str(uuid.uuid4());schema={'food':SCHEMA['food']}
  self.assertEqual(imp.category_map_for(schema,[{'id':actual,'name':[{'language':'en','value':SCHEMA['food']['en']}]}]),{prep.category_id('food'):actual})
  with self.assertRaises(ValueError):imp.category_map_for(schema,[])
 def test_unknown_sources_and_categories_are_not_imported(self):
  row=item();mapping={prep.category_id('food'):str(uuid.uuid4())}
  self.assertEqual(imp.validate(row,mapping)['categoryIds'],list(mapping.values()))
  with self.assertRaises(ValueError):imp.validate(row,{})
  row['catalogueSource']['license']='unlicensed'
  with self.assertRaises(ValueError):imp.validate(row,mapping)
 def test_import_budget_keeps_room_for_staging_indexes_and_server(self):
  self.assertGreater(imp.required_import_space(9_214_933,530_000_000),40*1024**3)
  self.assertGreaterEqual(imp.required_import_space(0,0),5*1024**3)

@unittest.skipUnless(os.environ.get('AITA_CATALOGUE_IMPORT_TEST_PORT'),'Isolated PostgreSQL required')
class ImportTests(unittest.TestCase):
 def setUp(self):
  port=os.environ['AITA_CATALOGUE_IMPORT_TEST_PORT'];self.assertNotEqual(port,'5432')
  self.env=dict(os.environ,PGHOST='127.0.0.1',PGPORT=port,PGUSER='bogdan',PGDATABASE='aita_public_import_test')
  imp.psql(self.env,"DROP TABLE IF EXISTS open_goods_catalogue,public_catalogue_imports,pilot_stock; CREATE TABLE open_goods_catalogue(id uuid primary key,code text unique,sort_name text,search_text text,payload text); CREATE INDEX ON open_goods_catalogue((lpad(code,14,'0'))); CREATE TABLE public_catalogue_imports(source_key text primary key,row_count int); CREATE TABLE pilot_stock(value text); INSERT INTO pilot_stock VALUES('stock and prices untouched');")
  self.mapping={prep.category_id('food'):str(uuid.uuid4())}
 def perform(self,rows,apply=True,count=None):
  with tempfile.TemporaryDirectory() as td:
   root=pathlib.Path(td);archive=root/'goods.gz'
   with gzip.open(archive,'wt') as f:
    for row in rows:f.write(json.dumps(row)+'\n')
   manifest={'rows':len(rows) if count is None else count,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()}
   return imp.import_rows(self.env,archive,manifest,self.mapping,root/'import.log',apply)
 def test_dry_run_rolls_back_and_rerun_preserves_existing_ids_and_stock(self):
  old=item('012345678905','Old title');new=item('0012345678905','Новый товар')
  self.perform([old],False);self.assertEqual(imp.psql(self.env,'SELECT count(*) FROM open_goods_catalogue'),'0')
  self.perform([old]);self.perform([new]);self.perform([new])
  data=json.loads(imp.psql(self.env,'SELECT payload FROM open_goods_catalogue'))
  self.assertEqual(data['id'],old['id']);self.assertEqual(data['barcode'],old['barcode']);self.assertEqual(data['name'],new['name'])
  self.assertEqual(imp.psql(self.env,'SELECT count(*) FROM open_goods_catalogue'),'1')
  self.assertEqual(imp.psql(self.env,'SELECT value FROM pilot_stock'),'stock and prices untouched')
 def test_bad_last_row_and_wrong_count_never_partially_commit(self):
  good=item();bad=item('1234567890129')
  with self.assertRaises(ValueError):self.perform([good,bad])
  self.assertEqual(imp.psql(self.env,'SELECT count(*) FROM open_goods_catalogue'),'0')
  with self.assertRaises(ValueError):self.perform([good],count=2)
  self.assertEqual(imp.psql(self.env,'SELECT count(*) FROM open_goods_catalogue'),'0')

if __name__=='__main__':unittest.main()
