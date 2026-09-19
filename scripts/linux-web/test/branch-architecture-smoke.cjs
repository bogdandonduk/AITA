// Synthetic branch/warehouse browser regression. All API calls are intercepted.
// NODE_PATH=/tmp/aita-browser-check/node_modules AITA_WEB_DIST=... AITA_ARTIFACTS=...
// AITA_TEST_SCENE=physical|parent|seller|cart node scripts/linux-web/test/branch-architecture-smoke.cjs
// Optional AITA_STRESS_ROUNDS, AITA_FREEZE_CYCLES and AITA_IDLE_MILLIS record lifecycle memory; AITA_VISIBILITY_SECONDS tests a synthetic visibility signal.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene=process.env.AITA_TEST_SCENE||'physical';
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-pass013-stock-browser';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
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
const calls=[],errors=[],memory=[],rendererCrashes=[],consoleErrors=[],lifecycle=[];const browserCdp=await browser.newBrowserCDPSession();await browserCdp.send('Target.setDiscoverTargets',{discover:true});browserCdp.on('Target.targetCrashed',event=>rendererCrashes.push(event));await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({path:p,method:req.method(),storeId,body:req.postData()});let payload=[];
 if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
 else if(p==='/stores/get')payload=[parent];
 else if(p==='/stock/get')payload=[itemFor(storeId)];
 else if(p==='/stockBatches/get')payload=batchesFor(storeId);
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
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.stack||e.message));page.on('crash',()=>errors.push('crash'));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});

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
const until=async(check,label)=>{for(let i=0;i<60&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),label);};
try {
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(10000);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 await shot('initial');await sampleMemory('initial');
 if(scene==='cart') {
  await button('Sale').click({force:true});await page.waitForTimeout(1200);
  assert.equal(await button('Clear cart?').count(),0,'permanent empty cart has no clear action');
  assert.equal(await button('Cart 1').count(),1,'cart has its translated accessible label');await shot('cart-empty');
  await page.getByRole('button').filter({hasText:/^Mountain honey/}).click({force:true});await page.waitForTimeout(800);
  assert.ok((await button('Cart 1').innerText()).includes('(1)'),'cart tab observes added goods');
  assert.equal(await button('Clear cart?').count(),1);await shot('cart-filled');
  await button('Clear cart?').click({force:true});await page.waitForTimeout(350);
  await page.getByRole('button').filter({hasText:/^Clear$/}).click({force:true});await page.waitForTimeout(900);await shot('cart-cleared');
  // Reload verifies the committed local cart journal, independent of modal semantics cleanup.
  await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(5000);await page.keyboard.press('Tab');await page.waitForTimeout(300);
  await button('Sale').click({force:true});await page.waitForTimeout(700);
  assert.equal(await button('Clear cart?').count(),0,'cleared cart remains empty after reload');
  assert.equal((await button('Cart 1').innerText()).includes('(1)'),false);await shot('cart-cleared-reloaded');
 } else if(scene==='seller') {
  await button('Menu').click({force:true});await page.waitForTimeout(650);await clickText('Shop window');
  await until(()=>calls.some(c=>c.path==='/market/seller'),'seller dashboard loaded');
  await tab(/^Locations$/);await shot('locations-before');
  for(const title of ['Show selected locations and item availability','Management warehouse · Warehouse','West pickup branch']) assert.equal(await button(title).count(),1,'location toggle is named for accessibility');
  await button('Management warehouse · Warehouse').click({force:true});await page.waitForTimeout(350);
  await button('Save storefront').click({force:true});await until(()=>calls.some(c=>c.path==='/market/seller/storefront'),'location save');
  const change=JSON.parse(calls.filter(c=>c.path==='/market/seller/storefront').at(-1).body);
  assert.deepEqual([...change.locationStoreIds].sort(),[parent.id,physical.id].sort());
  assert.equal(change.storefront.branchStoreId,internet.id);assert.equal(change.storefront.shareBranchAvailability,true);
  await shot('locations-saved');await page.setViewportSize({width:390,height:844});await page.waitForTimeout(600);await tab(/^Locations$/);await shot('locations-phone');
 } else {
  await button('Stock').click({force:true});await until(()=>calls.some(c=>c.path==='/stock/get'),'stock loaded');await page.waitForTimeout(1500);await shot('stock');
  assert.ok(calls.some(c=>c.path==='/stockBatches/get'),'stock batch fixture was loaded');
  if(scene==='parent') {
   for(const action of ['Sale','Return','Supply']) assert.equal(await button(action).count(),0,'management parent exposes no '+action+' navigation');
   assert.equal(await page.getByText('Shelf',{exact:true}).count(),0,'management stock has no active shelf');
   assert.ok(!calls.slice(calls.findIndex(c=>c.path==='/stores/get')+1).some(c=>c.path==='/subscriptions/store/get'&&c.storeId===parent.id),'management parent must not request a subscription after architecture loads');
  } else {
   const first=await button('Batches').first().boundingBox();assert.ok(first,'active batch preview is visible');
   const x=first.x+first.width/2,y=first.y+first.height/2;
   await page.mouse.move(x,y);await page.mouse.down();await page.waitForTimeout(750);await page.mouse.move(x+45,y,{steps:8});await page.waitForTimeout(250);await shot('batch-drag-border');
   await page.mouse.move(x,y,{steps:8});await page.mouse.up();await page.waitForTimeout(350);
  }
  await button('Add batch').click({force:true});await page.waitForTimeout(850);await button('Cancel').click({force:true});await page.waitForTimeout(850);await shot('batch-tabs');
  if(scene==='parent') {
   assert.equal(await page.getByText(/^Warehouse batches \(2\)$/).count(),1);
   assert.equal(await page.getByText(/^Shelf order/).count(),0);
   assert.equal(await page.getByText('Branch stock',{exact:true}).count(),0);
  } else {
   assert.equal(await page.getByText(/^Shelf order \(2\)$/).count(),1);
   await tab(/^Branch stock$/);await shot('branch-locations');
  }
  await tab(/^Incoming batches \(0\)$/);await shot('incoming');
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(700);await shot('tabs-phone');
 }

 await page.setViewportSize({width:1440,height:1040});await page.waitForTimeout(500);
 if(Number(process.env.AITA_STRESS_ROUNDS||0)>0) {
  await page.keyboard.press('Escape');await page.waitForTimeout(500);
  for(let round=1;round<=Number(process.env.AITA_STRESS_ROUNDS);round++) {
   await physicalNav('Menu');await physicalNav('Stock');
   if(round%5===0) await sampleMemory('navigation-'+round);
  }
  await shot('stress-final');
 }

 if(Number(process.env.AITA_VISIBILITY_SECONDS||0)>0) {
  const stockStatusRequests=()=>calls.filter(c=>c.path==='/market/seller/stock-status').length;
  assert.ok(stockStatusRequests()>0,'publication polling is active before hiding');
  await page.evaluate(()=>{Object.defineProperty(document,'visibilityState',{configurable:true,get:()=> 'hidden'});document.dispatchEvent(new Event('visibilitychange'));});
  await page.waitForTimeout(800);const hiddenCount=stockStatusRequests();await sampleMemory('synthetic-hidden-start');
  await page.waitForTimeout(Number(process.env.AITA_VISIBILITY_SECONDS)*1000);
  assert.equal(stockStatusRequests(),hiddenCount,'hidden page does not poll publication status');
  await sampleMemory('synthetic-hidden-end');
  await page.evaluate(()=>{delete document.visibilityState;document.dispatchEvent(new Event('visibilitychange'));});
  await until(()=>stockStatusRequests()>hiddenCount,'publication polling resumes once visible');await sampleMemory('synthetic-visible-resumed');
 }

 if(Number(process.env.AITA_FREEZE_CYCLES||0)>0) {
  await pageCdp.send('Page.enable');await pageCdp.send('Page.setLifecycleEventsEnabled',{enabled:true});
  pageCdp.on('Page.lifecycleEvent',event=>lifecycle.push(event));
  for(let cycle=1;cycle<=Number(process.env.AITA_FREEZE_CYCLES);cycle++) {
   await sampleMemory('before-freeze-'+cycle);
   await pageCdp.send('Page.setWebLifecycleState',{state:'frozen'});
   await new Promise(resolve=>setTimeout(resolve,Number(process.env.AITA_IDLE_MILLIS||2500)));
   await sampleMemory('frozen-'+cycle);
   await pageCdp.send('Page.setWebLifecycleState',{state:'active'});await page.waitForTimeout(1200);
   await physicalNav('Menu');await physicalNav('Stock');
   await sampleMemory('resumed-'+cycle);await shot('resumed-'+cycle);
  }
 }
 await sampleMemory('final');assert.deepEqual(errors,[]);assert.deepEqual(rendererCrashes,[]);console.log('BRANCH UI PASS:',scene);
} catch(error) {try{await shot('failure');}catch{}if(!errors.includes('crash')) {fs.writeFileSync(out+'/semantics.txt',await page.locator('body').evaluate(x=>x.outerHTML));fs.writeFileSync(out+'/buttons.json',JSON.stringify(await page.getByRole('button').evaluateAll(xs=>xs.map(x=>({name:x.getAttribute('aria-label'),text:x.innerText,html:x.outerHTML}))),null,2));}throw error;}
finally {
fs.writeFileSync(out+'/result.json',JSON.stringify({calls,errors,memory,rendererCrashes,consoleErrors,lifecycle},null,2));await browser.close();server.close();
}
})().catch(e=>{console.error(e);process.exitCode=1});
