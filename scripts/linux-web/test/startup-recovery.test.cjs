const {test} = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const index = fs.readFileSync(path.resolve(__dirname, '../../../composeApp/src/wasmJsMain/resources/index.html'), 'utf8');
async function fixture(run) {
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/composeApp.js')) {
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
  assert.match(await page.locator('#aita-startup-message').textContent(), /Обновите браузер/);
  assert.equal(await page.locator('#aita-startup a').getAttribute('href'), 'https://github.com/bogdandonduk/AITA/releases/latest');
  assert.equal(await page.evaluate(() => window.aitaWebEntry), undefined);
}));
