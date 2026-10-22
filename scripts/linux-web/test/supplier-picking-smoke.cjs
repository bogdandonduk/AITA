// Quantity picking, offline journal and guarded dispatch in the compiled application. All APIs are mocked.
const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path'),assert=require('node:assert/strict');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-pass031-supplier-picking';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
const malformedCache=process.env.AITA_TEST_MALFORMED_ACCOUNT==='1';
let account={id:id(99),publicId:'SUPPLIER-TEST',phoneNumber:'',email:'supplier@example.test',firstName:'Supplier',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:JSON.stringify([id(70)]),createdAt:1,isActive:true,appLanguage:'en',appModeId:2,appFontId:'noto_sans'};
const profile={id:id(70),userIds:[account.id],name:tr('Mountain Foods'),isActive:true};
const stores=[{id:id(31),title:'Central branch'},{id:id(32),title:'Riverside branch'}];
const qty={id:'0',immutableUnitName:tr('pc.'),total:4,pricedAmount:1,roundTotal:true};
const price=(amount)=>({price:String(amount),currency:'KZT',supplierId:profile.id});
const orders=stores.map((s,i)=>({order:{id:id(201+i),storeId:s.id,supplierId:profile.id,userId:account.id,status:'Sent',orderedAtMillis:100,createdAtMillis:100,updatedAtMillis:200,storeNameSnapshot:tr(s.title),storePublicIdSnapshot:'STORE-'+(31+i),storeAddressTextSnapshot:s.title+' address',isActive:true},lines:[{id:id(301+i),orderId:id(201+i),goodsItemId:id(401+i),requestedQuantity:qty,expectedSupplyPrice:price(100+i*25),goodsItemNameSnapshot:tr('Mountain honey'),goodsItemBarcodeSnapshots:['4006381333931'],goodsItemMeasurementUnitIdSnapshot:'0',isActive:true}]}));
orders[0].lines.push({...orders[0].lines[0],id:id(399),goodsItemId:id(499),goodsItemNameSnapshot:tr('Alpine tea'),goodsItemBarcodeSnapshots:['TEA-123'],supplierAcceptedQuantity:{...qty,total:2}});
orders[0].lines.push({...orders[0].lines[0],id:id(398),goodsItemId:id(498),goodsItemNameSnapshot:tr('Mountain rice'),goodsItemBarcodeSnapshots:['RICE-WEIGHT'],goodsItemMeasurementUnitIdSnapshot:'1',supplierAcceptedQuantity:{...qty,id:'1',immutableUnitName:tr('kg'),roundTotal:false,total:3}});
let prices=stores.map((s,i)=>({id:id(501+i),userId:account.id,storeId:s.id,supplierId:profile.id,goodsItemId:id(401+i),supplyPrice:price(100+i*25),supplierGoodsName:'Mountain honey',supplierBarcode:'HONEY-'+(31+i),isActive:true,updatedAtMillis:100}));
const contracts=[{id:id(701),storeId:stores[0].id,supplierId:profile.id,authorUserId:account.id,title:tr('Central partnership'),summary:tr('Weekly shelf replenishment'),status:'active',storeNameSnapshot:tr('Central branch'),supplierNameSnapshot:profile.name,revision:1,createdAtMillis:100,updatedAtMillis:200,isActive:true}];
const dashboard=()=>({supplierIds:[profile.id],supplierProfiles:[{supplierId:profile.id,name:profile.name,orderCount:2,openOrderCount:2,catalogSkuCount:1,savedOfferCount:2,partnerCount:2}],generatedAtMillis:Date.now(),orderCount:2,openOrderCount:2,actionRequiredOrderCount:2,lineCount:2,catalogSkuCount:1,partnerCount:2,partnerHighlights:stores.map(s=>({storeId:s.id,storeNameSnapshot:tr(s.title),storePublicIdSnapshot:'STORE-'+s.id.slice(-2),orderCount:1,openOrderCount:1,catalogSkuCount:1,savedOfferCount:1,validOfferCount:1,latestStatus:'Sent'}))});
const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});const context=await browser.newContext({viewport:{width:1440,height:1100},locale:'en-US'});
await context.addInitScript(account=>{if(localStorage.getItem('supplier-test-initialized'))return;localStorage.setItem('supplier-test-initialized','true');if(!localStorage.getItem('aita.auth_tokens'))localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-supplier-token',refreshToken:'isolated-supplier-refresh'}));if(!localStorage.getItem('aita.user_account'))localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-supplier-test');},malformedCache?{...account,supplierAccountIds:[id(70)]}:account);
const calls=[],errors=[],checks=[],commandErrors=[];let appState={revision:0,enabled:true},delaySave=0,stateOffline=false;
await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers();calls.push({path:p,method:req.method(),supplierId:headers.supplier_id,storeId:headers.store_id,body:req.postData(),offline:stateOffline});if(stateOffline)return route.abort('internetdisconnected');let payload=[];
 if(p==='/auth/session')payload={};
 else if(p==='/stores/get'&&calls.filter(c=>c.path===p).length>60)return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 else if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:2};
 else if(p==='/user/app-state'){if(req.method()==='PUT'){const b=req.postDataJSON();appState={revision:appState.revision+1,enabled:b.enabled,document:b.document,updatedAtMillis:Date.now()};}payload={state:appState};}
 else if(p==='/suppliers/get')payload=[profile];
 else if(p==='/supplierOrders/get')payload=orders;
 else if(p==='/supplierOrders/status'){const body=req.postDataJSON();for(const bundle of orders)if(body.orderIds.includes(bundle.order.id))bundle.order.status=body.status;payload=orders.filter(bundle=>body.orderIds.includes(bundle.order.id));}
 else if(p==='/supplierOrders/dashboard')payload=dashboard();
 else if(p==='/supplierContracts/get')payload=contracts;
 else if(p==='/supplierGoodsPrices/my'||p==='/supplierGoodsPrices/get')payload=prices;
 else if(p==='/supplierGoodsPrices/upsert'){const b=req.postDataJSON();assert.equal(b.supplierId,profile.id);assert.ok(stores.some(s=>s.id===b.storeId));assert.equal(b.expectedUpdatedAtMillis,prices.find(x=>x.id===b.id)?.updatedAtMillis||0);if(delaySave)await new Promise(r=>setTimeout(r,delaySave));payload={...b,updatedAtMillis:Date.now()};prices=prices.filter(x=>x.id!==b.id).concat(payload);}
 else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'SUPPLIER'};
 else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
 else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:null,negative:false,payload:JSON.stringify(payload)})});
});
orders[0].order.status='Confirmed';orders[0].order.confirmedDeliveryTimeMillis=Date.now()+86400000;orders[0].lines[0].supplierAcceptedQuantity=qty;orders[0].lines.forEach(l=>l.supplierOfferedSupplyPrice=price(100));
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));page.setDefaultTimeout(15000);
const shot=async name=>{await page.screenshot({path:out+'/'+name+'.png'});console.log(out+'/'+name+'.png');};
try{
await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(10000);await page.keyboard.press('Tab');await page.waitForTimeout(500);
const until=async(check,message)=>{for(let i=0;i<100;i++){if(await check())return;await page.waitForTimeout(150);}throw Error(message);};
const reveal=async locator=>{for(let i=0;i<12;i++){const b=await locator.boundingBox(),v=page.viewportSize(),measurable=b&&b.width>1&&b.height>1;if(measurable&&b.y>=0&&b.y+b.height<=v.height)return;const delta=measurable?(b.y<0?b.y-v.height*.3:b.y+b.height-v.height*.65):500;await page.mouse.move(v.width*.75,v.height*.55);await page.mouse.wheel(0,Math.max(-1200,Math.min(1200,delta)));await page.waitForTimeout(300);}throw Error('Could not reveal Compose control');};
const clickName=async(name,nth=0)=>{
 const target=page.getByRole('button',{name,exact:true}).nth(nth);await reveal(target);
 if(name==='Add scanned quantity'){
  // Compose renders on canvas over its accessibility DOM. Click the observed canvas position;
  // a real pointer press spans frames while zero-duration forced DOM clicks can lose focus handoff.
  const b=await target.boundingBox();await page.mouse.click(b.x+b.width/2,b.y+b.height/2,{delay:60});
 }else await target.click({force:true});
 await page.waitForTimeout(800);
};
const clickText=async(text)=>{await page.getByRole('button').filter({hasText:new RegExp('^'+text+'$')}).first().click({force:true});await page.waitForTimeout(800);};
const openRun=async()=>{await clickName('Dispatch');await page.getByRole('list',{name:'Dispatch, Open delivery run',exact:true}).filter({hasText:'Central branch'}).click({force:true});await page.waitForTimeout(800);await clickName('Checklist');};
await openRun();await shot('picking-initial');
const text=()=>page.locator('body').ariaSnapshot();
await until(async()=>/Checked: 0\/3/.test(await text()),'Checklist did not load');
const edit=async(label,value,delay=30)=>{const unnamed=page.getByRole('textbox',{name:'',exact:true}).and(page.locator(':not(textarea):not(input)'));const field=label==='Quantity prepared'?unnamed.last():label==='Product, barcode or order'?unnamed.first():page.getByRole('textbox',{name:label,exact:true}).and(page.locator(':not(textarea):not(input)')).first();await reveal(field);await field.click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('Backspace');await page.keyboard.type(value,{delay});if(delay)await page.waitForTimeout(350);};
const scanInputValues=async()=>{const field=page.getByRole('textbox',{name:'Scan a picking barcode',exact:true}).and(page.locator(':not(textarea):not(input)')).first();await reveal(field);await field.click({force:true});await page.waitForTimeout(150);return page.locator('input,textarea').evaluateAll(xs=>xs.map(x=>x.value));};
const scan=async value=>{await edit('Scan a picking barcode',value,0);if(process.env.AITA_TEST_PICKING_BUTTON==='1')await clickName('Add scanned quantity');else await page.keyboard.press('Enter');await page.waitForTimeout(800);};
await scan('4006381333931123');assert.match(await text(),/This barcode is not an agreed item/);assert.match(await text(),/Checked: 0\/3/);assert.ok((await scanInputValues()).includes('4006381333931123'));checks.push('Longer code never picks a shorter prefix and is retained for correction');
await scan('4006381333931');assert.match(await text(),/Quantity prepared: 1 \/ 4/);assert.match(await text(),/Checked: 0\/3/);assert.ok(!(await scanInputValues()).includes('4006381333931'));checks.push((process.env.AITA_TEST_PICKING_BUTTON==='1'?'Explicit scan action':'Scanner Enter')+' adds one physical unit, not the whole order');
await clickName('Count quantity',0);await edit('Quantity prepared','5');await clickName('Confirm');assert.match(await text(),/Enter a quantity from zero/);checks.push('Over-picking is rejected inline');
await edit('Quantity prepared','3');await clickName('Confirm');await until(async()=>/Quantity prepared: 3 \/ 4/.test(await text()),'Partial quantity not saved');
await shot('picking-partial');
await clickName('Actions');await clickName('Mark packed');assert.equal(calls.filter(c=>c.path==='/supplierOrders/status').length,0);checks.push('Packing rejects incomplete physical checklist');
await clickName('Checklist');await scan('4006381333931');await scan('TEA-123');assert.match(await text(),/Checked: 1\/3/);
await edit('Product, barcode or order','honey');await page.waitForTimeout(500);assert.doesNotMatch(await text(),/Mountain rice/);
await scan('RICE-WEIGHT');assert.match(await text(),/Mountain rice/);assert.equal(await page.getByRole('textbox',{name:'',exact:true}).and(page.locator(':not(textarea):not(input)')).count(),2);
await edit('Quantity prepared','3');await clickName('Confirm');await until(async()=>/Checked: 2\/3/.test(await text()),'Weighted product scan did not reveal its editor');checks.push('Product-only weighted scan reveals the quantity editor even when a name filter hides its line');
await page.setViewportSize({width:390,height:844});await page.waitForTimeout(700);await shot('picking-phone');
stateOffline=true;await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(10000);await page.keyboard.press('Tab');await page.setViewportSize({width:1440,height:1100});await page.waitForTimeout(700);
await openRun();assert.match(await text(),/Checked: 2\/3/);assert.match(await text(),/Quantity prepared: 1 \/ 2/);await scan('TEA-123');await until(async()=>/Checked: 3\/3/.test(await text()),'Offline scan did not save');assert.ok(calls.some(c=>c.offline&&c.path==='/auth/session'),'Offline authentication must genuinely fail rather than be mocked successful');assert.match(await text(),/offline|Offline/);checks.push('All backend requests fail, including authentication: cached profile, orders and physical quantities survive reload; picking works offline');
await shot('picking-offline-complete');
assert.equal(calls.filter(c=>c.path==='/supplierOrders/status').length,0);checks.push('Physical counting never changes server order or inventory');
await clickName('Catalog');await shot('catalog-offline');fs.writeFileSync(out+'/catalog-offline-aria.txt',await text());assert.match(await text(),/Mountain honey/);assert.match(await text(),/100 KZT/);assert.match(await text(),/Saved offers: 2/);assert.match(await text(),/1 Price book/);
await clickName('Supplier contracts');await shot('contracts-offline');fs.writeFileSync(out+'/contracts-offline-aria.txt',await text());assert.match(await text(),/Central partnership/);checks.push('Cached supplier catalog prices and contract remain viewable while every backend endpoint is unavailable');
stateOffline=false;await openRun();await clickName('Actions');await shot('picking-ready-actions');
assert.match(await text(),/Fully checked orders: 1\/1/);
assert.deepEqual(errors,[]);checks.push('No JavaScript exception or renderer crash');
console.log('SUPPLIER PICKING BROWSER PASS:',checks.join('; '));
}catch(error){await shot('failure');fs.writeFileSync(out+'/failure-aria.txt',await page.locator('body').ariaSnapshot());throw error;
}finally{fs.writeFileSync(out+'/result.json',JSON.stringify({checks,errors,commandErrors,calls},null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
