/* Chrome/CDP replay for Clear Month occurrence -> editor.open slot routing. */
const {spawn}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),readline=require("node:readline");
const root=path.resolve(__dirname,"../../..");
const chrome=process.env.CHROME_PATH||"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const bundle=path.join(root,"app/src/main/assets/powercal-clear-default.pcskin");
const reportDir=path.resolve(process.env.POWERCAL_E2E_REPORT_DIR||
  path.join(root,".local/android-test/reports/separation-s0-timeline-drag/month-occurrence-cdp"));
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));

async function main(){
  fs.mkdirSync(reportDir,{recursive:true});
  const profile=fs.mkdtempSync(path.join(reportDir,"chrome-profile-"));
  const server=spawn("python",["tools/skin-studio/skin_studio.py","serve","--port","0"],
    {cwd:root,stdio:["ignore","pipe","pipe"]});
  let browser,socket;
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
    const target=await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,
      {method:"PUT"})).json();
    socket=new WebSocket(target.webSocketDebuggerUrl);
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
    const evaluate=async(expression,options={})=>{
      const result=await call("Runtime.evaluate",{
        expression,returnByValue:true,awaitPromise:true,...options
      });
      if(result.exceptionDetails)
        throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);
      return result.result.value;
    };
    await call("Runtime.enable");await call("Page.enable");
    const waitFor=async(run,expression,label)=>{
      for(let i=0;i<200;i++){
        const result=await run(expression);
        if(result)return result;
        await delay(100);
      }
      throw Error(label+" timed out");
    };
    await waitFor(evaluate,"document.readyState==='complete'&&typeof loadPackage==='function'",
      "Studio startup");
    const packageBase64=fs.readFileSync(bundle).toString("base64");
    const loaded=await evaluate(`(async()=>{
      const bytes=Uint8Array.from(atob("${packageBase64}"),c=>c.charCodeAt(0));
      const response=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:bytes});
      if(!response.ok)throw Error('Canonical .pcskin import returned '+response.status);
      const skin=await response.json();await loadPackage(skin);
      return {id:skin.manifest.id,fileCount:Object.keys(skin.files).length};
    })()`);
    if(loaded?.id!=="powercal-clear-default")throw Error("Canonical Clear did not load");
    await waitFor(evaluate,"document.querySelector('#previewState')?.textContent.includes('连接成功')",
      "Studio Preview Host connection");
    await evaluate("document.querySelector('#webPreview').scrollIntoView({block:'center'})");
    let frame;
    for(let i=0;i<150&&!frame;i++){
      const tree=await call("Page.getFrameTree");
      frame=tree.frameTree.childFrames?.find(item=>item.frame.url.includes('/preview/'))?.frame;
      if(!frame)await delay(100);
    }
    if(!frame)throw Error("Studio Preview iframe was not created");
    const world=await call("Page.createIsolatedWorld",{frameId:frame.id,worldName:"month-occurrence-qa"});
    const frameEval=expression=>evaluate(expression,{contextId:world.executionContextId});
    await waitFor(frameEval,"!!document.querySelector('.clear-tabbar [data-action=\\\"month\\\"]')",
      "Clear Month tab");

    const fixture=await evaluate(`(()=>{
      const select=document.querySelector('#scenario');select.value='repeating';
      select.dispatchEvent(new Event('change',{bubbles:true}));
      const state=simulatedState();
      return {scenario:select.value,event:state.timeline.items.find(x=>x.eventSeq===8)};
    })()`);
    if(fixture.scenario!=="repeating"||fixture.event?.revision!==2)
      throw Error("Repeating Host Mock fixture did not load: "+JSON.stringify(fixture));
    await frameEval("document.querySelector('.clear-tabbar [data-action=\\\"month\\\"]')?.click()");
    await waitFor(frameEval,"!document.querySelector('#monthPage').hidden&&"+
      "document.querySelector('#monthAgenda h2')?.textContent==='2026-10-22'",
      "Month agenda");
    const agenda=await frameEval(`(()=>{
      const button=document.querySelector("#monthAgenda .agenda-event["+
        "data-event-seq='8'][data-original-occurrence-key='2026-10-22T09:00']");
      return button?{eventSeq:Number(button.dataset.eventSeq),revision:Number(button.dataset.revision),
        originalOccurrenceKey:button.dataset.originalOccurrenceKey,title:button.textContent}:null;
    })()`);
    if(!agenda)throw Error("Projected month occurrence button is missing its original slot key");
    await frameEval("document.querySelector(\"#monthAgenda .agenda-event["+
      "data-event-seq='8'][data-original-occurrence-key='2026-10-22T09:00']\")?.click()");
    const opened=await waitFor(evaluate,`(()=>{
      const editor=simulatedState().editing.editor;
      return editor.open?editor:null;
    })()`,"Host editor.open result");
    if(opened.eventSeq!==agenda.eventSeq||opened.revision!==agenda.revision||
      opened.occurrenceKey!==agenda.originalOccurrenceKey||
      !opened.requiresRecurrenceScope||!opened.canEditSelectedOccurrence)
      throw Error("Month agenda lost owner/revision/original slot: "+JSON.stringify({agenda,opened}));
    await frameEval("document.querySelector('[data-scope=SINGLE_OCCURRENCE]')?.click()");
    const scoped=await waitFor(evaluate,`(()=>{
      const editor=simulatedState().editing.editor;
      return editor.recurrenceScope==='SINGLE_OCCURRENCE'?{
        recurrenceScope:editor.recurrenceScope,occurrenceKey:editor.occurrenceKey,
        startText:editor.form.startText}:null;
    })()`,"Single-occurrence scope");
    if(scoped.occurrenceKey!==agenda.originalOccurrenceKey||
      scoped.startText!=="2026-10-22 09:00")
      throw Error("Single-occurrence editor did not retain the selected month slot: "+JSON.stringify(scoped));

    const screenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});
    fs.writeFileSync(path.join(reportDir,"month-occurrence-open-studio-preview.png"),
      Buffer.from(screenshot.data,"base64"));
    const result={package:loaded,fixture:{eventSeq:fixture.event.eventSeq,revision:fixture.event.revision},
      agenda,opened:{eventSeq:opened.eventSeq,revision:opened.revision,
        occurrenceKey:opened.occurrenceKey,requiresRecurrenceScope:opened.requiresRecurrenceScope,
        canEditSelectedOccurrence:opened.canEditSelectedOccurrence},scoped};
    fs.writeFileSync(path.join(reportDir,"month-occurrence-open-result.json"),
      JSON.stringify(result,null,2)+"\n");
    console.log("CLEAR MONTH OCCURRENCE OPEN CDP PASS",JSON.stringify(result));
    socket.close();
  }finally{
    if(socket)try{socket.close()}catch{}
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
