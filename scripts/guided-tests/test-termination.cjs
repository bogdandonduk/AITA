'use strict';
// Verify graceful cancellation closes the owned Playwright browser before Node exits.
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {spawn}=require('node:child_process');
(async()=>{
 if(process.platform==='win32'){console.log('SKIP: SIGTERM contract is Unix-only; Windows CTRL_BREAK requires native validation');return;}
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'aita-guided-stop-')),marker=path.join(folder,'browser-closed');
 const script=`require(${JSON.stringify(path.join(__dirname,'preload.cjs'))}); (async()=>{const {chromium}=require('playwright'); const b=await chromium.launch(); b.once('disconnected',()=>require('fs').writeFileSync(${JSON.stringify(marker)},'closed')); await b.newPage(); console.log('READY'); await new Promise(()=>{});})().catch(e=>{console.error(e);process.exit(1)});`;
 const env={...process.env,AITA_GUIDED_HEADLESS:'1',AITA_GUIDED_SLOW_MS:'0'};delete env.AITA_GUIDED_CONTROL;delete env.AITA_ARTIFACTS;
 const child=spawn(process.execPath,['-e',script],{env,stdio:['ignore','pipe','pipe'],detached:true});
 try {
  await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Browser never became ready')),20000);child.stdout.on('data',chunk=>{if(String(chunk).includes('READY')){clearTimeout(timer);resolve()}});child.once('exit',code=>reject(Error('Premature exit '+code)));});
  const finished=new Promise(resolve=>child.once('exit',(code,signal)=>resolve({code,signal})));
  child.kill('SIGTERM');
  const outcome=await Promise.race([finished,new Promise((_,reject)=>setTimeout(()=>reject(Error('Graceful browser close timed out')),15000).unref())]);
  assert.equal(outcome.code,130);assert.equal(fs.readFileSync(marker,'utf8'),'closed');
  console.log('PASS: SIGTERM closes the isolated Chromium browser before exiting');
 } finally {
  try{process.kill(-child.pid,'SIGKILL')}catch(error){if(error.code!=='ESRCH')throw error}
  fs.rmSync(folder,{recursive:true,force:true});
 }
})().catch(error=>{console.error(error);process.exitCode=1;});
