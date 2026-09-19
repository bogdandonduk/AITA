const {test,before,after} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const {chromium} = require('playwright');
const root = path.resolve(__dirname,'../../..');
let server,browser,origin;
before(async()=>{
 server=http.createServer((req,res)=>{
  if(req.url.endsWith('.ttf')){res.setHeader('Content-Type','font/ttf');res.end(fs.readFileSync(path.join(root,'composeApp/src/commonMain/composeResources/font',path.basename(req.url))));}
  else if(req.url==='/aita-print.js'){res.setHeader('Content-Type','text/javascript');res.end(fs.readFileSync(path.join(root,'composeApp/src/wasmJsMain/resources/aita-print.js')));}
  else {res.setHeader('Content-Type','text/html');res.end('<!doctype html><p>Application with unsaved cart</p><script src="/aita-print.js"></script>');}
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));origin='http://127.0.0.1:'+server.address().port;
 browser=await chromium.launch({headless:true});
});
after(async()=>{await browser?.close();await new Promise(r=>server?.close(r));});
test('only prepared document reaches print and document scripts cannot run',async()=>{
 const context=await browser.newContext();
 await context.addInitScript(()=>{window.print=function(){top.printed={text:document.body.innerText,title:document.title,font:getComputedStyle(document.body).fontFamily,fonts:document.fonts.check('12px AITAWebFont')};};});
 const p=await context.newPage();await p.goto(origin);
 await p.evaluate(()=>aitaPrintDocument('Receipt','<!doctype html><meta charset="utf-8"><body>Receipt 125 ₸ Қазақша<script>top.injected=true;window.print()</script></body>'));
 const result=await p.evaluate(()=>({printed, injected:window.injected,app:document.querySelector('p').textContent}));
 assert.equal(result.printed.text,'Receipt 125 ₸ Қазақша');assert.equal(result.printed.title,'Receipt');
 assert.equal(result.printed.fonts,true);assert.match(result.printed.font,/AITAWebFont/);
 assert.equal(result.injected,undefined);assert.equal(result.app,'Application with unsaved cart');
 await context.close();
});
test('print failures reject instead of reporting success',async()=>{
 const context=await browser.newContext();await context.addInitScript(()=>{window.print=()=>{throw Error('disabled');};});
 const p=await context.newPage();await p.goto(origin);
 await assert.rejects(p.evaluate(()=>aitaPrintDocument('Report','<!doctype html><body>Report</body>')),/print-unavailable/);
 assert.equal(await p.locator('iframe').count(),0);await context.close();
});
test('58 and 80 mm receipts keep roll width and trim unused page length; labels keep their own size',async()=>{
 const context=await browser.newContext();
 await context.addInitScript(()=>{window.print=function(){top.printed={html:document.documentElement.outerHTML,rules:[...document.styleSheets].flatMap(s=>[...s.cssRules].map(r=>r.cssText))};};});
 const p=await context.newPage();await p.goto(origin);
 for(const widthMm of [58,80]){
  const width=widthMm*72/25.4;
  await p.evaluate(({width})=>aitaPrintDocument('Receipt','<!doctype html><meta name="aita-receipt-paper" content="'+width+',8"><style>@page{size:'+width+'pt 842pt;margin:8pt}body{width:'+(width-16)+'pt;margin:0}.line{font-size:12pt}</style><body><div class="line">Receipt 125 ₸</div></body>'),{width});
  const printed=await p.evaluate(()=>window.printed);
  const pageRule=printed.rules.filter(r=>r.startsWith('@page')).at(-1);
  assert.match(pageRule,/size:/);assert.ok(!pageRule.includes('842pt'),'receipt should not waste an A4 length');
  const pdfPage=await context.newPage();await pdfPage.setContent(printed.html);await pdfPage.evaluate(()=>document.fonts.ready);
  const pdf=await pdfPage.pdf({preferCSSPageSize:true});
  const media=pdf.toString('latin1').match(/\/MediaBox\s*\[\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*\]/);
  assert.ok(media,'PDF page dimensions');assert.ok(Math.abs(Number(media[1])-width)<1,`${widthMm} mm receipt width preserved`);
  assert.ok(Number(media[2])<100,'short roll receipt should not be A4 height');await pdfPage.close();
 }
 await p.evaluate(()=>aitaPrintDocument('Label','<!doctype html><style>@page{size:58mm 40mm;margin:0}.label{width:58mm;height:40mm}</style><body><div class="label">Adhesive label</div></body>'));
 const label=await p.evaluate(()=>window.printed.rules.filter(r=>r.startsWith('@page')).at(-1));
 assert.match(label,/size: 58mm 40mm/);assert.doesNotMatch(label,/842|595|A4/);await context.close();
});

test('large viewport never lengthens a short receipt; long receipts paginate within the height cap',async()=>{
 const context=await browser.newContext();
 await context.addInitScript(()=>{window.print=function(){top.printed={html:document.documentElement.outerHTML,rules:[...document.styleSheets].flatMap(s=>[...s.cssRules].map(r=>r.cssText))};};});
 const p=await context.newPage();await p.goto(origin);
 for(const lines of [1,250]){
  await p.evaluate(({lines})=>aitaPrintDocument('AITA receipt','<!doctype html><meta name="aita-receipt-paper" content="164.4,8,396"><style>@page{size:164.4pt 396pt;margin:8pt}body{margin:0;width:148.4pt;min-height:10000px}main{display:flow-root}.line{font-size:10pt}</style><body><main id="aita-print-content"><div>aita.kz</div>'+Array.from({length:lines},(_,i)=>'<div class="line">Item '+i+' · 125 ₸</div>').join('')+'</main></body>'),{lines});
  const printed=await p.evaluate(()=>window.printed);
  const rule=printed.rules.filter(r=>r.startsWith('@page')).at(-1);
  const height=Number(rule.match(/size:\s*[\d.]+pt\s+([\d.]+)pt/)[1]);
  assert.ok(height<=396,'all receipts are bounded');if(lines===1)assert.ok(height<100,'viewport excluded from receipt height');
  // Real native-generated receipt HTML does not have a body min-height; remove this artificial probe before pagination.
  const html=printed.html.replace('min-height:10000px','min-height:0');
  const pdfPage=await context.newPage();await pdfPage.setContent(html);await pdfPage.evaluate(()=>document.fonts.ready);
  const pdf=await pdfPage.pdf({preferCSSPageSize:true});const raw=pdf.toString('latin1');
  const count=Number(raw.match(/\/Count\s+(\d+)/)[1]);
  if(lines===1)assert.equal(count,1);else assert.ok(count>1&&count<30,'long receipt paginates without one enormous sheet');
  await pdfPage.close();
 }
 await context.close();
});

test('receipt frame remains rendered inside a Compose-style shadow root',async()=>{
 const context=await browser.newContext();
 await context.addInitScript(()=>{window.print=function(){top.printed={text:document.body.innerText,height:document.getElementById('aita-print-content').getBoundingClientRect().height};};});
 const p=await context.newPage();await p.goto(origin);
 await p.evaluate(()=>{document.body.attachShadow({mode:'open'}).innerHTML='<p>Application with unsaved cart</p>';});
 await p.evaluate(()=>aitaPrintDocument('Receipt','<!doctype html><meta name="aita-receipt-paper" content="164.4,8,396"><style>body{margin:0;width:148.4pt}main{display:flow-root}</style><body><main id="aita-print-content">AITA 125 ₸ Қазақша</main></body>'));
 const printed=await p.evaluate(()=>window.printed);
 assert.match(printed.text,/AITA 125 ₸ Қазақша/);assert.ok(printed.height>0);
 assert.equal(await p.evaluate(()=>document.body.shadowRoot.querySelector('p').textContent),'Application with unsaved cart');
 await p.evaluate(()=>document.body.shadowRoot.querySelector('iframe').contentWindow.dispatchEvent(new Event('afterprint')));
 assert.equal(await p.locator('iframe').count(),0);await context.close();
});

test('native-generated sales and test slips render nonempty, bounded PDF pages through the shadow-root print path',
 {skip:!process.env.AITA_RECEIPT_FIXTURES},async()=>{
 const context=await browser.newContext();
 await context.addInitScript(()=>{window.print=function(){top.printed={html:document.documentElement.outerHTML,text:document.body.innerText,height:document.getElementById('aita-print-content').getBoundingClientRect().height};};});
 const p=await context.newPage();await p.goto(origin);
 await p.evaluate(()=>{document.body.attachShadow({mode:'open'}).innerHTML='<p>Application with unsaved cart</p>';});
 const fixtures=fs.readdirSync(process.env.AITA_RECEIPT_FIXTURES).filter(n=>/^(test|sale)-.*\.html$/.test(n));
 assert.ok(fixtures.some(n=>n.startsWith('sale-'))&&fixtures.some(n=>n.startsWith('test-')));
 for(const name of fixtures){
  const html=fs.readFileSync(path.join(process.env.AITA_RECEIPT_FIXTURES,name),'utf8');
  await p.evaluate(html=>aitaPrintDocument('Receipt',html),html);
  const printed=await p.evaluate(()=>window.printed);assert.ok(printed.height>0);
  if(name.startsWith('sale-')){assert.match(printed.text,/Honey \/ Мёд \/ Бал/);assert.match(printed.text,/Total:/);assert.match(printed.html,/<svg/);}
  else assert.match(printed.text,/aita.kz/);
  const preview=await context.newPage();await preview.setContent(printed.html);await preview.evaluate(()=>document.fonts.ready);
  const pdf=await preview.pdf({preferCSSPageSize:true});await preview.close();
  const raw=pdf.toString('latin1'),pages=Number(raw.match(/\/Count\s+(\d+)/)[1]);assert.ok(pages>=1&&pages<20);
  const height=Number(raw.match(/\/MediaBox\s*\[\s*0\s+0\s+[\d.]+\s+([\d.]+)/)[1]);
  assert.ok(height<=843);if(name.startsWith('test-')){assert.equal(pages,1);assert.ok(height<=397);}
  if(process.env.AITA_RECEIPT_ARTIFACTS){fs.mkdirSync(process.env.AITA_RECEIPT_ARTIFACTS,{recursive:true});fs.writeFileSync(path.join(process.env.AITA_RECEIPT_ARTIFACTS,name.replace('.html','.pdf')),pdf);}
  await p.evaluate(()=>document.body.shadowRoot.querySelector('iframe').contentWindow.dispatchEvent(new Event('afterprint')));
 }
 await context.close();
});
