/* Chrome/CDP replay for Clear's versioned, occurrence-scoped Memo toggle. */
const {spawn}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),readline=require("node:readline");
const root=path.resolve(__dirname,"../../..");
const chrome=process.env.CHROME_PATH||"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const bundle=path.join(root,"app/src/main/assets/powercal-clear-default.pcskin");
const reportDir=path.resolve(process.env.POWERCAL_E2E_REPORT_DIR||
  path.join(root,".local/android-test/reports/separation-s0-timeline-drag/memo-occurrence-toggle-cdp"));
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
    const executionContexts=new Map();
    socket.onmessage=event=>{
      const message=JSON.parse(event.data);
      if(!message.id){
        if(message.method==="Runtime.executionContextCreated")
          executionContexts.set(message.params.context.id,message.params.context);
        else if(message.method==="Runtime.executionContextDestroyed")
          executionContexts.delete(message.params.executionContextId);
        else if(message.method==="Runtime.executionContextsCleared")
          executionContexts.clear();
        return;
      }
      if(!pending.has(message.id))return;
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
    if(loaded?.id!=="powercal-clear-default")throw Error("Canonical Clear package did not load");
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
    const frameEval=async expression=>{
      let lastContextError;
      for(let attempt=0;attempt<30;attempt++){
        const contexts=[...executionContexts.values()].filter(context=>
          context.auxData?.frameId===frame.id&&context.auxData?.isDefault).reverse();
        for(const context of contexts){
          try{return await evaluate(expression,{contextId:context.id})}
          catch(error){
            if(!/Cannot find context with specified id|Cannot find execution context/i.test(error.message))
              throw error;
            executionContexts.delete(context.id);lastContextError=error;
          }
        }
        await delay(50);
      }
      throw lastContextError||Error("Clear Preview main-world execution context was not created");
    };
    await waitFor(frameEval,"typeof window.PowerCalEdit==='object'&&document.querySelector('#memoPage')",
      "Clear Preview main-world application");
    const fixture=await evaluate(`(()=>{
      const select=document.querySelector('#scenario');select.value='repeating';
      select.dispatchEvent(new Event('change',{bubbles:true}));
      return simulatedState().memoEntries.filter(item=>item.eventSeq===31);
    })()`);
    if(fixture.length!==2||fixture.some(item=>item.completed))
      throw Error("Repeating Memo fixture did not start with two open slots: "+JSON.stringify(fixture));
    await frameEval("document.querySelector('.clear-tabbar [data-action=\\\"memo\\\"]')?.click()");
    await waitFor(frameEval,"!document.querySelector('#memoPage').hidden&&"+
      "document.querySelectorAll('#memoList .memo-row').length===2","Memo page");
    const key="2026-10-23T00:00";
    const initial=await frameEval(`(()=>{
      const button=[...document.querySelectorAll('#memoList [data-action=toggleMemo]')]
        .find(item=>item.dataset.occurrenceKey==='${key}');
      return button?{eventSeq:Number(button.dataset.eventSeq),revision:Number(button.dataset.revision),
        occurrenceKey:button.dataset.occurrenceKey,completed:button.dataset.completed,
        label:button.getAttribute('aria-label')}:null;
    })()`);
    if(!initial||initial.eventSeq!==31||initial.revision!==2||initial.completed!=="false")
      throw Error("Clear did not project the selected Memo owner/revision/slot: "+JSON.stringify(initial));
    await frameEval(`(()=>[...document.querySelectorAll('#memoList [data-action=toggleMemo]')]
      .find(item=>item.dataset.occurrenceKey==='${key}')?.click())()`);
    const toggled=await waitFor(evaluate,`(()=>{
      const rows=simulatedState().memoEntries.filter(item=>item.eventSeq===31);
      const selected=rows.find(item=>item.occurrenceKey==='${key}');
      const sibling=rows.find(item=>item.occurrenceKey==='2026-10-22T00:00');
      return selected?.completed===true&&sibling?.completed===false?{selected,sibling}:null;
    })()`,"Occurrence-scoped Host toggle");
    if(toggled.selected.revision!==initial.revision+1||
      toggled.sibling.revision!==initial.revision+1)
      throw Error("A Memo completion must advance the formal owner revision once: "+JSON.stringify(toggled));
    await frameEval("document.querySelector('[data-filter=done]')?.click()");
    const visible=await waitFor(frameEval,`(()=>{
      const row=[...document.querySelectorAll('#memoList .memo-row')]
        .find(item=>item.querySelector('.memo-title')?.textContent==='重复 Memo · 明天');
      const button=row?.querySelector('[data-action=toggleMemo]');
      return button?.dataset.occurrenceKey==='${key}'&&button.dataset.completed==='true'&&
        button.getAttribute('aria-label')==='恢复未完成'
        ?{title:row.querySelector('.memo-title').textContent,occurrenceKey:button.dataset.occurrenceKey,
          completed:button.dataset.completed,label:button.getAttribute('aria-label')}:null;
    })()` ,"Completed Memo UI row");

    await evaluate(`onSkinAction(JSON.stringify({version:1,type:'action',payload:{name:'toggleMemo',
      eventSeq:${initial.eventSeq},expectedRevision:${initial.revision},occurrenceKey:'${key}',completed:false}}))`);
    await evaluate(`onSkinAction(JSON.stringify({version:1,type:'action',payload:{name:'toggleMemo',
      eventSeq:${initial.eventSeq},expectedRevision:1,occurrenceKey:'${key}',completed:true}}))`);
    const replay=await evaluate(`(()=>{
      const rows=simulatedState().memoEntries.filter(item=>item.eventSeq===31);
      return {selected:rows.find(item=>item.occurrenceKey==='${key}')?.completed,
        sibling:rows.find(item=>item.occurrenceKey==='2026-10-22T00:00')?.completed,
        rejected:(document.querySelector('#bridgeLog')?.textContent||'').includes('拒绝过期或不匹配')};
    })()`);
    if(!replay.selected||replay.sibling||!replay.rejected)
      throw Error("Duplicate/stale toggle changed the wrong occurrence or was accepted: "+JSON.stringify(replay));

    const fallbackClicked=await frameEval(`(()=>{
      if(!window.PowerCalEdit)return false;
      window.PowerCalEdit.routeAction=()=>false;
      const title=[...document.querySelectorAll('#memoList .memo-title')]
        .find(item=>item.dataset.originalOccurrenceKey==='${key}');
      title?.click();return !!title;
    })()`);
    if(!fallbackClicked)throw Error("Could not force the generic openEvent fallback");
    const fallback=await waitFor(evaluate,`(()=>{
      const line=[...(document.querySelector('#bridgeLog')?.children||[])]
        .map(item=>item.textContent)
        .find(text=>text.includes('Studio Mock accepted openEvent eventSeq=31'));
      return line?.includes('revision=3 occurrenceKey=${key}')?line:null;
    })()` ,"Occurrence-preserving openEvent fallback");

    const screenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});
    fs.writeFileSync(path.join(reportDir,"memo-occurrence-toggle-studio-preview.png"),
      Buffer.from(screenshot.data,"base64"));
    const result={package:loaded,initial,afterToggle:{selected:toggled.selected,sibling:toggled.sibling},
      visible,replay,fallback};
    fs.writeFileSync(path.join(reportDir,"memo-occurrence-toggle-result.json"),
      JSON.stringify(result,null,2)+"\n");
    console.log("CLEAR MEMO OCCURRENCE TOGGLE CDP PASS",JSON.stringify(result));
    socket.close();
  }finally{
    if(socket)try{socket.close()}catch{}
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
