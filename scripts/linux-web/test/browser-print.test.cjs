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
