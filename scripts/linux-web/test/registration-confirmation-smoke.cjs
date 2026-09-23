// Exercise the real registration UI without creating an account or sending email.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});
 const page=await browser.newPage({viewport:{width:1280,height:1100}});const errors=[],calls=[];let target,proof;
 const base=process.env.AITA_WEB_TEST_URL||process.env.AITA_WEB_LOCAL_ORIGIN||'http://127.0.0.1:18029';
 await page.route('**/*',async route=>{
  const u=new URL(route.request().url());if(u.origin===base)return route.continue();
  if(u.hostname==='aita.kz')return route.fulfill({response:await route.fetch({url:base+u.pathname+u.search})});
  if(u.pathname.includes('global')||u.pathname.includes('bootstrap'))return route.fulfill({status:503,body:'{}'});
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','Content-Type':'application/json'},body:JSON.stringify({negative:false,payload:'{}'})});
 });
 await page.route('https://aita.kz/**',async route=>{const u=new URL(route.request().url());return route.fulfill({response:await route.fetch({url:base+u.pathname+u.search})})});
 await page.route('**/auth/**',async route=>{
  const q=route.request(),path=new URL(q.url()).pathname;
  if(q.method()==='GET')return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','Content-Type':'application/json'},body:JSON.stringify({negative:false,payload:JSON.stringify({enabled:true,emailReady:true,emailLoginEnabled:true,emailRecoveryEnabled:true,contactVerificationEnabled:true,registrationEmailVerificationRequired:true})})});
  const body=q.postDataJSON();calls.push(path);
  const now=Date.now(),flowId='02000000-0000-4000-8000-000000000001';let payload;
  if(path.endsWith('/registration/email/request')){target=body.target;assert.equal(body.address,'probe020@example.invalid');payload={flowId,nextStep:'EMAIL_CODE',expiresAtMillis:now+600000,resendAfterMillis:now+60000,serverTimeMillis:now,maskedDestination:'p***@example.invalid'};}
  else if(path.endsWith('/registration/email/verify')){assert.equal(body.flowId,flowId);assert.equal(body.code,'123456');proof={flowId,receipt:'isolated-contact-proof-'.repeat(3)};payload={proof,target,address:'probe020@example.invalid',channel:'EMAIL',expiresAtMillis:now+600000,serverTimeMillis:now};}
  else if(path==='/auth/signUp'){assert.deepEqual(body.contactEmailProofs,[proof]);assert.ok(body.contactVerificationId);return route.fulfill({status:400,contentType:'application/json',body:JSON.stringify({negative:true,message:'Isolated test: account creation intentionally not performed'})});}
  else throw Error('Unexpected authentication mutation '+path);
  return route.fulfill({status:200,headers:{'X-AITA-Server':'AITA','Content-Type':'application/json'},body:JSON.stringify({negative:false,payload:JSON.stringify(payload)})});
 });
 page.on('pageerror',e=>errors.push(e.message));page.on('crash',()=>errors.push('Renderer crashed'));
 try{
  await page.goto(base);await page.locator('canvas').waitFor({timeout:90000});await page.keyboard.press('Tab');await page.getByRole('button',{name:/Перейти к подтверждению email|Continue to email confirmation/}).waitFor({timeout:60000});await page.waitForTimeout(4000);
  async function enter(loc,text){await loc.click({force:true});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.insertText(text);await page.waitForTimeout(150);}
  await enter(page.getByRole('textbox',{name:'',exact:true}).nth(1),'7771234567');
  await enter(page.getByRole('textbox',{name:/^Введите адрес email$|^Enter email address$/}),'probe020@example.invalid');
  await enter(page.getByRole('textbox',{name:/^Введите имя$|^Enter first name$/}),'Test');
  await enter(page.getByRole('textbox',{name:/^Введите фамилию$|^Enter last name$/}),'Registration');
  await enter(page.getByRole('textbox',{name:/^Введите пароль$|^Enter password$/}).last(),'Test-Local-020!');
  await enter(page.getByRole('textbox',{name:/^Повторите пароль$|^Repeat password$/}),'Test-Local-020!');
  await page.getByRole('button',{name:/Перейти к подтверждению email|Continue to email confirmation/}).click({force:true});
  const send=page.getByRole('button',{name:/^Отправить код подтверждения$|^Send confirmation code$/});
  await send.waitFor({timeout:15000});assert(await send.isEnabled());await send.click({force:true});
  await page.getByRole('textbox',{name:'000000',exact:true}).waitFor();
  await enter(page.getByRole('textbox',{name:'000000',exact:true}),'１２３４５６');
  const confirm=page.getByRole('button',{name:/^Подтвердить email$|^Confirm email$/});await confirm.click({force:true});
  const create=page.getByRole('button',{name:/^Создать аккаунт$|^Create account$/});await create.waitFor();assert(await create.isEnabled());await create.click({force:true});
  await page.waitForTimeout(1000);assert.deepEqual(calls,['/auth/registration/email/request','/auth/registration/email/verify','/auth/signUp']);assert.deepEqual(errors,[]);
  if(process.env.AITA_WEB_TEST_OUTPUT){fs.mkdirSync(process.env.AITA_WEB_TEST_OUTPUT,{recursive:true});fs.writeFileSync(process.env.AITA_WEB_TEST_OUTPUT+'/result.json',JSON.stringify({passed:true,calls,errors},null,2));}
  console.log('PASS: guest registration can send and verify email, normalize pasted digits, and submit the bound proof');
 }catch(e){console.error('CALLS',calls);if(process.env.AITA_WEB_TEST_OUTPUT){fs.mkdirSync(process.env.AITA_WEB_TEST_OUTPUT,{recursive:true});await page.screenshot({path:process.env.AITA_WEB_TEST_OUTPUT+'/registration-failure.png'});fs.writeFileSync(process.env.AITA_WEB_TEST_OUTPUT+'/registration-failure.txt',await page.locator('body').ariaSnapshot())}throw e;}
 finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
