// Isolated browser regression: saved mode/menu restoration and remote session revocation.
// All API calls are intercepted. Set AITA_WEB_DIST and optionally AITA_ARTIFACTS.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const scene='physical';
const fs=require('fs'),http=require('http'),path=require('path');
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0'),tr=value=>[{language:'en',value}];
(async()=>{
const dist=process.env.AITA_WEB_DIST,out=process.env.AITA_ARTIFACTS||'/tmp/aita-store-printer-browser';if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url.split('?')[0]==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
let account={id:id(99),publicId:'STORE-TEST',phoneNumber:'',email:'store@example.test',firstName:'Branch',lastName:'Tester',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',activeStoreId:id(scene==='parent'?30:(scene==='physical'||scene==='cart')?31:32)};
const store=(n,name,parent,type)=>({id:id(n),publicId:'STORE-'+n,parentStoreId:parent? id(parent):null,userIds:[account.id],storeTypeIds:[],name:tr(name),alias:[],description:[],companyForms:[],location:{},address:name+' address',phoneNumbers:[],emails:[],countryLocales:['kz'],createdAt:1,branchType:type,architectureVersion:2});
const physical=store(31,'West pickup branch',30,'PHYSICAL'),internet=store(32,'Internet branch',30,'INTERNET'),parent={...store(30,'Management warehouse',null,null),branches:[physical,internet]};
const stores=[parent,physical,internet],quantity={id:'0',immutableUnitName:tr('pc.'),total:12,pricedAmount:1,roundTotal:true},price={price:'250',currency:'KZT',supplierId:''};
const itemFor=storeId=>({id:id(Number(storeId.slice(-2))*100),storeId,name:tr('Mountain honey'),description:[],barcodes:['4006381333931'],measurementUnitId:'0',salePrices:[price],supplyPrices:[{...price,price:'100'}],activeShelfBatchId:id(Number(storeId.slice(-2))*100+1),isActive:true});
const batchesFor=storeId=>[1,2].map(n=>({id:id(Number(storeId.slice(-2))*100+n),storeId,goodsItemId:itemFor(storeId).id,quantity:{...quantity,total:n===1?12:6},supplyPrice:{...price,price:'100'},salePriceOverride:price,status:'Delivered',shelfPriority:3-n,isActive:true}));
const browser=await chromium.launch({headless:true,channel:'chromium',args:['--enable-unsafe-swiftshader']});
const sockets=new Set(),calls=[],errors=[];let sequence=0,deleted=false,rejectTarget=null,revokedSecond=false; const deviceSockets=new Map();
const publish=(entity)=>{const data=JSON.stringify({id:'event-'+(++sequence),entity,userId:entity==='user/active-store'?account.id:null,reason:'shared_state_changed',type:'changed',sequence,createdAtMillis:Date.now()});for(const socket of sockets)socket.send(data);};
async function makeClient(device){
 const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US'});
 await context.addInitScript(({account,device})=>{if(!localStorage.getItem('aita.fixture-seeded')){localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-store-token',refreshToken:'isolated-store-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.fixture-seeded','1');}localStorage.setItem('aita.installation_id','isolated-'+device);}, {account,device});
 await context.routeWebSocket('**',socket=>{sockets.add(socket);deviceSockets.set(device,socket);socket.onClose(()=>sockets.delete(socket));socket.send(JSON.stringify({type:'connected',entity:'all',reason:'websocket_connected',createdAtMillis:Date.now()}));});
 await context.route('**/*',async route=>{
  const u=new URL(route.request().url());if(u.origin===origin)return route.continue();if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname+u.search});return route.fulfill({response});}
  const p=u.pathname.replace(/^\/api\//,'/'),req=route.request(),headers=req.headers(),storeId=headers.store_id||account.activeStoreId;calls.push({device,path:p,method:req.method(),storeId,body:req.postData()});let payload=[];
  if(revokedSecond && device==='second' && !p.includes('/res/') && !p.includes('ping') && !p.includes('config') && !p.includes('client-updates') && p!=='/auth/capabilities') {
   const message=[{language:'en',value:'This session was revoked. Sign in again to access your account.',messageTemplate:{key:'auth.session_revoked'}}];
   return route.fulfill({status:401,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:JSON.stringify(message),negative:true,payload:null})});
  }
  if(p==='/user/get')payload=account;
  else if(p==='/stores/active'){if(req.postData().replaceAll('"','')===rejectTarget)return route.fulfill({status:403,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});account={...account,activeStoreId:req.postData().replaceAll('"','')||null};payload=null;setTimeout(()=>publish('user/active-store'),50);}
  else if(p==='/stores/delete'){deleted=true;account={...account,activeStoreId:parent.id};payload=null;setTimeout(()=>publish('stores'),50);}
  else if(p==='/user/update'){account={...account,...req.postDataJSON()};payload=account;}
  else if(p==='/user/app-mode'){account={...account,appModeId:req.postDataJSON().appModeId};payload={accountId:account.id,appModeId:account.appModeId};}
  else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
  else if(p==='/stores/get')payload=[{...parent,branches:deleted?[internet]:[physical,internet]}];
  else if(p==='/stock/get')payload=[itemFor(storeId)];
  else if(p==='/stockBatches/get')payload=batchesFor(storeId);
  else if(p==='/subscriptions/store/get')payload={subscription:{id:id(600),storeId,ownerUserId:account.id,planId:'internal_lifetime',status:'active',accessKind:'lifetime',autoRenew:false,currentPeriodStartMillis:1,currentPeriodEndMillis:null,revision:1},charges:[],plans:[],canManage:true,serverTimeMillis:Date.now()};
  else if(p==='/market/seller/stock-status')payload={accountId:account.id,storeId,parentStoreId:parent.id,marketplaceEnabled:true,entries:[],checkedAtMillis:Date.now()};
  else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'STORE'};
  else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
  else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:null,negative:false,payload:payload===null?null:JSON.stringify(payload)})});
 });
 await context.addInitScript(()=>{window.print=function(){top.testPrintHtml=document.documentElement.outerHTML;};});
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.stack||e.message));page.on('crash',()=>errors.push(device+' crashed'));
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(6500);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 return {page,context};
}
const button=(p,name)=>p.getByRole('button',{name,exact:true});
const waitFor=async (condition,label)=>{for(let i=0;i<80;i++){if(await condition())return;await new Promise(r=>setTimeout(r,250));}throw Error('Timed out: '+label);};
const sql=await require(path.join(dist,'sql-wasm.js'))({locateFile:()=>path.join(dist,'sql-wasm.wasm')});
const preference=async (page,key)=>{const bytes=await page.evaluate(()=>new Promise((resolve,reject)=>{const open=indexedDB.open('aita.local.v1',1);open.onerror=()=>reject(open.error);open.onsuccess=()=>{const db=open.result,read=db.transaction('sqlite','readonly').objectStore('sqlite').get('main');read.onsuccess=()=>{resolve(Array.from(read.result));db.close()};read.onerror=()=>{reject(read.error);db.close()}}}));const db=new sql.Database(new Uint8Array(bytes));try{return db.exec('SELECT value FROM key_value WHERE key = ?',[key])[0]?.values[0]?.[0]??null}finally{db.close()}};
let clients=[];
try {
 clients=[await makeClient('first'),await makeClient('second')];const [a,b]=clients.map(c=>c.page);
 for(const p of [a,b])assert.equal(await button(p,'Sale').count(),1,'saved Store mode opens without Marketplace');
 await button(a,'Menu').click({force:true});await a.waitForTimeout(700);
 for(let i=0;i<8 && !await a.getByText('Downloads',{exact:true}).count();i++){await a.mouse.move(100,750);await a.mouse.wheel(0,600);await a.waitForTimeout(250);} await a.screenshot({path:path.join(out,'menu.png')});fs.writeFileSync(path.join(out,'menu.txt'),await a.locator('body').innerText());await a.getByText('Downloads',{exact:true}).first().click({force:true});await a.waitForTimeout(800);
 assert.equal(await a.getByText('Open web app',{exact:true}).count(),0,'web Downloads does not offer itself');
 await a.screenshot({path:path.join(out,'downloads.png')});
 // Reload an already signed-in store workspace with its last menu location.
 await a.reload();await a.waitForTimeout(6500);await a.keyboard.press('Tab');await a.waitForTimeout(500);
 assert.equal(await a.getByText('Downloads',{exact:true}).count()>0,true,'Downloads restores after reload');
 assert.equal(await button(a,'Sale').count(),1,'reload retains Store mode');
 await waitFor(()=>deviceSockets.has('second'),'second device realtime socket');
 revokedSecond=true;await deviceSockets.get('second').close({code:1008,reason:'Session revoked'});
 await waitFor(async()=>await b.evaluate(()=>localStorage.getItem('aita.auth_tokens')===null),'revoked client clears durable credentials');
 await b.waitForTimeout(2500);await b.keyboard.press('Tab');await b.screenshot({path:path.join(out,'revocation-inspect.png')});fs.writeFileSync(path.join(out,'revocation-aria.txt'),await b.locator('body').ariaSnapshot());await waitFor(async()=>await b.getByRole('button',{name:/^(Log in|Sign in)$/i}).count()>0,'revoked client returns to authentication');
 assert.notEqual(await a.evaluate(()=>localStorage.getItem('aita.auth_tokens')),null,'other device remains signed in');
 await b.screenshot({path:path.join(out,'revoked-device.png')});await b.reload();await b.waitForTimeout(5000);await b.keyboard.press('Tab');await waitFor(async()=>await b.getByRole('button',{name:/^Log in$/i}).count()>0,'revoked device remains signed out after reload');assert.equal(await b.evaluate(()=>localStorage.getItem('aita.auth_tokens')),null);
 assert.equal(errors.length,0,errors.join('\n'));
 fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({passed:true,calls:calls.map(c=>({path:c.path,method:c.method})),errors},null,2));
 console.log('PASS: saved Store mode, Downloads reload, web action hidden, remote revocation logout');
} finally {await browser.close();server.close()}
})().catch(e=>{console.error(e.stack);process.exitCode=1});
