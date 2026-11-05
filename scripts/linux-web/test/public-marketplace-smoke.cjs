// Isolated marketplace browser flow: every API call is mocked; no real account or stock changes.
const {chromium}=require('playwright');
const fs=require('fs'),http=require('http'),path=require('path');
const dist=process.env.AITA_WEB_DIST;if(!dist)throw Error('Set AITA_WEB_DIST to the built distribution');
const output=path.join(process.env.AITA_ARTIFACTS||'build/marketplace-smoke','guest-market');
fs.mkdirSync(output,{recursive:true});
const id=n=>'00000000-0000-0000-0000-'+String(n).padStart(12,'0');
const shops=[{storeId:id(80),branchStoreId:id(180),shareBranchAvailability:true,displayName:'Green Basket',city:'Almaty',publicAddress:'Abay avenue, 42',pickupNote:'Collect your shopping at the front counter',published:true,revision:1},{storeId:id(81),branchStoreId:id(181),shareBranchAvailability:true,displayName:'Everyday Market',city:'Almaty',publicAddress:'Dostyk avenue, 18',pickupNote:'Open every day',published:true,revision:1}];
const categories=['Pantry','Fresh food','Home & care'].map((s,i)=>({id:id(i+1),name:[{language:'en',value:s}],ancestorIds:[]}));
function offers(now){return ['Mountain honey','Wholegrain bread','Garden tomatoes','Breakfast oats','Fresh milk','Everyday soap'].map((title,i)=>({id:id(101+i),storefront:shops[i%2],title,description:'A thoughtful everyday choice for your next shopping trip.',gtin:null,categoryIds:[categories[i%3].id],priceMinor:[285000,89000,125000,149000,75000,59000][i],originalPriceMinor:i===0?350000:null,currencyCode:'KZT',pricedAmount:1,unitId:'piece',unitName:[{language:'en',value:'pack'}],availability:'recorded_in_stock',checkedAtMillis:now,sourceUpdatedAtMillis:10,saved:i===2,product:{imageUrls:['https://media.example.com/product-'+i+'.svg','https://media.example.com/product-'+(i+1)+'.svg'],brand:'Everyday essentials',manufacturer:'Local producer',countryOfOrigin:'Kazakhstan',attributes:[{name:'Pack size',value:'1 pack'}]}}));}
function picture(i){const colors=['#d9a961','#c3ad86','#d9705a','#caa77e','#acc5d1','#92bda8'];return `<svg xmlns="http://www.w3.org/2000/svg" width="500" height="400" viewBox="0 0 500 400"><rect width="500" height="400" fill="#f1eee7"/><ellipse cx="250" cy="337" rx="85" ry="15" fill="#222" opacity=".08"/><rect x="175" y="87" width="150" height="240" rx="28" fill="${colors[i%6]}"/><rect x="193" y="173" width="114" height="103" rx="10" fill="#fff9ec"/><path d="M228 220l15 16 30-34" fill="none" stroke="#47534a" stroke-width="7" stroke-linecap="round"/><rect x="185" y="78" width="130" height="29" rx="8" fill="#536258"/></svg>`;}
(async()=>{
 const server=http.createServer((req,res)=>{try{const p=path.join(dist,req.url==='/'?'index.html':decodeURIComponent(req.url.split('?')[0]));const bytes=fs.readFileSync(p);res.setHeader('Content-Type',p.endsWith('.wasm')?'application/wasm':p.endsWith('.js')?'text/javascript':p.endsWith('.html')?'text/html':p.endsWith('.css')?'text/css':p.endsWith('.svg')?'image/svg+xml':'application/octet-stream');res.end(bytes);}catch{res.writeHead(404);res.end();}}).listen(0,'127.0.0.1');
 await new Promise(r=>server.once('listening',r));const origin='http://127.0.0.1:'+server.address().port;
 const browser=await chromium.launch({headless:true,channel:'chromium',args:['--enable-unsafe-swiftshader']});
 const context=await browser.newContext({viewport:{width:1440,height:1040},locale:'en-US'});
 await context.addInitScript(()=>{localStorage.setItem('aita.installation_id','isolated-guest-test');localStorage.setItem('aita.key_appLanguage','en');localStorage.setItem('aita.key_appTheme','0');});
 await context.routeWebSocket('**',ws=>ws.close());const calls=[],errors=[];let offline=false;
 await context.route('**/*',async route=>{
  const u=new URL(route.request().url());if(u.origin===origin)return route.continue();
  if(u.hostname==='aita.kz'){const response=await route.fetch({url:origin+u.pathname});return route.fulfill({response});}
  if(u.hostname==='media.example.com')return route.fulfill({contentType:'image/svg+xml',body:picture(parseInt(u.pathname.match(/\d+/)[0]))});
  const p=u.pathname.replace(/^\/api\//,'/');calls.push({path:p,body:route.request().postData(),authorization:route.request().headers().authorization});
  let payload=[];const now=Date.now();
  if(p==='/market/public/discovery'){
   if(offline)return route.abort('internetdisconnected');
   const input=route.request().postDataJSON();const q=input.query;
   const rows=offers(now).map(o=>({...o,saved:false})).filter(o=>(!q.brand||o.product.brand.toLowerCase().includes(q.brand.toLowerCase()))&&(!q.withPhotos||o.product.imageUrls.length>0)&&(!q.savedOnly||o.saved)&&(!q.storefrontId||o.storefront.storeId===q.storefrontId)&&(!q.categoryId||o.categoryIds.includes(q.categoryId))&&(!q.text||o.title.toLowerCase().includes(q.text.toLowerCase())));
   payload={publicVersion:1,discovery:{query:q,limit:input.limit,page:{offers:rows,checkedAtMillis:now},totalOffers:rows.length,totalShops:new Set(rows.map(o=>o.storefront.storeId)).size,categoryVersion:'a'.repeat(64),categories,accountId:null,storefront:q.storefrontId?shops.find(s=>s.storeId===q.storefrontId):null}};
  }
  else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
  else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:true,payload:null})});
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','content-type':'application/json'},body:JSON.stringify({negative:false,payload:JSON.stringify(payload)})});
 });
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('crash'));

 try {
  const assert=require('node:assert/strict');
  const shot=async name=>page.screenshot({path:path.join(output,name+'.png')});
  const button=name=>page.getByRole('button',{name,exact:true});
  const settle=()=>page.waitForTimeout(800);
  await page.goto('https://aita.kz/');await page.locator('canvas').first().waitFor({timeout:120000});await page.waitForTimeout(9000);await page.keyboard.press('Tab');await settle();
  assert.ok(calls.some(c=>c.path==='/market/public/discovery'),'Public catalogue loads without sign-in');
  assert.ok(calls.filter(c=>c.path==='/market/public/discovery').every(c=>!c.authorization),'Public API has no account token');
  await button('Войти').first().waitFor({timeout:10000});
  assert.match(await page.locator('body').ariaSnapshot(),/Найдите то, что порадует/);
  await shot('home-wide');
  await page.setViewportSize({width:390,height:844});await settle();await shot('home-narrow');
  await page.setViewportSize({width:1440,height:1040});await settle();
  await button('Войти').first().click({force:true});await settle();
  await button('Назад').waitFor({timeout:10000});await shot('login');
  assert.equal(await page.evaluate(()=>localStorage.getItem('aita.user_account')),null,'Guest navigation never fabricates an account');
  await button('Назад').click({force:true});await settle();
  const field=page.getByRole('textbox').first();await field.click({force:true});await page.keyboard.type('honey');await page.waitForTimeout(1700);
  assert.ok(calls.some(c=>c.path==='/market/public/discovery'&&JSON.parse(c.body).query.text==='honey'));
  await shot('search');
  assert.equal(await button('Вернуться в маркетплейс').count(),0,'Back navigation uses a compact header arrow');
  await button('Войти').first().click({force:true});await settle();
  assert.equal(await button('Назад').count(),1,'Authentication has exactly one standard header back arrow');
  await button('Назад').click({force:true});await page.waitForTimeout(1000);
  assert.match(await page.locator('body').ariaSnapshot(),/honey/,'Returning from sign-in preserves guest catalogue search');
  assert.equal(JSON.parse(calls.filter(c=>c.path==='/market/public/discovery').at(-1).body).query.text,'honey');
  await button('Назад').first().click({force:true});await page.waitForTimeout(1300);
  await page.mouse.move(900,700);await page.mouse.wheel(0,550);await settle();await shot('offers');
  const product=page.getByRole('button').filter({hasText:/Mountain honey/}).first();
  for(let i=0;i<8;i++){const box=await product.boundingBox().catch(()=>null);if(box&&box.y>130&&box.y<900)break;await page.mouse.wheel(0,250);await settle();}
  await product.click({force:true});await settle();await shot('product');
  assert.match(await page.locator('body').ariaSnapshot(),/Избранное и список покупок доступны после входа/);
  await page.keyboard.press('Escape');await settle();
  offline=true;await page.reload();await page.waitForTimeout(10000);await page.keyboard.press('Tab');await settle();await shot('offline');
  assert.match(await page.locator('body').ariaSnapshot(),/Показаны сохранённые предложения/,'Guest home survives offline reload');
  assert.ok(calls.every(c=>!/^\/(user\/get|stock\/|workers\/|market\/shopping-list)/.test(c.path)),'Guest home does not fetch private workspaces');
  assert.deepEqual(errors,[]);
  console.log('PUBLIC MARKETPLACE PASS: guest home, responsive layout, sign-in navigation, search, product, bounded offline cache; no private credentials.');
 } catch(error) {
  await page.screenshot({path:path.join(output,'failure.png')});fs.writeFileSync(path.join(output,'failure-aria.txt'),await page.locator('body').ariaSnapshot());throw error;
 } finally {
  fs.writeFileSync(path.join(output,'result.json'),JSON.stringify({errors,calls},null,2));await browser.close();server.close();
 }
})().catch(e=>{console.error(e);process.exitCode=1;});
