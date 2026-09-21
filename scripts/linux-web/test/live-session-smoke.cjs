// Built Wasm app + actual isolated AITA backend: no mocked authentication or realtime events.
// Set AITA_WEB_DIST, AITA_ARTIFACTS, AITA_TEST_API_URL, AITA_TEST_LOGIN, AITA_TEST_PASSWORD.
// The disposable account must own a parent with one operating branch; this test creates/deletes
// an empty temporary branch and revokes its other sessions. Never use a production backend.
const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path'),assert=require('node:assert/strict');
const base=process.env.AITA_TEST_API_URL||'http://127.0.0.1:18018',origin='http://127.0.0.1:18019',out=process.env.AITA_ARTIFACTS,dist=process.env.AITA_WEB_DIST;
const login=process.env.AITA_TEST_LOGIN,password=process.env.AITA_TEST_PASSWORD;
if(!/^http:\/\/127\.0\.0\.1:[1-9][0-9]{4}$/.test(base)||!login?.endsWith('@example.invalid')||!password)throw Error('Use a disposable loopback backend on a high port and an @example.invalid fixture account');
if(!out||!dist)throw Error('Set AITA_ARTIFACTS and AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
const decode=d=>typeof d.payload==='string'?JSON.parse(d.payload):d.payload;
async function api(tokens,endpoint,method='GET',body){
 const r=await fetch(base+'/'+endpoint,{method,headers:{'Content-Type':'application/json',...(tokens?{Authorization:'Bearer '+tokens.accessToken}:{})},body:body===undefined?undefined:typeof body==='string'?body:JSON.stringify(body)});
 const d=await r.json();assert(r.status>=200 && r.status<300,endpoint+' status '+r.status+' '+JSON.stringify(d.message));assert.equal(d.negative,false,endpoint+' rejected');return decode(d);
}
const wait=async(fn,label,ms=90000)=>{const end=Date.now()+ms;while(Date.now()<end){if(await fn())return;await new Promise(r=>setTimeout(r,500))}throw Error('Timed out: '+label)};
(async()=>{
 const server=http.createServer((req,res)=>{try{let f=path.join(dist,new URL(req.url,origin).pathname);if(req.url==='/')f=path.join(dist,'index.html');res.setHeader('content-type',f.endsWith('.wasm')?'application/wasm':f.endsWith('.js')?'text/javascript':f.endsWith('.html')?'text/html':f.endsWith('.svg')?'image/svg+xml':f.endsWith('.css')?'text/css':'application/octet-stream');res.end(fs.readFileSync(f))}catch{res.writeHead(404);res.end()}}).listen(18019,'127.0.0.1');await new Promise(r=>server.once('listening',r));
 const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});const errors=[],calls=[],connections=[],pages=[],resources=[];
 try{
  const sessions=[];for(const n of ['a','b'])sessions.push(await api(null,'auth/logIn','POST',{login,password,deviceInfo:{installationId:'probe018-live-'+n,platformName:'Web/Wasm',deviceName:'Isolated browser '+n}}));
  const account=await api(sessions[0],'user/get'),stores=await api(sessions[0],'stores/get'),parent=stores.find(x=>!x.parentStoreId);assert(parent);
  await api(sessions[0],'stores/active','PUT',parent.id);
  const updatedAccount=await api(sessions[0],'user/get');

  for(let i=0;i<2;i++){
   const context=await browser.newContext({viewport:{width:1360,height:900},locale:'en-US'});
   await context.addInitScript(({tokens,account,base})=>{
    if(!localStorage.getItem('aita.fixture-seeded')){localStorage.setItem('aita.auth_tokens',JSON.stringify(tokens));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.fixture-seeded','1')}
    // Only route the address to loopback. Authentication/protocols and the handshake are untouched.
    const Native=window.WebSocket;window.WebSocket=class extends Native{constructor(url,protocols){const u=new URL(url);super(u.pathname==='/rt/updates'?base.replace('http:','ws:')+'/rt/updates':url,protocols)}};
   },{tokens:sessions[i],account:updatedAccount,base});
   await context.route('**/*',async route=>{
    const req=route.request(),u=new URL(req.url());if(u.origin===origin)return route.continue();
    if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname+u.search});return route.fulfill({response})}
    calls.push({client:i,path:u.pathname,method:req.method()});
    // Never send the isolated account's requests to a production service.
    try{const response=await route.fetch({url:base+u.pathname+u.search,timeout:45000});return route.fulfill({response})}
    catch(e){if(!context.pages().every(p=>p.isClosed()))return route.abort('failed')}
   });
   const page=await context.newPage();page.on('console',m=>{if(m.type()==='error')resources.push({error:m.text().replace(/eyJ[A-Za-z0-9_.-]+/g,'[redacted]')})});page.on('response',r=>resources.push({path:new URL(r.url()).pathname,status:r.status()}));page.on('pageerror',e=>errors.push({client:i,error:e.message}));page.on('crash',()=>errors.push({client:i,error:'renderer crashed'}));
   page.on('websocket',ws=>ws.on('framereceived',e=>{try{const d=JSON.parse(e.payload.toString());if(d.type==='connected')connections.push(i)}catch{}}));
   await page.goto(origin);pages.push(page);
  }
  await wait(()=>new Set(connections).size===2,'both REAL browser sockets authenticated',150000);
  const selected=p=>p.evaluate(()=>JSON.parse(localStorage.getItem('aita.user_account')||'null')?.activeStoreId);
  await wait(async()=>await selected(pages[0])===parent.id && await selected(pages[1])===parent.id,'parent restored on both browsers');
  const branch=parent.branches[0];assert(branch);
  await api(sessions[0],'stores/active','PUT',branch.id);
  await wait(async()=>await selected(pages[0])===branch.id && await selected(pages[1])===branch.id,'selection propagated through real backend and sockets');
  await api(sessions[0],'stores/active','PUT',parent.id);
  await wait(async()=>await selected(pages[1])===parent.id,'second device returned to parent');
  const temporary=await api(sessions[0],'stores/add','POST',{...branch,id:'',publicId:'',name:[{language:'en',value:'Disposable sync test branch'}],emails:[],phoneNumbers:[],branches:[],address:'Isolated test address',location:{provider:'manual',name:'Isolated test address',fallbackAddress:'Isolated test address',primaryLanguage:'en'},legalId:'',legalIdTypeId:''});
  assert(temporary?.id,'temporary branch created in isolated database');
  await api(sessions[0],'stores/active','PUT',temporary.id);
  await wait(async()=>await selected(pages[0])===temporary.id && await selected(pages[1])===temporary.id,'new branch selected on both devices');
  await api(sessions[0],'stores/delete','DELETE',temporary.id);
  await wait(async()=>await selected(pages[0])===parent.id && await selected(pages[1])===parent.id,'deletion moved both devices back to parent');
  const security=await api(sessions[0],'security/sessions/get');assert(security.length>=2,'both sessions visible');
  await api(sessions[0],'security/sessions/revokeOthers','POST',{});
  await wait(()=>pages[1].evaluate(()=>localStorage.getItem('aita.auth_tokens')===null),'revoked browser signed out',90000);
  assert(await pages[0].evaluate(()=>localStorage.getItem('aita.auth_tokens')),'requesting browser stays logged in');
  await pages[1].reload();await wait(()=>pages[1].evaluate(()=>localStorage.getItem('aita.auth_tokens')===null),'revocation survives reload');
  for(let i=0;i<pages.length;i++){await pages[i].keyboard.press('Tab');await pages[i].waitForTimeout(400);await pages[i].screenshot({path:path.join(out,'client-'+i+'.png')});fs.writeFileSync(path.join(out,'client-'+i+'.txt'),await pages[i].locator('body').innerText())}
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({passed:true,connections,calls,errors},null,2));console.log('PASS: real browser authentication, active-store propagation and session revocation');
 }catch(e){for(let i=0;i<pages.length;i++){await pages[i].screenshot({path:path.join(out,'failure-'+i+'.png')}).catch(()=>{});fs.writeFileSync(path.join(out,'failure-'+i+'.txt'),await pages[i].locator('body').innerText().catch(()=>''))}fs.writeFileSync(path.join(out,'failure.json'),JSON.stringify({error:e.stack,connections,calls,errors,resources},null,2));throw e}
 finally{await browser.close();server.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
