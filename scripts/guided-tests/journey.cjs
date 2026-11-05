'use strict';
// Real compiled UI; all service responses are disposable local fixtures. No server auth bypass.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const base=process.env.AITA_WEB_TEST_URL;
assert.match(base,/^http:\/\/127\.0\.0\.1:\d+$/);
const out=process.env.AITA_ARTIFACTS;
const id=n=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
const tr=value=>[{language:'en',value}];
const email='guided-'+Date.now()+'@example.invalid',password='Guided-test-only-42!',code='123456';
let account=null,stores=[],items=[],batches=[],transactions=[],registered=false,deleted=false,emailTarget=null;
const calls=[],stages=[],fixtureErrors=[],browserErrors=[];
const tokens={accessToken:'isolated-guided-access',refreshToken:'isolated-guided-refresh'};
const flow=()=>({flowId:id(1),nextStep:'EMAIL_CODE',maskedDestination:'g***@example.invalid',expiresAtMillis:Date.now()+600000,resendAfterMillis:Date.now()+60000,serverTimeMillis:Date.now()});
const proof={flowId:id(1),receipt:'guided-test-contact-proof-'.repeat(3)};
const step=async title=>{console.log('STEP '+title);stages.push(title);await global.aitaGuidedStep?.(title);};
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-unsafe-swiftshader']});
 const context=await browser.newContext({viewport:{width:1440,height:1100},locale:'en-US'});
 await context.routeWebSocket('**',ws=>ws.close());
 await context.route('**/*',async route=>{
  try {
  const request=route.request(),u=new URL(request.url());
  if(u.origin===base)return route.continue();
  if(u.hostname==='aita.kz'&&!u.pathname.startsWith('/api/')){const response=await route.fetch({url:base+u.pathname});return route.fulfill({response});}
  const p=u.pathname.replace(/^\/api\//,'/');
  const body=()=>{try{return request.postDataJSON();}catch{return request.postData();}};
  calls.push({path:p,method:request.method()}); // Deliberately no passwords or OTP values in logs.
  let payload=[],status=200;
  if(p==='/auth/capabilities')payload={enabled:true,emailCodeLoginEnabled:true,passwordRecoveryEnabled:true,contactEmailVerificationEnabled:true,registrationEmailVerificationRequired:true,emailSecondFactorEnabled:true};
  else if(p==='/auth/registration/email/request'){assert.equal(body().address,email);emailTarget=body().target;payload=flow();}
  else if(p==='/auth/registration/email/verify'){assert.equal(body().code,code);payload={proof,target:emailTarget,address:email,channel:'EMAIL',expiresAtMillis:Date.now()+600000,serverTimeMillis:Date.now()};}
  else if(p==='/auth/signUp'){
   assert.equal(body().email,email);assert.equal(body().password,password);assert.ok(body().contactVerificationId);assert.deepEqual(body().contactEmailProofs,[proof]);
   account={...body(),id:id(99),publicId:'GUIDED-USER',workerAccountIds:null,supplierAccountIds:null,createdAt:Date.now(),isActive:true,appLanguage:'en',appModeId:0,appFontId:'noto_sans',activeStoreId:null};
   delete account.password;delete account.contactEmailProofs;registered=true;payload=tokens;status=201;
  }
  else if(p==='/auth/refresh')payload=tokens;
  else if(p==='/auth/session'||p==='/auth/ping')payload={};
  else if(p==='/auth/security/settings')payload={email,emailVerified:true,authenticatorEnabled:false,securityRevision:1};
  else if(p==='/auth/security/email-proof/request')payload=flow();
  else if(p==='/auth/security/account/delete'){
   assert.equal(body().currentPassword,password);assert.equal(body().emailProof.code,code);assert.equal(body().confirmed,true);
   assert.equal(stores.filter(s=>s.isActive!==false).length,0,'Delete stores before deleting the test account');account=null;deleted=true;payload='deleted';
  }
  else if(p==='/user/get')payload=account;
  else if(p==='/user/update'){account={...account,...(body().account||body())};payload=account;}
  else if(p==='/user/app-mode'){if(request.method()!=='GET')account.appModeId=body().appModeId??body().modeId??0;payload={accountId:account?.id,appModeId:account?.appModeId||0};}
  else if(p==='/user/app-state')payload={state:{revision:0,enabled:false}};
  else if(p==='/stores/get')payload=stores.filter(s=>!s.parentStoreId&&s.isActive!==false).map(s=>({...s,branches:stores.filter(b=>b.parentStoreId===s.id&&b.isActive!==false)}));
  else if(p==='/stores/add'){
   const value=body();payload={...value,id:value.id||id(30+stores.length),publicId:'GUIDED-STORE-'+stores.length,userIds:[account.id],architectureVersion:2,createdAt:Date.now(),updatedAt:Date.now(),isActive:true};stores.push(payload);
  }
  else if(p==='/stores/active'){const value=body();account.activeStoreId=typeof value==='string'?value:value.storeId;payload={};}
  else if(p==='/stores/delete'){
   const value=body(),target=typeof value==='string'?value:value.id;stores=stores.filter(s=>s.id!==target&&s.parentStoreId!==target);account.activeStoreId=stores.find(s=>!s.parentStoreId)?.id||null;payload={};
  }
  else if(p==='/stock/get')payload=items.filter(i=>i.storeId===(request.headers().store_id||account?.activeStoreId));
  else if(p==='/stock/add'){payload=body();items.push(payload);}
  else if(p==='/stockBatches/get')payload=batches;
  else if(p==='/stockBatches/add'){payload=body();batches.push(...payload);}
  else if(p==='/transactions/get')payload=transactions;
  else if(p==='/transactions/search')payload=transactions.filter(t=>t.type==='purchase');
  else if(p==='/transactions/complete'){payload={...body(),id:id(800+transactions.length),receiptNumber:'00000000800'};transactions.push(payload);}
  else if(p==='/subscriptions/store/get')payload={subscription:{id:id(600),storeId:account?.activeStoreId,ownerUserId:account?.id,planId:'internal_lifetime',status:'active',accessKind:'lifetime',autoRenew:false,currentPeriodStartMillis:1,currentPeriodEndMillis:null,revision:1},charges:[],plans:[],canManage:true,serverTimeMillis:Date.now()};
  else if(p.includes('profile-photo'))payload={accountId:account?.id||'',mode:'STORE'};
  else if(p.includes('client-updates'))return route.fulfill({status:204,body:''});
  else if(p.includes('global')||p.includes('bootstrap'))return route.fulfill({status:503,body:JSON.stringify({negative:true,payload:null}),headers:{'content-type':'application/json','X-AITA-Server':'AITA'}});
  return route.fulfill({status,headers:{'content-type':'application/json','X-AITA-Server':'AITA'},body:JSON.stringify({negative:false,payload:JSON.stringify(payload)})});
  } catch(error) { fixtureErrors.push(String(error));console.error('Fixture assertion:',error);await route.fulfill({status:500,body:JSON.stringify({negative:true,payload:null})}).catch(()=>{}); }
 });
 const page=await context.newPage();page.setDefaultTimeout(20000);page.on('pageerror',e=>browserErrors.push(String(e)));page.on('crash',()=>browserErrors.push('Renderer crashed'));
 const button=name=>page.getByRole('button',{name,exact:true});
 const wait=async()=>page.waitForTimeout(650);
 const shot=async label=>{await page.screenshot({path:path.join(out,label+'.png')});fs.writeFileSync(path.join(out,label+'.txt'),await page.locator('body').ariaSnapshot());};
 const reveal=async locator=>{await locator.waitFor({state:'attached'});for(let n=0;n<24;n++){const b=await locator.boundingBox();if(b&&b.height>0&&b.y>=88&&b.y+b.height<=1030)return;
  if(n===8){await locator.focus();await page.waitForTimeout(500);}
  await page.mouse.move(1200,600);await page.mouse.move(850,600,{steps:4});await page.mouse.wheel(0,b&&b.height>0&&b.y<88?-350:350);await page.waitForTimeout(350);
 }};
 const click=async label=>{const locator=button(label).last();if(!['Menu','Sale','Stock'].includes(label))await reveal(locator);await locator.click({force:true,delay:60});await wait();};
 const enter=async(locator,value)=>{await reveal(locator);await locator.click({force:true,delay:60});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.press('Backspace');await page.keyboard.insertText(value);await page.waitForTimeout(180);};
 const until=async(check,message)=>{for(let n=0;n<120&&!check();n++)await page.waitForTimeout(250);assert.ok(check(),message);};
 try {
  await step('Register a disposable test user; confirm with a local test email code');
  await page.goto(base);await page.locator('canvas').first().waitFor({timeout:120000});await page.keyboard.press('Tab');
  await click(/^(Создать аккаунт|Create account)$/);
  await button(/^(Перейти к подтверждению email|Continue to email confirmation)$/).waitFor();
  await enter(page.getByRole('textbox',{name:'',exact:true}).nth(1),'7771234567');
  await enter(page.getByRole('textbox',{name:/^Введите адрес email$|^Enter email address$/}),email);
  await enter(page.getByRole('textbox',{name:/^Введите имя$|^Enter first name$/}),'Guided');
  await enter(page.getByRole('textbox',{name:/^Введите фамилию$|^Enter last name$/}),'Tester');
  await enter(page.getByRole('textbox',{name:/^Введите пароль$|^Enter password$/}).last(),password);
  await enter(page.getByRole('textbox',{name:/^Повторите пароль$|^Repeat password$/}),password);
  await click(/^(Перейти к подтверждению email|Continue to email confirmation)$/);
  await click(/^(Отправить код подтверждения|Send confirmation code)$/);
  { const codeInput=page.getByRole('textbox',{name:'000000',exact:true});
  await codeInput.waitFor({state:'visible'});await page.waitForTimeout(400);
  await codeInput.click({force:true,delay:60});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.insertText(code);await page.waitForTimeout(250); }
  await click(/^(Подтвердить email|Confirm email)$/);
  await page.getByText(/Email confirmed|Email подтверждён/).first().waitFor();
  await click(/^(Создать аккаунт|Create account)$/);
  await until(()=>registered,'Registration reached the isolated API');await page.waitForTimeout(2500);await shot('01-registered');
  await page.mouse.click(1397,82);await wait();
  await step('Create a management store and a physical test branch');
  await click('Menu');await shot('02-menu');
  await click('Stores');await shot('03-stores');
  await page.getByRole('button',{name:'',exact:true}).last().click({force:true});await wait();await shot('04-store-editor');
  // Subsequent fields use accessible labels; they remain checked against the actual compiled UI.
  await enter(page.getByRole('textbox').filter({has:button('Default')}).first(),'Guided test warehouse');
  await click(/^My phone:/);await click(/^My email:/);
  await enter(page.getByRole('textbox',{name:'Enter the address',exact:true}),'Test warehouse, Almaty');
  await enter(page.getByRole('textbox',{name:'12 digits',exact:true}),'123456789012');
  await shot('05-store-name');
  // Leave the text editor before scrolling its Compose canvas in a headed browser.
  await page.mouse.click(1180,900);await page.mouse.move(800,850);await page.mouse.wheel(0,600);await wait();
  await click('Add store');await until(()=>stores.length===1,'Management store created');await page.waitForTimeout(1500);await shot('06-store-created');
  await click('Add branch');await page.mouse.move(800,500);await page.mouse.wheel(0,-3000);await wait();await shot('07-branch-editor');
  await enter(page.getByRole('textbox').filter({has:button('Default')}).first(),'Guided test branch');
  await click(/^Parent store phone:/);await click(/^Parent store email:/);
  await enter(page.getByRole('textbox',{name:'Enter the address',exact:true}),'Test branch, Almaty');
  await click('Add branch');await until(()=>stores.length===2,'Physical branch created');await page.waitForTimeout(1500);await shot('08-branch-created');
  await click('Set active across account devices');
  await until(()=>account.activeStoreId===stores[1].id,'Branch selected');await page.waitForTimeout(1000);
  await step('Scan a new barcode, add a product and an initial batch');
  await click('Sale');await page.keyboard.type('2618000001248\n',{delay:8});await page.waitForTimeout(1000);await shot('09-quick-add');
  await enter(page.getByRole('textbox').filter({has:button('Default')}).first(),'Guided tea');
  await enter(page.getByRole('textbox').nth(3),'750');
  await enter(page.getByRole('textbox').last(),'5');
  await click('Save item and add to cart');await until(()=>items.length===1&&batches.length===1,'Item and batch saved');
  assert.equal(items[0].name[0].value,'Guided tea');assert.equal(items[0].salePrices[0].price,'750');
  await page.waitForTimeout(1200);await shot('10-stock-and-cart');
  await step('Reload and verify the new stock and cart remain saved');
  await page.reload({waitUntil:'domcontentloaded'});await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(2500);await page.keyboard.press('Tab');
  await step('Take payment and complete a sale without a physical printer');
  await click('Payment');await shot('11-payment');await click('Receipt');await shot('12-receipt');
  await click('Complete');await until(()=>transactions.length===1,'Sale completed exactly once');await page.waitForTimeout(1200);await shot('13-sale-complete');
  assert.ok(transactions[0].clientOperationId);assert.ok(transactions[0].goodsInTransaction.length);
  if(process.env.AITA_GUIDED_RETURN==='1') {
   await step('Scan the sale receipt and add its item to a return cart');
   await click('Return');await page.keyboard.type('9910300000000002\n',{delay:8});await page.waitForTimeout(1200);
   await shot('return-01-receipt');await click('Items · 1');
   await click('Add to return');
   await click('Cart');
   await page.reload({waitUntil:'domcontentloaded'});await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(2000);await page.keyboard.press('Tab');
   await click('Return');
   for(let n=0;n<7;n++)await click('Add');
   assert.ok(await button('5 pc.').count(),'Receipt-linked return quantity stops at the five sold items');
   await click('Item in acceptable condition');await shot('return-02-cart');
   await click('Return destination');await shot('return-03-batches');
   await click('Create a Returned batch');
   await click('Payment');await click('Receipt');await click('Complete');
   await until(()=>transactions.length===2,'Return completed');
   const returned=transactions[1];assert.equal(returned.type,'return');
   assert.equal(returned.goodsInTransaction[0].originalTransactionId,transactions[0].id);
   assert.equal(returned.goodsInTransaction[0].returnDestinationKind,'RETURNED');
   assert.ok(returned.goodsInTransaction[0].quantity<=transactions[0].goodsInTransaction[0].quantity);
   await page.waitForTimeout(1000);await shot('return-04-complete');
  }
  await step('Delete the disposable branch and management store');
  await click('Menu');await click('Stores');await shot('14-before-delete');
  await click('svg/33_0.svg');await shot('15-delete-confirmation');
  if(await button('Delete').count())await click('Delete');
  await until(()=>stores.length===1,'Branch deleted');await page.waitForTimeout(800);await shot('16-branch-deleted');
  await page.reload({waitUntil:'domcontentloaded'});await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(2000);await page.keyboard.press('Tab');
  await click('svg/33_0.svg');if(await button('Delete').count())await click('Delete');
  await until(()=>stores.length===0,'Management store deleted');await page.waitForTimeout(800);
  await page.reload({waitUntil:'domcontentloaded'});await page.locator('canvas').first().waitFor({timeout:90000});await page.waitForTimeout(2000);await page.keyboard.press('Tab');
  await step('Delete the disposable user using password and a local email proof');
  await click('User account');await page.mouse.click(1190,800);await page.keyboard.press('Tab');await page.mouse.move(800,500);await page.mouse.wheel(0,1200);await wait();await click('Delete account');await shot('17-account-delete');
  await enter(page.getByRole('textbox').first(),password);
  await click('Get email confirmation');
  { const codeInput=page.getByRole('textbox',{name:'000000',exact:true});
  await codeInput.waitFor({state:'visible'});await page.waitForTimeout(400);
  await codeInput.click({force:true,delay:60});await page.keyboard.press('ControlOrMeta+A');await page.keyboard.insertText(code);await page.waitForTimeout(250); }
  await click('Delete account');await shot('18-delete-account-confirm');
  await click('Delete account');await until(()=>deleted,'Test account deleted through the UI');
  assert.deepEqual(fixtureErrors,[]);assert.deepEqual(browserErrors,[]);
  await page.waitForTimeout(1500);await shot('19-clean-session');
  const savedTokens=await page.evaluate(()=>localStorage.getItem('aita.auth_tokens'));
  assert.ok(!savedTokens || savedTokens==='null','Deleting the user clears the saved sign-in tokens');
  assert.ok(deleted,'Account deletion must finish; cleanup alone is not a UI pass');
 } catch(error) {
  await shot('failure').catch(()=>{});throw error;
 } finally {
  fs.writeFileSync(path.join(out,'journey.json'),JSON.stringify({kind:'fixture-ui',registered,deleted,stages,calls,fixtureErrors,browserErrors,remainingFixtureCounts:{stores:stores.length,items:items.length,batches:batches.length,transactions:transactions.length}},null,2));
  // Always tear down fixtures, even when a UI assertion or user Stop interrupts the journey.
  account=null;stores=[];items=[];batches=[];transactions=[];
  fs.writeFileSync(path.join(out,'cleanup.json'),JSON.stringify({fixtureDataDiscarded:true,productionTouched:false,browserProfile:'ephemeral'}));
  await context.close();await browser.close();
 }
})().catch(error=>{console.error(error);process.exitCode=1;});
