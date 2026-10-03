const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),crypto=require('crypto');
const {privateKey,publicKey}=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
const feed='https://aita-api.bogdan-donduk.workers.dev/client-updates/';
const release={schema:1,channel:'RELEASE',version:'1.2.4',build:25,publishedAtMillis:Date.now()-1000,expiresAtMillis:Date.now()+100000,
artifacts:[{os:'LINUX',arch:'X64',kind:'DEB',url:feed+'artifacts/abc.deb',bytes:500000,sha256:'a'.repeat(64)}]};
function envelope(value){const payload=Buffer.from(JSON.stringify(value));return{payload:payload.toString('base64'),signature:crypto.sign('sha256',payload,privateKey).toString('base64')};}
async function run(data) {
const make=()=>({children:[],textContent:'',append(...children){this.children.push(...children)},replaceChildren(){this.children=[]},addEventListener(){}});
const elements=Object.fromEntries(['intro','refresh','status','files'].map(n=>[n,make()]));
const sandbox={navigator:{language:'ru-RU'},document:{documentElement:{},getElementById:n=>elements[n],createElement:make},crypto:crypto.webcrypto,atob:s=>Buffer.from(s,'base64').toString('binary'),Uint8Array,TextDecoder,URL,AbortSignal,Date,JSON,Number,
fetch:async url=>({ok:true,text:async()=>JSON.stringify(url==='downloads-public-key.json'?{spki:publicKey.export({type:'spki',format:'der'}).toString('base64')}:data)})};
vm.createContext(sandbox);vm.runInContext(fs.readFileSync('composeApp/src/wasmJsMain/resources/downloads.js','utf8'),sandbox);
for(let i=0;i<50&&elements.refresh.disabled;i++)await new Promise(r=>setTimeout(r,20));
assert.equal(elements.refresh.disabled,false);return elements;
}
(async()=>{
const good=await run(envelope(release));assert.equal(good.files.children.length,1);assert.equal(good.status.textContent,'1.2.4 · 25');
for(const bad of [{...envelope(release),signature:Buffer.alloc(256).toString('base64')},envelope({...release,expiresAtMillis:Date.now()-1}),envelope({...release,artifacts:[{...release.artifacts[0],url:'https://evil.example/install.exe'}]})]){
const result=await run(bad);assert.equal(result.files.children.length,0);assert.match(result.status.textContent,/Не удалось/);
}console.log('PASS: recovery downloads signature, expiry, trusted artifact URL, actionable error');
})();
