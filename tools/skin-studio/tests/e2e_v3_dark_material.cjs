/* Verify Studio material edits preserve Clear's authored dark palette in Chrome. */
const {spawn}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),readline=require("node:readline");
const root=path.resolve(__dirname,"../../..");
const chrome=process.env.CHROME_PATH||"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir=path.join(root,".local/android-test/reports/separation-20261027/studio-dark-material");
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function main(){
  fs.mkdirSync(reportDir,{recursive:true});
  const profile=fs.mkdtempSync(path.join(reportDir,"chrome-profile-"));
  const server=spawn("python",["tools/skin-studio/skin_studio.py","serve","--port","0"],
    {cwd:root,stdio:["ignore","pipe","pipe"]});
  let browser;
  try{
    const line=await Promise.race([
      new Promise((resolve,reject)=>{
        readline.createInterface({input:server.stdout}).once("line",resolve);
        server.once("error",reject);server.once("exit",code=>reject(Error("Studio exited "+code)));
      }),delay(15000).then(()=>{throw Error("Studio startup timed out")})
    ]);
    const origin=line.match(/http:\/\/127\.0\.0\.1:\d+/)?.[0];
    if(!origin)throw Error("No Studio URL: "+line);
    browser=spawn(chrome,["--headless=new","--disable-gpu","--no-sandbox",
      "--disable-background-networking","--no-first-run","--no-default-browser-check",
      "--disable-site-isolation-trials","--disable-features=IsolateOrigins,site-per-process",
      "--remote-allow-origins=*","--remote-debugging-port=0","--user-data-dir="+profile,
      "about:blank"],{stdio:"ignore"});
    const portFile=path.join(profile,"DevToolsActivePort");
    for(let i=0;i<150&&!fs.existsSync(portFile);i++){
      if(browser.exitCode!==null)throw Error("Chrome exited "+browser.exitCode);
      await delay(100);
    }
    if(!fs.existsSync(portFile))throw Error("Chrome startup timed out");
    const port=fs.readFileSync(portFile,"utf8").split(/\r?\n/)[0];
    const target=await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,{method:"PUT"})).json();
    const socket=new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
    let nextId=0;const pending=new Map();
    socket.onmessage=event=>{
      const message=JSON.parse(event.data);if(!message.id||!pending.has(message.id))return;
      const {resolve,reject}=pending.get(message.id);pending.delete(message.id);
      message.error?reject(Error(message.error.message)):resolve(message.result);
    };
    const call=(method,params={})=>new Promise((resolve,reject)=>{
      const id=++nextId;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params}));
    });
    await call("Runtime.enable");
    await call("Page.enable");
    const pageReady=async()=>{
      for(let i=0;i<150;i++){
        const result=await call("Runtime.evaluate",{expression:
          "document.readyState==='complete'&&typeof loadPackage==='function'",returnByValue:true});
        if(result.result.value)return true;
        await delay(100);
      }
      throw Error("Studio page startup timed out");
    };
    await pageReady();
    await call("Runtime.evaluate",{expression:
      "(()=>{const e=document.querySelector('#theme');e.value='dark';e.dispatchEvent(new Event('change',{bubbles:true}))})()"});
    const loaded=await call("Runtime.evaluate",{expression:`(async()=>{
      const data=await (await fetch('/api/template-v3')).json();
      data.manifest.id='dark-material-test';
      await loadPackage(data);
      const row=[...document.querySelectorAll('#materialSlots .materialRow')]
        .find(item=>item.querySelector('span')?.textContent==='文字颜色');
      if(!row)throw Error('Clear text color material missing');
      const scenario=document.querySelector('#scenario');
      scenario.value='ai-review';scenario.dispatchEvent(new Event('change',{bubbles:true}));
      const color=row.querySelector('input[type=color]');
      color.value='#16171B';color.dispatchEvent(new Event('input',{bubbles:true}));
      const css=new TextDecoder().decode(Uint8Array.from(atob(payload().files['web/style.css']),c=>c.charCodeAt(0)));
      if(!css.includes(':root:not(.dark)'))throw Error('material source does not preserve dark tokens');
      return true;
    })()`,awaitPromise:true,returnByValue:true});
    if(loaded.exceptionDetails)throw Error(loaded.exceptionDetails.exception?.description||loaded.exceptionDetails.text);
    const waitParent=async(expression,label)=>{
      for(let i=0;i<150;i++){
        const result=await call("Runtime.evaluate",{expression,returnByValue:true});
        if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);
        if(result.result.value)return result.result.value;
        await delay(100);
      }
      throw Error(label+" timed out");
    };
    await waitParent("document.querySelector('#previewState')?.textContent.includes('连接成功')",
      "Studio preview refresh");
    await call("Runtime.evaluate",{expression:
      "document.querySelector('#webPreview').scrollIntoView({block:'center'})"});
    const childFrame=async()=>{
      for(let i=0;i<150;i++){
        const tree=await call("Page.getFrameTree");
        const child=tree.frameTree.childFrames?.find(frame=>frame.frame.url.includes('/preview/'));
        if(child)return child.frame;
        await delay(100);
      }
      const diagnostics=await call("Runtime.evaluate",{expression:
        "JSON.stringify({state:document.querySelector('#previewState')?.textContent,src:document.querySelector('#webPreview')?.src})",
        returnByValue:true});
      throw Error("Studio preview frame missing: "+diagnostics.result.value+" "+JSON.stringify(await call("Page.getFrameTree")));
    };
    let frame=await childFrame();
    const frameWorld=await call("Page.createIsolatedWorld",{frameId:frame.id,worldName:"dark-material-qa"});
    const evaluateFrame=expression=>call("Runtime.evaluate",{
      contextId:frameWorld.executionContextId,expression,returnByValue:true
    });
    const waitFor=async(expression,label)=>{
      for(let i=0;i<150;i++){
        const result=await evaluateFrame(expression);
        if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);
        if(result.result.value)return result.result.value;
        await delay(100);
      }
      const diagnostics=await evaluateFrame(`({theme:document.documentElement.className,
        prefersDark:matchMedia('(prefers-color-scheme: dark)').matches,
        label:getComputedStyle(document.documentElement).getPropertyValue('--label').trim(),
        body:getComputedStyle(document.body).color})`);
      throw Error(label+" timed out: "+JSON.stringify(diagnostics.result.value));
    };
    await evaluateFrame("document.querySelector('.clear-tabbar [data-action=\\\"ai\\\"]')?.click()");
    await waitFor("document.querySelector('.ai-proposal-item')",
      "AI proposal scenario setup");
    const dark=await waitFor(`(()=>{
      const panel=document.querySelector('.ai-proposal-item');if(!panel)return null;
      const css=getComputedStyle(panel),token=getComputedStyle(document.documentElement)
        .getPropertyValue('--label').trim();
      const luminance=value=>{const c=value.match(/[\\d.]+/g).slice(0,3).map(Number).map(v=>v/255)
        .map(v=>v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4);return .2126*c[0]+.7152*c[1]+.0722*c[2]};
      const a=luminance(css.color),b=luminance(css.backgroundColor),contrast=(Math.max(a,b)+.05)/(Math.min(a,b)+.05);
      return document.documentElement.classList.contains('dark')&&token==='#F3F3F6'&&contrast>=4.5
        ?{theme:'dark',labelToken:token,text:css.color,background:css.backgroundColor,contrast}:null;
    })()`,"dark Clear token preservation and proposal-card contrast");
    await call("Runtime.evaluate",{expression:
      "(()=>{const e=document.querySelector('#theme');e.value='light';e.dispatchEvent(new Event('change',{bubbles:true}))})()"});
    const light=await waitFor(`(()=>{
      const token=getComputedStyle(document.documentElement).getPropertyValue('--label').trim().toLowerCase();
      const body=getComputedStyle(document.body);
      return !document.documentElement.classList.contains('dark')&&token==='#16171b'
        ?{theme:'light',labelToken:token,text:body.color,background:body.backgroundColor}:null;
    })()`,"light material application");
    const result={darkToken:"#F3F3F6",lightToken:"#16171B",dark,light};
    fs.writeFileSync(path.join(reportDir,"chrome-dark-material-result.json"),JSON.stringify(result,null,2)+"\n");
    console.log("BROWSER CLEAR DARK MATERIAL PASS",JSON.stringify(result));
    socket.close();
  }finally{
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
