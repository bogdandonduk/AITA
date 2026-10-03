const fs = require('node:fs'), vm = require('node:vm'), assert = require('node:assert/strict'), { test } = require('node:test');
const source = fs.readFileSync('composeApp/src/wasmJsMain/resources/aita-recovery.js','utf8');
function open(saved = {}) {
 const handlers = {}, timers = []; const storage = { ...saved };
 const sandbox = {Date, JSON, Math, sessionStorage: {getItem:k=>storage[k]||null,setItem:(k,v)=>storage[k]=v},
 window:{addEventListener:(n,cb)=>handlers[n]=cb},setTimeout:cb=>{timers.push(cb);return timers.length;},clearTimeout:()=>{}};
 vm.createContext(sandbox);vm.runInContext(source,sandbox);
 return { api:sandbox.aitaRecovery,storage,handlers,timers };
}
test('two unfinished startup crashes pause auto-start without touching app storage',()=>{
 const a=open({'cart':'keep','account':'keep'});assert.equal(a.api.blocked,false);a.api.begin();
 const b=open(a.storage);assert.equal(b.api.previousIncomplete,true);assert.equal(b.api.blocked,false);b.api.begin();
 const c=open(b.storage);assert.equal(c.api.blocked,true);assert.equal(c.storage.cart,'keep');assert.equal(c.storage.account,'keep');
 c.api.retry();assert.equal(open(c.storage).api.blocked,false);
});
test('normal navigation and a stable first frame do not accumulate crash attempts',()=>{
 const a=open();a.api.begin();a.handlers.pagehide();assert.equal(open(a.storage).api.previousIncomplete,false);
 const b=open();b.api.begin();b.api.ready();assert.equal(open(b.storage).api.previousIncomplete,true);
 b.timers[0]();assert.equal(open(b.storage).api.previousIncomplete,false);
});
test('stale or corrupt markers cannot permanently lock the app',()=>{
 for(const marker of ['broken',JSON.stringify({pending:true,failures:3,at:Date.now()-86400001})])
 assert.equal(open({'aita.startup-attempt':marker}).api.blocked,false);
});
