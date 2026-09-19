// Parent marketplace-profile copying and branch save isolation. All API calls are mocked.
// AITA_TEST_PARENT=1 checks the management parent editor instead of the branch copy flow.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('fs'),http=require('http'),path=require('path');
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-parent-profile-smoke';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
let account={id:id(99),publicId:'STORE-TEST',phoneNumber:'',email:'store@example.test',firstName:'Branch',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',activeStoreId:id(process.env.AITA_TEST_PARENT==='1'?30:32)};
const store=(n,name,parent,type)=>({id:id(n),publicId:'STORE-'+n,parentStoreId:parent? id(parent):null,userIds:[account.id],storeTypeIds:[],name:tr(name),alias:[],description:[],companyForms:[],location:{},address:name+' address',phoneNumbers:[],emails:[],countryLocales:['kz'],createdAt:1,branchType:type,architectureVersion:2});
const physical=store(31,'West pickup branch',30,'PHYSICAL'),internet=store(32,'Internet branch',30,'INTERNET'),parent={...store(30,'Management warehouse',null,null),branches:[physical,internet]};
const stores=[parent,physical,internet],quantity={id:'0',immutableUnitName:tr('pc.'),total:12,pricedAmount:1,roundTotal:true},price={price:'250',currency:'KZT',supplierId:''};
const itemFor=storeId=>({id:id(Number(storeId.slice(-2))*100),storeId,name:tr(storeId===parent.id?'Generic mountain honey':'Internet honey stock'),description:tr(storeId===parent.id?'Generic parent description':'Internet warehouse notes'),imagePaths:[storeId===parent.id?'https://images.example.org/parent.jpg':'https://images.example.org/branch.jpg'],marketplaceProfile:{automaticFromStock:true,product:{brand:storeId===parent.id?'Parent brand':'Branch brand',ingredients:'Honey'}},barcodes:['4006381333931'],measurementUnitId:'0',salePrices:[price],supplyPrices:[{...price,price:'100'}],activeShelfBatchId:id(Number(storeId.slice(-2))*100+1),isActive:true});
const batchesFor=storeId=>[{id:id(Number(storeId.slice(-2))*100+1),storeId,goodsItemId:itemFor(storeId).id,quantity,supplyPrice:{...price,price:'100'},salePriceOverride:price,status:'Delivered',shelfPriority:2,isActive:true}];
let shop={storeId:internet.id,branchStoreId:internet.id,displayName:'Public internet shop',city:'Almaty',publicAddress:'Internet pickup counter',pickupNote:'Confirm before arrival',published:true,revision:1,shareBranchAvailability:true},selected=[physical.id];
const locations=[{storeId:parent.id,name:parent.name,address:parent.address,warehouse:true},{storeId:physical.id,name:physical.name,address:physical.address,warehouse:false}];
const dashboard=()=>({storefront:shop,listings:[],locationStoreIds:selected,availableLocations:locations});
const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US'});
await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-store-token',refreshToken:'isolated-store-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-store-test');},account);
const calls=[],errors=[];let appState={revision:0,enabled:true};await context.routeWebSocket('**',ws=>ws.close());
await context.route('**/*',async route=>{
 const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({path:p,method:req.method(),storeId,body:req.postData()});let payload=[];
 if(p==='/auth/session')payload={};
 else if(p==='/user/get')payload=account;
 else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state'){if(req.method()==='PUT'){const body=req.postDataJSON();appState={revision:appState.revision+1,enabled:body.enabled,document:body.document,updatedAtMillis:Date.now()};}payload={state:appState};}
 else if(p==='/stores/get')payload=[parent];
 else if(p==='/stock/get')payload=[itemFor(storeId)];
 else if(p==='/stock/parent/get')payload=[itemFor(parent.id)];
 else if(p==='/stock/update')payload=req.postDataJSON();
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
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));page.setDefaultTimeout(15000);
const checks=[];let copiedDraft=null,savedItem=null;

const until=async(check,message)=>{for(let n=0;n<100;n++){if(await check())return;await page.waitForTimeout(150);}throw Error(message);};
const shot=async name=>{await page.screenshot({path:out+'/'+name+'.png'});console.log(out+'/'+name+'.png');};
const click=async name=>{await page.getByRole('button',{name,exact:true}).first().click({force:true});await page.waitForTimeout(750);};
const tab=async text=>{await page.getByRole('button').filter({hasText:new RegExp('^'+text+'$')}).first().click({force:true});await page.waitForTimeout(750);};
const localDraft=()=>page.evaluate(id=>{for(const key of Object.keys(localStorage)){try{const record=JSON.parse(localStorage.getItem(key));for(const [owner,value] of Object.entries(record?.document?.drafts||{})){if(typeof value==='string'&&value.startsWith('aita-stock-add-edit-draft-v1:')){const fields=value.slice('aita-stock-add-edit-draft-v1:'.length).split('\u001e');if(fields[0]===id)return {owner,id:fields[0],barcodes:JSON.parse(fields[1]),name:JSON.parse(fields[2]),description:JSON.parse(fields[3]),profile:fields[18]?JSON.parse(fields[18]):null};}}}catch{}}return null;},itemFor(account.activeStoreId).id);
try {
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(10000);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 await click('Stock');await until(()=>calls.some(c=>c.path==='/stock/get'),'Stock did not load');await page.waitForTimeout(1200);await shot('stock');
 // Existing stock edit icon is intentionally an image control on this canvas UI.
 await page.mouse.click(683,333);await page.waitForTimeout(750);await shot('edit-item');
 await tab('Marketplace profile');await shot('profile-open');assert.deepEqual(errors,[],'Opening profile tab must not cause infinite-height layout failure');
 if(process.env.AITA_TEST_PARENT==='1') {
  assert.equal(await page.getByRole('button',{name:'Use parent profile',exact:true}).count(),0);
  assert.ok(await page.getByText('Generic product information for internet branches. Each branch can copy it and keep its own changes.',{exact:true}).count()>0);
  assert.ok(await page.getByRole('button',{name:'Confirm',exact:true}).count()>0);
  checks.push('Management parent retains editable generic Marketplace profile and Save action');
 } else {
  await click('Use parent profile');await until(()=>calls.some(c=>c.path==='/stock/parent/get'),'Parent catalogue was not requested');await page.waitForTimeout(1000);await shot('parent-picker');
  const firstItem=page.getByRole('button').filter({hasText:/^Generic mountain honey/}).first();
  await firstItem.hover({force:true});await page.waitForTimeout(250);await shot('parent-picker-first-hover');
  await page.getByRole('button',{name:'Use parent profile',exact:true}).last().click({force:true});await page.waitForTimeout(1000);
  await until(async()=>((await localDraft())?.profile?.automaticFromStock===false),'Resolved parent profile was not written to draft');
  copiedDraft=await localDraft();
  assert.equal(copiedDraft.profile.name[0].value,'Generic mountain honey');assert.equal(copiedDraft.profile.description[0].value,'Generic parent description');
  assert.equal(copiedDraft.profile.product.brand,'Parent brand');assert.deepEqual(copiedDraft.profile.product.imageUrls,['https://images.example.org/parent.jpg']);
  assert.equal(copiedDraft.name[0].value,'Internet honey stock');assert.equal(copiedDraft.description[0].value,'Internet warehouse notes');assert.deepEqual(copiedDraft.barcodes,['4006381333931']);
  checks.push('Explicit parent selection copies resolved public text, facts and photos independently; stock fields are preserved');await shot('copied-profile');
  // Dismissing a Compose canvas sheet can leave its accessibility textbox node stale.
  // Focus the visible public-name field natively, then assert the actual persisted value.
  await page.mouse.click(945,363);await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('Backspace');await page.keyboard.type('Internet exclusive honey ',{delay:70});
  await until(async()=>((await localDraft())?.profile?.name?.[0]?.value==='Internet exclusive honey '),'Branch profile edit was not persisted');
  assert.equal(calls.filter(c=>c.path==='/stock/update').length,0,'Draft edits must not auto-save');
  await page.mouse.click(1080,951);await page.waitForTimeout(750);await until(()=>calls.some(c=>c.path==='/stock/update'),'Profile save was not submitted');
  const writes=calls.filter(c=>c.path==='/stock/update');assert.equal(writes.length,1);savedItem=JSON.parse(writes[0].body);
  assert.equal(savedItem.id,itemFor(internet.id).id);assert.equal(savedItem.storeId,internet.id);assert.equal(writes[0].storeId,internet.id);
  assert.equal(savedItem.marketplaceProfile.automaticFromStock,false);assert.equal(savedItem.marketplaceProfile.name[0].value,'Internet exclusive honey');
  assert.equal(savedItem.name[0].value,'Internet honey stock');assert.deepEqual(savedItem.imagePaths,['https://images.example.org/branch.jpg']);
  assert.equal(calls.filter(c=>c.path==='/stock/update'&&c.storeId===parent.id).length,0);
  checks.push('Profile typing preserves spaces; Save normalizes final whitespace and submits one branch-scoped update with stock identity and parent unchanged');await shot('saved-profile');
 }
 await page.setViewportSize({width:390,height:844});await page.waitForTimeout(700);await shot('phone');
 assert.deepEqual(errors,[]);checks.push('No JavaScript exception or renderer crash');console.log('PARENT PROFILE BROWSER PASS:',checks.join('; '));
} catch(error) {try{await shot('failure');fs.writeFileSync(out+'/buttons.json',JSON.stringify(await page.getByRole('button').evaluateAll(xs=>xs.map(x=>({name:x.getAttribute('aria-label'),text:x.innerText}))),null,2));}catch{}throw error;}
finally {fs.writeFileSync(out+'/result.json',JSON.stringify({checks,copiedDraft,savedItem,calls,errors},null,2));await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
