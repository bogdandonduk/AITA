#!/usr/bin/env python3
"""Build the independent ODbL address index from Geofabrik OSM extracts.
Requires osmium 4.3.1 and scipy. Does not contact a geocoding API, mutate production,
or import contributor identities. Run --help. Preserve manifest + ODbL attribution with DB.
"""
import argparse, hashlib, json, math, os, sqlite3, subprocess, time, unicodedata
from pathlib import Path
import osmium
from scipy.spatial import cKDTree
SOURCES = {'KZ':'asia/kazakhstan','KG':'asia/kyrgyzstan','TJ':'asia/tajikistan','UZ':'asia/uzbekistan','RU':'russia'}
LANGS = ('ru','en','kk','ky','tg','uz')
def norm(text):
    return unicodedata.normalize('NFKC', text).lower().replace('ё','е')
def digest(path):
    with path.open('rb') as f: return hashlib.file_digest(f,'sha256').hexdigest()
def position(obj):
    if obj.is_node():
        return (obj.location.lat, obj.location.lon) if obj.location.valid() else None
    if obj.is_way():
        points = [(n.lat, n.lon) for n in obj.nodes if n.location.valid()]
        if len(points) != len(obj.nodes) or not points: return None
        if len(points)>1 and points[0]==points[-1]: points.pop()
        return sum(p[0] for p in points)/len(points), sum(p[1] for p in points)/len(points)
    return None
def xyz(lat,lon):
    a,b=math.radians(lat),math.radians(lon)
    return math.cos(a)*math.cos(b),math.cos(a)*math.sin(b),math.sin(a)
def build_country(db, country, pbf, work):
    places=[]
    # Small locality index lets address points without addr:city display the nearby settlement.
    for obj in osmium.FileProcessor(str(pbf), osmium.osm.NODE).with_filter(osmium.filter.KeyFilter('place')):
        tags=dict(obj.tags)
        if tags.get('place') in ('city','town','village','hamlet') and tags.get('name') and obj.location.valid():
            places.append((obj.location.lat,obj.location.lon,tags['name'],tags.get('name:ru',''),tags.get('name:en','')))
    tree=cKDTree([xyz(p[0],p[1]) for p in places]) if places else None
    index=work/(country.lower()+'-locations.idx')
    if index.exists(): index.unlink() # only this run's temporary node-location index
    count=0; batch=[]
    stream=osmium.FileProcessor(str(pbf)).with_locations('sparse_file_array,'+str(index)).with_filter(osmium.filter.KeyFilter('addr:housenumber','place','highway'))
    for obj in stream:
        if obj.is_relation(): continue # no guessed relation centroids
        tags=dict(obj.tags); house=tags.get('addr:housenumber',''); street=tags.get('addr:street',tags.get('addr:place',''))
        place=tags.get('place','') in ('city','town','village','hamlet','suburb','quarter')
        road=obj.is_way() and tags.get('highway') and tags.get('name')
        if not ((house and street) or (place and tags.get('name')) or road): continue
        pos=position(obj)
        if pos is None: continue
        lat,lon=pos; nearest=''; nearby=[]
        city=tags.get('addr:city', tags.get('addr:town', tags.get('addr:village','')))
        if not city and tree is not None:
            distance,i=tree.query(xyz(lat,lon))
            if distance*6371000 <= 40000:
                city=places[int(i)][2]; nearby=list(places[int(i)][3:]); nearest=city
        title=(street+' '+house).strip() if house else tags['name']
        subtitle=', '.join(dict.fromkeys(x for x in (city,tags.get('addr:district'),tags.get('addr:region'),country) if x and x!=title))
        names={l:tags['name:'+l] for l in LANGS if tags.get('name:'+l)} if not house else {}
        aliases=' '.join([title,subtitle]+list(names.values())+nearby+[tags.get('alt_name',''),tags.get('old_name',''),tags.get('addr:street:ru','')])
        osm_id=('n' if obj.is_node() else 'w')+str(obj.id)
        kind='house' if house else ('street' if road else tags['place'])
        row=(country+':'+osm_id,country,title,subtitle,lat,lon,kind,tags.get('addr:postcode',''),json.dumps(names,ensure_ascii=False),norm(aliases),house,nearest)
        batch.append(row);count+=1
        if len(batch)>=5000:
            db.executemany('INSERT OR IGNORE INTO addresses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',batch);db.commit();batch.clear()
        if count%100000==0: print(f'INDEX {country}: {count:,} addresses/streets/places',flush=True)
    if batch: db.executemany('INSERT OR IGNORE INTO addresses VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',batch);db.commit()
    del stream
    if index.exists(): index.unlink()
    print(f'READY {country}: {count:,} records; approximate locality labels retained separately',flush=True)
    return count

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--countries',nargs='+',choices=SOURCES,default=list(SOURCES));a=p.parse_args()
    a.work.mkdir(parents=True,exist_ok=True);a.output.parent.mkdir(parents=True,exist_ok=True)
    temp=a.output.with_suffix('.building.sqlite'); assert not a.output.exists(), 'Immutable output exists: use a new dated path'
    if temp.exists(): raise RuntimeError('Incomplete build exists; inspect before removing its .building.sqlite file')
    db=sqlite3.connect(temp);db.execute('PRAGMA journal_mode=DELETE');db.execute('PRAGMA cache_size=-32768')
    db.execute('CREATE TABLE addresses(id TEXT PRIMARY KEY,country TEXT NOT NULL,title TEXT NOT NULL,subtitle TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,kind TEXT NOT NULL,postal TEXT NOT NULL,names TEXT NOT NULL,search TEXT NOT NULL,house TEXT NOT NULL,approximate_locality TEXT NOT NULL)')
    manifest={'schema':1,'createdAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'license':'ODbL-1.0','attribution':'© OpenStreetMap contributors','licenseUrl':'https://www.openstreetmap.org/copyright','sources':[],'coverage':'Mapped OSM addresses, named streets and settlements; incomplete. Way coordinates represent geometry centres; nearby locality labels may be approximate.'}
    for country in a.countries:
        url='https://download.geofabrik.de/'+SOURCES[country]+'-latest.osm.pbf'; path=a.work/(country+'.osm.pbf')
        print(f'DOWNLOAD {country}: {url}',flush=True)
        if not path.exists():
            part=path.with_suffix('.part');subprocess.run(['curl','--fail','--location','--retry','5','--retry-delay','5','--connect-timeout','30','--max-time','10800','--output',str(part),url],check=True);os.replace(part,path)
        count=build_country(db,country,path,a.work)
        manifest['sources'].append({'country':country,'url':url,'sha256':digest(path),'bytes':path.stat().st_size,'records':count})
    db.execute('CREATE INDEX addresses_country ON addresses(country)')
    db.execute("CREATE VIRTUAL TABLE address_search USING fts5(search,content=addresses,content_rowid=rowid,tokenize='unicode61 remove_diacritics 2',prefix='2 3 4')")
    db.execute("INSERT INTO address_search(address_search) VALUES ('rebuild')")
    db.execute('CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)');db.execute('INSERT INTO metadata VALUES (?,?)',('manifest',json.dumps(manifest,ensure_ascii=False)))
    db.execute('PRAGMA user_version=1');db.commit()
    assert db.execute('PRAGMA quick_check').fetchone()[0]=='ok'
    print('READY total records:',db.execute('SELECT COUNT(*) FROM addresses').fetchone()[0],flush=True);db.close()
    os.replace(temp,a.output);manifest['databaseSha256']=digest(a.output);manifest['databaseBytes']=a.output.stat().st_size
    a.output.with_suffix('.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print('READY independent address index:',a.output,flush=True)
if __name__=='__main__': main()
