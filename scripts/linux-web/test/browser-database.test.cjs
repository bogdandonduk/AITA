const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const fs = require('node:fs');
const { chromium } = require('playwright');
const path = require('node:path');
const workerSource = fs.readFileSync(path.resolve(__dirname, '../../../composeApp/src/wasmJsMain/resources/aita-db-worker.js'), 'utf8');
let server, browser, context, origin;
before(async () => {
  server = http.createServer((req, res) => {
    if (req.url.startsWith('/aita-db-worker.js')) {
      res.setHeader('Content-Type', 'text/javascript');
      const quota = req.url.includes('quota') ? "IDBObjectStore.prototype.put = function() { throw new DOMException('Simulated quota failure', 'QuotaExceededError'); };\n" : '';
      res.end(quota + workerSource);
    } else if (req.url === '/sql-wasm.js' || req.url === '/sql-wasm.wasm') {
      res.setHeader('Content-Type', req.url.endsWith('.wasm') ? 'application/wasm' : 'text/javascript');
      res.end(fs.readFileSync(require.resolve('sql.js/dist' + req.url)));
    } else { res.setHeader('Content-Type','text/html'); res.end('<!doctype html><title>Database test</title>'); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  origin = 'http://127.0.0.1:' + server.address().port;
  browser = await chromium.launch({headless:true});
  context = await browser.newContext();
});
after(async () => { await browser?.close(); await new Promise(resolve => server?.close(resolve)); });
async function page(quota=false) {
  const page = await context.newPage(); await page.goto(origin);
  await page.evaluate(quota => {
    window.worker = new Worker('/aita-db-worker.js' + (quota ? '?quota' : ''));
    let next = 0;
    window.query = (sql, params=[], action='exec') => new Promise((resolve, reject) => {
      const id = next++;
      const timer = setTimeout(() => reject(new Error('Worker request timed out')), 10000);
      const listener = event => {
        if(event.data.id !== id) return;
        clearTimeout(timer); worker.removeEventListener('message', listener);
        event.data.error ? reject(new Error(event.data.error.message)) : resolve(event.data.results.values);
      };
      worker.addEventListener('message', listener); worker.postMessage({id, sql, params, action});
    });
  }, quota);
  return page;
}
async function sql(p, command, params=[]) { return p.evaluate(([s,p]) => query(s,p), [command, params]); }
test('durable writes, reload, rollback, competing tab, and failed storage write', async () => {
  let p = await page();
  await sql(p, 'CREATE TABLE key_value (key TEXT PRIMARY KEY, value TEXT)');
  await sql(p, 'INSERT INTO key_value VALUES (?, ?)', ['cart', 'Себет 🛒']);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key = ?', ['cart']), [['Себет 🛒']]);
  await p.close();
  p = await page();
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key = ?', ['cart']), [['Себет 🛒']]);
  await p.evaluate(() => query(null, [], 'begin_transaction'));
  await sql(p, 'UPDATE key_value SET value = ?', ['rollback']);
  await p.evaluate(() => query(null, [], 'rollback_transaction'));
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['Себет 🛒']]);
  await p.evaluate(() => query(null, [], 'begin_transaction'));
  await sql(p, 'UPDATE key_value SET value = ?', ['committed']);
  await p.evaluate(() => query(null, [], 'end_transaction'));
  const second = await page();
  await assert.rejects(sql(second, 'SELECT value FROM key_value'), /already open in another tab/);
  await second.close(); await p.close();
  p = await page(true);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['committed']]);
  await assert.rejects(sql(p, 'UPDATE key_value SET value = ?', ['must-not-publish']), /quota failure/);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['committed']]);
  await p.close();
  p = await page();
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['committed']]);
  await p.close();
});
