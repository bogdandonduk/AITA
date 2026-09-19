// Dropdown styling, category search, disabled security choices and update URL regression. All API calls are intercepted.
// Set NODE_PATH to the installed browser-test dependencies, AITA_WEB_DIST and AITA_ARTIFACTS.
// AITA_TEST_THEME=0 (light) or 1 (dark).
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene='physical';
const fs=require('fs'),http=require('http'),path=require('path');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-dropdown-browser';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url.split('?')[0]==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
let account={id:id(99),publicId:'STORE-TEST',phoneNumber:'',email:'store@example.test',firstName:'Branch',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',appThemeId:Number(process.env.AITA_TEST_THEME||0),activeStoreId:id(scene==='parent'?30:(scene==='physical'||scene==='cart')?31:32)};
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
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname+u.search});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({path:p,method:req.method(),storeId,body:req.postData()});let payload=[];
 if(p==='/auth/session')payload={};
 else if(p==='/auth/capabilities')payload={enabled:true,emailSecondFactorEnabled:false,authenticatorTwoFactorEnabled:true,authenticatorLoginPolicyEnabled:true};
 else if(p==='/auth/security/settings')payload={email:account.email,emailVerified:true,authenticatorEnabled:false};
 else if(p==='/generic/goodsCategories/get')payload=[{id:'food',typeIds:[],name:tr('Food'),quantityUnitId:'0',imagePaths:null},{id:'hardware',typeIds:[],name:tr('Hardware'),quantityUnitId:'0',imagePaths:null}];
 else if(p==='/user/get')payload=account;
 else if(p==='/user/update'||p==='/user/preferences/update'){account={...account,...req.postDataJSON()};payload=account;}
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
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.stack||e.message));page.on('crash',()=>errors.push('crash'));page.setDefaultTimeout(12000);
const checks=[];
const shot=async name=>{await page.screenshot({path:out+'/'+name+'.png'});console.log('SCREENSHOT',name);};
const click=async label=>{await page.getByRole('button',{name:label,exact:true}).first().click({force:true});await page.waitForTimeout(650);};
const tab=async label=>{await page.getByRole('button').filter({hasText:new RegExp('^'+label+'$')}).first().click({force:true});await page.waitForTimeout(650);};
const until=async(check,label)=>{for(let n=0;n<100;n++){if(await check())return;await page.waitForTimeout(150);}throw Error(label);};
try {
 await page.goto('https://aita.kz/?keep=a%20b&_aita_build=13&_aita_refresh=1501#stock');
 await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(8000);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 assert.equal(page.url(),'https://aita.kz/?keep=a%20b#stock');checks.push('Actual Wasm startup cleans update-only URL parameters');
 await click('Stock');await until(()=>calls.some(x=>x.path==='/stock/get'),'stock loaded');await page.waitForTimeout(800);
 await click('Add batch');await shot('batch-closed');
 const inputs=await page.getByRole('textbox').count();await click('Standard');
 await shot('batch-open');assert.equal(await page.getByRole('textbox').count(),inputs,'Batch type needs no search field');
 for(const name of ['Standard','Returned','Universal'])assert.ok(await page.getByRole('button',{name,exact:true}).count()>0);
 await click('Universal');await until(()=>page.getByText('A simple stock pool. Quantities and units remain separate for each item.',{exact:true}).count().then(n=>n>0),'Universal selection applies');
 await shot('batch-universal');await click('Universal');await click('Returned');await shot('batch-returned');
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(600);await click('Returned');await shot('batch-narrow-open');await click('Standard');
 await page.setViewportSize({width:1440,height:1040});await page.waitForTimeout(600);await click('Cancel');
 checks.push('Batch kind opens, selects and closes with no search in wide and narrow layouts');
 await page.mouse.move(1060,110);await page.mouse.wheel(-2200,0);await page.waitForTimeout(600);await tab('Info');await shot('item-info');
 fs.writeFileSync(out+'/info-buttons.json',JSON.stringify(await page.getByRole('button').evaluateAll(xs=>xs.map(x=>({text:x.innerText,name:x.getAttribute('aria-label')}))),null,2));
 await page.mouse.move(1100,690);await page.mouse.wheel(0,650);await page.waitForTimeout(700);
 await click('Food');await shot('category-search-open');
 const search=page.getByRole('textbox',{name:'Search by any data',exact:true}).last();
 await search.click({force:true});await page.keyboard.type('Hard');await page.waitForTimeout(700);
 assert.equal(await page.getByRole('button',{name:'Hardware',exact:true}).count(),1);
 assert.equal(await page.getByRole('button',{name:'Food',exact:true}).count(),1,'Food remains only in the header after filtering');
 await shot('category-filtered');await click('Hardware');checks.push('Category search filters and selects');
 await click('Menu');await click('Sign-in & security');await page.waitForTimeout(900);
 const fields=await page.getByRole('textbox').count();await click('None');
 assert.equal(await page.getByRole('textbox').count(),fields,'Security method needs no search field');
 // Compose's Wasm accessibility bridge does not expose aria-disabled consistently.
 // Exercise the real pointer route and verify it cannot change security or close selection.
 const beforeChoice=calls.length;await click('Email');
 assert.equal(await page.getByRole('button',{name:'None',exact:true}).count(),2,'disabled choice leaves the dropdown open and None selected');
 assert.ok(!calls.slice(beforeChoice).some(c=>c.path.startsWith('/auth/') && c.method!=='GET'),'disabled choice cannot submit a security change');
 await shot('two-factor-open');
 await page.keyboard.press('Escape');await page.setViewportSize({width:390,height:844});await page.waitForTimeout(700);
 await click('None');await shot('two-factor-narrow');checks.push('Two-factor dropdown preserves availability in wide and narrow layouts');
 assert.deepEqual(errors,[]);assert.deepEqual(rendererCrashes,[]);console.log('DROPDOWN BROWSER PASS',checks.join('; '));
} catch(e){try{await shot('failure');fs.writeFileSync(out+'/buttons.json',JSON.stringify(await page.getByRole('button').evaluateAll(xs=>xs.map(x=>({text:x.innerText,name:x.getAttribute('aria-label'),html:x.outerHTML}))),null,2));}catch{}throw e;}
finally{fs.writeFileSync(out+'/result.json',JSON.stringify({checks,calls,errors,rendererCrashes},null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
