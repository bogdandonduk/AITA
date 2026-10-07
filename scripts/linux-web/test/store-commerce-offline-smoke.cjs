// Isolated browser regression. Every API call is intercepted; no production account is used.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene=process.env.AITA_TEST_SCENE||'physical';
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-'+'receipt-batch-actions-smoke';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
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
await context.addInitScript(()=>{window.print=function(){top.__printAttempts=(top.__printAttempts||0)+1;if(top.__failPrint)throw Error("Test printer failure");top.__labelPrints=(top.__labelPrints||0)+1;top.__printedText=document.body.innerText;setTimeout(()=>window.dispatchEvent(new Event("afterprint")),50);};});
await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-store-token',refreshToken:'isolated-store-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-store-test');},account);
let addedBatches=[],addedItems=[],batchAttempts=0,offline=false,updatedBatches=[],completedTransactions=[],writeOffs=[];
let buyers=[{id:id(710),storeId:physical.id,name:'Loyal buyer',email:'loyal@example.test',discountPercent:7,promoTitle:'Welcome',revision:1,isActive:true}];
const parentBuyer={id:id(711),storeId:parent.id,name:'Parent buyer',phone:'+77000000000',discountPercent:5,revision:1,isActive:true};
let loseWriteOffReply=true;
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
 else if(p==='/stock/get')payload=[itemFor(storeId),...addedItems];
 else if(p==='/stock/add'){payload=req.postDataJSON();if(!addedItems.some(i=>i.id===payload.id))addedItems.push(payload);}
 else if(p==='/stockBatches/get')payload=[...batchesFor(storeId).map(b=>updatedBatches.find(x=>x.id===b.id)||b),...addedBatches];
 else if(p==='/stockBatches/update'){payload=req.postDataJSON().map(b=>({...b,expectedUpdatedAtMillis:null,updatedAtMillis:2}));updatedBatches=payload;}
 else if(p==='/transactions/complete'){payload={...req.postDataJSON(),id:id(800),receiptNumber:'00000000800'};completedTransactions.push(payload);}
 else if(p.endsWith('/buyers')) {
  const targetStore=p.split('/')[2];
  if(req.method()==='POST') {const request=req.postDataJSON();payload={...request.buyer,revision:request.buyer.revision+1,updatedAtMillis:Date.now()};buyers=buyers.filter(b=>b.id!==payload.id).concat(payload);}
  else payload={storeId:targetStore,buyers:buyers.filter(b=>b.storeId===targetStore),parentBuyers:targetStore===parent.id?[]:[parentBuyer]};
 }
 else if(p.endsWith('/writeoffs')) {
  if(req.method()==='POST') {const command=req.postDataJSON();let old=writeOffs.find(r=>r.record.command.id===command.id);
   if(old)payload=old;else {const b=updatedBatches.find(b=>b.id===command.batchId)||batchesFor(command.storeId).find(b=>b.id===command.batchId);assert.ok(b);assert.ok(command.quantity<=b.quantity.total);
    const batch={...b,quantity:{...b.quantity,total:b.quantity.total-command.quantity},updatedAtMillis:Date.now()};updatedBatches=updatedBatches.filter(x=>x.id!==b.id).concat(batch);
    payload={batch,record:{command,goodsItemId:b.goodsItemId,goodsName:tr('Mountain honey'),quantityUnit:{...b.quantity,total:command.quantity},supplyPrice:b.supplyPrice,cost:command.quantity*100,actorUserId:account.id,timeMillis:Date.now(),batchRemaining:batch.quantity.total}};writeOffs.push(payload);
   }
    if(loseWriteOffReply){loseWriteOffReply=false;offline=true;return route.abort('internetdisconnected');}
  } else payload={records:writeOffs.map(v=>v.record)};
 }
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
const until=async(check,label)=>{for(let i=0;i<240&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),label);};
try {
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(7000);await page.keyboard.press('Tab');
 await button('Stock').click({force:true});await page.waitForTimeout(1000);
 await button('Add batch').first().click({force:true});await page.waitForTimeout(500);await button('Back').last().click({force:true});await page.waitForTimeout(500);
 await button('svg/29_0.svg').nth(1).click({force:true});await page.waitForTimeout(500);
 await button('Write-offs').click({force:true});await page.waitForTimeout(500);
 await page.getByRole('textbox').first().fill('3',{force:true});await button('Write off').click({force:true});await page.waitForTimeout(300);await button('Confirm').last().click({force:true});
 await until(()=>writeOffs.length===1,'First write-off reached simulated server');await page.waitForTimeout(2500);
 await page.reload({waitUntil:'domcontentloaded'});await page.locator('canvas').first().waitFor({timeout:60000});await page.waitForTimeout(4000);await page.keyboard.press('Tab');
 await shot('offline-reloaded');fs.writeFileSync(out+'/offline-reloaded.txt',await page.locator('body').ariaSnapshot());
 assert.match(await page.locator('body').ariaSnapshot(),/9(?:\.0)? pc\./,'Offline reload retains the deducted stock');
 assert.equal(writeOffs.length,1);assert.equal(updatedBatches[0].quantity.total,9);
 offline=false;
 await button('Refresh').first().click({force:true});
 await until(()=>calls.filter(c=>c.path.endsWith('/writeoffs')&&c.method==='POST').length>=2,'Pending write-off replayed');await page.waitForTimeout(1500);
 const attempts=calls.filter(c=>c.path.endsWith('/writeoffs')&&c.method==='POST').map(c=>JSON.parse(c.body));
 assert.equal(new Set(attempts.map(a=>a.id)).size,1,'Lost acknowledgement must reuse the command identity');
 assert.equal(writeOffs.length,1);assert.equal(updatedBatches[0].quantity.total,9,'Retry must not subtract again');
 console.log('PASS: acknowledgement loss, offline reload, reconnect and identical replay leave one ledger entry and correct balance');
 await page.reload({waitUntil:'domcontentloaded'});await page.locator('canvas').first().waitFor({timeout:60000});await page.waitForTimeout(2500);await page.keyboard.press('Tab');
 await button('Menu').last().click({force:true});await page.waitForTimeout(600);await button('Analytics').click({force:true});await page.waitForTimeout(1800);
 fs.writeFileSync(out+'/analytics-open.txt',await page.locator('body').ariaSnapshot());
 await button('Write-offs').click({force:true});await page.waitForTimeout(900);await shot('writeoff-analytics');
 fs.writeFileSync(out+'/analytics.txt',await page.locator('body').ariaSnapshot());
 const analytics=await page.locator('body').ariaSnapshot();assert.match(analytics,/Damage \/ breakage/);assert.match(analytics,/300\.00 KZT/);
 console.log('PASS: analytics shows canonical write-off reason and cost after synchronization');

} catch(e){await shot('failure');fs.writeFileSync(out+'/failure-aria.txt',await page.locator('body').ariaSnapshot());throw e;}
finally {fs.writeFileSync(out+'/calls.json',JSON.stringify(calls,null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
