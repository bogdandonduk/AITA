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
