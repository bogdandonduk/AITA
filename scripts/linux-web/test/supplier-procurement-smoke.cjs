// Isolated browser regression. Every API call is intercepted; no production account is used.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene='physical';
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-supplier-procurement';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
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
await context.addInitScript(()=>{window.print=function(){top.__labelPrints=(top.__labelPrints||0)+1;top.__printedText=document.body.innerText;setTimeout(()=>window.dispatchEvent(new Event("afterprint")),50);};});
await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-store-token',refreshToken:'isolated-store-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-store-test');},account);
let addedBatches=[],addedItems=[],batchAttempts=0,offline=false;
const supplier={id:id(70),userIds:[account.id],name:tr('Mountain Foods'),isActive:true};
let supplierOrders=[],createAttempts=[],receiveAttempts=[],lostCreate=true,lostReceipt=true;
const receipts=new Map();
const calls=[],errors=[],memory=[],rendererCrashes=[],consoleErrors=[],lifecycle=[];const browserCdp=await browser.newBrowserCDPSession();await browserCdp.send('Target.setDiscoverTargets',{discover:true});browserCdp.on('Target.targetCrashed',event=>rendererCrashes.push(event));await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({path:p,method:req.method(),storeId,body:req.postData()});if(offline)return route.abort('internetdisconnected');let payload=[];
 if(p==='/auth/session'||p==='/auth/ping')payload={};
 else if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
 else if(p==='/stores/get')payload=[parent];
 else if(p==='/suppliers/get')payload=[supplier];
 else if(p==='/supplierOrders/get')payload=supplierOrders;
 else if(p==='/supplierOrders/dashboard')payload={supplierIds:[supplier.id],supplierProfiles:[],generatedAtMillis:Date.now()};
 else if(p==='/supplierOrders/add'){
  const request=req.postDataJSON();createAttempts.push(request);assert.ok(request.order.id);
  payload=supplierOrders.find(o=>o.order.id===request.order.id);
  if(!payload){payload={order:{...request.order,userId:account.id,createdAtMillis:Date.now(),updatedAtMillis:Date.now()},lines:request.lines.map((line,i)=>({...line,id:id(800+i),receivedQuantityTotal:0,deliveredBatchIds:[]}))};supplierOrders.push(payload);}
  if(lostCreate)return route.fulfill({status:503,headers:{'content-type':'application/json','X-AITA-Server':'AITA'},body:JSON.stringify({negative:true,payload:null})});
 }
 else if(p==='/supplierOrders/receive'){
  const request=req.postDataJSON();receiveAttempts.push(request);assert.ok(request.receiptId);
  payload=supplierOrders.find(o=>o.order.id===request.orderId);assert.ok(payload);
  if(!receipts.has(request.receiptId)){
   for(const input of request.receivedLines){const line=payload.lines.find(l=>l.id===input.orderLineId);assert.ok(line);assert.ok(input.receivedQuantity.total>0&&input.receivedQuantity.total<=line.requestedQuantity.total-line.receivedQuantityTotal);line.receivedQuantityTotal+=input.receivedQuantity.total;const batch={id:id(900+addedBatches.length),storeId:physical.id,goodsItemId:line.goodsItemId,quantity:input.receivedQuantity,supplyPrice:input.actualSupplyPrice,isActive:true,status:'Delivered'};addedBatches.push(batch);line.deliveredBatchIds.push(batch.id);}
   payload.order.status=payload.lines.every(l=>l.receivedQuantityTotal===l.requestedQuantity.total)?'Delivered':'PartiallyDelivered';payload.order.updatedAtMillis=Date.now();receipts.set(request.receiptId,request);
  }
  if(lostReceipt)return route.fulfill({status:503,headers:{'content-type':'application/json','X-AITA-Server':'AITA'},body:JSON.stringify({negative:true,payload:null})});
 }
 else if(p==='/stock/get')payload=[itemFor(storeId),...addedItems];
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

const page=await context.newPage();page.setDefaultTimeout(25000);page.on('pageerror',e=>errors.push(String(e)));page.on('crash',()=>errors.push('crash'));
const shot=async name=>{await page.screenshot({path:out+'/'+name+'.png'});fs.writeFileSync(out+'/'+name+'.txt',await page.locator('body').ariaSnapshot());};
const button=name=>page.getByRole('button',{name,exact:true});
const click=async name=>{await button(name).first().click({force:true});await page.waitForTimeout(700);};
const enter=async(name,value)=>{await page.getByRole('textbox',{name,exact:true}).first().click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('Backspace');await page.keyboard.insertText(value);await page.waitForTimeout(300);};
const until=async(check,label)=>{for(let n=0;n<120&&!check();n++)await page.waitForTimeout(250);assert.ok(check(),label);};
try{
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(5000);await page.keyboard.press('Tab');
 await click('Stock');await shot('stock');
 await button('svg/29_0.svg').first().click({force:true});await page.waitForTimeout(700);
 await shot('edit-item');
 await page.mouse.move(1200,108);await page.mouse.wheel(1500,0);await page.waitForTimeout(700);
 await click(/^Orders/);await shot('order-form');
 if(process.env.AITA_SUPPLIER_PROBE==='1')return;
 await enter('Quantity','10');await enter('Expected supply price','100');
 await click('Send to supplier');await until(()=>createAttempts.length>0,'Order was sent');
 await button('Retry saved request').waitFor({timeout:60000});await shot('lost-order-response');
 const original=createAttempts[0];assert.equal(supplierOrders.length,1);lostCreate=false;
 await click('Retry saved request');await until(()=>createAttempts.length>=2,'Order was retried');
 assert.deepEqual(createAttempts.at(-1),original);assert.equal(supplierOrders.length,1);
 await click('History');await shot('order-history');
 await enter('Quantity received now','4');await click('Receive delivery');await click('Confirm');
 await until(()=>receiveAttempts.length>0,'Receipt submitted');await page.waitForTimeout(2500);await shot('lost-receipt-response');
 const originalReceipt=receiveAttempts[0];assert.equal(addedBatches.length,1);lostReceipt=false;
 await button('Retry saved receipt').waitFor({timeout:60000});
 assert.equal(await page.getByRole('textbox',{name:'Quantity received now',exact:true}).count(),0,'Unconfirmed quantity must be frozen');
 await click('Retry saved receipt');await click('Confirm');await until(()=>receiveAttempts.length>=2,'Receipt retry submitted');await page.waitForTimeout(2000);
 assert.deepEqual(receiveAttempts.at(-1),originalReceipt);assert.equal(addedBatches.length,1);await shot('partial-delivery');
 lostReceipt=true;await enter('Quantity received now','6');await click('Receive delivery');await click('Confirm');await until(()=>addedBatches.length===2,'Remaining delivery submitted');
 await button('Retry saved receipt').waitFor({timeout:60000});const finalReceipt=receiveAttempts.at(-1);await shot('full-delivery-response-lost');
 assert.notEqual(finalReceipt.receiptId,originalReceipt.receiptId);assert.equal(supplierOrders[0].order.status,'Delivered');
 // Reload hydrates the server's Delivered state plus the device's unacknowledged receipt.
 await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6000);await page.keyboard.press('Tab');
 await button('Retry saved receipt').waitFor({timeout:60000});await shot('delivered-pending-retry-after-reload');
 const beforeRetry=receiveAttempts.length;lostReceipt=false;await click('Retry saved receipt');await click('Confirm');await until(()=>receiveAttempts.length>beforeRetry,'Delivered-order acknowledgment retried');await page.waitForTimeout(1500);
 assert.deepEqual(receiveAttempts.at(-1),finalReceipt);assert.equal(addedBatches.length,2);assert.equal(supplierOrders[0].lines[0].receivedQuantityTotal,10);await shot('completed-delivery');
 assert.deepEqual(errors,[]);console.log('PASS: stable order retry, partial receipt retry without duplicate stock, full delivery');
}catch(error){await shot('failure');throw error;}
finally{fs.writeFileSync(out+'/result.json',JSON.stringify({errors,calls,createAttempts,receiveAttempts,supplierOrders,addedBatches},null,2));await browser.close();server.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
