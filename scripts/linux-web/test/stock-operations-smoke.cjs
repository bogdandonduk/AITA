// Stock, batch, analytics and payment browser regression. All APIs use isolated fixtures.
// Set NODE_PATH to Playwright, AITA_WEB_DIST and optionally AITA_ARTIFACTS.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene=process.env.AITA_TEST_SCENE||'physical';
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'build/stock-operations-smoke';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
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
let addedBatches=[],batchAttempts=0;
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
 else if(p==='/stockBatches/get')payload=[...batchesFor(storeId),...addedBatches];
 else if(p==='/stockBatches/add') {
  batchAttempts++;
  if(batchAttempts===1)return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null,message:[{language:'en',value:'Test batch save unavailable'}]})});
  payload=req.postDataJSON().map((b,i)=>({...b,id:id(800+i)}));addedBatches.push(...payload);
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
const button=label=>page.getByRole('button',{name:label,exact:true});
// Compose draws pointer targets on canvas; semantics may briefly lag during scene animation.
const physicalNav=async label=>{await page.mouse.click(scene==='parent'?(label==='Menu'?792:648):(label==='Menu'?1008:864),1010);await page.waitForTimeout(700);};
const clickText=async label=>{const target=page.getByText(label,{exact:true});await target.first().waitFor({timeout:15000});await target.first().click({force:true});await page.waitForTimeout(650);};
const tab=async pattern=>{await page.getByRole('button').filter({hasText:pattern}).click({force:true});await page.waitForTimeout(600);};
const until=async(check,label)=>{for(let i=0;i<60&&!check();i++)await page.waitForTimeout(250);assert.ok(check(),label);};
try {
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(7000);await page.keyboard.press('Tab');
 await button('Stock').click({force:true});await page.waitForTimeout(1500);
 await button('Generic prices').click({force:true});await page.waitForTimeout(500);
 const prices=page.getByRole('textbox').filter({has:page.getByRole('button',{name:'KZT',exact:true})});
 async function typeIn(locator,text){await locator.click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.type(text);await page.waitForTimeout(300);}
 await typeIn(prices.nth(0),'123.45');await typeIn(prices.nth(1),'4');
 await page.getByRole('button').filter({hasText:/120%/}).click({force:true});await page.waitForTimeout(700);
 fs.writeFileSync(out+'/markup-aria.txt',await page.locator('body').ariaSnapshot());await shot('markup');
 await context.grantPermissions(['clipboard-read','clipboard-write']);
 async function fieldValue(locator){await locator.click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('ControlOrMeta+C');await page.waitForTimeout(150);const value=await page.evaluate(()=>navigator.clipboard.readText());await page.keyboard.press('ArrowRight');return value;}
 assert.equal(await fieldValue(prices.nth(1)), '148.14', 'Markup must replace previously typed sale value');
 await button('Add batch').click({force:true});await page.waitForTimeout(1000);
 fs.writeFileSync(out+'/batch-before-aria.txt',await page.locator('body').ariaSnapshot());await shot('batch');
 const qty=page.getByRole('textbox',{name:'Quantity',exact:true});await typeIn(qty,'2');
 await page.getByRole('button',{name:/^10 (pc\.|pcs\.)$/}).click({force:true});await page.waitForTimeout(500);
 assert.equal(await fieldValue(qty), '10', 'Quantity quick fill must replace previously typed value');
 fs.writeFileSync(out+'/batch-after-aria.txt',await page.locator('body').ariaSnapshot());await shot('batch-quick');
 await button('Standard').click({force:true});await page.waitForTimeout(350);await button('Returned').click({force:true});await page.waitForTimeout(400);
 await button('Confirm').click({force:true});await until(()=>batchAttempts===1,'First batch attempt');await page.waitForTimeout(700);
 assert.match(await page.locator('body').ariaSnapshot(),/Test batch save unavailable/,'Save errors visible');
 await button('Confirm').click({force:true});await until(()=>batchAttempts===2,'Batch retry');await page.waitForTimeout(1000);
 assert.equal(addedBatches[0].quantity.total,10);assert.equal(addedBatches[0].kind,'RETURNED');
 await button('Add batch').first().click({force:true});await page.waitForTimeout(1500);
 assert.equal(await button('Returned').count(),1,'Next batch restores last chosen kind');
 await shot('batch-restored');
 await button('Menu').click({force:true});await page.waitForTimeout(700);
 await button('Analytics').click({force:true});await page.waitForTimeout(1800);await shot('analytics');
 fs.writeFileSync(out+'/analytics-before.txt',await page.locator('body').ariaSnapshot());
 const analyticsBefore=await button('Today').boundingBox();
 await page.mouse.move(900,650);await page.mouse.wheel(0,650);await page.waitForTimeout(800);await shot('analytics-scrolled');
 fs.writeFileSync(out+'/analytics-after.txt',await page.locator('body').ariaSnapshot());
 const analyticsAfter=await button('Today').boundingBox().catch(()=>null);assert.ok(analyticsBefore && (!analyticsAfter || analyticsAfter.y < analyticsBefore.y-100),'Analytics filters scroll with the report');
 await button('Sale').last().click({force:true});await page.waitForTimeout(1000);
 await page.getByRole('button').filter({hasText:/^Mountain honey/}).click({force:true});await page.waitForTimeout(700);
 await button('Payment').click({force:true});await page.waitForTimeout(900);
 await button('Mixed').click({force:true});await page.waitForTimeout(700);
 await shot('mixed');fs.writeFileSync(out+'/mixed-aria.txt',await page.locator('body').ariaSnapshot());
 await context.grantPermissions(['clipboard-read','clipboard-write']);
 const fields=page.locator('[role="textbox"]');
 async function typePayment(index,text){await fields.nth(index).click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.type(text);await page.waitForTimeout(500);}
 async function value(index){await fields.nth(index).click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('ControlOrMeta+C');await page.waitForTimeout(100);const v=await page.evaluate(()=>navigator.clipboard.readText());await page.keyboard.press('ArrowRight');return v;}
 await typePayment(1,'75');assert.equal(await value(2),'175.00');
 await page.keyboard.press('ControlOrMeta+A');await page.keyboard.type('125');await page.waitForTimeout(700);
 assert.match(await page.locator('body').ariaSnapshot(),/Debt: 50\.00/, 'Editing card fills remaining debt');
 await page.mouse.move(1000,650);await page.mouse.wheel(0,-1800);await page.waitForTimeout(900);
 await page.getByRole('textbox',{name:'0.00',exact:true}).first().click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.type('20');await page.waitForTimeout(700);
 assert.equal(await value(2),'230.00');
 await shot('mixed-filled');
 console.log('PASS: mixed payment fills card and debt, then recomputes after cash edit');

 assert.deepEqual(errors,[]);
 console.log('STOCK OPERATIONS PASS: corrected prices, batch quick fill, failed-save retry, remembered choices, scrolling analytics and mixed-payment remainder');
} catch(e){await shot('failure');fs.writeFileSync(out+'/failure-aria.txt',await page.locator('body').ariaSnapshot());throw e;}
finally {fs.writeFileSync(out+'/calls.json',JSON.stringify(calls,null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
