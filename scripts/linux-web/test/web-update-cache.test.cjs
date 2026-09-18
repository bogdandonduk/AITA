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
      res.end(`window.runningBuild = ${version};`);
    } else if (req.url.startsWith('/styles.css')) {
      res.setHeader('Content-Type', 'text/css'); res.end('');
    } else {
      res.setHeader('Content-Type', 'text/html');
      res.setHeader('Cache-Control', 'no-cache'); res.end(index);
    }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    await page.goto(origin);
    await page.waitForFunction(() => window.runningBuild === 1);
    await page.evaluate(() => localStorage.setItem('test-unsent-cart', 'keep-me'));
    version = 2;
    await page.goto(origin + '/?unrelated=keep&_aita_build=2&_aita_refresh=123');
    await page.waitForFunction(() => window.runningBuild === 2);
    assert.equal(await page.evaluate(() => localStorage.getItem('test-unsent-cart')), 'keep-me');
    assert.deepEqual(requested, ['/composeApp.js', '/composeApp.js?_aita_build=2&_aita_refresh=123']);

    // URL parameters must never manufacture an installed build or redirect script loading.
    await page.goto(origin + '/?_aita_build=https%3A%2F%2Fother.invalid%2Fscript.js&_aita_refresh=999');
    await page.waitForFunction(() => window.runningBuild === 2);
    assert.equal(requested.at(-1), '/composeApp.js?_aita_refresh=999');
  } finally {
    await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
});
