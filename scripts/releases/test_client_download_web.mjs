import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { webcrypto, createHash } from 'node:crypto';

const source = fs.readFileSync(new URL('../../composeApp/src/wasmJsMain/kotlin/kz/aita/ClientDownloadsWeb.kt', import.meta.url), 'utf8');
const code = '(function()' + source.match(/private fun saveDownloadWeb[\s\S]*?js\("""([\s\S]*?)"""\)/)[1] + ')()';
const bytes = new TextEncoder().encode('verified release download');
const sha = createHash('sha256').update(bytes).digest('hex');
function attempt({ content = bytes, expected = bytes.length, hash = sha, picker = true, cancel = false } = {}) {
  const actions = [], requests = [], files = [];
  const handle = { createWritable: async () => {
    actions.push('create');
    return { write: async blob => files.push(new Uint8Array(await blob.arrayBuffer())), close: async () => actions.push('close'), abort: async () => actions.push('abort') };
  } };
  const done = new Promise(resolve => vm.runInNewContext(code, {
    url:'https://updates.example.org/app.exe', expected, hash, fileName:'AITA.exe',
    window: picker ? { showSaveFilePicker: () => { actions.push('picker'); return cancel ? Promise.reject(new DOMException('Cancelled', 'AbortError')) : Promise.resolve(handle); } } : {},
    crypto:webcrypto, Uint8Array, Blob, AbortController, Promise, clearTimeout,
    setTimeout:(fn, delay) => { const timer = setTimeout(fn, delay); timer.unref(); return timer; },
    URL:{createObjectURL: blob => { files.push(blob); return 'blob:owned'; },revokeObjectURL:()=>{}},
    document:{createElement:()=>({style:{},click:()=>actions.push('browser-download'),remove:()=>{}}),body:{appendChild:()=>{}}},
    fetch:async (url, options) => { requests.push({url,options}); return new Response(content); },
    progress:()=>{}, result:resolve,
  }));
  return { done, actions, requests, files };
}
test('save picker opens synchronously and only verified bytes reach the chosen file', async () => {
  const run = attempt(); assert.deepEqual(run.actions, ['picker']);
  assert.equal(await run.done, '');
  assert.deepEqual(run.actions, ['picker','create','close']);
  assert.deepEqual(run.files[0], bytes);
  assert.equal(run.requests[0].options.credentials, 'omit');
  assert.equal(run.requests[0].options.redirect, 'error');
});
test('wrong hash, truncated and oversized bytes never create a destination', async () => {
  for (const input of [{hash:'0'.repeat(64)}, {content:bytes.slice(1)}, {content:new Uint8Array([...bytes,1])}]) {
    const run = attempt(input); assert.equal(await run.done, 'integrity'); assert.deepEqual(run.actions, ['picker']);
  }
});
test('cancelling the picker does not fetch or save anything', async () => {
  const run = attempt({cancel:true}); assert.equal(await run.done, 'cancelled'); assert.equal(run.requests.length,0); assert.equal(run.files.length,0);
});
test('browser-managed fallback receives verified blob and reports no filesystem path', async () => {
  const run = attempt({picker:false}); assert.equal(await run.done, '');
  assert.deepEqual(run.actions,['browser-download']);
  assert.deepEqual(new Uint8Array(await run.files[0].arrayBuffer()),bytes);
});
