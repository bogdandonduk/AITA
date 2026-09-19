const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function page(visibility='visible') {
    const document = new EventTarget(), window = new EventTarget();
    document.visibilityState = visibility;
    const context = vm.createContext({document,window});
    vm.runInContext(fs.readFileSync(path.join(__dirname,'../../../composeApp/src/wasmJsMain/resources/aita-page-lifecycle.js'),'utf8'), context);
    const events=[];
    const unsubscribe=context.aitaPageActivity.subscribe(active=>events.push(active));
    return {document,window,events,unsubscribe,fire:(target,name)=>target.dispatchEvent(new Event(name))};
}
test('an initially hidden tab never starts periodic work',()=>{
    const p=page('hidden');assert.deepEqual(p.events,[false]);
    p.document.visibilityState='visible';p.fire(p.document,'visibilitychange');
    assert.deepEqual(p.events,[false,true]);
});
test('freeze and resume remain paused until visible, with one wakeup',()=>{
    const p=page();p.document.visibilityState='hidden';p.fire(p.document,'visibilitychange');
    p.fire(p.document,'freeze');p.fire(p.document,'resume');
    assert.deepEqual(p.events,[true,false]);
    p.document.visibilityState='visible';p.fire(p.document,'visibilitychange');p.fire(p.window,'pageshow');
    assert.deepEqual(p.events,[true,false,true]);
});
test('pagehide blocks work even if browser still reports visible',()=>{
    const p=page();p.fire(p.window,'pagehide');p.fire(p.document,'visibilitychange');
    assert.deepEqual(p.events,[true,false]);p.fire(p.window,'pageshow');
    assert.deepEqual(p.events,[true,false,true]);
});
test('a visible frozen page waits for resume and duplicate events do not restart work',()=>{
    const p=page();p.fire(p.document,'freeze');p.fire(p.document,'freeze');p.fire(p.document,'visibilitychange');
    assert.deepEqual(p.events,[true,false]);p.fire(p.document,'resume');p.fire(p.document,'resume');
    assert.deepEqual(p.events,[true,false,true]);p.unsubscribe();p.fire(p.window,'pagehide');
    assert.deepEqual(p.events,[true,false,true]);
});
