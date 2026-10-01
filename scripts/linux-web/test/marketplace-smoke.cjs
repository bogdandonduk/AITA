// Isolated marketplace browser flow: every API call is mocked; no real account or stock changes.
const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path');
const dist=process.env.AITA_WEB_DIST;if(!dist)throw Error('Set AITA_WEB_DIST to the built distribution');
const output=path.join(process.env.AITA_ARTIFACTS||'build/marketplace-smoke','marketplace');
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
 const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US'});
 await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-market-token',refreshToken:'isolated-market-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-market-device');localStorage.setItem('aita.key_appTheme','7');},account);
 await context.routeWebSocket('**',ws=>ws.close());const calls=[],errors=[];
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
   const rows=offers(now).map(o=>({...o,saved:savedOffers.includes(o.id)})).filter(o=>(!q.savedOnly||o.saved)&&(!q.storefrontId||o.storefront.storeId===q.storefrontId)&&(!q.categoryId||o.categoryIds.includes(q.categoryId))&&(!q.text||o.title.toLowerCase().includes(q.text.toLowerCase())));
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
  else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'MARKETPLACE'};
  else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
  else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:false,payload:JSON.stringify(payload)})});
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));
 try{
  await page.goto('https://aita.kz/');await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(12000);
  await page.keyboard.press('Tab');await page.waitForTimeout(1000);
  const assert=require('node:assert/strict');
  const shot=async name=>page.screenshot({path:path.join(output,name+'.png')});
  const click=async(x,y)=>{await page.mouse.click(x,y,{delay:100});await page.waitForTimeout(1800);};
  const until=async check=>{for(let i=0;i<40&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),'Expected marketplace action did not complete');};
  assert.ok(calls.some(x=>x.path==='/market/discovery'));
  await shot('market-wide');
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(1200);await shot('market-phone');
  await page.setViewportSize({width:1440,height:1040});await page.waitForTimeout(1200);
  fs.writeFileSync(path.join(output,'home-aria.txt'),await page.locator('body').ariaSnapshot());
  await page.mouse.move(750,800);await page.mouse.wheel(0,450);await page.waitForTimeout(1000);await shot('home-promos');
  fs.writeFileSync(path.join(output,'promos-aria.txt'),await page.locator('body').ariaSnapshot());
  await page.getByRole('button').filter({hasText:/Mountain honey/}).first().click({force:true});await until(()=>calls.some(x=>x.path.endsWith('/detail')));await shot('product-detail');
  await click(460,483);await page.waitForTimeout(400);await shot('product-photo-two');
  await click(610,142);
  await shot('product-locations');
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(600);await shot('locations-phone');
  await page.setViewportSize({width:1440,height:1040});await page.waitForTimeout(600);await click(445,142);
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(1200);await shot('detail-phone');
  await page.keyboard.press('Escape');await page.waitForTimeout(800);
  await page.setViewportSize({width:1440,height:1040});await page.waitForTimeout(1200);
  await click(165,155);await until(()=>calls.some(x=>x.path==='/market/shops/search'));await shot('shops');
  await click(321,347);await until(()=>savedShops.includes(id(80)));await shot('shop-saved');
  await click(625,1005);await click(165,155);
  await until(()=>calls.some(x=>x.path==='/market/shops/search' && JSON.parse(x.body).savedOnly));await shot('saved-shops');
  await click(810,1005);await shot('shopping-list');await click(330,190);await shot('trip-wide');
  await click(202,605);await until(()=>lines.filter(l=>l.collected).length===1);await shot('trip-collected');
  await click(331,477);await shot('trip-remaining');await click(230,477);
  await page.mouse.move(700,700);await page.mouse.wheel(0,530);await page.waitForTimeout(1200);await shot('trip-cleanup');
  await click(716,942);assert.equal(lines.filter(l=>l.collected).length,1);await shot('trip-reset-review');
  await click(719,545);await until(()=>lines.every(l=>!l.collected));assert.equal(shoppingRevision,3);
  await click(202,605);await until(()=>lines.filter(l=>l.collected).length===1);
  await page.mouse.move(700,700);await page.mouse.wheel(0,530);await page.waitForTimeout(1200);
  await click(716,895);assert.equal(lines.length,3,'Review must not remove anything');await shot('trip-remove-review');
  await page.keyboard.press('Escape');await page.waitForTimeout(600);assert.equal(lines.length,3,'Dismissing review must preserve the list');
  await click(716,895);await click(719,545);await until(()=>lines.length===2);assert.ok(lines.every(l=>l.offerId!==id(101)));assert.equal(shoppingRevision,5);
  await shot('trip-removed');await page.setViewportSize({width:390,height:844});await page.waitForTimeout(1200);await shot('trip-phone');
  await page.reload();await page.waitForTimeout(10000);await shot('restored');
  await page.setViewportSize({width:1440,height:1040});await page.waitForTimeout(1200);
  await click(450,1005);await page.waitForTimeout(1200);await page.keyboard.press('Tab');
  assert.match(await page.locator('body').ariaSnapshot(), /Find your next favourite shop/, 'The marketplace Shops section must survive reload');
  await page.getByRole('button',{name:'Products',exact:true}).click({force:true});await page.waitForTimeout(1200);
  const addOats = page.getByRole('button',{name:'Add to list',exact:true}).nth(1);
  for(let i=0;i<12;i++) {
   const box=await addOats.boundingBox().catch(()=>null);
   if(box && box.y>180 && box.y+box.height<980) break;
   await page.mouse.move(750,800);await page.mouse.wheel(0,300);await page.waitForTimeout(600);
  }
  await addOats.click({force:true});
  await until(()=>lines.some(l=>l.offerId===id(104)));assert.equal(shoppingRevision,6);await shot('offer-added');
  assert.deepEqual(errors,[]);
  assert.equal(lines.length,3);assert.deepEqual(savedShops,[id(80)]);
  const changes=calls.filter(x=>x.path==='/market/shopping-list/checklist').map(x=>JSON.parse(x.body));
  assert.deepEqual(changes.map(x=>x.checklistChange.action),['collect','uncheck','collect','remove_collected']);
  assert.ok(changes.every(x=>x.checklistChange.offerIds.length===1 && x.checklistChange.offerIds[0]===id(101)));
  console.log('MARKETPLACE PASS: responsive catalogue, gallery, saved shops, shopping-trip checkmarks, frozen removal review and reload; isolated account only');
 }catch(error){await page.screenshot({path:path.join(output,'failure.png')});throw error;}finally{fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({errors,calls},null,2));await browser.close();server.close();}
 console.log('Marketplace evidence saved:',output);
})().catch(e=>{console.error(e);process.exitCode=1;});
