const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../../../composeApp/src/wasmJsMain/kotlin/kz/aita/DiagnosticsWeb.kt'),'utf8');
function bridge(name,params,context){
 const body=source.match(new RegExp('private fun '+name+'[\\s\\S]*?js\\("""([\\s\\S]*?)"""\\)'))[1];
 // Kotlin 2.2 wraps this body in its declared parameter list; a nested arrow is not invoked.
 return vm.runInNewContext('('+params+') => '+body,context);
}
test('diagnostic journal ownership callback is actually executed',async()=>{
 let granted=0,unavailable=0;
 bridge('ownBrowserJournal','onGranted,onUnavailable',{navigator:{locks:{request:(_name,fn)=>{fn();return Promise.resolve();}}}})(()=>granted++,()=>unavailable++);
 assert.equal(granted,1);assert.equal(unavailable,0);
 bridge('ownBrowserJournal','onGranted,onUnavailable',{navigator:{}})(()=>granted++,()=>unavailable++);
 assert.equal(unavailable,1);
});
test('browser error hooks execute and only forward bounded stack symbols',()=>{
 const hooks={},events=[];
 bridge('browserErrorHooks','report',{window:{addEventListener:(name,fn)=>hooks[name]=fn}})((...values)=>events.push(values));
 assert.equal(typeof hooks.error,'function');assert.equal(typeof hooks.unhandledrejection,'function');
 hooks.error({error:{name:'TypeError',message:'private message',stack:'TypeError: private message\n    at example (https://private.example/path?secret=private-value:42:7)\n    at wasm-function[321]:0xff'}});
 assert.equal(events.length,1);assert.equal(events[0][0],'TypeError');assert.equal(events[0][1],'web.error');
 assert.match(events[0][2],/example\(browser.js:42\)/);assert.match(events[0][2],/wasm.function_321/);
 assert.doesNotMatch(JSON.stringify(events),/private|secret|https/);
 hooks.unhandledrejection({reason:{name:'AbortError',stack:'private'}});assert.equal(events.length,1);
});
