const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path'),readline=require('readline');
(async()=>{
 const root=path.resolve(__dirname,'../../..'),dist=process.env.AITA_WEB_DIST,out=path.join(process.env.AITA_ARTIFACTS||'build/appearance-browser','appearance');if(!dist)throw Error('Set AITA_WEB_DIST');fs.mkdirSync(out,{recursive:true});
 const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(fs.readFileSync(p));}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');await new Promise(r=>server.once('listening',r));
 const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});
 const context=await browser.newContext({viewport:{width:1280,height:1000},locale:'en-US'});
 let account={id:'0f01398b-015a-49e4-8838-ef1000000001',publicId:'BROWSER-TEST',phoneNumber:'',email:'browser@example.test',firstName:'Browser',lastName:'Test',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans'};
 await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-test-token',refreshToken:'isolated-test-refresh'}));if(!localStorage.getItem('aita.user_account'))localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-browser-test');},account);
 const ticket={id:'0f01398b-015a-49e4-8838-ef1000000002',publicId:'SUPPORT-TEST',userId:account.id,subject:'Saved support conversation',lastMessage:'Your conversation stays available offline',lastMessageAtMillis:Date.now(),updatedAtMillis:Date.now(),revision:1};
 const messages=[{id:'offline-message-1',ticketId:ticket.id,userId:account.id,senderUserId:'test-agent',senderRole:'agent',senderDisplayName:'Support team',body:'Your conversation stays available offline',sequence:1,createdAtMillis:Date.now()}];
 let offline=false,delayMs=0;const calls=[],errors=[];
 await context.routeWebSocket('**',ws=>ws.close());
 await context.route('**/*',async route=>{
  const u=new URL(route.request().url());if(u.origin===origin)return route.continue();
  if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
  const p=u.pathname.replace(/^\/api\//,'/');calls.push({path:p,method:route.request().method(),body:route.request().postData()});
  if(offline)return route.abort('connectionrefused');
  if(delayMs)await new Promise(r=>setTimeout(r,delayMs));
  let payload=[];
  if(['/auth/ping','/auth/session','/healthz','/readyz'].includes(p))payload={};
  else if(p==='/user/get')payload=account;
  else if(p==='/user/preferences/update'){account={...account,...route.request().postDataJSON()};payload=account;}
  else if(p.includes('profile-photo'))payload={accountId:account.id,mode:'STORE'};
  else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
  else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
  else if(p==='/support/workspace/tickets')payload={tickets:[ticket]};
  else if(p==='/support/workspace/messages')payload={ticket,messages};
  else if(p==='/support/workspace/read')payload={first:ticket,second:true};
  else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
  else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({message:null,negative:false,payload:JSON.stringify(payload)})});
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(8000);await page.keyboard.press('Tab');await page.waitForTimeout(500);
 async function shot(name){await page.screenshot({path:out+'/'+name+'.png'});return out+'/'+name+'.png';}
 const assert=require('node:assert/strict');
 async function click(name) { await page.getByRole('button',{name,exact:true}).click({force:true});await page.waitForTimeout(500); }
 async function until(check) {for(let i=0;i<60&&!check();i++)await page.waitForTimeout(100);assert.ok(check(),'Expected preference request did not complete');}
 async function font(name,id) {
  await page.getByRole('button').filter({hasText:new RegExp('^'+name+'Aa')}).click({force:true});
  await until(()=>account.appFontId===id);await page.waitForTimeout(350);
  assert.ok((await page.getByRole('button',{name:'Select',exact:true}).filter({hasText:new RegExp('^'+name+'Aa')}).innerText()).startsWith(name));
 }
 try {
  await click('Text font');await shot('fonts-wide');
  for(const [name,id] of [['Fira Sans','fira_sans'],['Fira Sans Condensed','fira_condensed'],['Lato','lato'],['Ubuntu','ubuntu'],['PT Sans','pt_sans'],['PT Serif','pt_serif'],['IBM Plex Serif','plex_serif'],['IBM Plex Mono','plex_mono'],['Noto Sans','noto_sans']]) {
   await font(name,id);await shot('font-'+id);
  }
  await font('Ubuntu','ubuntu');
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(600);await shot('fonts-phone');
  await page.setViewportSize({width:1280,height:1000});await page.waitForTimeout(600);
  await click('Workers');
  const identity=page.getByText('Your public worker ID',{exact:true});
  assert.equal(await identity.count(),1);
  const tile=await identity.boundingBox(),tabs=await page.getByRole('button').filter({hasText:'My work (0)'}).boundingBox();
  assert.ok(tile.y+tile.height<tabs.y);await shot('workers');
  await click('App mode');await shot('modes');
  await click('Downloads');await shot('downloads-wide');
  const folder=await page.getByRole('button',{name:'Download folder',exact:true}).boundingBox();
  const platform=await page.getByRole('button').filter({hasText:/^Android$/}).boundingBox();
  assert.ok(folder.y+folder.height<platform.y);
  assert.equal(await page.getByText('AAB',{exact:true}).count(),0);
  await page.getByRole('button').filter({hasText:/^Previous releases$/}).click({force:true});await page.waitForTimeout(600);
  assert.ok(await page.getByText('No previous releases for this platform yet',{exact:true}).count());
  await page.setViewportSize({width:390,height:844});await page.waitForTimeout(600);await shot('downloads-phone');
  await page.setViewportSize({width:1280,height:1000});await page.waitForTimeout(600);
  await click('Support');await page.getByRole('button').filter({hasText:'Saved support conversation'}).click({force:true});
  await page.waitForTimeout(800);await shot('support-online');
  assert.equal(await page.getByText('Your conversation stays available offline',{exact:true}).count(),1);
  offline=true;await page.reload();await page.waitForTimeout(5000);await page.keyboard.press('Tab');
  await page.getByRole('button').filter({hasText:'Saved support conversation'}).waitFor({state:'visible',timeout:10000});
  await shot('support-offline-inbox');
  await page.getByRole('button').filter({hasText:'Saved support conversation'}).click({force:true});await page.waitForTimeout(800);
  assert.equal(await page.getByText('Your conversation stays available offline',{exact:true}).count(),1);
  assert.ok(await page.getByText(/Offline · saved conversations and drafts/).count());await shot('support-offline-messages');
  await click('Text font');assert.ok((await page.getByRole('button',{name:'Select',exact:true}).filter({hasText:/^UbuntuAa/}).innerText()).startsWith('Ubuntu'));
  await shot('font-offline-restored');
  assert.deepEqual(errors,[]);
  console.log('APPEARANCE PASS: nine fonts, account preference requests, phone/wide layout, download tabs, worker identity and support history after offline reload');
 } catch(error) { await shot('failure');throw error; }
 finally { fs.writeFileSync(out+'/result.json',JSON.stringify({calls,errors},null,2));await browser.close();server.close(); }
})().catch(e=>{console.error(e);process.exitCode=1});
