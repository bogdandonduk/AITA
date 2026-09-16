#!/usr/bin/env python3
"""Validate and publish a local AITA help book and optional screenshots.

This stages files only; it does not SSH, deploy, modify a database or expose an upload API.
Screenshots become PUBLIC when this directory is served. Redact private data first.
Set AITA_HELP_CATALOG_FILE to <output>/tutorials.json and AITA_HELP_SCREENSHOTS_DIR
 to <output>/screenshots on the serving backend. Transfer assets before the JSON.
"""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import tempfile

MAX_BYTES = 2_097_152
MODES = {'STORE', 'BUYER', 'SUPPLIER', 'MANUFACTURER'}
CATEGORIES = {'START','ACCOUNT','STOCK','SALES','MONEY','TEAM','MARKETPLACE','PARTNERS','ORDERS','DEVICES','SETTINGS'}
LANGUAGES = {'en','ru','kk','ky','tg','uz'}
ID = re.compile(r'[a-z][a-z0-9.-]{1,95}')
ASSET = re.compile(r'[0-9a-f]{64}\.(png|jpg|webp)')

def require(value, message):
    if not value: raise ValueError(message)

def text(value, required=True):
    require(isinstance(value, dict) and len(value)<=7, 'Invalid translated text')
    require(not required or (isinstance(value.get('en'),str) and bool(value['en'].strip())), 'English fallback is required')
    for lang, content in value.items():
        require(lang in LANGUAGES and isinstance(content,str) and len(content)<=6000 and
                all(ord(c)>=32 or c in '\n\t' for c in content), 'Invalid text locale, length or control character')

def validate(book):
    require(isinstance(book,dict) and type(book.get('schema')) is int and book['schema']==1, 'Unsupported help schema')
    require(type(book.get('revision')) is int and 1<=book['revision']<=9_007_199_254_740_991, 'Invalid revision')
    tutorials=book.get('tutorials',[]); faqs=book.get('faqs',[])
    require(isinstance(tutorials,list) and 1<=len(tutorials)<=400 and isinstance(faqs,list) and len(faqs)<=300,'Invalid book size')
    ids={}
    for item in tutorials:
        require(ID.fullmatch(item['id']) and item['id'] not in ids, 'Invalid or repeated tutorial ID')
        modes=item['modes']; require(isinstance(modes,list) and modes and set(modes)<=MODES,'Invalid tutorial modes')
        ids[item['id']]=set(modes)
        require(item['category'] in CATEGORIES,'Invalid category')
        text(item['title']); text(item['introduction']); text(item.get('caution',{}),False)
        steps=item['steps']; require(isinstance(steps,list) and 2<=len(steps)<=12,'Each guide needs 2..12 steps')
        seen=set()
        for step in steps:
            require(ID.fullmatch(step['id']) and step['id'] not in seen,'Invalid or repeated step ID'); seen.add(step['id'])
            text(step['text']); images=step.get('screenshots',[]); require(isinstance(images,list) and len(images)<=4,'Too many screenshots')
            for image in images:
                require(ASSET.fullmatch(image['asset']),'Only content-addressed screenshot names are published')
                text(image['alt']); text(image.get('caption',{}),False)
                w=image['width'];h=image['height'];require(type(w) is int and type(h) is int and 1<=w<=6000 and 1<=h<=6000 and w*h<=24_000_000,'Image dimensions too large')
                require(image.get('language') is None or image['language'] in LANGUAGES,'Invalid screenshot language')
    seen=set()
    for faq in faqs:
        require(ID.fullmatch(faq['id']) and faq['id'] not in seen,'Invalid or repeated FAQ ID'); seen.add(faq['id'])
        require(faq['category'] in CATEGORIES,'Invalid FAQ category')
        require(isinstance(faq['modes'],list) and faq['modes'] and set(faq['modes'])<=MODES,'Invalid FAQ modes')
        text(faq['question']);text(faq['answer'])
        link=faq.get('tutorialId'); require(link is None or (link in ids and set(faq['modes'])<=ids[link]),'FAQ points outside its audience')
    return book

def image_dimensions(data):
    if data.startswith(b'\x89PNG\r\n\x1a\n') and len(data)>=24 and data[12:16]==b'IHDR':
        return 'png', *struct.unpack('>II',data[16:24])
    if data.startswith(b'\xff\xd8'):
        i=2
        while i+4<=len(data):
            if data[i]!=255: break
            marker=data[i+1];i+=2
            if marker in (0xD8,0xD9) or 0xD0<=marker<=0xD7: continue
            n=int.from_bytes(data[i:i+2],'big');require(n>=2 and i+n<=len(data),'Invalid JPEG segment')
            if marker in (0xC0,0xC1,0xC2,0xC3,0xC5,0xC6,0xC7,0xC9,0xCA,0xCB,0xCD,0xCE,0xCF):
                require(n>=7,'Invalid JPEG frame');return 'jpg',int.from_bytes(data[i+5:i+7],'big'),int.from_bytes(data[i+3:i+5],'big')
            i+=n
    if len(data)>=30 and data[:4]==b'RIFF' and data[8:12]==b'WEBP':
        kind=data[12:16]
        if kind==b'VP8X': return 'webp',1+int.from_bytes(data[24:27],'little'),1+int.from_bytes(data[27:30],'little')
        if kind==b'VP8 ' and data[23:26]==b'\x9d\x01\x2a': return 'webp',int.from_bytes(data[26:28],'little')&0x3fff,int.from_bytes(data[28:30],'little')&0x3fff
        if kind==b'VP8L' and data[20]==0x2f:
            bits=int.from_bytes(data[21:25],'little');return 'webp',1+(bits&0x3fff),1+((bits>>14)&0x3fff)
    raise ValueError('Screenshot is not a supported PNG, JPEG or WebP image')

def atomic_write(path,data):
    require(not path.is_symlink(),'Refusing a symlinked output')
    fd,name=tempfile.mkstemp(prefix='.aita-help-',dir=path.parent)
    try:
        with os.fdopen(fd,'wb') as f:
            os.chmod(name,0o644);f.write(data);f.flush();os.fsync(f.fileno())
        os.replace(name,path)
    finally:
        Path(name).unlink(missing_ok=True)

def bounded_read(path:Path, limit:int):
    with path.open('rb') as stream: data=stream.read(limit+1)
    require(len(data)<=limit,'Input grew past its size limit')
    return data

def publish(source:Path, output:Path, images:Path|None=None, public_images:bool=False):
    require(source.is_file() and not source.is_symlink() and source.stat().st_size<=MAX_BYTES,'Invalid book file')
    book=json.loads(bounded_read(source,MAX_BYTES).decode('utf-8'));copies={}
    for item in book.get('tutorials',[]):
        for step in item.get('steps',[]):
            for image in step.get('screenshots',[]):
                require(public_images,'Screenshots will be public: redact them and pass --ack-public-screenshots')
                require(images is not None,'--images is required when screenshots are referenced')
                name=image['asset'];require(isinstance(name,str) and re.fullmatch(r'[A-Za-z0-9._-]{1,160}',name),'Screenshot input must be a simple filename')
                root=images.absolute(); file=root/name
                require(root.resolve()==root and file.resolve().parent==root and not file.is_symlink() and file.is_file(),'Screenshot must be inside the canonical image directory')
                require(0<file.stat().st_size<=8_388_608,'Screenshot exceeds 8 MiB')
                data=bounded_read(file,8_388_608);kind,w,h=image_dimensions(data)
                name=hashlib.sha256(data).hexdigest()+'.'+kind
                image.update(asset=name,width=w,height=h);copies[name]=data
    validate(book)
    encoded=(json.dumps(book,ensure_ascii=False,indent=2)+'\n').encode('utf-8');require(len(encoded)<=MAX_BYTES,'Book too large')
    target=output.absolute(); require(target.resolve()==target and not target.is_symlink(),'Use a canonical output directory')
    target.mkdir(parents=True,exist_ok=True); destination=target/'tutorials.json'
    lock=target/'.publish.lock'; fd=os.open(lock,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600);os.close(fd)
    try:
        if destination.exists():
            require(not destination.is_symlink() and destination.stat().st_size<=MAX_BYTES,'Invalid existing book')
            previous=validate(json.loads(destination.read_text(encoding='utf-8')))
            require(book['revision']>previous['revision'],'Increment the revision before replacing a published book')
        folder=target/'screenshots';require(not folder.is_symlink(),'Screenshot directory is symlinked');folder.mkdir(exist_ok=True)
        for name,data in copies.items():
            f=folder/name
            if f.exists(): require(not f.is_symlink() and f.read_bytes()==data,'Immutable screenshot collision')
            else: atomic_write(f,data)
        atomic_write(destination,encoded)
    finally: lock.unlink(missing_ok=True)
    return destination

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--images',type=Path);p.add_argument('--ack-public-screenshots',action='store_true')
    args=p.parse_args()
    try: print(publish(args.input,args.output,args.images,args.ack_public_screenshots))
    except (OSError,ValueError,KeyError,TypeError) as e: p.exit(1,'Help book not published: '+str(e)+'\n')
if __name__=='__main__': main()
