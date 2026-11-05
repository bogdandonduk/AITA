const { chromium } = require('playwright');
const assert = require('node:assert/strict');
async function backgroundPixel(page) {
 const png = await page.screenshot();
 return page.evaluate(async base64 => {
  const bitmap = await createImageBitmap(await (await fetch('data:image/png;base64,' + base64)).blob());
  const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
  const context = canvas.getContext('2d');
  context.drawImage(bitmap, 0, 0);
  return Array.from(context.getImageData(10, 100, 1, 1).data);
 }, png.toString('base64'));
}
(async () => {
 const browser = await chromium.launch({headless: true, args:['--enable-unsafe-swiftshader']});
 const context = await browser.newContext({viewport:{width:1280,height:900},locale:'en-US'});
 if (process.env.AITA_WEB_LOCAL_ORIGIN) {
  await context.route('https://aita.kz/**', async route => {
   const target = new URL(route.request().url());
   const response = await route.fetch({url:process.env.AITA_WEB_LOCAL_ORIGIN + target.pathname + target.search});
   await route.fulfill({response});
  });
 }
 const page = await context.newPage();
 let resumeStartup;
 const startupGate = new Promise(resolve => { resumeStartup = resolve; });
 // Hold Wasm, allowing document/font readiness while checking the real slow-start splash.
 await page.route('**/*.wasm*', async route => { await startupGate; await route.fallback(); });
 const errors=[], failed=[], responses=[], emptyUpdateResponses=[];
 const themeEvidence={};
 // Compose exposes moving accessibility nodes before the sheet entrance animation
 // finishes. Forced clicks must wait for stable geometry rather than target its old position.
 async function clickReady(locator, beforeClick = null) {
  await locator.waitFor({state:'visible',timeout:15000});
  let previous=null,stable=0;
  for(let n=0;n<40;n++) {
   const box=await locator.boundingBox();
   if(box && previous && ['x','y','width','height'].every(k=>Math.abs(box[k]-previous[k])<0.5)) stable++;
   else stable=0;
   previous=box;
   if(stable>=3)break;
   await page.waitForTimeout(150);
  }
  assert(stable>=3,'The control must finish moving before it is clicked');
  // Capture pending reads after readiness waits, immediately before this gesture.
  if(beforeClick)beforeClick();
  await locator.click({force:true,delay:60});
  await page.waitForTimeout(500);
 }
 async function savedTheme() {
  const worker=page.workers().find(worker=>new URL(worker.url()).pathname==='/aita-db-worker.js');
  assert(worker,'Theme preferences use the durable browser database');
  return worker.evaluate(async()=>{await ready;return database.exec('SELECT value FROM key_value WHERE key = ?', ['key_appTheme'])[0]?.values[0]?.[0]??null;});
 }
 async function expectSavedTheme(value) {
  for(let n=0;n<100;n++){if(await savedTheme()===value)return;await page.waitForTimeout(100);}
  assert.equal(await savedTheme(),value,'The selected theme must be durably stored');
 }
 const backButton=()=>page.getByRole('button',{name:/^(Back|Назад|Артқа|Артка|Бозгашт|Orqaga)$/}).first();
 const downloadFooter=()=>page.getByRole('button').filter({hasText:/Download AITA for your devices|Скачать AITA для ваших устройств|Құрылғыларыңызға AITA жүктеп алу|Түзмөктөрүңүз үчүн AITA жүктөп алуу|Боргирии AITA барои дастгоҳҳои шумо|Qurilmalaringiz uchun AITA/}).last();
 const responseStatuses = new WeakMap(), activeRequests = new Set(), reloadRequests = new WeakSet();
 const requestDocuments = new WeakMap(), completedPublicDocuments = new Set(), publicBodyCancellations = [];
 let documentNumber = 0, priorPublicGeneration = null;
 async function verifyPublicCatalogueCommitted() {
  const worker = page.workers().find(worker => new URL(worker.url()).pathname === '/aita-db-worker.js');
  assert(worker, 'Browser database worker is available');
  const saved = await worker.evaluate(async () => {
   await ready;
   const key='public-market-home-v1', prefix=`aita-cache-chunks-v1:${key.length}:${key}:`;
   const get=key=>database.exec('SELECT value FROM key_value WHERE key = ?', [key])[0]?.values[0]?.[0];
   const manifest=get(prefix+'manifest');
   if(!manifest)return null;
   const [format,generation,length,count,checksum]=manifest.split('|');
   if(format!=='1' || !/^[a-f0-9]{1,64}$/.test(generation) || +count<1 || +count>2048)return null;
   let raw='';
   for(let i=0;i<+count;i++){const chunk=get(prefix+generation+':'+i);if(typeof chunk!=='string')return null;raw+=chunk;}
   let hash=0x811c9dc5;for(let i=0;i<raw.length;i++)hash=Math.imul(hash^raw.charCodeAt(i),0x01000193)>>>0;
   if(raw.length!==+length || hash.toString(16)!==checksum)return null;
   const value=JSON.parse(raw);
   return {generation,publicVersion:value.publicVersion,accountId:value.discovery.accountId,
    categories:Array.isArray(value.discovery.categories),offers:Array.isArray(value.discovery.page.offers)};
  });
  assert(saved && saved.publicVersion===1 && saved.accountId==null && saved.categories && saved.offers,
   'The app must parse and commit a valid public catalogue');
  assert.notEqual(saved.generation,priorPublicGeneration,'Reload must commit a fresh response, not reuse stale fallback');
  priorPublicGeneration=saved.generation;completedPublicDocuments.add(documentNumber);
 }
 const reloadCancellations=[], pageNavigationCancellations=[], pageNavigationRequests=new WeakSet(), navigationMarks=[];
 const requestIds=new WeakMap();let nextRequestId=0;
 function markPendingPublicRead() {
  for (const request of activeRequests) {
   if (request.method() === 'POST' && new URL(request.url()).pathname === '/market/public/discovery') {
    pageNavigationRequests.add(request);navigationMarks.push({requestId:requestIds.get(request),method:request.method(),path:'/market/public/discovery',gesture:'public-navigation',document:documentNumber});
   }
  }
 }
 function markPendingCapabilitiesRead() {
  for(const request of activeRequests) {
   if(request.method()==='GET' && new URL(request.url()).pathname==='/auth/capabilities') {
    pageNavigationRequests.add(request);
    navigationMarks.push({requestId:requestIds.get(request),method:'GET',path:'/auth/capabilities',gesture:'auth-to-downloads',document:documentNumber});
   }
  }
 }
 page.on('request', request => {requestIds.set(request,++nextRequestId);activeRequests.add(request);requestDocuments.set(request,documentNumber);});
 page.on('requestfinished', request => activeRequests.delete(request));
 page.on('response', response => responseStatuses.set(response.request(), response.status()));
 page.on('pageerror', error => errors.push(error.message));
 page.on('console', message => { if(message.type()==='error') errors.push(message.text()); });
 page.on('requestfailed', request => {
  const entry = {requestId:requestIds.get(request),path:new URL(request.url()).pathname,error:request.failure()?.errorText};
  // Ktor cancels the empty response channel after the server explicitly reports no release.
  if (entry.error === 'net::ERR_ABORTED' && responseStatuses.get(request) === 204 &&
      /^\/client-updates\/(release|test)\.json$/.test(entry.path)) emptyUpdateResponses.push(entry);
  else if (entry.error === 'net::ERR_ABORTED' && reloadRequests.has(request)) reloadCancellations.push(entry);
  // Guest navigation, theme changes and responsive layout can replace a composable
  // and cancel its pending read-only lookup. Only
  // requests already pending at our own navigation gesture qualify; never hide
  // an authentication mutation, a timeout, or an unrelated transport failure.
  else if (entry.error === 'net::ERR_ABORTED' && pageNavigationRequests.has(request)) pageNavigationCancellations.push(entry);
  // Ktor may cancel fetch's body stream after parsing it. Only accept that cleanup
  // for a 200 public read whose current document independently committed a fresh,
  // checksum-valid catalogue. Truncation, stale fallback and other errors still fail.
  else if(entry.error==='net::ERR_ABORTED' && responseStatuses.get(request)===200 &&
    request.method()==='POST' && entry.path==='/market/public/discovery')
    publicBodyCancellations.push({...entry,document:requestDocuments.get(request)});
  else failed.push(entry);
  activeRequests.delete(request);
 });
 page.on('response', response => {if(response.url().startsWith('http')) responses.push({path:new URL(response.url()).pathname,status:response.status()});});
 try {
  await page.goto(process.env.AITA_WEB_URL || 'https://aita.kz/',{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(() => document.querySelector('#aita-startup img')?.naturalWidth > 0);
  const splash = await page.locator('#aita-startup img').boundingBox();
  assert(splash && splash.width >= 112 && splash.width <= 160, 'Startup must display the AITA image');
  assert(Math.abs(splash.x + splash.width/2 - 640) < 2 && Math.abs(splash.y + splash.height/2 - 450) < 2,
   'Startup image must be centered');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-startup.png'});
  markPendingPublicRead();
  await page.setViewportSize({width:390,height:844});
  markPendingPublicRead();
  await page.emulateMedia({colorScheme:'dark'});
  assert.equal(await page.locator('#aita-startup').evaluate(element => getComputedStyle(element).backgroundColor), 'rgb(18, 21, 26)');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-startup-narrow-dark.png'});
  markPendingPublicRead();
  await page.emulateMedia({colorScheme:'light'});
  markPendingPublicRead();
  await page.setViewportSize({width:1280,height:900});
  resumeStartup();
  await page.locator('canvas').first().waitFor({state:'visible',timeout:60000});
  assert.equal(await page.locator('#aita-startup').count(), 0, 'Startup must disappear when the app starts');
  await page.waitForTimeout(5000);
  await page.waitForLoadState('networkidle');
  await verifyPublicCatalogueCommitted();
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-wide.png'});
  markPendingPublicRead();
  await page.setViewportSize({width:390,height:844});
  await page.waitForTimeout(1500);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow.png'});
  const lightPixel = await backgroundPixel(page);
  await page.keyboard.press('Tab');
  await clickReady(page.getByRole('button', {name: /^(App theme|Тема приложения|Қолданба тақырыбы|Мавзӯи барнома|Колдонмонун темасы|Ilova mavzusi)$/}));
  themeEvidence.beforeStored=await savedTheme();
  await clickReady(page.getByRole('button', {name: /Obsidian Black|Обсидиановый чёрный|Обсидиан қара|Обсидиан кара|Сиёҳи обсидианӣ|Obsidian qora/}),markPendingPublicRead);
  await expectSavedTheme('1');themeEvidence.selectedStored=await savedTheme();
  await clickReady(page.getByRole('button', {name: /^(Close|Cancel|Отменить|Болдырмау|Бекор кардан|Жокко чыгаруу|Bekor qilish)$/}).last());
  // Pixel and durable-storage checks below verify the actual rendered state.
  // Compose can retain the dismissed dialog in its accessibility tree until reload.
  await page.waitForTimeout(1000);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow-theme.png'});
  const selectedPixel = await backgroundPixel(page);
  Object.assign(themeEvidence,{lightPixel,selectedPixel});
  assert(selectedPixel.slice(0,3).every(value=>value<64),'Obsidian must render a dark surface; an open white sheet cannot pass this check');
  assert.notDeepEqual(selectedPixel, lightPixel, 'Theme click must change the rendered theme');
  const before = await page.evaluate(()=>localStorage.getItem('aita.installation_id'));
  // Reload deliberately interrupts requests already belonging to the old document.
  for (const request of activeRequests) reloadRequests.add(request);
  documentNumber++;
  await page.reload({waitUntil:'domcontentloaded'});
  await page.locator('canvas').first().waitFor({state:'visible',timeout:60000});
  await page.waitForTimeout(1500);
  await page.waitForLoadState('networkidle');
  await verifyPublicCatalogueCommitted();
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow-reloaded.png'});
  await expectSavedTheme('1');themeEvidence.reloadedStored=await savedTheme();
  themeEvidence.reloadedPixel=await backgroundPixel(page);
  assert.deepEqual(themeEvidence.reloadedPixel, selectedPixel, 'Selected theme must survive page reload');
  const after = await page.evaluate(()=>localStorage.getItem('aita.installation_id'));
  if (!before || before !== after) throw new Error('Browser installation identity did not survive reload');
  // The public marketplace is now the guest entry point. Visit authentication
  // through its real sign-in action before exercising the guest download footer.
  await page.keyboard.press('Tab');
  await clickReady(page.getByRole('button', {name:/^(Sign in|Log in|Войти|Кіру|Кирүү|Ворид шудан|Kirish)$/}).first(),markPendingPublicRead);
  await page.waitForTimeout(800);
  // The guest footer stays at the bottom, outside the scrolling authentication form.
  // A fresh catalogue request proves the click opened Downloads without signing in.
  async function openGuestDownloads() {
   await Promise.all([
    page.waitForResponse(response => new URL(response.url()).pathname === '/client-updates/release.json' && response.status() === 200),
    clickReady(downloadFooter(),markPendingCapabilitiesRead)
   ]);
   await page.waitForTimeout(1000);
   await page.waitForLoadState('networkidle');
  }
  await openGuestDownloads();
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-downloads.png'});
  await clickReady(backButton());
  await page.waitForTimeout(1000);
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-back.png'});
  await openGuestDownloads();
  markPendingPublicRead();
  await page.setViewportSize({width:1280,height:900});
  await page.waitForTimeout(1000);
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-downloads-wide.png'});
  await page.keyboard.press('Tab');
  await clickReady(page.getByRole('button', {name:/^(Download folder|Папка загрузок)$/}));
  await page.waitForTimeout(1000);
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-download-folder.png'});
  await Promise.all([
   page.waitForResponse(response => new URL(response.url()).pathname === '/client-updates/release.json' && response.status() === 200),
   clickReady(backButton())
  ]);
  await page.waitForLoadState('networkidle');
  for(const entry of publicBodyCancellations)if(!completedPublicDocuments.has(entry.document))failed.push(entry);
  console.log(JSON.stringify({themeEvidence,navigationMarks,publicBodyCancellations,publicDocumentsCommitted:[...completedPublicDocuments],url:page.url(),canvasCount:await page.locator('canvas').count(),text:await page.locator('body').innerText(),errors,failed,emptyUpdateResponses,reloadCancellations,pageNavigationCancellations,responses},null,2));
  if(errors.length || failed.length) process.exitCode=1;
 } catch(error) {
  resumeStartup();
  console.log(JSON.stringify({themeEvidence,navigationMarks,failure:error.message,text:await page.locator('body').innerText(),errors,failed,emptyUpdateResponses,reloadCancellations,pageNavigationCancellations,responses},null,2));
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-failure.png'});
  require('node:fs').writeFileSync(process.env.AITA_ARTIFACTS+'/web-failure-aria.txt',await page.locator('body').ariaSnapshot());
  process.exitCode=1;
 } finally {resumeStartup(); await context.unrouteAll({behavior:'ignoreErrors'}); await browser.close();}
})();
