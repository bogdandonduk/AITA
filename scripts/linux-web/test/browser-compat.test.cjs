const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.resolve(__dirname, '../../../composeApp/src/wasmJsMain/resources/aita-browser-compat.js'), 'utf8');
const sandbox = {};
vm.runInNewContext(source, sandbox);
const {needsBrowserUpdate, hasGraphics} = sandbox.aitaBrowserCompatibility;

test('rejects the crashing Chromium engine before Wasm, including derived browsers', () => {
    for (const ua of [
        'Mozilla/5.0 (X11; Linux x86_64) Chrome/122.0.6261.128 Safari/537.36',
        'Mozilla/5.0 Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0',
        'Mozilla/5.0 (Linux; Android 14; wv) Chrome/121.0.0.0 Mobile Safari/537.36',
        'Chromium/119.0.6045.0',
    ]) assert.equal(needsBrowserUpdate(ua), true, ua);
});

test('accepts the verified fixed engine and does not mistake iOS Chrome for V8', () => {
    for (const ua of [
        'Chrome/123.0.6312.122', 'Chrome/156.0.8078.47',
        'Mozilla/5.0 (iPhone) AppleWebKit/605.1.15 CriOS/122.0.0.0 Mobile Safari/604.1',
        'Mozilla/5.0 Firefox/144.0', 'Version/18.2 Safari/605.1.15', '', undefined,
    ]) assert.equal(needsBrowserUpdate(ua), false, ua);
});

test('graphics probe detects missing/lost contexts and releases only its own context', () => {
    let released = 0;
    const context = {isContextLost: () => false, getExtension: name => {
        assert.equal(name, 'WEBGL_lose_context');
        return {loseContext: () => released++};
    }};
    const document = {createElement: tag => {
        assert.equal(tag, 'canvas');
        return {getContext: api => { assert.equal(api, 'webgl2'); return context; }};
    }};
    assert.equal(hasGraphics(document), true);
    assert.equal(released, 1);
    context.isContextLost = () => true;
    assert.equal(hasGraphics(document), false);
    assert.equal(released, 2);
    assert.equal(hasGraphics({createElement: () => ({getContext: () => null})}), false);
    assert.equal(hasGraphics({createElement: () => { throw new Error('unavailable'); }}), false);
});
