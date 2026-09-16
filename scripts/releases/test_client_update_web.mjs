import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { webcrypto } from 'node:crypto';

// Execute the exact embedded JS used by the Kotlin/Wasm adapter, with isolated browser objects.
const text = fs.readFileSync(new URL('../../composeApp/src/wasmJsMain/kotlin/kz/aita/ClientUpdateWeb.kt', import.meta.url), 'utf8');
function body(name) { const m = text.match(new RegExp('private fun ' + name + '[\\s\\S]*?js\\("""([\\s\\S]*?)"""\\)')); assert.ok(m); return "(function()" + m[1] + ")()"; }
function browser(worker) {
  const result = [], navigations = [], timers = new Map(), listeners = new Map(); let nextTimer = 0;
  const navigator = worker ? {serviceWorker: {
    getRegistration: () => worker(),
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: name => listeners.delete(name),
  }} : {};
  const context = vm.createContext({ URL, navigator, result: ok => result.push(ok),
    location: { origin: 'https://shop.example.org', href: 'https://shop.example.org/app?draft=keep#sale', replace: url => navigations.push(url) },
    setTimeout: fn => { const n = ++nextTimer; timers.set(n, fn); return n; }, clearTimeout: n => timers.delete(n),
    releaseUrl: 'https://shop.example.org/app', build: '22',
  });
  return { result, navigations, timers, listeners, context,
    run: () => vm.runInContext(body('webInstall'),context), timeout: () => [...timers.values()].forEach(fn => fn()) };
}
const settle = () => new Promise(resolve => setImmediate(resolve));
test('reload keeps draft query and fragment without deleting storage', () => {
  const b = browser(); b.run(); assert.deepEqual(b.result,[true]);
  const next = new URL(b.navigations[0]); assert.equal(next.searchParams.get('draft'),'keep'); assert.equal(next.hash,'#sale'); assert.equal(next.searchParams.get('_aita_build'),'22');
});
test('a release cannot send the browser to a foreign origin', () => {
  const b = browser(); b.context.releaseUrl='https://another.example.org/app'; b.run(); assert.deepEqual(b.result,[false]); assert.equal(b.navigations.length,0);
});
test('waiting worker must acknowledge activation before reloading', async () => {
  const messages=[]; const b=browser(async () => ({update:async()=>{},waiting:{postMessage:m=>messages.push(m)}}));
  b.run(); await settle(); assert.equal(messages[0].type,'AITA_ACTIVATE_UPDATE'); assert.equal(b.navigations.length,0);
  b.listeners.get('controllerchange')(); assert.deepEqual(b.result,[true]); assert.equal(b.navigations.length,1); assert.equal(b.listeners.size,0);
});
test('timeout removes listeners and late worker callbacks cannot reload', async () => {
  const b=browser(async () => ({update:async()=>{},waiting:{postMessage:()=>{}}})); b.run(); await settle();
  const old=b.listeners.get('controllerchange'); b.timeout(); old(); assert.deepEqual(b.result,[false]); assert.equal(b.navigations.length,0); assert.equal(b.listeners.size,0);
});
test('hung registration cannot leave UI waiting forever', async () => {
  let resolve; const b=browser(()=>new Promise(r=>resolve=r)); b.run(); b.timeout(); resolve(null); await settle(); assert.deepEqual(b.result,[false]); assert.equal(b.navigations.length,0);
});
test('failed worker update never claims success', async () => {
  const b=browser(async()=>({update:async()=>{throw Error('offline')}})); b.run(); await settle(); assert.deepEqual(b.result,[false]); assert.equal(b.navigations.length,0);
});
test('browser without a registered worker safely reloads', async () => {
  const b=browser(async()=>null); b.run(); await settle(); assert.deepEqual(b.result,[true]); assert.equal(b.navigations.length,1);
});
test('real WebCrypto verifies signed metadata and rejects tampered bytes', async () => {
  const key=await webcrypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
  const payload=new TextEncoder().encode('signed release'); const signature=await webcrypto.subtle.sign('RSASSA-PKCS1-v1_5',key.privateKey,payload);
  const pub=await webcrypto.subtle.exportKey('spki',key.publicKey);
  const b64=bytes=>Buffer.from(bytes).toString('base64');
  async function verify(bytes) { return new Promise(result=>vm.runInNewContext(body('webVerify'),{
    crypto:webcrypto,Uint8Array,atob, key:b64(pub),payload:b64(bytes),signature:b64(signature),result
  })); }
  assert.equal(await verify(payload),true); assert.equal(await verify(new TextEncoder().encode('tampered')),false);
});

test('an installing worker finishes before activation or reload', async () => {
  const handlers = new Map(), messages = [];
  const worker = {state:'installing', addEventListener:(n,f)=>handlers.set(n,f),removeEventListener:n=>handlers.delete(n),postMessage:m=>messages.push(m)};
  const registration = {update:async()=>{},installing:worker,waiting:null};
  const b = browser(async()=>registration); b.run(); await settle();
  assert.equal(b.navigations.length,0); assert.equal(messages.length,0);
  worker.state='installed'; registration.waiting=worker; handlers.get('statechange')();
  assert.equal(messages[0].type,'AITA_ACTIVATE_UPDATE'); assert.equal(handlers.size,0);
  b.listeners.get('controllerchange')(); assert.equal(b.navigations.length,1);
});
test('installation timeout detaches worker listener and never reloads later', async () => {
  const handlers = new Map();
  const worker = {state:'installing',addEventListener:(n,f)=>handlers.set(n,f),removeEventListener:n=>handlers.delete(n)};
  const b=browser(async()=>({update:async()=>{},installing:worker}));b.run();await settle();
  const late=handlers.get('statechange');b.timeout();worker.state='activated';late();
  assert.equal(handlers.size,0);assert.deepEqual(b.result,[false]);assert.equal(b.navigations.length,0);
});
