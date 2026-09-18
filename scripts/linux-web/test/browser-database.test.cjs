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
      res.end(quota + workerSource.replace('committedBytes = bytes;', 'committedBytes = bytes; self.postMessage({durableWrite: true});'));
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
    let next = 0; window.durableWrites = 0;
    worker.addEventListener('message', e => { if (e.data.durableWrite) window.durableWrites++; });
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
  await sql(p, 'WITH target AS (SELECT key FROM key_value WHERE key = ?) UPDATE key_value SET value = ? WHERE key IN (SELECT key FROM target)', ['cart', 'cte-committed']);
  await p.close();
  p = await page();
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['cte-committed']]);
  await sql(p, 'UPDATE key_value SET value = ?', ['committed']);
  const second = await page();
  await assert.rejects(sql(second, 'SELECT value FROM key_value'), /already open in another tab/);
  await second.close(); await p.close();
  p = await page(true);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['committed']]);
  // A read-only transaction does not export/rewrite the entire database (even at quota).
  await p.evaluate(() => query(null, [], 'begin_transaction'));
  assert.deepEqual(await sql(p, 'WITH target AS (SELECT value FROM key_value) SELECT value FROM target'), [['committed']]);
  await p.evaluate(() => query(null, [], 'end_transaction'));
  await assert.rejects(sql(p, 'UPDATE key_value SET value = ?', ['must-not-publish']), /quota failure/);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['committed']]);
  await p.close();
  p = await page();
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value'), [['committed']]);
  await p.close();
});

test('large cache chunk batch persists once and a quota failure preserves the committed generation', async () => {
  let p = await page();
  await sql(p, 'CREATE TABLE IF NOT EXISTS key_value (key TEXT PRIMARY KEY, value TEXT)');
  await sql(p, 'INSERT OR REPLACE INTO key_value VALUES (?, ?)', ['cache-manifest', 'old']);
  const rows = Array.from({length:128}, (_,i) => ['cache-chunk-'+i, 'Ж₸'.repeat(32768)]);
  rows.push(['cache-manifest','new']);
  const statement = 'INSERT OR REPLACE INTO key_value(key,value) VALUES '+rows.map(()=>'(?,?)').join(',');
  const before = await p.evaluate(() => durableWrites);
  await sql(p, statement, rows.flat());
  assert.equal(await p.evaluate(() => durableWrites), before+1);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key=?', ['cache-manifest']), [['new']]);
  await p.close(); p = await page(true);
  const failedRows = [['cache-new-generation','unsaved'], ['cache-manifest','uncommitted']];
  await assert.rejects(sql(p, 'INSERT OR REPLACE INTO key_value VALUES (?,?),(?,?)', failedRows.flat()), /quota failure/);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key=?', ['cache-manifest']), [['new']]);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key=?', ['cache-new-generation']), []);
  await p.close(); p = await page();
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key=?', ['cache-manifest']), [['new']]);
  assert.deepEqual(await sql(p, 'SELECT value FROM key_value WHERE key=?', ['cache-chunk-127']), [[rows[127][1]]]);
  await p.close();
});
