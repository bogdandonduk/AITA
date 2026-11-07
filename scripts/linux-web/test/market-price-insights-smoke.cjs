// Real Compose/Wasm UI with synthetic accounts and a fully intercepted API. No production writes.
const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),http=require('node:http'),path=require('node:path');
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'build/pass037/market-price-ui';
if(!dist)throw Error('Set AITA_WEB_DIST to the compiled app');fs.mkdirSync(out,{recursive:true});
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0');
const tr=value=>[{language:'en',value},{language:'main',value}];
const account={id:id(99),publicId:'MARKET-PRICE-TEST',phoneNumber:'',workerAccountIds:null,supplierAccountIds:null,email:'price-report@example.test',firstName:'Price',lastName:'Test',countryLocale:'kz',createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',activeStoreId:id(31)};
const store={id:id(31),publicId:'STORE-31',parentStoreId:id(30),userIds:[account.id],storeTypeIds:[],name:tr('Price report branch'),alias:[],description:[],companyForms:[],location:{},address:'Test only',phoneNumbers:[],emails:[],countryLocales:['kz'],createdAt:1,branchType:'PHYSICAL',architectureVersion:2};
const parent={...store,id:id(30),publicId:'STORE-30',parentStoreId:null,name:tr('Price report parent'),branchType:null,branches:[store]};
const unit={id:'0',immutableUnitName:tr('pc.'),total:1,pricedAmount:1,roundTotal:true};
const goods=Array.from({length:32},(_,i)=>({id:id(3100+i),storeId:store.id,userId:account.id,name:tr(i===0?'Alpine tea':i===1?'Fresh milk':'Unmatched product '+i),barcodes:i<2?['4006381333931']:[],measurementUnitId:'0',salePrices:[{price:i===0?'150':'80',currency:'KZT',supplierId:''}],isActive:true}));
let offline=false,denied=false,priceCalls=[],apiCalls=[],errors=[],consoleMessages=[],delayPrices=0,priceNotification=null;
let watchFailReplies=false,watchCalls=[],watchState={accountId:account.id,storeId:store.id,revision:0,enabled:false,settings:{country:'kz',city:'',items:[],thresholdBasisPoints:500},observations:[],checkedAtMillis:null,nextCheckAtMillis:null,updatedAtMillis:0};
const watchRequests=new Map();
const rows=goods.map((g,i)=>({goodsItemId:g.id,name:g.name,barcodes:i<2?['04006381333931']:[],basis:i<2?{gtin:'04006381333931',currencyCode:'KZT',unitId:'0',pricedAmount:1}:null,
 ownPriceMinor:i===0?15000:8000,ownCurrencyCode:'KZT',referenceKind:'shelf',unitName:tr('pc.'),compatibleGroups:i<2?5:0,
 statistics:i<2?{groups:5,lowestMinor:9000,lowerQuartileMinor:9500,medianMinor:10000,upperQuartileMinor:11000,highestMinor:12000}:null,
 sampleLimited:i===1,updatedAtMillis:1}));
const server=http.createServer((req,res)=>{try{
 const p=path.resolve(dist,'.'+(req.url==='/'?'/index.html':decodeURIComponent(req.url.split('?')[0])));
 if(!p.startsWith(path.resolve(dist)+path.sep))throw Error('Invalid path');
 res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':p.endsWith('.json')?'application/json':p.endsWith('.png')?'image/png':p.endsWith('.ttf')?'font/ttf':'application/octet-stream');res.end(fs.readFileSync(p));
}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');
(async()=>{
 await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,channel:'chromium',args:['--enable-unsafe-swiftshader']});
 const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US',timezoneId:'Asia/Almaty',acceptDownloads:true});
 await context.addInitScript(account=>{if(!['http:','https:'].includes(location.protocol))return;
  if(!localStorage.getItem('aita.auth_tokens'))localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-price-token',refreshToken:'isolated-price-refresh'}));
  if(!localStorage.getItem('aita.user_account'))localStorage.setItem('aita.user_account',JSON.stringify(account));
  localStorage.setItem('aita.installation_id','isolated-price-test');
 },account);
 await context.addInitScript(()=>{window.print=function(){top.__pricePrints=top.__pricePrints||[];top.__pricePrints.push({text:document.body.innerText,html:document.documentElement.outerHTML});};});
 await context.routeWebSocket('**',ws=>ws.close());
 await context.route('**/*',async route=>{
  const req=route.request(),u=new URL(req.url());if(u.origin===origin)return route.continue();
  if(u.hostname==='aita.kz'&&!u.pathname.startsWith('/api/')){try{const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}catch{return route.abort();}}
  const p=u.pathname.replace(/^\/api\//,'/');apiCalls.push({path:p,method:req.method()});if(offline)return route.abort('internetdisconnected');
  let payload=[],status=200;
  if(p==='/auth/refresh')payload={accessToken:'isolated-price-token',refreshToken:'isolated-price-refresh',expiresAtMillis:Date.now()+3600000};
  else if(p==='/auth/session'||p==='/auth/ping')payload={};
  else if(p==='/auth/capabilities')payload={enabled:true,emailCodeLoginEnabled:true};
  else if(p==='/user/get'||p==='/user/update')payload=account;
  else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
  else if(p==='/user/app-state')payload={state:{revision:0,enabled:false}};
  else if(p==='/stores/get')payload=[parent];
  else if(p==='/stock/get')payload=goods;
  else if(p==='/notifications/get')payload=priceNotification?[priceNotification]:[];
  else if(p==='/notifications/read'){
   assert.equal(req.method(),'PUT');assert.ok(req.postDataJSON().includes(priceNotification.id));
   priceNotification={...priceNotification,readAtMillis:Date.now()};payload=[priceNotification];
  }
  else if(p==='/stockBatches/get')payload=goods.map((g,i)=>({id:id(4000+i),goodsItemId:g.id,storeId:store.id,quantity:{...unit,total:4},supplyPrice:{price:'50',currency:'KZT',supplierId:''},status:'OnShelf',isActive:true}));
  else if(p==='/analytics/market-prices/watch'){
   assert.equal(req.headers().store_id,store.id);
   if(denied){status=403;payload=null;}
   else if(req.method()==='GET')payload=watchState;
   else{
    const body=req.postDataJSON();watchCalls.push(body);
    if(watchRequests.has(body.requestId))assert.deepEqual(body,watchRequests.get(body.requestId));
    else if(body.expectedRevision!==watchState.revision){status=409;}
    else{
     watchRequests.set(body.requestId,body);const now=Date.now();
     watchState={...watchState,revision:watchState.revision+1,enabled:body.enabled,settings:body.enabled?body.settings:watchState.settings,
      observations:body.settings.items.map(it=>({itemId:it.itemId,name:rows.find(row=>row.goodsItemId===it.itemId).name,basis:{gtin:it.gtin,currencyCode:'KZT',unitId:'0',pricedAmount:1},medianMinor:10000,baselineMinor:10000,groups:5,checkedAtMillis:now,status:'watching'})),
      checkedAtMillis:now,nextCheckAtMillis:body.enabled?now+86400000:null,updatedAtMillis:now};
    }
    payload=watchState;
    if(watchFailReplies){status=503;payload=null;}
   }
  }
  else if(p==='/analytics/market-prices'){
   assert.equal(req.method(),'GET');assert.equal(req.headers().store_id,store.id);
   const request={country:u.searchParams.get('country')||'kz',city:u.searchParams.get('city')||'',search:u.searchParams.get('search')||'',limit:Number(u.searchParams.get('limit')||30)};
   for(const key of ['after','itemId','gtin'])if(u.searchParams.has(key))request[key]=u.searchParams.get(key);
   priceCalls.push(request);
   if(delayPrices)await new Promise(r=>setTimeout(r,delayPrices));
   if(denied){status=403;payload=null;}else{
    const matches=rows.filter(row=>(!request.itemId||row.goodsItemId===request.itemId)&&(!request.search||row.name.some(x=>x.value.toLowerCase().includes(request.search.toLowerCase()))));
    const after=matches.filter(row=>!request.after||row.goodsItemId>request.after),visible=after.slice(0,request.limit);
    payload={accountId:account.id,storeId:store.id,request,rows:visible,totalItems:matches.length,nextId:after.length>request.limit?visible.at(-1).goodsItemId:null,checkedAtMillis:Date.now(),cities:['Almaty','Astana']};
   }
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
 const click=async(label,direction=0)=>{const target=b(label).last();
  for(let i=0;direction&&i<50&&!(await target.count());i++){const size=page.viewportSize();await page.mouse.move(size.width/2,size.height*.65);await page.mouse.wheel(0,direction*650);await page.waitForTimeout(150);}
  await target.waitFor({timeout:45000});await target.scrollIntoViewIfNeeded();await page.waitForTimeout(600);await target.click({force:true});await page.waitForTimeout(500);
 };
 const openStockPrices=async()=>{
  await click('Stock');const target=b('Market prices').last();await target.waitFor({state:'attached',timeout:30000});
  // The stock tools are a horizontally scrollable row on a phone-sized viewport.
  // Scroll the actual Compose canvas rather than assuming every toolbar button is visible.
  for(let n=0;n<8&&!(await target.isVisible());n++){
   const anchor=await b('Import stock').boundingBox()||await b('Purchase planner').boundingBox();
   assert.ok(anchor,'stock tools have a visible scroll anchor');
   await page.mouse.move(page.viewportSize().width/2,anchor.y+anchor.height/2);
   await page.mouse.wheel(500,0);await page.waitForTimeout(350);
  }
  await click('Market prices');
 };
 const text=label=>page.getByText(label,{exact:true});
 const shot=async name=>{await page.screenshot({path:path.join(out,name+'.png')});fs.writeFileSync(path.join(out,name+'.txt'),await page.locator('body').ariaSnapshot());};
 const wait=async(test,label)=>{for(let i=0;i<160&&!test();i++)await page.waitForTimeout(250);assert.ok(test(),label);};
 try{
  await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6500);await page.keyboard.press('Tab');
  await openStockPrices();
  await wait(()=>priceCalls.length>0,'opened price report');await text('Comparable items on this page: 2 / 30').waitFor({timeout:45000});
  if(process.env.AITA_TEST_PRICE_REGION_ONLY!=='1'){
  await shot('wide-overview');
  await text('Alpine tea').last().scrollIntoViewIfNeeded();await page.waitForTimeout(500);
  await b('Details').first().click({force:true});await page.waitForTimeout(700);await shot('price-details');
  await text('Middle 50% of prices: 95.00 KZT — 110.00 KZT').waitFor({timeout:10000});
  watchFailReplies=true;await click('Follow daily',1);
  await wait(()=>watchCalls.length>0,'watch save reached server');
  await text('Could not confirm the alert settings. Connect and refresh or retry saving.').first().waitFor({timeout:30000});
  assert.equal(watchState.enabled,true,'server committed despite lost reply');
  watchFailReplies=false;
  await click('Daily price alerts · 0 / 10',-1);await click('Retry saving');
  await b('Daily price alerts · 1 / 10').waitFor({timeout:30000});
  assert.ok(watchCalls.length>=2);assert.equal(new Set(watchCalls.map(x=>x.requestId)).size,1,'retry uses identical idempotency key');
  await text('Following the market median').waitFor({timeout:10000});await shot('daily-price-alerts');
  await click('Stop following');await b('Daily price alerts · 0 / 10').waitFor({timeout:30000});assert.equal(watchState.enabled,false);
  await click('Daily price alerts · 0 / 10');
  await click('Above median',1);await text('50% · Above median').waitFor({timeout:10000});
  assert.equal(await text('Fresh milk').count(),0);
  await click('Below median');await text('Fresh milk').waitFor({timeout:10000});await shot('below-median');
  await click('All');await click('Next items',1);await text('Comparable items on this page: 0 / 2').waitFor({timeout:30000});
  assert.equal(priceCalls.at(-1).after,id(3129));await click('Previous items');await text('Comparable items on this page: 2 / 30').waitFor({timeout:30000});
  await click('Print / PDF',-1);await click('Save PDF via print dialog');
  await page.waitForFunction(()=>window.__pricePrints?.length>0,{},{timeout:45000});
  const printed=await page.evaluate(()=>window.__pricePrints.at(-1));
  assert.match(printed.text,/Alpine tea/);assert.match(printed.text,/150.00 KZT/);assert.match(printed.text,/100.00 KZT/);
  assert.match(printed.text,/Price report branch/);assert.match(printed.text,/5/);
  assert.equal((printed.html.match(/<section class="report-section">/g)||[]).length,32,'header, method and each of 30 products keep their own print section');
  fs.writeFileSync(path.join(out,'market-prices.html'),printed.html);
  const pdfPage=await context.newPage();await pdfPage.setContent(printed.html);await pdfPage.evaluate(()=>document.fonts.ready);
  const bytes=await pdfPage.pdf({preferCSSPageSize:true,printBackground:true});await pdfPage.close();fs.writeFileSync(path.join(out,'market-prices.pdf'),bytes);
  const media=bytes.toString('latin1').match(/\/MediaBox\s*\[\s*0\s+0\s+([\d.]+)\s+([\d.]+)\s*\]/);
  assert.ok(media);assert.ok(Math.abs(Number(media[1])-595)<1);assert.ok(Math.abs(Number(media[2])-842)<1);
  await page.bringToFront();await page.keyboard.press('Tab');await page.waitForTimeout(700);
  await click('Cancel');await page.keyboard.press('Tab');await page.waitForTimeout(700);
  offline=true;await click('Refresh prices',-1);await page.getByText(/Saved report · prices may have changed/).first().waitFor({timeout:45000});
  await shot('offline-report');
  await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6500);await page.keyboard.press('Tab');
  await openStockPrices();await text('Comparable items on this page: 2 / 30').waitFor({timeout:45000});
  await shot('offline-restored');
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(1000);
  await shot('narrow-report');
  offline=false;denied=true;await click('Refresh prices',-1);
  await text('Stock and analytics access are required for this store.').waitFor({timeout:30000});
  assert.equal(await text('Alpine tea').count(),0,'403 removes the previously cached report');await shot('access-denied');
  offline=true;await click('Refresh prices');
  await text('No saved report for these filters yet. Connect and try again.').waitFor({timeout:45000});
  assert.equal(await text('Alpine tea').count(),0,'offline retry cannot resurrect denied report');
  offline=false;denied=false;await click('Refresh prices');await text('Comparable items on this page: 2 / 30').waitFor({timeout:30000});
  }
  await click('Kazakhstan · All cities in this country',-1);
  await click('Kazakhstan');await click('Kyrgyzstan');
  const fields=page.getByRole('textbox').and(page.locator(':not(textarea):not(input)'));
  assert.equal(await fields.count(),2,'city and stock-search fields');
  // Compose's semantic textboxes forward keyboard input to the focused canvas editor.
  // Native fill() can otherwise append both strings to the first focused field.
  await fields.nth(0).click({force:true});await page.waitForTimeout(250);await page.keyboard.type('Bishkek');
  await fields.nth(1).click({force:true});await page.waitForTimeout(250);await page.keyboard.type('Alpine');await click('Compare prices');
  await text('Comparable items on this page: 1 / 1').waitFor({timeout:30000});
  assert.equal(priceCalls.at(-1).country,'kg');assert.equal(priceCalls.at(-1).city,'Bishkek');assert.equal(priceCalls.at(-1).search,'Alpine');
  await shot('regional-search');
  await click('Cancel');await shot('closed-report-stock');
  // Compose/Wasm 1.9.x can retain a dismissed dialog's accessible tree (CMP-10623).
  // A real restart also proves that the selected region/search were durably saved.
  await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6500);await page.keyboard.press('Tab');
  await openStockPrices();
  await b('Kyrgyzstan · Bishkek').waitFor({timeout:30000});await text('Comparable items on this page: 1 / 1').waitFor({timeout:30000});
  offline=true;await click('Kyrgyzstan · Bishkek');await click('Choose a saved report');
  await page.getByRole('button',{name:/Kazakhstan[\s\S]*30 items/}).click({force:true});
  await text('Comparable items on this page: 2 / 30').waitFor({timeout:30000});
  await page.getByText(/Saved report · prices may have changed/).first().waitFor({timeout:30000});
  await shot('saved-report-picker');
  await click('Previous items',-1);await b('Kyrgyzstan · Bishkek').waitFor({timeout:30000});
  await text('Comparable items on this page: 1 / 1').waitFor({timeout:30000});
  offline=false;await click('Kyrgyzstan · Bishkek');await click('Compare prices');
  await text('Comparable items on this page: 1 / 1').waitFor({timeout:30000});
  assert.equal(priceCalls.at(-1).country,'kg');assert.equal(priceCalls.at(-1).city,'Bishkek');assert.equal(priceCalls.at(-1).search,'Alpine','Back restores the editable filters as well as the report');
  offline=false;const alertTime=Date.now();
  priceNotification={id:'price-alert-fixture',userId:account.id,storeId:store.id,title:'Market price change',message:'Alpine tea: the market median changed.',
   type:'Neutral',category:'market_prices',source:'server',createdAtMillis:alertTime,shownAtMillis:alertTime,readAtMillis:null,isSavedOnServer:true,
   metadata:{country:'kg',city:'Bishkek',priceChanges:JSON.stringify({schema:1,accountId:account.id,storeId:store.id,country:'kg',city:'Bishkek',checkedAtMillis:alertTime,
    changes:[{itemId:goods[0].id,name:goods[0].name,basis:rows[0].basis,previousMinor:10000,currentMinor:11000}]})}};
  await click('Cancel');
  await page.setViewportSize({width:1440,height:1040});
  await page.reload();await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6500);await page.keyboard.press('Tab');
  await click(/^Menu(?:, Unread: \d+)?$/);await shot('unread-alert-menu');await click(/^Notifications(?: · \d+)?$/,1);
  await click('Unread (1)');
  await click('Price changes in this alert · 1',1);
  await page.getByText('Previous median', {exact:false}).last().waitFor({timeout:30000});
  const alertText=await page.locator('body').ariaSnapshot();
  assert.match(alertText,/Previous median 100\.00 KZT New median 110\.00 KZT Change: \+10%/);await shot('historical-price-alert');
  await click('Market prices',1);await b('Kyrgyzstan · Bishkek').waitFor({timeout:30000});
  await text('No active price alerts. Open an item’s details to follow it.').waitFor({timeout:30000});
  assert.equal(priceCalls.at(-1).country,'kg');assert.equal(priceCalls.at(-1).city,'Bishkek');
  await shot('notification-opens-region');
  assert.equal(priceNotification.readAtMillis,null,'unread item stays mounted while its report is open');
  await click('Cancel');await wait(()=>priceNotification.readAtMillis!=null,'closing the report acknowledges the alert');
  assert.deepEqual(errors,[]);assert.deepEqual(consoleMessages.filter(m=>m.type==='error'&&/Exception|RuntimeError|unreachable/i.test(m.text)),[]);
  console.log(process.env.AITA_TEST_PRICE_REGION_ONLY==='1'
   ? 'PASS regional search, offline saved-report selection, restored editable filters and unread historical alert deep link'
   : 'PASS price watch retry and removal, report, coverage, differences, details, pagination, PDF, offline restart, narrow layout, permission revocation, recovery, regional search, offline saved-report selection and unread historical alert deep link');
 }catch(error){await shot('failure').catch(()=>{});throw error;}
 finally{
  fs.writeFileSync(path.join(out,'evidence.json'),JSON.stringify({regionOnly:process.env.AITA_TEST_PRICE_REGION_ONLY==='1',priceCalls,watchCalls,watchState,apiCalls,errors,consoleMessages},null,2));
  await browser.close();await new Promise(r=>server.close(r));
 }
})().catch(e=>{console.error(e.stack||e);process.exitCode=1;server.close();});
