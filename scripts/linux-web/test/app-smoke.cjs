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
  await page.locator('canvas').first().waitFor({state:'visible',timeout:60000});
  await page.waitForTimeout(5000);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-wide.png'});
  await page.setViewportSize({width:390,height:844});
  await page.waitForTimeout(1500);
  await page.waitForLoadState('networkidle');
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-narrow.png'});
  const lightPixel = await backgroundPixel(page);
  await page.mouse.click(280, 292);
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
  console.log(JSON.stringify({url:page.url(),canvasCount:await page.locator('canvas').count(),text:await page.locator('body').innerText(),errors,failed,emptyUpdateResponses,reloadCancellations,responses},null,2));
  if(errors.length || failed.length) process.exitCode=1;
 } catch(error) {
  console.log(JSON.stringify({failure:error.message,text:await page.locator('body').innerText(),errors,failed,emptyUpdateResponses,reloadCancellations,responses},null,2));
  await page.screenshot({path:process.env.AITA_ARTIFACTS+'/web-failure.png'});
  process.exitCode=1;
 } finally {await browser.close();}
})();
