'use strict';
// Adapter for existing tests. No test gets a personal browser profile or external network.
const fs = require('node:fs');
const path = require('node:path');
const core = path.dirname(require.resolve('playwright-core/package.json'));
const {BrowserType} = require(path.join(core, 'lib/client/browserType.js'));
const {Browser} = require(path.join(core, 'lib/client/browser.js'));
const {BrowserContext} = require(path.join(core, 'lib/client/browserContext.js'));
const {Page} = require(path.join(core, 'lib/client/page.js'));
const {Locator} = require(path.join(core, 'lib/client/locator.js'));
const {Route} = require(path.join(core, 'lib/client/network.js'));
const {Keyboard, Mouse} = require(path.join(core, 'lib/client/input.js'));
const nativeFetch = global.fetch;
const control = process.env.AITA_GUIDED_CONTROL;
const artifact = process.env.AITA_ARTIFACTS;
const browsers = new Set();
const closingContexts = new WeakSet();
const allowedOrigins = new Set();
for (const value of [control, process.env.AITA_WEB_TEST_URL, process.env.AITA_WEB_LOCAL_ORIGIN]) {
  if (value && local(value)) allowedOrigins.add(new URL(value).origin);
}
const http = require('node:http');
const listen = http.Server.prototype.listen;
http.Server.prototype.listen = function(...args) {
  this.once('listening',()=>{
    const address=this.address();
    if(address && typeof address==='object' && ['127.0.0.1','::1'].includes(address.address)) {
      allowedOrigins.add('http://'+(address.address==='::1'?'[::1]':address.address)+':'+address.port);
    }
  });
  return listen.apply(this,args);
};
let stopping = false, step = 0;
function local(url) {
  try { const u = new URL(url); return ['http:', 'https:'].includes(u.protocol) && ['127.0.0.1', '[::1]', 'localhost'].includes(u.hostname); }
  catch { return false; }
}
function allowed(url) { return local(url) && allowedOrigins.has(new URL(url).origin); }
function guard(url) { if (!allowed(url)) throw new Error('Guided-test network fence blocked a request outside this test run: ' + new URL(url).origin); }
async function status(event) {
  if (!control) return {paused:false};
  const response = await nativeFetch(control + '/step', {method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(event),signal:AbortSignal.timeout(5000)});
  if (!response.ok) throw Error('Guided-test controller is no longer available');
  return response.json();
}
async function gate(label) {
  let state = await status({label,step:++step});
  while (state.paused && !state.stopped) { await new Promise(r=>setTimeout(r,250)); state = await status({label,step}); }
  if (state.stopped || stopping) throw new Error('Guided demonstration stopped by user');
}
global.aitaGuidedStep = gate;
global.fetch = (url, ...args) => {guard(typeof url === 'string' || url instanceof URL ? url : url.url); return nativeFetch(url, ...args);};
for (const name of ['continue','fetch']) {
  const original = Route.prototype[name];
  Route.prototype[name] = function(options={}) {guard(options.url || this.request().url()); return original.call(this, name === 'fetch' ? {maxRetries:['GET','HEAD'].includes(this.request().method())?2:0,...options,maxRedirects:0} : options);};
}
const launch = BrowserType.prototype.launch;
BrowserType.prototype.launch = async function(options={}) {
  const headless=process.env.AITA_GUIDED_HEADLESS === '1';
  const browser = await launch.call(this, {...options,channel:undefined,headless,
    args:[...(options.args||[]),...(!headless?['--window-size=1600,1200','--window-position=0,0']:[])],
    slowMo:Number(process.env.AITA_GUIDED_SLOW_MS || '160')});
  browsers.add(browser); browser.once('disconnected',()=>browsers.delete(browser)); return browser;
};
const newContext = Browser.prototype.newContext;
for(const prototype of [BrowserContext.prototype,Page.prototype]) {
  const route=prototype.route;
  prototype.route=function(url,handler,options) {
    const context=prototype===BrowserContext.prototype?this:this.context();
    return route.call(this,url,async(...args)=>{
      try {return await handler(...args);}
      catch(error) {
        if(!closingContexts.has(context))throw error;
        // Requests already in flight can finish after a fixture enters finally/close.
        // Preserve the test's real assertion instead of replacing it with ECONNRESET.
        await args[0].abort().catch(()=>{});
      }
    },options);
  };
}
Browser.prototype.newContext = async function(options={}) {
  const context = await newContext.call(this,{...options,serviceWorkers:'block',acceptDownloads:false});
  await context.route('**/*',route=>allowed(route.request().url()) ? route.continue() : route.abort('blockedbyclient'));
  await context.routeWebSocket('**',route=>route.close());
  await context.addInitScript(()=>{
    // Opening a print dialog or downloading an installer is not a real test success.
    window.print=()=>{window.__guidedPrintCalls=(window.__guidedPrintCalls||0)+1; window.dispatchEvent(new Event('afterprint'));};
  });
  context.on('page',page=>{
    if(process.env.AITA_GUIDED_HEADLESS!=='1')page.bringToFront().catch(()=>{});
    page.on('crash',()=>status({error:'Browser renderer crashed'}).catch(()=>{}));
    page.on('pageerror',error=>status({browserError:String(error).slice(0,1500)}).catch(()=>{}));
  });
  return context;
};
const originals = [];
for (const [prototype,names] of [[Locator.prototype,['click','dblclick','fill','check','uncheck','selectOption']], [Keyboard.prototype,['press','type','insertText']], [Mouse.prototype,['click','dblclick','wheel']], [Page.prototype,['goto','reload']]]) {
  for (const name of names) {
    const original=prototype[name]; if (!original) continue;
    originals.push([prototype,name,original]);
    prototype[name]=async function(...args){
      const selector=this._selector ? String(this._selector).replace(/\[.*?\]/g,'').slice(0,160) : '';
      // Never echo entered values: password fields are part of the demonstration.
      await gate(name+(selector?' · '+selector:''));
      return original.apply(this,args);
    };
  }
}
async function evidence() {
  if (!artifact) return;
  fs.mkdirSync(artifact,{recursive:true});
  let index=0;
  for (const browser of browsers) for (const context of browser.contexts()) for (const page of context.pages()) {
    await page.screenshot({path:path.join(artifact,`guided-final-${index}.png`),timeout:5000}).catch(()=>{});
    const aria=await page.locator('body').ariaSnapshot({timeout:5000}).catch(()=>'Unavailable');
    fs.writeFileSync(path.join(artifact,`guided-final-${index++}.txt`),aria);
  }
}
const close = Browser.prototype.close;
Browser.prototype.close = async function(...args) {for(const context of this.contexts())closingContexts.add(context);await evidence();return close.apply(this,args);};
const terminate=async()=>{if(stopping)return;stopping=true;for(const browser of browsers)for(const context of browser.contexts())closingContexts.add(context);await evidence();await Promise.allSettled([...browsers].map(browser=>close.call(browser)));process.exit(130);};
process.once('SIGTERM',terminate);
if(process.platform==='win32')process.once('SIGBREAK',terminate);
module.exports = {local,guard};
