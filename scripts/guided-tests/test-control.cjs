'use strict';
// Small runner-contract test: no compiled app, production account or external service required.
const assert=require('node:assert/strict');
const http=require('node:http');
let paused=false,stopped=false;
const events=[];
const server=http.createServer((request,response)=>{
 if(request.url==='/download') {
  response.setHeader('content-type','text/plain');response.setHeader('content-disposition','attachment; filename=fixture.txt');response.end('isolated-download-fixture');
 } else if(request.url==='/step') {
  let body='';request.on('data',chunk=>body+=chunk);request.on('end',()=>{
   events.push(JSON.parse(body));response.setHeader('content-type','application/json');response.end(JSON.stringify({paused,stopped}));
  });
 } else {response.setHeader('content-type','text/html');response.end('<button onclick="window.clicked=(window.clicked||0)+1">Example</button>');}
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 process.env.AITA_GUIDED_CONTROL='http://127.0.0.1:'+server.address().port;
 process.env.AITA_GUIDED_HEADLESS='1';process.env.AITA_GUIDED_SLOW_MS='0';
 const {guard}=require('./preload.cjs');
 for(const address of ['https://aita.kz','http://127.0.0.1:8080','http://localhost:5432','https://127.0.0.1.evil.test'])assert.throws(()=>guard(address));
 const {chromium}=require('playwright');const browser=await chromium.launch();
 try {
  const page=await browser.newPage();await page.goto(process.env.AITA_GUIDED_CONTROL);
  paused=true;
  const clicking=page.getByRole('button',{name:'Example'}).click();
  await new Promise(resolve=>setTimeout(resolve,400));
  assert.equal(await page.evaluate(()=>window.clicked||0),0,'Pause stops the next user action');
  paused=false;await clicking;
  assert.equal(await page.evaluate(()=>window.clicked),1,'Continue resumes precisely the pending action');
  const downloads=await browser.newContext({acceptDownloads:true});
  const downloadPage=await downloads.newPage();await downloadPage.goto(process.env.AITA_GUIDED_CONTROL);
  await downloadPage.setContent('<a href="/download">Fixture download</a>');
  const [download]=await Promise.all([downloadPage.waitForEvent('download'),downloadPage.getByRole('link',{name:'Fixture download'}).click()]);
  assert.equal(await download.failure(),null);
  assert.equal(require('node:fs').readFileSync(await download.path(),'utf8'),'isolated-download-fixture');
  await downloads.close();
  stopped=true;
  await assert.rejects(page.getByRole('button',{name:'Example'}).click(),/stopped by user/);
  assert.equal(await page.evaluate(()=>window.clicked),1,'Stop does not perform the pending action');
  assert.ok(events.some(event=>event.label.startsWith('click')));
  console.log('PASS: external/arbitrary-loopback fence, explicit isolated file download, Pause, Continue and Stop');
 } finally {await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
