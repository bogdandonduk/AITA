import test from 'node:test';
import assert from 'node:assert/strict';
import gateway, { safeOriginErrorCode, fetchPrivateOrigin } from '../src/index.js';
const origin = 'https://aita-api.bogdan-donduk.workers.dev';
const req = (path, options) => new Request(origin + path, options);
const fail = (message) => { throw new Error(message); };
const quiet = async (block) => {
  const saved = console.error; console.error = () => {};
  try { return await block(); } finally { console.error = saved; }
};

test('edge health is independent of origin and tells whether binding is configured', async () => {
  const response = await gateway.fetch(req('/_edge/health'), {});
  assert.equal(response.status, 200);
  assert.equal((await response.json()).originBindingConfigured, false);
});
test('binding health never probes the origin', async () => {
  let calls = 0;
  const response = await gateway.fetch(req('/_edge/health'), { AITA_ORIGIN: { fetch() { calls++; } } });
  assert.equal((await response.json()).originBindingConfigured, true); assert.equal(calls, 0);
});
test('missing binding has a specific sanitized 503 and no cache', async () => quiet(async () => {
  const r = await gateway.fetch(req('/readyz'), {});
  assert.equal(r.status, 503); assert.equal(r.headers.get('x-aita-origin-error'), 'origin_binding_missing');
  assert.match(r.headers.get('cache-control'), /no-store/);
  assert.equal((await r.json()).retryable, true);
}));
test('origin exception strips addresses credentials and private query strings', () => {
  assert.equal(safeOriginErrorCode(new Error('ProxyError: destination_unavailable token=secret @10.1.2.3')), 'destination_unavailable');
  assert.equal(safeOriginErrorCode(new Error('private secret=12345')), 'origin_connection_failed');
});
test('one transient health failure is retried once', async () => {
  let calls = 0;
  const r = await gateway.fetch(req('/auth/ping'), { AITA_ORIGIN: { fetch() {
    if (++calls === 1) return fail('ProxyError: connection_terminated');
    return new Response('ok');
  } } });
  assert.equal(r.status, 200); assert.equal(calls, 2);
});
test('repeated transient health failures stop after two attempts', async () => quiet(async () => {
  let calls = 0;
  const r = await gateway.fetch(req('/readyz'), { AITA_ORIGIN: { fetch() { calls++; return fail('proxy_internal_error'); } } });
  assert.equal(r.status, 503); assert.equal(calls, 2);
}));
test('configuration and rate-limit errors are never amplified with immediate retries', async () => quiet(async () => {
  for (const code of ['dns_error', 'destination_not_found', 'tls_certificate_error', 'rate_limited', 'connection_timeout']) {
    let calls = 0;
    const r = await gateway.fetch(req('/readyz'), { AITA_ORIGIN: { fetch() { calls++; return fail(code); } } });
    assert.equal(r.status, 503); assert.equal(calls, 1); assert.equal((await r.json()).code, code);
  }
}));
test('mutations and token refresh are not replayed on any origin failure', async () => quiet(async () => {
  for (const path of ['/stockBatches/move', '/stockBatches/decideMove', '/transactions/complete', '/auth/refresh', '/auth/login/password']) {
    let calls = 0;
    const r = await gateway.fetch(req(path, { method: 'POST', body: '{}' }), { AITA_ORIGIN: { fetch() { calls++; return fail('connection_terminated'); } } });
    const body = await r.json();
    assert.equal(calls, 1); assert.equal(body.retryable, false); assert.equal(body.outcomeUnknown, true);
  }
}));
test('GET outside the fixed health list is not replayed', async () => quiet(async () => {
  let calls = 0;
  await gateway.fetch(req('/arbitrary'), { AITA_ORIGIN: { fetch() { calls++; return fail('connection_terminated'); } } });
  assert.equal(calls, 1);
}));
test('an origin HTTP 500 is forwarded, not silently retried', async () => {
  let calls = 0;
  const r = await gateway.fetch(req('/readyz'), { AITA_ORIGIN: { fetch() { calls++; return new Response('origin', { status: 500 }); } } });
  assert.equal(r.status, 500); assert.equal(await r.text(), 'origin'); assert.equal(calls, 1);
});
test('WebSocket 101 and socket identity remain untouched', async () => {
  const upgraded = { status: 101, webSocket: { marker: 'live' } };
  const r = await gateway.fetch(req('/rt/updates', { headers: { upgrade: 'websocket' } }), { AITA_ORIGIN: { fetch() { return upgraded; } } });
  assert.equal(r, upgraded);
});
test('forwarding preserves pinned auth and body, strips spoofed forwarding identity', async () => {
  let seen;
  await gateway.fetch(req('/stockBatches/move?one=2', { method: 'POST', body: '{"total":3}', headers: {
    authorization: 'Bearer TEST_ONLY', 'store_id': 'branch', 'x-forwarded-for': 'forged', 'cf-connecting-ip': '192.0.2.5'
  } }), { AITA_ORIGIN: { async fetch(r) { seen = r; return new Response('ok'); } } });
  assert.equal(seen.headers.get('authorization'), 'Bearer TEST_ONLY');
  assert.equal(seen.headers.get('store_id'), 'branch');
  assert.notEqual(seen.headers.get('x-forwarded-for'), 'forged');
  assert.equal(new URL(seen.url).search, '?one=2'); assert.equal(await seen.text(), '{"total":3}');
});
test('status-page probes release both origin bodies', async () => {
  let cancelled = 0;
  const r = await gateway.fetch(req('/', { headers: { accept: 'text/html' } }), { AITA_ORIGIN: { fetch() {
    return new Response(new ReadableStream({ cancel() { cancelled++; } }));
  } } });
  assert.equal(r.status, 200); assert.equal(cancelled, 2);
});
test('handshake deadline cancels the in-flight origin request', async () => {
  let cancelled = false;
  await assert.rejects(fetchPrivateOrigin({ AITA_ORIGIN: { fetch(r) { return new Promise((_, reject) => {
    r.signal.addEventListener('abort', () => { cancelled = true; reject(r.signal.reason); });
  }); } } }, req('/readyz'), 20), /origin_probe_timeout/);
  assert.equal(cancelled, true);
});
test('client cancellation is propagated without waiting for origin timeout', async () => {
  const c = new AbortController(); let entered;
  const ready = new Promise(r => { entered = r; });
  const p = fetchPrivateOrigin({ AITA_ORIGIN: { fetch(r) { return new Promise((_, reject) => {
    r.signal.addEventListener('abort', () => reject(r.signal.reason)); entered();
  }); } } }, req('/rt/updates', { signal: c.signal }), 10_000);
  await ready; c.abort(); await assert.rejects(p, /client_disconnected/);
});
test('completed handshake has no leftover timer that kills a live socket', async () => {
  let signal;
  await fetchPrivateOrigin({ AITA_ORIGIN: { fetch(r) { signal = r.signal; return { status: 101 }; } } }, req('/rt/updates'), 10);
  await new Promise(r => setTimeout(r, 30)); assert.equal(signal.aborted, false);
});
test('pre-cancelled requests never reach origin', async () => {
  const c = new AbortController(); c.abort(); let calls = 0;
  await assert.rejects(fetchPrivateOrigin({ AITA_ORIGIN: { fetch() { calls++; } } }, req('/readyz', { signal: c.signal }), 10), /client_disconnected/);
  assert.equal(calls, 0);
});
test('error bodies never expose raw exceptions and HEAD remains bodyless', async () => quiet(async () => {
  const r = await gateway.fetch(req('/readyz', { method: 'HEAD' }), { AITA_ORIGIN: { fetch() { return fail('dns_error secret=private'); } } });
  assert.equal(await r.text(), ''); assert.equal(r.headers.get('x-aita-origin-error'), 'dns_error');
}));
test('public edge diagnostics are CORS readable without touching the private origin', async () => {
  let called=0;
  const response = await gateway.fetch(req('/_edge/health'), { AITA_ORIGIN: { fetch() { called++; throw Error('not reached'); } } });
  assert.equal(response.status,200); assert.equal(response.headers.get('access-control-allow-origin'),'*');
  const body = await response.json(); assert.equal(body.gateway,'aita-workers-vpc');
  assert.equal(body.originBindingConfigured,true); assert.equal(called,0);
});
test('edge diagnostics have a bodyless preflight and reject mutation methods', async () => {
  const preflight=await gateway.fetch(req('/_edge/health',{method:'OPTIONS'}),{});
  assert.equal(preflight.status,204); assert.equal(await preflight.text(),'');
  const bad=await gateway.fetch(req('/_edge/health',{method:'POST'}),{}); assert.equal(bad.status,405);
  const head=await gateway.fetch(req('/_edge/health',{method:'HEAD'}),{}); assert.equal(await head.text(),'');
});

test('edge preflight accepts the exact no-cache diagnostic request headers', async () => {
  const response=await gateway.fetch(req('/_edge/health',{method:'OPTIONS',headers:{
    origin:'https://client.example', 'access-control-request-method':'GET',
    'access-control-request-headers':'cache-control,pragma,x-aita-connection-probe'
  }}),{});
  const allowed=response.headers.get('access-control-allow-headers').toLowerCase().split(',').map(s=>s.trim());
  for(const name of ['cache-control','pragma','x-aita-connection-probe']) assert.ok(allowed.includes(name));
  assert.equal(response.headers.get('access-control-allow-origin'),'*');
});
