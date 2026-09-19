const { test } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const index = fs.readFileSync(path.resolve(__dirname, '../../../composeApp/src/wasmJsMain/resources/index.html'), 'utf8');

test('update reload bypasses a previously cached entry script without changing local data', async () => {
  let version = 1;
  const requested = [];
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/composeApp.js')) {
      requested.push(req.url);
      res.setHeader('Content-Type', 'text/javascript');
      // Reproduce the old deployment's cache header, still held by returning browsers.
      res.setHeader('Cache-Control', 'public, max-age=14400');
      res.end(`window.runningBuild = ${version}; globalThis.aitaWebEntry.ready();`);
    } else if (req.url.startsWith('/styles.css')) {
      res.setHeader('Content-Type', 'text/css'); res.end('');
    } else if (req.url.endsWith('.js')) {
      res.setHeader('Content-Type', 'text/javascript'); res.end('');
    } else {
      res.setHeader('Content-Type', 'text/html');
      res.setHeader('Cache-Control', 'no-cache'); res.end(index);
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.addInitScript(() => history.replaceState({ navigation: 'keep' }, '', location.href));
    await page.goto(origin);
    await page.waitForFunction(() => window.runningBuild === 1);
    await page.evaluate(() => localStorage.setItem('test-unsent-cart', 'keep-me'));
    version = 2;
    await page.goto(origin + '/?unrelated=keep%20exact&_aita_build=2&_aita_refresh=123#stock');
    await page.waitForFunction(() => window.runningBuild === 2);
    assert.equal(await page.evaluate(() => localStorage.getItem('test-unsent-cart')), 'keep-me');
    assert.equal(page.url(), origin + '/?unrelated=keep%20exact#stock');
    assert.deepEqual(await page.evaluate(() => history.state), { navigation: 'keep' });
    assert.deepEqual(requested, ['/composeApp.js', '/composeApp.js?_aita_build=2&_aita_refresh=123']);

    // A clean-URL reload must not return to the original, still-fresh cached build 1.
    await page.reload();
    await page.waitForFunction(() => window.runningBuild === 2);
    assert.equal(page.url(), origin + '/?unrelated=keep%20exact#stock');
    const anotherTab = await page.context().newPage();
    await anotherTab.goto(origin);
    await anotherTab.waitForFunction(() => window.runningBuild === 2);
    assert.equal(anotherTab.url(), origin + '/');
    await anotherTab.close();

    // URL parameters must never manufacture an installed build or redirect script loading.
    await page.goto(origin + '/?_aita_build=https%3A%2F%2Fother.invalid%2Fscript.js&_aita_refresh=999');
    await page.waitForFunction(() => window.runningBuild === 2);
    assert.equal(requested.at(-1), '/composeApp.js?_aita_refresh=999');
    assert.equal(page.url(), origin + '/');
  } finally {
    await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
});

test('cleanup waits for app startup and preserves unrelated query bytes and history entries', async () => {
  const server = http.createServer((req, res) => {
    if (req.url.startsWith('/composeApp.js')) {
      res.setHeader('Content-Type', 'text/javascript'); res.end('window.entryLoaded = true;');
    } else if (req.url.endsWith('.js') || req.url.startsWith('/styles.css')) res.end('');
    else { res.setHeader('Content-Type', 'text/html'); res.end(index); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    const suffix = '/?keep=a%20b&keep=c+d&_aita_build=13&%5Faita_build=13&_aita_refresh=123&token=x%2By%3D#return';
    await page.goto(origin + suffix);
    await page.waitForFunction(() => window.entryLoaded);
    assert.equal(page.url(), origin + suffix, 'A failed/incomplete app startup retains retry hints');
    const length = await page.evaluate(() => history.length);
    await page.evaluate(() => {
      history.replaceState({ navigation: ['stock', 'item'] }, '', location.href);
      globalThis.aitaWebEntry.ready();
      globalThis.aitaWebEntry.ready();
    });
    assert.equal(page.url(), origin + '/?keep=a%20b&keep=c+d&token=x%2By%3D#return');
    assert.equal(await page.evaluate(() => history.length), length);
    assert.deepEqual(await page.evaluate(() => history.state), { navigation: ['stock', 'item'] });

    // Optional cache-hint persistence and cosmetic history cleanup must not break startup.
    const blocked = await browser.newPage();
    await blocked.addInitScript(() => {
      Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('Blocked', 'SecurityError'); } });
    });
    await blocked.goto(origin + '/?_aita_build=13&_aita_refresh=456');
    await blocked.waitForFunction(() => window.entryLoaded);
    await blocked.evaluate(() => globalThis.aitaWebEntry.ready());
    assert.equal(blocked.url(), origin + '/');
    await blocked.evaluate(() => {
      history.replaceState(null, '', '?_aita_refresh=777');
      history.replaceState = () => { throw new DOMException('Blocked', 'SecurityError'); };
      globalThis.aitaWebEntry.ready();
    });
    await blocked.close();
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
});
