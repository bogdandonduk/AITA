// Supplier offer drafts and malformed account-cache recovery. Every API is mocked; no production writes.
// AITA_TEST_MALFORMED_ACCOUNT=1 selects the startup recovery regression.
const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path'),assert=require('node:assert/strict');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-pass013-supplier-browser';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
const malformedCache=process.env.AITA_TEST_MALFORMED_ACCOUNT==='1';
let account={id:id(99),publicId:'SUPPLIER-TEST',phoneNumber:'',email:'supplier@example.test',firstName:'Supplier',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:JSON.stringify([id(70)]),createdAt:1,isActive:true,appLanguage:'en',appModeId:2,appFontId:'noto_sans'};
const profile={id:id(70),userIds:[account.id],name:tr('Mountain Foods'),isActive:true};
const stores=[{id:id(31),title:'Central branch'},{id:id(32),title:'Riverside branch'}];
const qty={id:'0',immutableUnitName:tr('pc.'),total:12,pricedAmount:1,roundTotal:true};
const price=(amount)=>({price:String(amount),currency:'KZT',supplierId:profile.id});
const orders=stores.map((s,i)=>({order:{id:id(201+i),storeId:s.id,supplierId:profile.id,userId:account.id,status:'Sent',orderedAtMillis:100,createdAtMillis:100,updatedAtMillis:200,storeNameSnapshot:tr(s.title),storePublicIdSnapshot:'STORE-'+(31+i),storeAddressTextSnapshot:s.title+' address',isActive:true},lines:[{id:id(301+i),orderId:id(201+i),goodsItemId:id(401+i),requestedQuantity:qty,expectedSupplyPrice:price(100+i*25),goodsItemNameSnapshot:tr('Mountain honey'),goodsItemBarcodeSnapshots:['4006381333931'],goodsItemMeasurementUnitIdSnapshot:'0',isActive:true}]}));
let prices=stores.map((s,i)=>({id:id(501+i),userId:account.id,storeId:s.id,supplierId:profile.id,goodsItemId:id(401+i),supplyPrice:price(100+i*25),supplierGoodsName:'Mountain honey',supplierBarcode:'HONEY-'+(31+i),isActive:true,updatedAtMillis:100}));
const dashboard=()=>({supplierIds:[profile.id],supplierProfiles:[{supplierId:profile.id,name:profile.name,orderCount:2,openOrderCount:2,catalogSkuCount:1,savedOfferCount:2,partnerCount:2}],generatedAtMillis:Date.now(),orderCount:2,openOrderCount:2,actionRequiredOrderCount:2,lineCount:2,catalogSkuCount:1,partnerCount:2,partnerHighlights:stores.map(s=>({storeId:s.id,storeNameSnapshot:tr(s.title),storePublicIdSnapshot:'STORE-'+s.id.slice(-2),orderCount:1,openOrderCount:1,catalogSkuCount:1,savedOfferCount:1,validOfferCount:1,latestStatus:'Sent'}))});
const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});const context=await browser.newContext({viewport:{width:1440,height:1100},locale:'en-US'});
await context.addInitScript(account=>{if(!localStorage.getItem('aita.auth_tokens'))localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-supplier-token',refreshToken:'isolated-supplier-refresh'}));if(!localStorage.getItem('aita.user_account'))localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-supplier-test');},malformedCache?{...account,supplierAccountIds:[id(70)]}:account);
const calls=[],errors=[],checks=[],commandErrors=[];let appState={revision:0,enabled:true},delaySave=0,stateOffline=false;
await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers();calls.push({path:p,method:req.method(),supplierId:headers.supplier_id,storeId:headers.store_id,body:req.postData()});let payload=[];
 if(p==='/auth/session')payload={};
 else if(p==='/stores/get'&&calls.filter(c=>c.path===p).length>60)return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 else if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:2};
 else if(p==='/user/app-state'){if(stateOffline)return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});if(req.method()==='PUT'){const b=req.postDataJSON();appState={revision:appState.revision+1,enabled:b.enabled,document:b.document,updatedAtMillis:Date.now()};}payload={state:appState};}
 else if(p==='/suppliers/get')payload=[profile];
 else if(p==='/supplierOrders/get')payload=orders;
 else if(p==='/supplierOrders/dashboard')payload=dashboard();
 else if(p==='/supplierGoodsPrices/my'||p==='/supplierGoodsPrices/get')payload=prices;
 else if(p==='/supplierGoodsPrices/upsert'){const b=req.postDataJSON();assert.equal(b.supplierId,profile.id);assert.ok(stores.some(s=>s.id===b.storeId));if(delaySave)await new Promise(r=>setTimeout(r,delaySave));payload={...b,updatedAtMillis:Date.now()};prices=prices.filter(x=>x.id!==b.id).concat(payload);}
 else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'SUPPLIER'};
 else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
 else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:null,negative:false,payload:JSON.stringify(payload)})});
});
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));page.setDefaultTimeout(15000);
const shot=async name=>{await page.screenshot({path:out+'/'+name+'.png'});console.log(out+'/'+name+'.png');};
try{
await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(10000);await page.keyboard.press('Tab');await page.waitForTimeout(500);await shot('initial');console.log('READY',out);
const until=async(check,message)=>{for(let i=0;i<100;i++){if(await check())return;await page.waitForTimeout(150);}throw Error(message);};
const clickName=async(name,nth=0)=>{await page.getByRole('button',{name,exact:true}).nth(nth).click({force:true});await page.waitForTimeout(1200);};
const clickText=async(text)=>{await page.getByRole('button').filter({hasText:new RegExp('^'+text+'$')}).first().click({force:true});await page.waitForTimeout(1200);};
const openProduct=async()=>{await page.mouse.click(470,629);await page.waitForTimeout(1200);await clickName('Edit');};
const draftKey='supplier-offer-draft:'+account.id+':'+profile.id+':'+stores[0].id+':'+id(401);
const localDraft=()=>page.evaluate(key=>{for(const name of Object.keys(localStorage)){if(!name.startsWith('aita.ui_draft_'))continue;try{const value=JSON.parse(localStorage.getItem(name));const raw=value.document?.hosts?.SupplierCatalogMainNavigationScreenModelRoute?.[key];if(raw)return JSON.parse(raw);}catch{}}return null;},draftKey);
if(malformedCache){
 await until(async()=>await page.getByRole('button',{name:'Catalog',exact:true}).count()>0,'Account did not recover from malformed cache');
 const countAtReady=calls.filter(c=>c.path==='/stores/get').length;await page.waitForTimeout(5000);
 const storeRequests=calls.filter(c=>c.path==='/stores/get').length;
 assert.ok(calls.some(c=>c.path==='/user/get'),'Authoritative account was never requested');
 assert.ok(storeRequests<=8,'Unbounded stores/get requests: '+storeRequests);
 assert.ok(storeRequests-countAtReady<=2,'Store reads continued without invalidation');
 assert.equal(await page.evaluate(()=>typeof JSON.parse(localStorage.getItem('aita.user_account')).supplierAccountIds),'string');
 assert.ok(await page.evaluate(()=>!!localStorage.getItem('aita.auth_tokens')));
 await shot('malformed-cache-recovered');checks.push('Malformed cached account recovered; session retained; bounded store requests='+storeRequests);
}else{
 await clickName('Catalog');await shot('catalog-wide');await openProduct();await shot('offer-editor');
 await page.getByRole('textbox',{name:'Supply price • KZT',exact:true}).click({force:true});
 await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('Backspace');await page.keyboard.type('137',{delay:100});
 await until(async()=>(await localDraft())?.fields.price==='137','Edited offer was not persisted locally');
 assert.equal((await localDraft()).owner,draftKey);checks.push('Edited offer journal is scoped to account, supplier, store and goods');
 await shot('unsaved-central');
 await clickText('Overview');await shot('overview');await clickText('Store offers');
 assert.equal((await localDraft()).fields.price,'137');
 await clickName('Catalog');await openProduct();assert.equal((await localDraft()).fields.price,'137');
 await shot('draft-after-navigation');
 assert.equal(calls.filter(c=>c.path==='/supplierGoodsPrices/upsert').length,0);checks.push('Tabs and catalogue navigation preserve draft without sending offer');
 // Make cloud UI-state unavailable: reload must recover this draft from the device journal.
 stateOffline=true;await page.reload();await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(10000);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 await openProduct();await shot('draft-after-local-reload');
 assert.equal((await localDraft()).fields.price,'137');
 assert.equal(calls.filter(c=>c.path==='/supplierGoodsPrices/upsert').length,0);checks.push('Page reload restores local draft while account-state endpoint is unavailable');
 await clickName('Save offer');await until(()=>calls.some(c=>c.path==='/supplierGoodsPrices/upsert'),'Save was not submitted');
 const changes=calls.filter(c=>c.path==='/supplierGoodsPrices/upsert').map(c=>JSON.parse(c.body));assert.equal(changes.length,1);
 assert.equal(changes[0].storeId,stores[0].id);assert.equal(changes[0].supplierId,profile.id);assert.equal(changes[0].goodsItemId,id(401));assert.equal(changes[0].supplyPrice.price,'137');
 assert.equal(prices.find(p=>p.storeId===stores[1].id).supplyPrice.price,'125');checks.push('Explicit save submits restored137 exactly once to Central; Riverside stays125');
 await shot('saved-central');await page.setViewportSize({width:390,height:844});await page.waitForTimeout(1200);await shot('supplier-phone');
}
assert.deepEqual(errors,[]);checks.push('No JavaScript exception or renderer crash');
console.log('SUPPLIER BROWSER PASS:',checks.join('; '));
} catch(error){await shot('failure');throw error;
}finally{fs.writeFileSync(out+'/result.json',JSON.stringify({checks,errors,commandErrors,calls,prices,appState},null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
