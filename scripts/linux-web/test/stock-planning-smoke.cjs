// Synthetic accounts and intercepted API only. Selected source files never leave this test browser.
const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),http=require('node:http'),path=require('node:path');
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'build/pass036/stock-planning-ui';
const browserTimeZone=process.env.AITA_TEST_TIMEZONE||'Asia/Almaty';
if(!dist)throw Error('Set AITA_WEB_DIST to the compiled app');fs.mkdirSync(out,{recursive:true});
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value},{language:'main',value}];
const account={id:id(99),publicId:'PLANNING-TEST',phoneNumber:'',email:'planner@example.test',firstName:'Planner',lastName:'Test',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',activeStoreId:id(31)};
const store={id:id(31),publicId:'STORE-31',parentStoreId:id(30),userIds:[account.id],storeTypeIds:[],name:tr('Planning branch'),alias:[],description:[],companyForms:[],location:{},address:'Test only',phoneNumbers:[],emails:[],countryLocales:['kz'],createdAt:1,branchType:'PHYSICAL',architectureVersion:2};
const parent={...store,id:id(30),publicId:'STORE-30',parentStoreId:null,name:tr('Planning parent'),branchType:null,branches:[store]};
const unit={id:'0',immutableUnitName:tr('pc.'),total:1,pricedAmount:1,roundTotal:true},price={price:'100',currency:'KZT',supplierId:''};
const goods={id:id(3100),userId:account.id,storeId:store.id,name:tr('Planning tea'),barcodes:['4006381333931'],measurementUnitId:'0',salePrices:[{...price,price:'150'}],supplyPrices:[price],isActive:true};
const supplier={id:id(700),userIds:[],name:tr('Fixture supplier'),categoryIds:[],addedAt:1,isActive:true};
let addedItems=[],addedBatches=[],orderBodies=[],offline=false,loseOrderReply=false,plannerDenied=false;
let apiCalls=[],errors=[],consoleMessages=[],serverOrders=[],reminderWrites=[];
const importTimings=[];
let reminder={storeId:store.id,revision:0,enabled:false,timeZoneId:'UTC',daysBeforeOrder:3};
const day=86400000,now=Date.now(),snapshot={storeId:store.id,generatedAtMillis:now,startMillis:now-28*day,endMillisExclusive:now-day,lookbackDays:28,timeZoneId:'UTC',items:[{goodsItem:goods,unit,sold:56,returned:0,observationDays:28,saleDays:14,stock:[{quantity:4}],incoming:[],unlimited:false,uncertainUnits:false}]};
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':p.endsWith('.json')?'application/json':p.endsWith('.png')?'image/png':p.endsWith('.ttf')?'font/ttf':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');
(async()=>{
await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
const browser=await chromium.launch({headless:true,channel:'chromium',args:['--enable-unsafe-swiftshader']});
const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US',timezoneId:browserTimeZone,acceptDownloads:true});
await context.addInitScript(account=>{if(!['http:','https:'].includes(location.protocol))return;localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-planner-token',refreshToken:'isolated-planner-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-planner-test');},account);
// Inspect the real prepared document, including loaded fonts, before producing a test PDF copy.
// This does not pretend a physical printer or a user-operated Save as PDF dialog succeeded.
await context.addInitScript(()=>{window.print=function(){top.__planningPrints=top.__planningPrints||[];top.__planningPrints.push({text:document.body.innerText,html:document.documentElement.outerHTML,fonts:document.fonts.check('12px AITAWebFont')});};});
await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const req=route.request(),u=new URL(req.url());
 if(u.origin===origin)return route.continue();
 if(u.hostname==='aita.kz'&&!u.pathname.startsWith('/api/')){try{const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}catch{return route.abort();}}
 const p=u.pathname.replace(/^\/api\//,'/'),headers=req.headers();apiCalls.push({path:p,method:req.method()});
 if(offline)return route.abort('internetdisconnected');
 let payload=[],status=200;const body=()=>req.postDataJSON();
 if(p==='/auth/refresh')payload={accessToken:'isolated-planner-token',refreshToken:'isolated-planner-refresh',expiresAtMillis:now+3600000};
 else if(p==='/auth/session'||p==='/auth/ping')payload={};
 else if(p==='/auth/capabilities')payload={enabled:true,emailCodeLoginEnabled:true};
 else if(p==='/user/get'||p==='/user/update')payload=account;
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state')payload={state:{revision:0,enabled:false}};
 else if(p==='/stores/get')payload=[parent];
 else if(p==='/stock/get')payload=[goods,...addedItems].filter(x=>x.storeId===(headers.store_id||store.id));
 else if(p==='/stockBatches/get')payload=[{id:id(3101),goodsItemId:goods.id,storeId:store.id,quantity:{...unit,total:4},supplyPrice:price,status:'OnShelf',isActive:true},...addedBatches];
 else if(p==='/stock/add'){const b=body();assert.ok(!addedItems.some(x=>x.id===b.id),'No second create for an imported item');addedItems.push(b);payload=b;}
 else if(p==='/stockBatches/add'){const values=body();for(const b of values){assert.ok(!addedBatches.some(x=>x.id===b.id),'No duplicate imported batch');addedBatches.push(b);}payload=values;}
 else if(p==='/suppliers/get')payload=[supplier];
 else if(p==='/supplierOrders/get')payload=serverOrders;
 else if(p==='/supplierOrderLines/get')payload=orderBodies.flatMap(b=>b.lines);
 else if(p==='/supplierOrders/add'){
   const b=body();orderBodies.push(b);payload=serverOrders.find(x=>x.order.id===b.order.id);
   if(!payload){payload={...b,lines:b.lines.map((line,i)=>({...line,id:id(800+i),orderId:b.order.id}))};serverOrders.push(payload);}
   if(loseOrderReply)return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 }
 else if(p==='/analytics/replenishment/reminder'){
   if(req.method()==='PUT'){
     const change=body();assert.equal(change.expectedRevision,reminder.revision);assert.equal(change.timeZoneId,browserTimeZone,'Reminder uses a portable IANA zone');reminderWrites.push(change);
     reminder={...reminder,revision:reminder.revision+1,enabled:change.enabled,settings:change.settings,timeZoneId:change.timeZoneId,daysBeforeOrder:change.daysBeforeOrder,dueAtMillis:change.enabled?Date.now()+86400000:null,updatedAtMillis:Date.now()};
   }
   payload=reminder;
 }
 else if(p==='/analytics/replenishment'){
   assert.equal(u.searchParams.get('zone'),browserTimeZone,'Server snapshots use a portable IANA zone, never SYSTEM');
   if(plannerDenied){status=403;payload=null;}else payload={...snapshot,lookbackDays:Number(u.searchParams.get('days')||28),timeZoneId:u.searchParams.get('zone')||'UTC'};
 }
 else if(p==='/subscriptions/store/get')payload={subscription:{id:id(600),storeId:store.id,ownerUserId:account.id,planId:'internal_lifetime',status:'active',accessKind:'lifetime',autoRenew:false,currentPeriodStartMillis:1,currentPeriodEndMillis:null,revision:1},charges:[],plans:[],canManage:true,serverTimeMillis:Date.now()};
 else if(p==='/market/seller/stock-status')payload={accountId:account.id,storeId:store.id,parentStoreId:parent.id,marketplaceEnabled:false,entries:[],checkedAtMillis:Date.now()};
 else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'STORE'};
 else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
 else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 return route.fulfill({status,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:null,negative:status>=400,payload:payload===null?null:JSON.stringify(payload)})});
});
const page=await context.newPage();page.on('console',m=>consoleMessages.push({type:m.type(),text:m.text()}));page.on('pageerror',e=>errors.push(e.stack||e.message));page.on('crash',()=>errors.push('renderer crash'));
const b=label=>page.getByRole('button',{name:label,exact:true});
const click=async label=>{const target=b(label).last();await target.waitFor({timeout:45000});await page.waitForTimeout(500);await target.click({force:true});await page.waitForTimeout(600);};
const until=async(test,label)=>{for(let i=0;i<180&&!test();i++)await page.waitForTimeout(250);assert.ok(test(),label);};
const shot=async name=>{await page.screenshot({path:path.join(out,name+'.png')});fs.writeFileSync(path.join(out,name+'.txt'),await page.locator('body').ariaSnapshot());};
const confirm=async()=>{for(const label of ['Confirm','Yes','Continue']){const target=b(label);if(await target.count()){await target.last().click({force:true});await page.waitForTimeout(600);return;}}};
try{
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6500);await page.keyboard.press('Tab');
 await click('Stock');const plannerButton=await b('Purchase planner').boundingBox();await click('Import stock');
 await shot('import-open');
 if(process.env.AITA_STRESS_IMPORT==='1'){
   // Full supported row count, then a rejected over-limit export, without approving any stock writes.
   const rows=Array.from({length:3000},(_,i)=>`Synthetic item ${i};000${String(i).padStart(10,'0')};1,5;kg;1500;1000`).join('\n');
   const largeBook=path.resolve(out,'synthetic-3000.xlsx');
   require('node:child_process').execFileSync('python3',[path.join(__dirname,'fixtures/generate-stock-workbook.py'),largeBook]);
   for(const file of [
     {name:'synthetic-3000.csv',mimeType:'text/csv',buffer:Buffer.from('Name;Barcode;Quantity;Unit;Sale price;Supply price\n'+rows)},
     {name:'synthetic-3000.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:fs.readFileSync(largeBook)}
   ]){
     const started=Date.now();
     const [chooser]=await Promise.all([page.waitForEvent('filechooser',{timeout:45000}),click('Choose spreadsheet')]);
     await chooser.setFiles(file);
     await page.getByText('Rows: 3000',{exact:true}).waitFor({timeout:60000});
     await click('Review these rows');
     await page.getByText('Reviewed: 0 / 3000 · Imported: 0',{exact:true}).waitFor({timeout:60000});
     importTimings.push({name:file.name,bytes:file.buffer.length,elapsedMillis:Date.now()-started});
     await shot(file.name.endsWith('xlsx')?'stress-xlsx':'stress-csv');
     assert.equal(addedItems.length,0);assert.equal(addedBatches.length,0);
     const approvalStarted=Date.now();await click('Approve clear readings');
     await page.getByText('Reviewed: 3000 / 3000 · Imported: 0',{exact:true}).waitFor({timeout:45000});
     importTimings.push({name:'approve-'+file.name,elapsedMillis:Date.now()-approvalStarted});
     if(file.name.endsWith('.xlsx')){
       // Exercise large durable drafts, including bounded checkpoint lookups after reopening.
       await page.waitForTimeout(2000);const restoreStarted=Date.now();
       await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});
       await page.waitForTimeout(6000);await page.keyboard.press('Tab');await click('Stock');await click('Import stock');
       await page.getByText('Reviewed: 3000 / 3000 · Imported: 0',{exact:true}).waitFor({timeout:45000});
       importTimings.push({name:'restore-3000-rows',elapsedMillis:Date.now()-restoreStarted});
       await shot('stress-restored');
     }
     await click(/^Discard review/);await confirm();
     await page.getByText('Reviewed: 0 / 0 · Imported: 0',{exact:true}).waitFor({timeout:30000});
   }
   const [tooLarge]=await Promise.all([page.waitForEvent('filechooser',{timeout:45000}),click('Choose spreadsheet')]);
   await tooLarge.setFiles({name:'over-limit.csv',mimeType:'text/csv',buffer:Buffer.from('Name,Price\n'+Array.from({length:3001},(_,i)=>`Item${i},1`).join('\n'))});
   await page.getByText(/Split this export into smaller files/).waitFor({timeout:30000});await shot('stress-rejected');
   assert.equal(addedItems.length,0);assert.equal(addedBatches.length,0);
   assert.deepEqual(errors,[]);
   assert.deepEqual(consoleMessages.filter(m=>m.type==='error'&&/Exception|RuntimeError|unreachable/i.test(m.text)),[]);
   console.log('PASS 3000-row CSV/XLSX review and restart, discard, 3001-row rejection; no stock writes',JSON.stringify(importTimings));
   return;
 }
 if(process.env.AITA_TEST_BULK_IMPORT==='1'){
   const count=130,started=Date.now();
   const rows=Array.from({length:count},(_,i)=>`Bulk item ${i};000${String(i).padStart(10,'0')};1,5;kg;1500;1000`).join('\n');
   const [chooser]=await Promise.all([page.waitForEvent('filechooser',{timeout:45000}),click('Choose spreadsheet')]);
   await chooser.setFiles({name:'bulk-import.csv',mimeType:'text/csv',buffer:Buffer.from('Name;Barcode;Quantity;Unit;Sale price;Supply price\n'+rows)});
   await click('Review these rows');await click(/^Approve clear readings/);await click('Import reviewed items');await confirm();
   for(let attempt=0;attempt<1440&&(addedItems.length<count||addedBatches.length<count);attempt++)await page.waitForTimeout(250);
   assert.equal(addedItems.length,count,'Every approved item is created');assert.equal(addedBatches.length,count,'Every approved quantity creates its batch');
   await page.getByText(`Reviewed: ${count} / ${count} · Imported: ${count}`,{exact:true}).waitFor({timeout:60000});
   assert.equal(new Set(addedItems.map(x=>x.id)).size,count);assert.equal(new Set(addedBatches.map(x=>x.id)).size,count);
   assert.ok(addedBatches.every(x=>x.quantity.total===1.5));
   await shot('bulk-complete');await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});
   await page.waitForTimeout(6000);await page.keyboard.press('Tab');await click('Stock');await click('Import stock');
   await page.getByText(`Reviewed: ${count} / ${count} · Imported: ${count}`,{exact:true}).waitFor({timeout:45000});
   await shot('bulk-restored');assert.equal(addedItems.length,count);assert.equal(addedBatches.length,count);
   assert.deepEqual(errors,[]);assert.deepEqual(consoleMessages.filter(m=>m.type==='error'&&/Exception|RuntimeError|unreachable/i.test(m.text)),[]);
   console.log(`PASS ${count} real stock/batch fixture writes and completed-draft restart in ${Date.now()-started}ms`);return;
 }
 const [chooser]=await Promise.all([page.waitForEvent('filechooser',{timeout:45000}),click('Choose spreadsheet')]);
 await chooser.setFiles({name:'migration.csv',mimeType:'text/csv',buffer:Buffer.from('Name;Barcode;Quantity;Unit;Sale price;Supply price\nImported tea;00012345;5;pcs;150;100\nImported rice;00054321;1,5;kg;1200;900\n')});
 await page.bringToFront();await page.keyboard.press('Tab');await page.waitForTimeout(2000);await shot('mapping');
 fs.writeFileSync(path.join(out,'mapping-dom.json'),JSON.stringify(await page.evaluate(()=>({focused:document.hasFocus(),visibility:document.visibilityState,canvases:Array.from(document.querySelectorAll('canvas')).map(c=>({width:c.width,height:c.height,style:c.style.cssText}))})),null,2));
 await click('Review these rows');await shot('import-review');
 assert.equal(addedItems.length,0,'Nothing imported before explicit review');
 await click(/^Approve clear readings/);await confirm();
 await click('Import reviewed items');await confirm();
 await until(()=>addedItems.length===2&&addedBatches.length===2,'CSV creates actual items and batches');
 assert.equal(addedItems[0].barcodes[0],'00012345');assert.equal(addedBatches[1].quantity.total,1.5);
 await shot('import-complete');
 const [xlsxChooser]=await Promise.all([page.waitForEvent('filechooser',{timeout:45000}),click('Choose spreadsheet')]);
 await xlsxChooser.setFiles(path.join(__dirname,'fixtures','stock-import.xlsx'));
 await click('Review these rows');await shot('xlsx-review');
 assert.equal(addedItems.length,2,'Reading XLSX alone does not create stock');
 await click(/^Approve clear readings/);await confirm();await click('Import reviewed items');await confirm();
 await until(()=>addedItems.length===3&&addedBatches.length===3,'XLSX creates the reviewed item and batch');
 assert.equal(addedItems[2].barcodes[0],'00077777');assert.equal(addedBatches[2].quantity.total,2);
 await shot('xlsx-complete');
 // A complete local import is durable without a reachable API, including its batch dependency.
 offline=true;
 const [offlineChooser]=await Promise.all([page.waitForEvent('filechooser',{timeout:45000}),click('Choose spreadsheet')]);
 await offlineChooser.setFiles({name:'offline.csv',mimeType:'text/csv',buffer:Buffer.from('Name;Barcode;Quantity;Unit;Sale price;Supply price\nOffline flour;00088888;3;kg;450;300\n')});
 await click('Review these rows');await click(/^Approve clear readings/);await click('Import reviewed items');await confirm();
 await page.getByText(/Reviewed: 4 \/ 4 · Imported: 4/).waitFor({timeout:90000});
 assert.equal(addedItems.length,3,'Offline stock import is queued, not a pretend server write');await shot('import-offline');
 await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6000);await page.keyboard.press('Tab');
 await click('Stock');await click('Import stock');await page.getByText(/Reviewed: 4 \/ 4 · Imported: 4/).waitFor({timeout:45000});await shot('import-offline-restored');
 assert.equal(addedItems.length,3);assert.equal(addedBatches.length,3);
 offline=false;
 // Reopen after app restart; imported rows remain complete and are not re-enqueued.
 await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6000);await page.keyboard.press('Tab');
 await click('Stock');await until(()=>addedItems.length===4&&addedBatches.length===4,'reconnect flushes the durable stock outbox');await click('Import stock');await shot('import-restored');assert.equal(addedItems.length,4);assert.equal(addedBatches.length,4);
 await page.keyboard.press('Escape');await page.waitForTimeout(800);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 // Compose 1.9.2 has upstream CMP-9368/CMP-10623: closing a Dialog leaves stale a11y nodes.
 // Exercise the real visible pointer target captured before opening the import sheet.
 // The planner opens a new dialog, whose accessibility tree is independently live.
 assert.ok(plannerButton);await page.mouse.click(plannerButton.x+plannerButton.width/2,plannerButton.y+plannerButton.height/2);
 await until(()=>apiCalls.some(x=>x.path==='/analytics/replenishment'),'planner requests scoped sales');
 await page.getByRole('button',{name:/^Planning tea Suggested purchase/}).last().waitFor({timeout:30000});await shot('planner-wide');
 await click('Planning settings');await shot('planner-settings');
 await click('Apply');
 await click('Use suggestions');
 // Pick the supplier in the standard app dropdown.
 const supplierText=page.getByText('Supplier',{exact:true}).last();await supplierText.scrollIntoViewIfNeeded();await supplierText.click({force:true});await page.waitForTimeout(500);
 await page.getByText('Fixture supplier',{exact:true}).last().click({force:true});await page.waitForTimeout(500);
 loseOrderReply=true;await click('Send reviewed order');await confirm();
 await until(()=>orderBodies.length>=1,'reviewed plan sent');await page.waitForTimeout(2500);await shot('planner-pending');
 loseOrderReply=false;await click('Retry the same draft');await confirm();await until(()=>orderBodies.length>=2,'pending order retried');
 assert.deepEqual(orderBodies[1],orderBodies[0],'lost acknowledgement preserves exact order identity and payload');
 assert.equal(orderBodies[0].lines[0].requestedQuantity.total,43);assert.equal(orderBodies[0].order.status,'Sent');
 await page.getByText(/Order sent\./).waitFor({timeout:45000});await shot('planner-sent');
 await click('Planning settings');
 await click('Regular replenishment');await page.getByText('New Year',{exact:true}).last().click({force:true});
 await click('Apply');await click('Seasonal reminder');
 await until(()=>apiCalls.some(x=>x.path==='/analytics/replenishment/reminder'),'reminder reads only after opening controls');
 await click('Save reminder');await until(()=>reminderWrites.length===1,'explicit seasonal reminder save');
 assert.equal(reminderWrites[0].settings.event,'new_year');assert.equal(reminderWrites[0].enabled,true);
 await page.getByText('Reminder scheduled',{exact:true}).waitFor({timeout:30000});await shot('reminder-scheduled');
 await click('Turn off reminder');await until(()=>reminderWrites.length===2,'reminder can be disabled');assert.equal(reminderWrites[1].enabled,false);
 await click('Seasonal reminder');
 offline=true;await click('Refresh');await page.getByText(/Saved snapshot\./).first().waitFor({timeout:45000});await shot('planner-offline');
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(900);await shot('planner-phone');
 offline=false;plannerDenied=true;await click('Refresh');await page.getByText(/viewing permissions are required/).first().waitFor({timeout:30000});
 assert.equal(await page.getByRole('button',{name:/^Planning tea Suggested purchase/}).count(),0,'403 clears visible cached rows');
 // Printing uses a validated reviewed selection, including planning assumptions and data age.
 plannerDenied=false;await page.setViewportSize({width:1440,height:1040});await click('Refresh');
 await page.getByRole('button',{name:/^Planning tea Suggested purchase/}).last().waitFor({timeout:30000});
 await click('Use suggestions');
 await page.getByRole('button',{name:/^Planning tea Suggested purchase/}).last().scrollIntoViewIfNeeded();
 const quantity=page.getByRole('textbox').last();await quantity.click({force:true});await page.keyboard.press('Control+A');await page.keyboard.press('Backspace');await page.keyboard.insertText('0');await page.waitForTimeout(300);
 assert.equal(await quantity.innerText(),'0','Quantity replacement reaches the Compose field');
 await click('View / print plan');await page.getByText(/Enter a valid positive quantity/).last().waitFor({timeout:15000});
 await page.getByRole('button',{name:/^Planning tea Suggested purchase/}).last().scrollIntoViewIfNeeded();
 await page.getByRole('textbox').last().click({force:true});await page.keyboard.press('Control+A');await page.keyboard.press('Backspace');await page.keyboard.insertText('43');await page.waitForTimeout(300);await click('View / print plan');
 await page.getByText('Printable summary',{exact:true}).waitFor({timeout:30000});await shot('plan-report');
 await click('Save PDF via print dialog');
 await page.waitForFunction(()=>window.__planningPrints?.length>0,{},{timeout:45000});
 const printed=await page.evaluate(()=>window.__planningPrints.at(-1));
 assert.match(printed.text,/Planning tea.*43 pc\./s);assert.match(printed.text,/Extra reserve/);assert.equal(printed.fonts,true);
 assert.doesNotMatch(printed.html,/aita-receipt-paper/,'PDF keeps report paper, independent of receipt printer');
 fs.writeFileSync(path.join(out,'purchasing-plan.html'),printed.html);
 const pdfPage=await context.newPage();await pdfPage.setContent(printed.html);await pdfPage.evaluate(()=>document.fonts.ready);
 const bytes=await pdfPage.pdf({preferCSSPageSize:true,printBackground:true});await pdfPage.close();
 fs.writeFileSync(path.join(out,'purchasing-plan.pdf'),bytes);
 const media=bytes.toString('latin1').match(/\/MediaBox\s*\[\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*\]/);
 assert.ok(media);assert.ok(Math.abs(Number(media[1])-595)<1);assert.ok(Math.abs(Number(media[2])-842)<1);
 assert.equal(fs.readFileSync(path.join(out,'purchasing-plan.pdf')).subarray(0,5).toString(),'%PDF-');
 assert.deepEqual(errors,[]);assert.deepEqual(consoleMessages.filter(m=>m.type==='error'&&/Exception|RuntimeError|unreachable/i.test(m.text)),[],'No swallowed composition errors');console.log('PASS local CSV/XLSX import, actual stock writes, restart, purchasing formula, exact retry, seasonal reminder, offline cache, responsive layout, access denial and printable PDF');
} catch(error){await shot('failure').catch(()=>{});throw error;}
finally{fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({errors,consoleMessages,addedItems,addedBatches,orderBodies,reminderWrites,importTimings,apiCalls,productionTouched:false},null,2));await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
