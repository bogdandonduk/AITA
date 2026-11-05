// Offline goods and batch creation, restart durability, reconnect synchronization and connection-banner lifetime. Set AITA_AUTO_SYNC=1 to omit the manual Retry action. All APIs use isolated fixtures.
// Set NODE_PATH to Playwright, AITA_WEB_DIST and AITA_ARTIFACTS.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene='physical';
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'build/'+'inventory-offline-smoke';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
let account={id:id(99),publicId:'STORE-TEST',phoneNumber:'',email:'store@example.test',firstName:'Branch',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',activeStoreId:id(scene==='parent'?30:(scene==='physical'||scene==='cart')?31:32)};
const store=(n,name,parent,type)=>({id:id(n),publicId:'STORE-'+n,parentStoreId:parent? id(parent):null,userIds:[account.id],storeTypeIds:[],name:tr(name),alias:[],description:[],companyForms:[],location:{},address:name+' address',phoneNumbers:[],emails:[],countryLocales:['kz'],createdAt:1,branchType:type,architectureVersion:2});
const physical=store(31,'West pickup branch',30,'PHYSICAL'),internet=store(32,'Internet branch',30,'INTERNET'),parent={...store(30,'Management warehouse',null,null),branches:[physical,internet]};
const stores=[parent,physical,internet],quantity={id:'0',immutableUnitName:tr('pc.'),total:12,pricedAmount:1,roundTotal:true},price={price:'250',currency:'KZT',supplierId:''};
const itemFor=storeId=>({id:id(Number(storeId.slice(-2))*100),storeId,name:tr('Mountain honey'),description:[],barcodes:['4006381333931'],measurementUnitId:'0',salePrices:[price],supplyPrices:[{...price,price:'100'}],activeShelfBatchId:id(Number(storeId.slice(-2))*100+1),isActive:true});
const batchesFor=storeId=>[1,2].map(n=>({id:id(Number(storeId.slice(-2))*100+n),storeId,goodsItemId:itemFor(storeId).id,quantity:{...quantity,total:n===1?12:6},supplyPrice:{...price,price:'100'},salePriceOverride:price,status:'Delivered',shelfPriority:3-n,isActive:true}));
let shop={storeId:internet.id,branchStoreId:internet.id,displayName:'Public internet shop',city:'Almaty',publicAddress:'Internet pickup counter',pickupNote:'Confirm before arrival',published:true,revision:1,shareBranchAvailability:true},selected=[physical.id];
const locations=[{storeId:parent.id,name:parent.name,address:parent.address,warehouse:true},{storeId:physical.id,name:physical.name,address:physical.address,warehouse:false}];
const dashboard=()=>({storefront:shop,listings:[],locationStoreIds:selected,availableLocations:locations});
const browser=await chromium.launch({headless:true,channel:'chromium',args:['--enable-unsafe-swiftshader']});const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US'});
await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-store-token',refreshToken:'isolated-store-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-store-test');},account);
let addedBatches=[],addedItems=[],batchAttempts=0,offline=false;
const editedItems=new Map(),acceptedEdits=new Map();
const calls=[],errors=[],memory=[],rendererCrashes=[],consoleErrors=[],lifecycle=[],bannerChecks=[];const browserCdp=await browser.newBrowserCDPSession();await browserCdp.send('Target.setDiscoverTargets',{discover:true});browserCdp.on('Target.targetCrashed',event=>rendererCrashes.push(event));await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({path:p,method:req.method(),storeId,body:req.postData()});if(offline)return route.abort('internetdisconnected');let payload=[];
 if(p==='/auth/session'||p==='/auth/ping')payload={};
 else if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
 else if(p==='/stores/get')payload=[parent];
 else if(p==='/stock/get')payload=[itemFor(storeId),...addedItems].map(i=>editedItems.get(i.id)||i);
 else if(p==='/stock/update'){const edit=req.postDataJSON();assert.ok(edit.updateOperationId);
  if(acceptedEdits.has(edit.updateOperationId))payload=acceptedEdits.get(edit.updateOperationId);
  else {const before=editedItems.get(edit.id)||addedItems.find(i=>i.id===edit.id)||itemFor(storeId);assert.equal(edit.expectedUpdatedAtMillis,before.updatedAtMillis||0);payload={...edit,updateOperationId:null,expectedUpdatedAtMillis:null,updatedAtMillis:(before.updatedAtMillis||0)+1};editedItems.set(edit.id,payload);acceptedEdits.set(edit.updateOperationId,payload);}
 }
 else if(p==='/stock/add'){payload=req.postDataJSON();if(!addedItems.some(i=>i.id===payload.id))addedItems.push(payload);}
 else if(p==='/stockBatches/get')payload=[...batchesFor(storeId),...addedBatches];
 else if(p==='/stockBatches/add') {
  batchAttempts++;
  payload=req.postDataJSON();for(const b of payload)if(!addedBatches.some(x=>x.id===b.id))addedBatches.push(b);
 }

 else if(p==='/stockBatches/branchAvailability')payload={rootStoreId:parent.id,currentStoreId:storeId,sourceGoodsItemId:headers.goods_item_id||itemFor(storeId).id,locations:stores.map(s=>({storeId:s.id,parentStoreId:s.parentStoreId,name:s.name,address:s.address,isCurrentStore:s.id===storeId,isParentStore:s.id===parent.id,goodsItemId:itemFor(s.id).id,totalQuantity:quantity,batchCount:1,batches:batchesFor(s.id)})),movements:[]};
 else if(p==='/subscriptions/store/get')payload={subscription:{id:id(600),storeId,ownerUserId:account.id,planId:'internal_lifetime',status:'active',accessKind:'lifetime',autoRenew:false,currentPeriodStartMillis:1,currentPeriodEndMillis:null,revision:1},charges:[],plans:[],canManage:true,serverTimeMillis:Date.now()};
 else if(p==='/market/seller/stock-status')payload={accountId:account.id,storeId,parentStoreId:parent.id,marketplaceEnabled:true,entries:[],checkedAtMillis:Date.now()};
 else if(p==='/market/seller')payload=dashboard();
 else if(p==='/market/seller/storefront'){const body=req.postDataJSON();shop={...body.storefront,revision:shop.revision+1};selected=body.locationStoreIds||selected;payload=dashboard();}
 else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'STORE'};
 else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
 else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:null,negative:false,payload:JSON.stringify(payload)})});
});
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.stack||e.message));page.on('crash',()=>errors.push('crash'));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());if(m.text().startsWith('AITA connection:'))fs.appendFileSync(out+'/connection.log',Date.now()+' '+m.text()+'\n');});

const shot=async name=>{await page.screenshot({path:out+'/'+scene+'-'+name+'.png'});};
const pageCdp=await context.newCDPSession(page);await pageCdp.send('Performance.enable');
const sampleMemory=async label=>{
 const metrics=await pageCdp.send('Performance.getMetrics');const processes=await browserCdp.send('SystemInfo.getProcessInfo');
 const renderer=processes.processInfo.filter(p=>p.type==='renderer').map(p=>{let rss=0,swap=0;try{const status=fs.readFileSync('/proc/'+p.id+'/status','utf8');rss=Number(status.match(/^VmRSS:\s+(\d+)/m)?.[1]||0)*1024;swap=Number(status.match(/^VmSwap:\s+(\d+)/m)?.[1]||0)*1024;}catch{}return {pid:p.id,rss,swap};});
 memory.push({label,time:Date.now(),metrics:Object.fromEntries(metrics.metrics.filter(x=>['JSHeapUsedSize','JSHeapTotalSize','Nodes','Documents','LayoutCount','TaskDuration'].includes(x.name)).map(x=>[x.name,x.value])),renderer});
 console.log('MEMORY',label,JSON.stringify(memory.at(-1)));
};
const button=label=>page.getByRole('button',{name:label,exact:true});
// Compose draws pointer targets on canvas; semantics may briefly lag during scene animation.
const physicalNav=async label=>{await page.mouse.click(scene==='parent'?(label==='Menu'?792:648):(label==='Menu'?1008:864),1010);await page.waitForTimeout(700);};
const clickText=async label=>{const target=page.getByText(label,{exact:true});await target.first().waitFor({timeout:15000});await target.first().click({force:true});await page.waitForTimeout(650);};
const tab=async pattern=>{await page.getByRole('button').filter({hasText:pattern}).click({force:true});await page.waitForTimeout(600);};
const until=async(check,label)=>{for(let i=0;i<240&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),label);};
const connectedBanner=()=>page.getByText('Server connected',{exact:true});
const offlineBanner=()=>page.getByText("Can't reach server. Offline mode",{exact:true});
const assertOfflineBannerPersists=async label=>{
 await offlineBanner().waitFor({state:'visible',timeout:30000});
 const appearedAt=Date.now();await page.waitForTimeout(4600);
 assert.equal(await offlineBanner().isVisible(),true,'Offline warning must remain after the connected-banner timeout');
 assert.equal(await connectedBanner().isVisible(),false,'Offline state must not also display connected acknowledgement');
 bannerChecks.push({label,offlineVisibleAfterMillis:Date.now()-appearedAt});
};
// Start observing before reconnect, without moving the clock or changing app state. The four
// second acknowledgement must be visible long enough to read and then release its screen space.
const observeConnectedAcknowledgement=async label=>{
 await connectedBanner().waitFor({state:'visible',timeout:30000});
 const appearedAt=Date.now();await page.waitForTimeout(2500);
 assert.equal(await connectedBanner().isVisible(),true,'Connected acknowledgement must not disappear immediately');
 await connectedBanner().waitFor({state:'hidden',timeout:7500});
 const visibleMillis=Date.now()-appearedAt;
 assert.ok(visibleMillis>=3200&&visibleMillis<10000,'Connected acknowledgement should hide around four seconds: '+visibleMillis+'ms');
 assert.equal(await offlineBanner().isVisible(),false,'Recovery must remove the old offline warning');
 bannerChecks.push({label,connectedVisibleMillis:visibleMillis});
};
try {
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(7000);await page.keyboard.press('Tab');
 await button('Stock').click({force:true});await page.waitForTimeout(1500);

 async function typeIn(locator,text){await locator.click({force:true});await page.waitForTimeout(200);await page.keyboard.press('ControlOrMeta+A');await page.keyboard.type(text,{delay:20});await page.waitForTimeout(300);}
 await button('Confirm').click({force:true});await page.waitForTimeout(500);
 let aria=await page.locator('body').ariaSnapshot();
 assert.match(aria,/Enter the item name/);assert.match(aria,/Enter at least one barcode/);
 await shot('inline-validation');
 await typeIn(page.getByRole('textbox',{name:'Handheld barcode scanner',exact:true}),'2618000001248');
 const translated=page.getByRole('textbox').filter({has:button('Default')});
 await typeIn(translated.nth(0),'Offline toy');
 await button('Generic prices').click({force:true});await page.waitForTimeout(500);
 const prices=page.getByRole('textbox').filter({has:button('KZT')});
 await typeIn(prices.nth(0),'0');await typeIn(prices.nth(1),'250');
 offline=true;await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
 await button('Confirm').click({force:true});
 await page.getByText(/Stock changes waiting to sync: 1/).first().waitFor({timeout:60000});
 await assertOfflineBannerPersists('offline-item');
 await shot('offline-item');assert.equal(addedItems.length,0);
 await typeIn(page.getByRole('textbox',{name:'Search by any data',exact:true}).first(),'Offline toy');await page.waitForTimeout(1000);
 const offlineItem=page.getByRole('button',{name:/^Offline toy .*Barcode: 2618000001248 /});
 await offlineItem.getByRole('button',{name:'Add batch',exact:true}).click({force:true});await page.waitForTimeout(900);
 await typeIn(page.getByRole('textbox',{name:'Quantity',exact:true}),'5');
 await button('Confirm').click({force:true});await page.waitForTimeout(1500);
 assert.match(await page.locator('body').ariaSnapshot(),/Stock changes waiting to sync: 2/);assert.equal(addedBatches.length,0);
 await shot('offline-batch');
 await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6000);await page.keyboard.press('Tab');
 await button('Stock').click({force:true});await page.waitForTimeout(1200);
 assert.match(await page.locator('body').ariaSnapshot(),/Stock changes waiting to sync: 2/);
 assert.match(await page.locator('body').ariaSnapshot(),/Offline toy/);
 await assertOfflineBannerPersists('offline-reload');
 await shot('offline-after-reload');
 const connectedAcknowledgement=observeConnectedAcknowledgement('item-and-batch-reconnect');
 // Keep an observer rejection handled while the existing stock synchronization assertions run.
 connectedAcknowledgement.catch(()=>{});
 offline=false;console.log('RECONNECT',Date.now());await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 const retry=button('Retry').first();if(!process.env.AITA_AUTO_SYNC && await retry.count())await retry.click({force:true});
 await until(()=>addedItems.length===1 && addedBatches.length===1,'Item and batch synchronized');await page.waitForTimeout(1800);
 assert.deepEqual(addedItems[0].barcodes,['2618000001248']);assert.equal(addedBatches[0].goodsItemId,addedItems[0].id);assert.equal(addedBatches[0].quantity.total,5);
 assert.doesNotMatch(await page.locator('body').ariaSnapshot(),/Stock changes waiting to sync/);
 await connectedAcknowledgement;
 // Navigating performs more authenticated reads; none should resurrect the same connection.
 await button('Menu').click({force:true});await page.waitForTimeout(700);
 assert.equal(await connectedBanner().isVisible(),false,'Menu navigation must not restart a settled connected banner');
 await button('Stock').click({force:true});await page.waitForTimeout(1300);
 assert.equal(await connectedBanner().isVisible(),false,'Stock navigation and healthy requests must keep the banner hidden');
 bannerChecks.push({label:'healthy-navigation',connectedHidden:true});
 assert.deepEqual(errors,[]);await shot('synchronized');
 console.log('PASS: field-specific errors; offline item and batch survive reload; reconnect syncs the same item and batch IDs');
 // Edit the same durable card offline, then restart before the server sees the edit.
 offline=true;await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
 await button('svg/29_0.svg').first().click({force:true});await page.waitForTimeout(800);
 await typeIn(page.getByRole('textbox').filter({has:button('Default')}).first(),'Offline toy edited');
 await button('Confirm').click({force:true});await page.waitForTimeout(1200);
 assert.match(await page.locator('body').ariaSnapshot(),/Stock changes waiting to sync: 1/);assert.equal(editedItems.size,0);
 await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(4500);await page.keyboard.press('Tab');
 await button('Stock').click({force:true});await page.waitForTimeout(800);
 assert.match(await page.locator('body').ariaSnapshot(),/Offline toy edited/);await shot('edited-offline-after-reload');
 offline=false;await page.evaluate(()=>window.dispatchEvent(new Event('online')));
 if(!process.env.AITA_AUTO_SYNC && await button('Retry').count())await button('Retry').first().click({force:true});
 await until(()=>editedItems.size===1,'Offline edit synchronized');await page.waitForTimeout(1000);
 assert.equal(editedItems.get(addedItems[0].id).name[0].value,'Offline toy edited');assert.equal(acceptedEdits.size,1);
 assert.doesNotMatch(await page.locator('body').ariaSnapshot(),/Stock changes waiting to sync/);assert.deepEqual(errors,[]);
 await shot('edited-synchronized');console.log('PASS: offline item edit survives reload and syncs once with its original revision');


} catch(e){await shot('failure');fs.writeFileSync(out+'/failure-aria.txt',await page.locator('body').ariaSnapshot());throw e;}
finally {fs.writeFileSync(out+'/calls.json',JSON.stringify(calls,null,2));fs.writeFileSync(out+'/connection-banner-checks.json',JSON.stringify(bannerChecks,null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
