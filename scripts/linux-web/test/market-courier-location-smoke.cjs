// Isolated marketplace browser flow: every API call is mocked; no real account or stock changes.
const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path');
const dist=process.env.AITA_WEB_DIST;if(!dist)throw Error('Set AITA_WEB_DIST to the built distribution');
const output=path.join(process.env.AITA_ARTIFACTS||'build/marketplace-smoke','courier');
fs.mkdirSync(output,{recursive:true});
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0');
const account={id:id(99),publicId:'MARKET-TEST',phoneNumber:'',email:'market@example.test',firstName:'Aida',lastName:'',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:process.env.AITA_TEST_LANGUAGE||'en',appThemeId:Number(process.env.AITA_TEST_THEME||0),appModeId:1};
const shops=[{storeId:id(80),branchStoreId:id(180),shareBranchAvailability:true,displayName:'Green Basket',city:'Almaty',publicAddress:'Abay avenue, 42',pickupNote:'Collect your shopping at the front counter',published:true,revision:1},{storeId:id(81),branchStoreId:id(181),shareBranchAvailability:true,displayName:'Everyday Market',city:'Almaty',publicAddress:'Dostyk avenue, 18',pickupNote:'Open every day',published:true,revision:1}];
const categories=['Pantry','Fresh food','Home & care'].map((s,i)=>({id:id(i+1),name:[{language:'en',value:s}],ancestorIds:[]}));
function offers(now){return ['Mountain honey','Wholegrain bread','Garden tomatoes','Breakfast oats','Fresh milk','Everyday soap'].map((title,i)=>({id:id(101+i),storefront:shops[i%2],title,description:'A thoughtful everyday choice for your next shopping trip.',gtin:null,categoryIds:[categories[i%3].id],priceMinor:[285000,89000,125000,149000,75000,59000][i],originalPriceMinor:i===0?350000:null,currencyCode:'KZT',pricedAmount:1,unitId:'piece',unitName:[{language:'en',value:'pack'}],availability:'recorded_in_stock',checkedAtMillis:now,sourceUpdatedAtMillis:10,saved:i===2,product:{imageUrls:['https://media.example.com/product-'+i+'.svg','https://media.example.com/product-'+(i+1)+'.svg'],brand:'Everyday essentials',manufacturer:'Local producer',countryOfOrigin:'Kazakhstan',attributes:[{name:'Pack size',value:'1 pack'}]}}));}
function picture(i){const colors=['#d9a961','#c3ad86','#d9705a','#caa77e','#acc5d1','#92bda8'];return `<svg xmlns="http://www.w3.org/2000/svg" width="500" height="400" viewBox="0 0 500 400"><rect width="500" height="400" fill="#f1eee7"/><ellipse cx="250" cy="337" rx="85" ry="15" fill="#222" opacity=".08"/><rect x="175" y="87" width="150" height="240" rx="28" fill="${colors[i%6]}"/><rect x="193" y="173" width="114" height="103" rx="10" fill="#fff9ec"/><path d="M228 220l15 16 30-34" fill="none" stroke="#47534a" stroke-width="7" stroke-linecap="round"/><rect x="185" y="78" width="130" height="29" rx="8" fill="#536258"/></svg>`;}
(async()=>{
 const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));const bytes=fs.readFileSync(p);res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(bytes);}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');
 await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});
 const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US',geolocation:{latitude:43.25,longitude:76.95}});
 await context.grantPermissions(['geolocation'],{origin:'https://aita.kz'});
 await context.addInitScript(()=>{window.__deliveryWatches=new Set();const geo=navigator.geolocation;const watch=geo.watchPosition.bind(geo),clear=geo.clearWatch.bind(geo);geo.watchPosition=(...args)=>{const id=watch(...args);window.__deliveryWatches.add(id);return id;};geo.clearWatch=id=>{window.__deliveryWatches.delete(id);return clear(id);};});
 await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-market-token',refreshToken:'isolated-market-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-market-device');localStorage.setItem('aita.key_appTheme','7');},account);
 await context.routeWebSocket('**',ws=>ws.close());const calls=[],errors=[];
 let orders=[{id:id(777),buyerId:id(77),branchStoreId:shops[0].branchStoreId,shopName:shops[0].displayName,
 request:{storefrontId:shops[0].storeId,shoppingRevision:1,policyRevision:1,mode:'EXPRESS',address:{recipient:'Buyer',phone:'+77000000000',address:'Almaty, Abay 42'},lines:[{line:{offerId:id(101),storeId:shops[0].storeId,title:'Mountain honey',shopName:shops[0].displayName,units:1,basis:{currencyCode:'KZT',unitId:'piece',pricedAmount:1}},unitPriceMinor:285000}],currency:'KZT',feeMinor:90000},
 revision:5,status:'ON_THE_WAY',createdAtMillis:Date.now()-3600000,updatedAtMillis:Date.now(),estimatedFromMillis:Date.now()+600000,estimatedToMillis:Date.now()+1200000,courierId:account.id,courierName:'Aida',courierAccepted:true,events:[]}],deliveryCommands=[],loseDeliveryReply=false;
 let shoppingRevision=1,savedRevision=0,savedShops=[],savedOffers=[id(103)],records={};
 let lines=offers(Date.now()).slice(0,3).map((o,i)=>({offerId:o.id,storeId:o.storefront.storeId,title:o.title,shopName:o.storefront.displayName,units:i+1,basis:{gtin:null,currencyCode:o.currencyCode,unitId:o.unitId,pricedAmount:o.pricedAmount},unitName:o.unitName,updatedAtMillis:1,collected:false}));
 function shopping(now){return {userId:account.id,revision:shoppingRevision,lines:lines.map(line=>{const o=offers(now).find(x=>x.id===line.offerId);return {line,offer:o,unitPriceMinor:o.priceMinor,subtotalMinor:o.priceMinor*line.units,status:'estimated'};}),checkedAtMillis:now};}

 await context.route('**/*',async route=>{
  const u=new URL(route.request().url());if(u.origin===origin)return route.continue();
  if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
  if(u.hostname==='media.example.com')return route.fulfill({contentType:'image/svg+xml',body:picture(parseInt(u.pathname.match(/\d+/)[0]))});
  const p=u.pathname.replace(/^\/api\//,'/');calls.push({path:p,body:route.request().postData()});
  let payload=[];const now=Date.now();
  if(p==='/user/get')payload=account;
  else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:1};
  else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
  else if(p==='/market/discovery'){
   const input=route.request().postDataJSON();const q=input.query;
   const rows=offers(now).map(o=>({...o,saved:savedOffers.includes(o.id)})).filter(o=>(!q.brand||o.product.brand.toLowerCase().includes(q.brand.toLowerCase()))&&(!q.withPhotos||o.product.imageUrls.length>0)&&(!q.savedOnly||o.saved)&&(!q.storefrontId||o.storefront.storeId===q.storefrontId)&&(!q.categoryId||o.categoryIds.includes(q.categoryId))&&(!q.text||o.title.toLowerCase().includes(q.text.toLowerCase())));
   payload={query:q,limit:input.limit,page:{offers:rows,checkedAtMillis:now},totalOffers:rows.length,totalShops:new Set(rows.map(o=>o.storefront.storeId)).size,categoryVersion:'a'.repeat(64),categories,accountId:account.id,storefront:q.storefrontId?shops.find(s=>s.storeId===q.storefrontId):null};
  } else if(p==='/market/shopping-list' && route.request().method()==='GET')payload=shopping(now);
  else if(p==='/market/shopping-list' || p==='/market/shopping-list/checklist'){
   const command=route.request().postDataJSON();let error=null;
   if(!records[command.commandId]){
    if(command.expectedRevision!==shoppingRevision)error='market.shopping_changed';
    else if(command.checklistChange){const ch=command.checklistChange;const ids=new Set(ch.offerIds);
     if(ch.action==='remove_collected' && lines.some(l=>ids.has(l.offerId)&&!l.collected))error='market.checklist_changed';
     else {lines=ch.action==='remove_collected'?lines.filter(l=>!ids.has(l.offerId)):lines.map(l=>ids.has(l.offerId)?{...l,collected:ch.action==='collect',updatedAtMillis:now}:l);shoppingRevision++;}
    }else {let old=lines.find(l=>l.offerId===command.offerId);if(!old){const o=offers(now).find(o=>o.id===command.offerId);old={offerId:o.id,storeId:o.storefront.storeId,title:o.title,shopName:o.storefront.displayName,unitName:o.unitName};}
     lines=lines.filter(l=>l.offerId!==command.offerId);if(command.units>0)lines.push({...old,units:command.units,basis:command.basis,collected:false,updatedAtMillis:now});shoppingRevision++;}
    records[command.commandId]={commandId:command.commandId,accepted:!error,appliedRevision:error?null:shoppingRevision,errorKey:error};
   }
   payload={...records[command.commandId],snapshot:shopping(now)};
  }
  else if(p.startsWith('/market/offers/') && p.endsWith('/detail')){payload={accountId:account.id,offer:offers(now).find(o=>o.id===p.split('/')[3]),branchAvailability:[{branchId:id(301),name:[{language:'en',value:'West pickup branch'}],publicAddress:'Tole bi street, 10',availability:'recorded_in_stock',checkedAtMillis:now},{branchId:id(302),name:[{language:'en',value:'Main warehouse'}],publicAddress:'Warehouse road, 2',availability:'confirm_with_store',checkedAtMillis:now}],checkedAtMillis:now};}
  else if(p==='/market/saved-shops'){
   if(route.request().method()==='PUT'){const ch=route.request().postDataJSON();savedShops=savedShops.filter(x=>x!==ch.storeId);if(ch.saved)savedShops.push(ch.storeId);savedRevision++;}
   payload={accountId:account.id,revision:savedRevision,storeIds:savedShops,checkedAtMillis:now};
  }
  else if(p==='/market/shops/search'){const input=route.request().postDataJSON();const visible=shops.filter(s=>(!input.savedOnly||savedShops.includes(s.storeId))&&(!input.city||s.city.toLowerCase().includes(input.city.toLowerCase()))&&(!input.text||(s.displayName+' '+s.publicAddress).toLowerCase().includes(input.text.toLowerCase())));payload={accountId:account.id,request:input,shops:visible.map(storefront=>({storefront,publishedOffers:3,saved:savedShops.includes(storefront.storeId)})),totalShops:visible.length,checkedAtMillis:now};}
  else if(p.startsWith('/market/delivery/policy/'))payload={branchStoreId:shops.find(s=>s.storeId===p.split('/').at(-1)).branchStoreId,revision:1,modes:['STANDARD','EXPRESS','PICKUP'],currency:'KZT',standardFeeMinor:50000,expressFeeMinor:90000,standardMinDays:2,standardMaxDays:5,expressMinMinutes:45,expressMaxMinutes:120,serviceArea:'Almaty',instructions:'The shop confirms the request'};
  else if(p==='/market/delivery/commands'){
   const command=route.request().postDataJSON();deliveryCommands.push(command);let order=orders.find(o=>o.id===(command.orderId||command.commandId));
   if(command.action==='REQUEST'&&!order){order={id:command.commandId,buyerId:account.id,branchStoreId:shops.find(s=>s.storeId===command.request.storefrontId).branchStoreId,shopName:shops.find(s=>s.storeId===command.request.storefrontId).displayName,request:command.request,revision:0,status:'REQUESTED',createdAtMillis:now,updatedAtMillis:now,estimatedFromMillis:now+45*60000,estimatedToMillis:now+120*60000,handoverCode:'123456',events:[{action:'REQUEST',status:'REQUESTED',atMillis:now}]};orders.push(order);}
   if(command.action==='DELIVER'){order.status='DELIVERED';order.revision++;order.updatedAtMillis=now;order.handoverCode=null;order.events.push({action:'DELIVER',status:'DELIVERED',atMillis:now});}
   if(loseDeliveryReply){loseDeliveryReply=false;return route.abort('internetdisconnected');}
   payload={commandId:command.commandId,order,replayed:deliveryCommands.filter(x=>x.commandId===command.commandId).length>1};
  }
  else if(p==='/market/delivery/orders')payload={accountId:account.id,view:u.searchParams.get('view')||'buyer',orders:orders.map(o=>({...o,handoverCode:null,position:null})),checkedAtMillis:now};
  else if(p.endsWith('/position')){const point=route.request().postDataJSON();orders[0].position={...point.position,receivedAtMillis:now};payload=orders[0];}
  else if(p.startsWith('/market/delivery/orders/'))payload=orders.find(o=>o.id===p.split('/').at(-1));
  else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'MARKETPLACE'};
  else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
  else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:false,payload:JSON.stringify(payload)})});
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));
 const assert=require('node:assert/strict');
 const button=label=>page.getByRole('button',{name:label,exact:true});
 const shot=async name=>page.screenshot({path:path.join(output,name+'.png')});
 const until=async(check,label)=>{for(let i=0;i<120&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),label);};
 try {
  await page.goto('https://aita.kz/');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(7000);await page.keyboard.press('Tab');await page.waitForTimeout(700);
  await page.mouse.click(810,1005);await page.waitForTimeout(1800);await button('Courier jobs').click({force:true});await page.waitForTimeout(1200);
  await page.getByRole('button',{name:/Green Basket Express courier · On the way/}).click({force:true});await page.waitForTimeout(1000);
  fs.writeFileSync(path.join(output,'courier-aria.txt'),await page.locator('body').ariaSnapshot());await shot('courier-tracking');
  await button('Share my location while this order is open').click({force:true});
  await until(()=>calls.some(c=>c.path.endsWith('/position')),'Browser geolocation reached the courier endpoint');
  const point=JSON.parse(calls.find(c=>c.path.endsWith('/position')).body).position;
  assert.equal(point.latitude,43.25);assert.equal(point.longitude,76.95);
  assert.equal(await page.evaluate(()=>window.__deliveryWatches.size),1,'One active browser geolocation watcher');
  await page.keyboard.press('Escape');await page.waitForTimeout(1000);
  assert.equal(await page.evaluate(()=>window.__deliveryWatches.size),0,'Closing the order stops browser tracking');
  assert.deepEqual(errors,[]);console.log('PASS: explicit courier consent starts real browser geolocation API; closing the sheet removes its watcher');
 } catch(e){await shot('failure');fs.writeFileSync(path.join(output,'failure-aria.txt'),await page.locator('body').ariaSnapshot());throw e;}
 finally{fs.writeFileSync(path.join(output,'calls.json'),JSON.stringify(calls,null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
