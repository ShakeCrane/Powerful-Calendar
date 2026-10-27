/* Real browser source edit, preview and package round trip. No npm dependencies. */
const {spawn} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.join(root, ".local/android-test/reports/separation-20261027/source-editor");
const delay = ms => new Promise(resolve=>setTimeout(resolve,ms));
async function main(){
  fs.mkdirSync(reportDir,{recursive:true});
  const profile=fs.mkdtempSync(path.join(reportDir,"browser-profile-"));
  const server=spawn("python",["tools/skin-studio/skin_studio.py","serve","--port","0"],
    {cwd:root,stdio:["ignore","pipe","pipe"]});
  let browser;
  try{
    const line=await Promise.race([
      new Promise((resolve,reject)=>{
        readline.createInterface({input:server.stdout}).once("line",resolve);
        server.once("error",reject);server.once("exit",code=>reject(Error("Studio exited "+code)));
      }), delay(15000).then(()=>{throw Error("Studio startup timed out")})]);
    const origin=line.match(/http:\/\/127\.0\.0\.1:\d+/)?.[0];
    if(!origin)throw Error("No Studio URL: "+line);
    browser=spawn(chrome,["--headless=new","--disable-gpu","--no-sandbox",
      "--disable-background-networking","--no-first-run","--no-default-browser-check",
      "--remote-allow-origins=*","--remote-debugging-port=0","--user-data-dir="+profile,
      "about:blank"],{stdio:"ignore"});
    const portFile=path.join(profile,"DevToolsActivePort");
    for(let n=0;n<150&&!fs.existsSync(portFile);n++){
      if(browser.exitCode!==null)throw Error("Browser exited "+browser.exitCode);
      await delay(100);
    }
    if(!fs.existsSync(portFile))throw Error("Browser startup timed out");
    const port=fs.readFileSync(portFile,"utf8").split(/\r?\n/)[0];
    const target=await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,{method:"PUT"})).json();
    const socket=new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
    let id=0;const pending=new Map();
    socket.onmessage=event=>{const message=JSON.parse(event.data);if(message.id&&pending.has(message.id)){
      const {resolve,reject}=pending.get(message.id);pending.delete(message.id);
      message.error?reject(Error(message.error.message)):resolve(message.result);
    }};
    const call=(method,params={})=>new Promise((resolve,reject)=>{
      const seq=++id;pending.set(seq,{resolve,reject});socket.send(JSON.stringify({id:seq,method,params}));
    });
    await call("Runtime.enable");
    let ready=false;
    for(let n=0;n<150&&!ready;n++){
      try{
        const state=await call("Runtime.evaluate",{expression:"document.readyState === 'complete' && typeof loadPackage === 'function'",returnByValue:true});
        ready=state.result.value===true;
      }catch(error){if(!/context was destroyed|Cannot find context/.test(error.message))throw error}
      if(!ready)await delay(100);
    }
    if(!ready)throw Error("Studio page startup timed out");
    const expression=`(async()=>{
      const check=(v,m)=>{if(!v)throw Error(m)};
      for(let i=0;i<150;i++){
        if(typeof loadPackage==='function' && document.querySelector('#skinName')?.value)break;
        await new Promise(r=>setTimeout(r,100));
      }
      const data=await (await fetch('/api/template-v3')).json();
      data.manifest.id='restricted-source-test'; data.manifest.capabilities=['host.read'];
      const meta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(data.files['project.json']),c=>c.charCodeAt(0))));
      delete meta.layers;meta.thirdPartyMetadata={keep:'原样保留'};
      data.files['project.json']=btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(meta)+'\\n')));
      await loadPackage(data); document.querySelector('#advancedMode').click();
      const select=document.querySelector('#extraSourceFile');
      check([...select.options].some(o=>o.value==='web/edit.js'),'edit.js missing');
      select.value='web/edit.js';select.dispatchEvent(new Event('change'));
      const field=document.querySelector('#extraSourceCode'), before=field.value;
      check(before.includes('PowerCalEdit'),'edit.js contents missing');
      field.value=before+'\\n// browser-source-roundtrip\\n';
      field.dispatchEvent(new Event('input',{bubbles:true}));
      const project=payload();
      check(JSON.stringify(project.manifest.capabilities)==='["host.read"]','capabilities changed');
      check(project.files['project.json']===data.files['project.json'],'untouched project metadata changed');
      const post=async(url,body,type)=>{const r=await fetch(url,{method:'POST',headers:{'Content-Type':type},body});
        check(r.ok,url+' returned '+r.status);return r};
      const preview=await (await post('/api/preview-v3',JSON.stringify(project),'application/json')).json();
      const previewText=await (await fetch(preview.url.replace('web/index.html','web/edit.js'))).text();
      check(previewText===field.value,'preview source differs');
      const zip=await (await post('/api/export-v3',JSON.stringify(project),'application/json')).arrayBuffer();
      const imported=await (await post('/api/import-v3',zip,'application/octet-stream')).json();
      check(JSON.stringify(imported.manifest.capabilities)==='["host.read"]','import capabilities changed');
      for(const [name,bytes] of Object.entries(project.files))
        check(imported.files[name]===bytes,'round trip changed '+name);
      await loadPackage(imported);document.querySelector('#advancedMode').click();
      select.value='web/edit.js';select.dispatchEvent(new Event('change'));
      check(field.value===before+'\\n// browser-source-roundtrip\\n','reimport edit missing');
      document.querySelector('#simpleMode').click();window.prompt=()=> 'customShade';
      document.querySelector('#addMaterial').click();
      const visual=payload();
      const visualMeta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(visual.files['project.json']),c=>c.charCodeAt(0))));
      check(visualMeta.thirdPartyMetadata.keep==='原样保留','custom metadata lost');
      check(visualMeta.slots.some(s=>s.id==='customShade'),'visual metadata not saved');
      const visualZip=await (await post('/api/export-v3',JSON.stringify(visual),'application/json')).arrayBuffer();
      const visualImported=await (await post('/api/import-v3',visualZip,'application/octet-stream')).json();
      check(visualImported.files['project.json']===visual.files['project.json'],'visual metadata roundtrip changed');
      return {file:'web/edit.js', files:Object.keys(project.files).length, capabilities:imported.manifest.capabilities};
    })()`;
    const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
    if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    console.log("BROWSER SOURCE ROUNDTRIP PASS",JSON.stringify(result.result.value));
    socket.close();
  }finally{
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
