const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {webcrypto,createHash}=require('node:crypto');
const source=fs.readFileSync(require('node:path').join(__dirname,'../../../composeApp/src/wasmJsMain/kotlin/kz/aita/ClientDownloadsWeb.kt'),'utf8');
const body=source.split('= js("""{')[1].split('}""")')[0];
const execute=new Function('url','expected','hash','fileName','progress','result','window','crypto','fetch','setTimeout','clearTimeout',body);
const data=Buffer.alloc(900001);for(let i=0;i<data.length;i++)data[i]=i%251;
const hash=createHash('sha256').update(data).digest('hex');
async function run(fetcher, options={}){
 let saved=null,progress=[],calls=[];
 const window={showSaveFilePicker:async()=>({createWritable:async()=>({write:async b=>saved=Buffer.from(await b.arrayBuffer()),close:async()=>{},abort:async()=>{saved=null;}})})};
 if(options.pickFailure)window.showSaveFilePicker=async()=>{throw options.pickFailure};
 if(options.writeFailure)window.showSaveFilePicker=async()=>({createWritable:async()=>({write:async()=>{throw options.writeFailure},abort:async()=>{saved=null}})});
 const result=await new Promise(resolve=>execute('https://updates.example.org/app.exe',data.length,hash,'AITA.exe',(n)=>progress.push(n),resolve,window,webcrypto,async(u,o)=>{calls.push(o.headers?.Range||'');return fetcher(calls.length,o);},(fn,ms)=>setTimeout(fn,[500,1000,2000].includes(ms)?1:ms),clearTimeout));
 return {saved,progress,calls,result};
}
test('browser resumes truncated binary then verifies complete hash before saving',async()=>{
 const r=await run(n=>n===1?new Response(data.subarray(0,450000)):new Response(data.subarray(450000),{status:206,headers:{'Content-Range':`bytes 450000-${data.length-1}/${data.length}`}}));
 assert.equal(r.result,'');assert.deepEqual(r.saved,data);assert.deepEqual(r.calls,['','bytes=450000-']);
 assert.ok(r.progress.every((p,i,a)=>!i||p>=a[i-1]));
});
test('range ignored safely restarts without corrupt append',async()=>{
 const r=await run(n=>new Response(n===1?data.subarray(0,450000):data));assert.equal(r.result,'');assert.deepEqual(r.saved,data);
});
test('repeated truncation is network failure, not authenticity failure',async()=>{
 const r=await run(()=>new Response(data.subarray(0,450000)));assert.equal(r.result,'network');assert.equal(r.calls.length,4);assert.equal(r.saved,null);
});
test('hash mismatch and bad ranges fail closed without retries',async()=>{
 let r=await run(()=>new Response(Buffer.alloc(data.length)));assert.equal(r.result,'integrity');assert.equal(r.calls.length,1);assert.equal(r.saved,null);
 r=await run(n=>n===1?new Response(data.subarray(0,450000)):new Response(data,{status:206,headers:{'Content-Range':`bytes 0-${data.length-1}/${data.length}`}}));assert.equal(r.result,'integrity');assert.equal(r.calls.length,2);assert.equal(r.saved,null);
});

test('picker cancellation and file-save errors remain distinct from transport failure',async()=>{
 let r=await run(()=>new Response(data),{pickFailure:new DOMException('User cancelled','AbortError')});
 assert.equal(r.result,'cancelled');assert.equal(r.calls.length,0);assert.equal(r.saved,null);
 r=await run(()=>new Response(data),{writeFailure:new DOMException('Disk full','QuotaExceededError')});
 assert.equal(r.result,'storage');assert.equal(r.calls.length,1);assert.equal(r.saved,null);
 r=await run(()=>new Response(data),{writeFailure:new DOMException('User cancelled','AbortError')});
 assert.equal(r.result,'cancelled');assert.equal(r.saved,null);
});
