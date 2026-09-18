const test=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const source=fs.readFileSync(require('node:path').join(__dirname,'../../../composeApp/src/wasmJsMain/kotlin/kz/aita/ProfilePhotoPickerWeb.kt'),'utf8');
const fn=source.match(/private fun selectProfilePhoto[\s\S]*?js\("""([\s\S]*?)"""\)/)[1];
function picker(options={}) {
 const events={},windows={},timers=new Map(),results=[];let id=0,removed=false,opened=0,reader;
 const input={style:{},files:[],addEventListener:(n,f)=>events[n]=f,remove:()=>removed=true,
  showPicker:()=>{opened++;if(options.blocked)throw Error('NotAllowedError');}};
 const context={document:{createElement:()=>input,body:{appendChild:()=>{}}},window:{addEventListener:(n,f)=>windows[n]=f,removeEventListener:n=>delete windows[n]},
 setTimeout:(f,ms)=>{const key=++id;timers.set(key,{f,ms});return key;},clearTimeout:k=>timers.delete(k),
 FileReader:class {constructor(){reader=this;this.readyState=1;}readAsDataURL(){}abort(){this.readyState=2;}}};
 const open=vm.runInNewContext('(result) => '+fn,context),handle=open((data,error)=>results.push({data,error}));
 return {input,events,windows,timers,results,handle,get reader(){return reader;},get removed(){return removed;},get opened(){return opened;},expire:ms=>[...timers.values()].filter(t=>t.ms===ms).forEach(t=>t.f())};
}
test('blocked native picker is reported immediately and all handlers are cleaned',()=>{
 const p=picker({blocked:true});assert.equal(p.opened,1);assert.deepEqual(p.results,[{data:'',error:'unavailable'}]);assert.equal(p.timers.size,0);assert.equal(Object.keys(p.windows).length,0);assert.ok(p.removed);
});
test('cancel and silent-browser deadline allow another choice',()=>{
 const p=picker();p.events.cancel();assert.deepEqual(p.results,[{data:'',error:''}]);assert.equal(p.timers.size,0);
 const q=picker();q.expire(300000);assert.deepEqual(q.results,[{data:'',error:'unavailable'}]);assert.ok(q.removed);
});
test('focus return does not cancel an image already being read',()=>{
 const p=picker();p.input.files=[{size:5,type:'image/png'}];p.events.change();p.windows.focus();p.expire(1000);assert.equal(p.results.length,0);
 p.reader.result='data:image/png;base64,aGVsbG8=';p.reader.onload();assert.deepEqual(p.results,[{data:'aGVsbG8=',error:''}]);assert.equal(p.timers.size,0);
});
test('leaving the editor aborts pending read and suppresses late callbacks',()=>{
 const p=picker();p.input.files=[{size:5,type:'image/png'}];p.events.change();const late=p.reader.onload;
 p.handle.cancel();p.reader.result='data:image/png;base64,aGVsbG8=';late();assert.equal(p.results.length,0);assert.equal(p.reader.readyState,2);assert.ok(p.removed);
});
test('oversized and invalid files are not decoded',()=>{
 for(const [file,error] of [[{size:8388609,type:'image/png'},'size'],[{size:2,type:'application/pdf'},'format']]){
  const p=picker();p.input.files=[file];p.events.change();assert.deepEqual(p.results,[{data:'',error}]);assert.equal(p.reader,undefined);
 }
});
