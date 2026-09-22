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
 const responseStatuses = new WeakMap(), activeRequests = new Set(), reloadRequests = new WeakSet();
 const reloadCancellations=[];
 page.on('request', request => activeRequests.add(request));
 page.on('requestfinished', request => activeRequests.delete(request));
 page.on('response', response => responseStatuses.set(response.request(), response.status()));
 page.on('pageerror', error => errors.push(error.message));
 page.on('console', message => { if(message.type()==='error') errors.push(message.text()); });
 page.on('requestfailed', request => {
  const entry = {path:new URL(request.url()).pathname,error:request.failure()?.errorText};
  // Ktor cancels the empty response channel after the server explicitly reports no release.
  if (entry.error === 'net::ERR_ABORTED' && responseStatuses.get(request) === 204 &&
      /^\/client-updates\/(release|test)\.json$/.test(entry.path)) emptyUpdateResponses.push(entry);
  else if (entry.error === 'net::ERR_ABORTED' && reloadRequests.has(request)) reloadCancellations.push(entry);
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
  await page.setViewportSize({width:390,height:844});
  await page.emulateMedia({colorScheme:'dark'});
  assert.equal(await page.locator('#aita-startup').evaluate(element => getComputedStyle(element).backgroundColor), 'rgb(18, 21, 26)');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-startup-narrow-dark.png'});
  await page.emulateMedia({colorScheme:'light'});
  await page.setViewportSize({width:1280,height:900});
  resumeStartup();
  await page.locator('canvas').first().waitFor({state:'visible',timeout:60000});
  assert.equal(await page.locator('#aita-startup').count(), 0, 'Startup must disappear when the app starts');
  await page.waitForTimeout(5000);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-wide.png'});
  await page.setViewportSize({width:390,height:844});
  await page.waitForTimeout(1500);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow.png'});
  const lightPixel = await backgroundPixel(page);
  await page.keyboard.press('Tab');
  await page.getByRole('button', {name: 'App theme', exact: true}).click({force:true});
  await page.getByRole('button', {name: /Obsidian Black/}).click({force:true});
  await page.getByRole('button', {name: /Close|Cancel/}).last().click({force:true});
  await page.waitForTimeout(1000);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow-theme.png'});
  const selectedPixel = await backgroundPixel(page);
  assert.notDeepEqual(selectedPixel, lightPixel, 'Theme click must change the rendered theme');
  const before = await page.evaluate(()=>localStorage.getItem('aita.installation_id'));
  // Reload deliberately interrupts requests already belonging to the old document.
  for (const request of activeRequests) reloadRequests.add(request);
  await page.reload({waitUntil:'domcontentloaded'});
  await page.locator('canvas').first().waitFor({state:'visible',timeout:60000});
  await page.waitForTimeout(1500);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow-reloaded.png'});
  assert.deepEqual(await backgroundPixel(page), selectedPixel, 'Selected theme must survive page reload');
  const after = await page.evaluate(()=>localStorage.getItem('aita.installation_id'));
  if (!before || before !== after) throw new Error('Browser installation identity did not survive reload');
  // The guest footer stays at the bottom, outside the scrolling authentication form.
  // A fresh catalogue request proves the click opened Downloads without signing in.
  async function openGuestDownloads() {
   await Promise.all([
    page.waitForResponse(response => new URL(response.url()).pathname === '/client-updates/release.json' && response.status() === 200),
    page.mouse.click(195, 804)
   ]);
   await page.waitForTimeout(1000);
   await page.waitForLoadState('networkidle');
  }
  await openGuestDownloads();
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-downloads.png'});
  await page.mouse.click(20, 58);
  await page.waitForTimeout(1000);
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-back.png'});
  await openGuestDownloads();
  await page.setViewportSize({width:1280,height:900});
  await page.waitForTimeout(1000);
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-downloads-wide.png'});
  await page.keyboard.press('Tab');
  await page.getByRole('button', {name:/^(Download folder|Папка загрузок)$/}).click({force:true});
  await page.waitForTimeout(1000);
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-guest-download-folder.png'});
  await Promise.all([
   page.waitForResponse(response => new URL(response.url()).pathname === '/client-updates/release.json' && response.status() === 200),
   page.mouse.click(20, 58)
  ]);
  await page.waitForLoadState('networkidle');
  console.log(JSON.stringify({url:page.url(),canvasCount:await page.locator('canvas').count(),text:await page.locator('body').innerText(),errors,failed,emptyUpdateResponses,reloadCancellations,responses},null,2));
  if(errors.length || failed.length) process.exitCode=1;
 } catch(error) {
  resumeStartup();
  console.log(JSON.stringify({failure:error.message,text:await page.locator('body').innerText(),errors,failed,emptyUpdateResponses,reloadCancellations,responses},null,2));
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-failure.png'});
  process.exitCode=1;
 } finally {resumeStartup(); await context.unrouteAll({behavior:'ignoreErrors'}); await browser.close();}
})();
