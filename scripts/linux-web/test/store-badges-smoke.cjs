// Official app-store badges at desktop and phone sizes. All APIs use isolated fixtures.
// Set NODE_PATH to Playwright, AITA_WEB_DIST and optionally AITA_ARTIFACTS.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene='parent';
const employee=process.env.AITA_TEST_WORKER_EMPLOYEE==='1';
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'build/worker-placement-smoke';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));if(/aita-install\.(ps1|py)$/.test(p)){res.setHeader('Content-Disposition','attachment; filename=\"'+path.basename(p)+'\"');}res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
let account={id:id(99),publicId:'STORE-TEST',phoneNumber:'',email:'store@example.test',firstName:'Branch',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:process.env.AITA_TEST_LANGUAGE||'en',appModeId:0,appFontId:'noto_sans',activeStoreId:id(scene==='parent'?30:(scene==='physical'||scene==='cart')?31:32)};
if(employee)account={...account,id:id(98),publicId:'EMPLOYEE-A',workerAccountIds:JSON.stringify([id(900)]),firstName:'Aida',lastName:'Team'};
const store=(n,name,parent,type)=>({id:id(n),publicId:'STORE-'+n,parentStoreId:parent? id(parent):null,userIds:[id(99)],storeTypeIds:[],name:tr(name),alias:[],description:[],companyForms:[],location:{},address:name+' address',phoneNumbers:[],emails:[],countryLocales:['kz'],createdAt:1,branchType:type,architectureVersion:2});
const physical=store(31,'West pickup branch',30,'PHYSICAL'),internet=store(32,'Internet branch',30,'INTERNET'),parent={...store(30,'Management warehouse',null,null),branches:[physical,internet]};
const stores=[parent,physical,internet],quantity={id:'0',immutableUnitName:tr('pc.'),total:12,pricedAmount:1,roundTotal:true},price={price:'250',currency:'KZT',supplierId:''};
const itemFor=storeId=>({id:id(Number(storeId.slice(-2))*100),storeId,name:tr('Mountain honey'),description:[],barcodes:['4006381333931'],measurementUnitId:'0',salePrices:[price],supplyPrices:[{...price,price:'100'}],activeShelfBatchId:id(Number(storeId.slice(-2))*100+1),isActive:true});
const batchesFor=storeId=>[1,2].map(n=>({id:id(Number(storeId.slice(-2))*100+n),storeId,goodsItemId:itemFor(storeId).id,quantity:{...quantity,total:n===1?12:6},supplyPrice:{...price,price:'100'},salePriceOverride:price,status:'Delivered',shelfPriority:3-n,isActive:true}));
let shop={storeId:internet.id,branchStoreId:internet.id,displayName:'Public internet shop',city:'Almaty',publicAddress:'Internet pickup counter',pickupNote:'Confirm before arrival',published:true,revision:1,shareBranchAvailability:true},selected=[physical.id];
const locations=[{storeId:parent.id,name:parent.name,address:parent.address,warehouse:true},{storeId:physical.id,name:physical.name,address:physical.address,warehouse:false}];
const dashboard=()=>({storefront:shop,listings:[],locationStoreIds:selected,availableLocations:locations});
const browser=await chromium.launch({headless:true,channel:'chromium',args:['--enable-unsafe-swiftshader']});const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US'});
await context.addInitScript(account=>{if(!localStorage.getItem('aita.auth_tokens'))localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-store-token',refreshToken:'isolated-store-refresh'}));if(!localStorage.getItem('aita.user_account'))localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-store-test');},account);
let addedBatches=[],batchAttempts=0;
let workers=[{id:id(900),storeId:parent.id,storePublicId:parent.publicId,storeName:parent.name,userId:id(98),userPublicId:'EMPLOYEE-A',firstName:'Aida',lastName:'Team',roleId:'standard',permissions:['stock_read'],jobTitle:'Assistant',salary:'120000',acceptedAtMillis:1,isActive:true,hasWorkshiftPassword:true},
{id:id(901),storeId:physical.id,storePublicId:physical.publicId,storeName:physical.name,userId:id(97),userPublicId:'EMPLOYEE-B',firstName:'Boris',lastName:'Team',roleId:'standard',permissions:['stock_read'],jobTitle:'Assistant',salary:'120000',acceptedAtMillis:1,isActive:true,hasWorkshiftPassword:true}];
const operations={};let placementAttempts=0;
const calls=[],errors=[],memory=[],rendererCrashes=[],consoleErrors=[],lifecycle=[];const browserCdp=await browser.newBrowserCDPSession();await browserCdp.send('Target.setDiscoverTargets',{discover:true});browserCdp.on('Target.targetCrashed',event=>rendererCrashes.push(event));await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({path:p,method:req.method(),storeId,body:req.postData()});let payload=[];
 if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
 else if(p==='/stores/get')payload=[parent];
 else if(p==='/workers/my/get')payload=workers.filter(w=>w.isActive&&w.userId===account.id);
 else if(p==='/stores/active'){const value=req.postData().trim();const selected=value.startsWith('"')?JSON.parse(value):value;assert.ok(stores.some(s=>s.id===selected));account={...account,activeStoreId:selected};payload={};}
 else if(p==='/workers/store/get')payload=workers.filter(w=>w.isActive);
 else if(p==='/workers/placement'){
  const c=req.postDataJSON();placementAttempts++;
  assert.ok(c.operationId);assert.ok(c.sourceStoreId!==c.destinationStoreId);
  if(!operations[c.operationId]){
   const old=workers.find(w=>w.id===c.membershipId);assert.ok(old?.isActive);
   const target=stores.find(s=>s.id===c.destinationStoreId);assert.equal(target.parentStoreId,parent.id);
   const placed={...old,id:id(1000+placementAttempts),storeId:target.id,storePublicId:target.publicId,storeName:target.name};
   if(old.storeId!==parent.id)old.isActive=false;workers.push(placed);operations[c.operationId]=placed;
  }
  if(placementAttempts===1)return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null,message:[{language:'en',value:'Temporary connection interruption; retry'}]})});
  payload=operations[c.operationId];
 }
 else if(p==='/stock/get')payload=[itemFor(storeId)];
 else if(p==='/stockBatches/get')payload=[...batchesFor(storeId),...addedBatches];
 else if(p==='/stockBatches/add') {
  batchAttempts++;
  if(batchAttempts===1)return route.fulfill({status:400,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null,message:[{language:'en',value:'Test batch save unavailable'}]})});
  payload=req.postDataJSON();addedBatches.push(...payload);
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
const helperDownloads=[];
context.on('page',opened=>opened.on('download',download=>helperDownloads.push(download)));
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.stack||e.message));page.on('crash',()=>errors.push('crash'));page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});

const shot=async name=>{await page.screenshot({path:out+'/'+scene+'-'+name+'.png'});};
const button=label=>page.getByRole('button',{name:label,exact:true});
// Compose draws pointer targets on canvas; semantics may briefly lag during scene animation.
const physicalNav=async label=>{await page.mouse.click(scene==='parent'?(label==='Menu'?792:648):(label==='Menu'?1008:864),1010);await page.waitForTimeout(700);};
const clickText=async label=>{const target=page.getByText(label,{exact:true});await target.first().waitFor({timeout:15000});await target.first().click({force:true});await page.waitForTimeout(650);};
const tab=async pattern=>{await page.getByRole('button').filter({hasText:pattern}).click({force:true});await page.waitForTimeout(600);};
const until=async(check,label)=>{for(let i=0;i<60&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),label);};
const reveal=async(locator)=>{for(let i=0;i<12;i++){const b=await locator.boundingBox().catch(()=>null);if(b && b.y>350 && b.y+b.height<965)return;await page.mouse.move(1100,700);await page.mouse.wheel(0,b&&b.y<350?-250:250);await page.waitForTimeout(350);}throw Error('Control did not become visible');};
try {
 const ru=process.env.AITA_TEST_LANGUAGE==='ru';
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(7500);await page.keyboard.press('Tab');
 await button(ru?'Меню':'Menu').click({force:true});await page.waitForTimeout(800);
 await page.mouse.move(130,780);await page.mouse.wheel(0,650);await page.waitForTimeout(600);await button(ru?'Загрузки':'Downloads').click({force:true});await page.waitForTimeout(1200);
 for(const platform of ['iOS','macOS']) {
  await button(platform).click({force:true});await page.waitForTimeout(600);
  assert.ok(await page.getByRole('button',{name:(ru?'Открыть магазин':'Open store')+' · App Store',exact:true}).count());
  await shot('badge-'+platform+(ru?'-ru':'-en'));
 }
 // Script methods remain available even with no downloadable app entry or a failing feed.
 for(const [platform,file,command] of [
  ['Windows','aita-install.ps1','powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$HOME\\Downloads\\aita-install.ps1"'],
  ['Linux','aita-install.py','python3 ./aita-install.py']
 ]) {
  await button(platform).click({force:true});await page.waitForTimeout(500);
  const method=button(ru?'Установить или обновить скриптом':'Install or update with a script');
  await method.waitFor({timeout:10000});await method.click({force:true});await page.waitForTimeout(500);
  assert.ok(await page.getByText(command,{exact:true}).count(),'Helper command visible for '+platform);
  await shot('installer-method-'+platform+(ru?'-ru':'-en'));
  await button(ru?'Скачать скрипт':'Download the script').click({force:true});
  for(let i=0;i<40&&!helperDownloads.some(d=>d.suggestedFilename()===file);i++)await page.waitForTimeout(200);
  const download=helperDownloads.find(d=>d.suggestedFilename()===file);
  assert.ok(download,'Browser receives script file '+file);
  assert.equal(await download.failure(),null,'Script transfer completes without an HTTP/download failure');
  const saved=await download.path();assert.ok(saved,'Completed script download has a local file');
  const expected=fs.readFileSync(path.join(dist,'install',file));
  assert.ok(expected.length>1000,'Packaged helper must contain the implementation, not an empty placeholder');
  assert.deepEqual(fs.readFileSync(saved),expected,'Downloaded helper bytes match the packaged verified-source file');
  await button(ru?'Отмена':'Cancel').click({force:true});await page.waitForTimeout(500);
 }
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(600);await shot('badges-phone');
 assert.deepEqual(errors,[]);console.log('PASS: official badges and script download methods rendered and actionable, locale '+(ru?'ru':'en'));
}catch(e){await shot('failure');fs.writeFileSync(out+'/failure-aria.txt',await page.locator('body').ariaSnapshot());throw e;}
finally {fs.writeFileSync(out+'/result.json',JSON.stringify({calls,errors},null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
