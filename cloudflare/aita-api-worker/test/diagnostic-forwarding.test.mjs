import test from 'node:test';
import assert from 'node:assert/strict';
import { setTrustedDiagnosticHeaders } from '../src/diagnostic-forwarding.js';

const key = 'test-fixture-only-not-a-production-secret-0000';
const env = { AITA_DIAGNOSTICS_GATEWAY_KEY: key };
function forwarded(path, method = 'POST', cf, settings = env) {
  const headers = new Headers({
    'x-aita-diagnostics-gateway-key': 'forged',
    'x-aita-diagnostic-country': 'US',
    'x-aita-diagnostic-region': 'private',
    'x-aita-diagnostic-extra': 'must disappear',
    accept: 'application/json',
  });
  setTrustedDiagnosticHeaders(headers, { method, cf }, settings, new URL(path, 'https://example.invalid'));
  return headers;
}
test('client-provided diagnostic headers are removed on every route', () => {
  for (const path of ['/healthz', '/transactions/complete', '/diagnostics/events']) {
    const result = forwarded(path);
    assert.deepEqual([...result.keys()], ['accept']);
  }
});
test('only recognized ingestion POSTs get gateway-owned coarse fields', () => {
  for (const path of ['/diagnostics/events', '/diagnostics/events/anonymous', '/diagnostics/events/anonymous/']) {
    const result = forwarded(path, 'POST', { country: 'KZ', regionCode: 'AST', city: 'private', latitude: 'private' });
    assert.equal(result.get('x-aita-diagnostics-gateway-key'), key);
    assert.equal(result.get('x-aita-diagnostic-country'), 'KZ');
    assert.equal(result.get('x-aita-diagnostic-region'), 'AST');
    assert.equal(result.get('x-aita-diagnostic-extra'), null);
    assert.equal([...result.values()].some(value => value.includes('private')), false);
  }
});
test('other paths methods malformed keys and unknown countries fail closed', () => {
  for (const path of ['/diagnostics/events/other', '/diagnostics/admin/events'])
    assert.equal(forwarded(path, 'POST', { country: 'KZ' }).get('x-aita-diagnostics-gateway-key'), null);
  assert.equal(forwarded('/diagnostics/events', 'GET', { country: 'KZ' }).get('x-aita-diagnostics-gateway-key'), null);
  for (const bad of ['', 'short', 'x'.repeat(513), '\n'.repeat(40)])
    assert.equal(forwarded('/diagnostics/events', 'POST', { country: 'KZ' }, { AITA_DIAGNOSTICS_GATEWAY_KEY: bad }).get('x-aita-diagnostics-gateway-key'), null);
  for (const country of ['XX', 'T1', 'kz', 'KZZ', '<x>'])
    assert.equal(forwarded('/diagnostics/events', 'POST', { country }).get('x-aita-diagnostics-gateway-key'), null);
  const region = forwarded('/diagnostics/events', 'POST', { country: 'KZ', regionCode: 'street=private' });
  assert.equal(region.get('x-aita-diagnostic-country'), 'KZ');
  assert.equal(region.get('x-aita-diagnostic-region'), null);
});
