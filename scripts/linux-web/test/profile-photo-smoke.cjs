const {chromium}=require('playwright'),fs=require('fs'),http=require('http'),path=require('path');
(async()=>{
 const root=process.env.AITA_WEB_DIST;if(!root)throw Error('Set AITA_WEB_DIST to the built web distribution');
 const artifacts=process.env.AITA_ARTIFACTS;if(!artifacts)throw Error('Set AITA_ARTIFACTS for screenshots');fs.mkdirSync(artifacts,{recursive:true});
 const server=http.createServer((req,res)=>{const file=path.join(root,decodeURIComponent(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));try{const b=fs.readFileSync(file);res.setHeader('Content-Type',file.endsWith('.wasm')?'application/wasm':file.endsWith('.js')?'text/javascript':file.endsWith('.html')?'text/html':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(b);}catch{res.statusCode=404;res.end();}}).listen(0,'127.0.0.1');
 await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});const context=await browser.newContext({viewport:{width:1280,height:1000},locale:'en-US'});
 const account={id:'0f01398b-015a-49e4-8838-ef1000000001',publicId:'BROWSER-TEST',phoneNumber:'',email:'browser@example.test',firstName:'Browser',lastName:'Test',countryLocale:'kz',workerAccountIds:null,supplierAccountIds:null,createdAt:1,isActive:true,appLanguage:'en',appModeId:0};
 await context.addInitScript(account=>{localStorage.setItem('aita.auth_tokens',JSON.stringify({accessToken:'isolated-test-token',refreshToken:'isolated-test-refresh'}));localStorage.setItem('aita.user_account',JSON.stringify(account));localStorage.setItem('aita.installation_id','isolated-browser-test');},account);
 await context.routeWebSocket('**',ws=>ws.close());
 const calls=[],errors=[];let photo={accountId:account.id,mode:'STORE'},jpeg='',previews=0,saves=0;
 await context.route('**/*',async route=>{
 const u=new URL(route.request().url());
 if(u.origin===origin)return route.continue();
 if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
 const p=u.pathname.replace(/^\/api\//,'/');calls.push(p);
 let payload=[];
 if(['/auth/ping','/auth/session','/healthz','/readyz'].includes(p))payload={};
 else if(p==='/user/get')payload=account;
 else if(p.includes('profile-photo')){
 if(p.endsWith('/preview')){previews++;payload={jpegBase64:jpeg};}
 else if(route.request().method()==='PUT'){saves++;photo={accountId:account.id,mode:'STORE',revision:1,jpegBase64:jpeg,updatedAtMillis:1};payload=photo;}
 else payload=photo;
 }
 else if(p==='/user/app-mode')payload={accountId:account.id,appModeId:0};
 else if(p==='/user/app-state')payload={state:{revision:0,enabled:true}};
 else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
 else if(p.includes('global') || p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
 return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:false,payload:JSON.stringify(payload)})});
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));
 try{
 await page.goto('https://aita.kz');await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(10000);
 await page.mouse.click(90,100);await page.waitForTimeout(2000);
 await page.keyboard.press('Tab');await page.waitForTimeout(1000);
 console.log('ERRORS',JSON.stringify(errors));require('assert').deepEqual(errors,[]);
 require('assert').ok(await page.evaluate(()=>localStorage.getItem('aita.runtime-diagnostics')), 'Browser diagnostic journal must initialize');
 await page.screenshot({path:path.join(artifacts,'photo-ui-initial.png')});
 {
 jpeg=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=c.height=512;c.getContext('2d').fillRect(0,0,512,512);return c.toDataURL('image/jpeg').split(',')[1];});
 await page.mouse.move(750,185);await page.waitForTimeout(150);
 let chooser=await Promise.all([page.waitForEvent('filechooser',{timeout:10000}),page.mouse.click(750,185,{delay:100})]).then(x=>x[0]);
 await chooser.setFiles({name:'test.jpg',mimeType:'image/jpeg',buffer:Buffer.from(jpeg,'base64')});
 await page.waitForTimeout(1500);require('assert').equal(previews,1);
 await page.mouse.click(814,249,{delay:100});await page.waitForTimeout(1500);require('assert').equal(saves,1);
 await page.screenshot({path:path.join(artifacts,'photo-ui-saved.png')});
 chooser=await Promise.all([page.waitForEvent('filechooser',{timeout:10000}),page.mouse.click(750,185,{delay:100})]).then(x=>x[0]);
 await chooser.setFiles([]);
 chooser=await Promise.all([page.waitForEvent('filechooser',{timeout:10000}),page.mouse.click(750,185,{delay:100})]).then(x=>x[0]);
 await chooser.setFiles([]);require('assert').deepEqual(errors,[]);
 console.log('PHOTO PASS: upload chooser, preview, save, change chooser and reopen after cancel');
 }
 }catch(error){await page.screenshot({path:path.join(artifacts,'photo-failure.png')});console.log('FAILURE DETAILS',JSON.stringify({errors,inputs:await page.locator('input[type=file]').count(),calls}));throw error;}finally{await browser.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
