const {test} = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const index = fs.readFileSync(path.resolve(__dirname, '../../../composeApp/src/wasmJsMain/resources/index.html'), 'utf8');
const compatibility = fs.readFileSync(path.resolve(__dirname, '../../../composeApp/src/wasmJsMain/resources/aita-browser-compat.js'), 'utf8');
async function fixture(run) {
  const server = http.createServer((req, res) => {
    if (req.url === '/aita-browser-compat.js') {
      res.setHeader('Content-Type', 'text/javascript'); res.end(compatibility);
    } else if (req.url.startsWith('/composeApp.js')) {
      res.setHeader('Content-Type', 'text/javascript');
      // Simulate a stale entry referencing a deleted chunk. Fresh-copy retry succeeds.
      res.end(req.url.includes('_aita_refresh=') ? 'window.appReady = true; aitaWebEntry.ready();' :
        'Promise.reject(new Error("ChunkLoadError: missing old application chunk"));');
    } else if (req.url.endsWith('.js') || req.url.startsWith('/styles.css')) res.end('');
    else { res.setHeader('Content-Type', 'text/html'); res.end(index); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({headless:true});
  try { await run(browser, 'http://127.0.0.1:' + server.address().port); }
  finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
test('stale chunk failure offers a fresh retry and preserves drafts and clean navigation URL', async () => fixture(async (browser, origin) => {
  const page = await browser.newPage(); await page.goto(origin + '/?keep=yes#stock');
  await page.evaluate(() => localStorage.setItem('unsent-test-cart', 'preserve'));
  await page.locator('#aita-startup-retry').waitFor();
  assert.match(await page.locator('#aita-startup-message').textContent(), /could not finish loading/);
  await page.locator('#aita-startup-retry').click();
  await page.waitForFunction(() => window.appReady);
  assert.equal(page.url(), origin + '/?keep=yes#stock');
  assert.equal(await page.evaluate(() => localStorage.getItem('unsent-test-cart')), 'preserve');
}));
test('unsupported Wasm receives a browser explanation and a usable native download link', async () => fixture(async (browser, origin) => {
  const context = await browser.newContext({locale:'ru-RU'});
  await context.addInitScript(() => { WebAssembly.validate = () => false; });
  const page = await context.newPage(); await page.goto(origin);
  assert.match(await page.locator('#aita-startup-message').textContent(), /WebAssembly/);
  assert.equal(await page.locator('#aita-startup a').getAttribute('href'), 'downloads.html');
  assert.equal(await page.evaluate(() => window.aitaWebEntry), undefined);
}));
test('Chrome 122 never starts Wasm or touches account/cart storage, even after retry', async () => fixture(async (browser, origin) => {
  const context = await browser.newContext({locale:'ru-RU', userAgent:'Mozilla/5.0 (X11; Linux x86_64) Chrome/122.0.6261.128 Safari/537.36'});
  const page = await context.newPage();
  let appRequests = 0;
  page.on('request', request => { if (request.url().includes('/composeApp.js')) appRequests++; });
  await page.goto(origin);
  await page.evaluate(() => { localStorage.setItem('unsent-test-cart', 'keep'); localStorage.setItem('account-test', 'keep'); });
  assert.match(await page.locator('#aita-startup-message').textContent(), /Самая новая версия не обязательна/);
  await page.locator('#aita-startup-retry').click();
  await page.waitForURL(/_aita_refresh=/);
  await page.locator('#aita-startup-retry').waitFor();
  assert.equal(appRequests, 0);
  assert.equal(await page.locator('#aita-startup a').getAttribute('href'), 'downloads.html');
  assert.deepEqual(await page.evaluate(() => [localStorage.getItem('unsent-test-cart'),localStorage.getItem('account-test')]), ['keep','keep']);
}));
test('unavailable WebGL has an immediate localized explanation instead of a blank canvas', async () => fixture(async (browser, origin) => {
  const context = await browser.newContext({locale:'ru-RU'});
  await context.addInitScript(() => { HTMLCanvasElement.prototype.getContext = () => null; });
  const page = await context.newPage();
  let appRequests = 0;
  page.on('request', request => { if (request.url().includes('/composeApp.js')) appRequests++; });
  await page.goto(origin);
  assert.match(await page.locator('#aita-startup-message').textContent(), /драйвер видеокарты/);
  assert.match(await page.locator('#aita-startup-message').textContent(), /Версия браузера подходит/);
  assert.equal(appRequests, 0);
  assert.equal(await page.locator('#aita-startup a').getAttribute('href'), 'downloads.html');
}));

test('Chrome 123 starts normally and does not require the latest Chrome', async () => fixture(async (browser, origin) => {
  const context = await browser.newContext({userAgent: 'Mozilla/5.0 Chrome/123.0.6312.122 Safari/537.36'});
  const page = await context.newPage();
  await page.goto(origin + '/?_aita_refresh=123');
  await page.waitForFunction(() => window.appReady);
  assert.equal(await page.locator('#aita-startup-retry').count(), 0);
}));

test('blocked storage explains the capability failure without asking for a browser update', async () => fixture(async (browser, origin) => {
  const context = await browser.newContext({locale: 'ru-RU'});
  await context.addInitScript(() => Object.defineProperty(window, 'indexedDB', {get() { throw new DOMException('Blocked', 'SecurityError'); }}));
  const page = await context.newPage();
  let appRequests = 0;
  page.on('request', request => { if (request.url().includes('/composeApp.js')) appRequests++; });
  await page.goto(origin);
  const message = await page.locator('#aita-startup-message').textContent();
  assert.match(message, /хранилищу браузера/);
  assert.doesNotMatch(message, /Обновите браузер/);
  assert.equal(appRequests, 0);
}));

test('all supported UI languages distinguish a graphics failure from a browser update', async () => fixture(async (browser, origin) => {
  const expected = {en: /version is supported/, ru: /Версия браузера подходит/, kk: /нұсқасы жарамды/, ky: /нускасы жарайт/, tg: /Нусхаи браузер мувофиқ/, uz: /versiyasi mos/};
  for (const [locale, pattern] of Object.entries(expected)) {
    const context = await browser.newContext({locale});
    await context.addInitScript(() => { HTMLCanvasElement.prototype.getContext = () => null; });
    const page = await context.newPage(); await page.goto(origin);
    assert.match(await page.locator('#aita-startup-message').textContent(), pattern);
    await context.close();
  }
}));
