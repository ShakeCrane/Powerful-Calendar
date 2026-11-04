/* Studio GUI/API proof: blank project -> authored pages/bindings -> save/reopen -> build/export/import. */
const {spawn} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const {createHash} = require("node:crypto");
const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.POWERCAL_E2E_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/S-06-20261104"));
const downloadDir = path.join(reportDir, "from-blank-downloads-" + process.pid);
const fixtureJs = path.join(__dirname, "fixtures/from-blank-functional/source/web/app.js");
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const hash = bytes => createHash("sha256").update(bytes).digest("hex");

async function main() {
  fs.mkdirSync(downloadDir, {recursive:true});
  const profile = fs.mkdtempSync(path.join(reportDir, "studio-from-blank-profile-"));
  const server = spawn("python", ["tools/skin-studio/skin_studio.py", "serve", "--port", "0"],
    {cwd:root,stdio:["ignore","pipe","pipe"]});
  let browser, socket;
  try {
    const firstLine = await Promise.race([
      new Promise((resolve,reject)=>{
        readline.createInterface({input:server.stdout}).once("line",resolve);
        server.once("error",reject); server.once("exit",code=>reject(Error("Studio exited "+code)));
      }), delay(15000).then(()=>{throw Error("Studio startup timed out");})
    ]);
    const origin=firstLine.match(/http:\/\/127\.0\.0\.1:\d+/)?.[0];
    if(!origin)throw Error("Studio URL missing: "+firstLine);
    browser=spawn(chrome,["--headless=new","--disable-gpu","--no-sandbox","--disable-background-networking",
      "--disable-site-isolation-trials","--disable-features=IsolateOrigins,site-per-process","--no-first-run",
      "--no-default-browser-check","--remote-allow-origins=*","--remote-debugging-port=0",
      "--user-data-dir="+profile,"about:blank"],{stdio:"ignore"});
    const activePort=path.join(profile,"DevToolsActivePort"); let port="";
    for(let n=0;n<150&&!port;n++){
      if(browser.exitCode!==null)throw Error("Chrome exited "+browser.exitCode);
      try{if(fs.existsSync(activePort))port=fs.readFileSync(activePort,"utf8").split(/\r?\n/)[0]}catch{}
      if(!port)await delay(100);
    }
    if(!port)throw Error("Chrome DevTools port did not appear");
    const target=await(await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,{method:"PUT"})).json();
    socket=new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
    let id=0; const pending=new Map();
    socket.onmessage=event=>{
      const message=JSON.parse(event.data); if(!message.id||!pending.has(message.id))return;
      const entry=pending.get(message.id);pending.delete(message.id);
      message.error?entry.reject(Error(`${entry.method} failed: ${message.error.message}`)):entry.resolve(message.result);
    };
    const call=(method,params={})=>new Promise((resolve,reject)=>{
      const requestId=++id;pending.set(requestId,{resolve,reject,method,params});socket.send(JSON.stringify({id:requestId,method,params}));
    });
    const evaluate=async expression=>{
      const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
      if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);
      return result.result.value;
    };
    const waitFor=async(predicate,message)=>{
      for(let n=0;n<240;n++){try{if(await predicate())return}catch{}await delay(100)}
      throw Error(message);
    };
    const mouseClick=async selector=>{
      await evaluate(`document.querySelector(${JSON.stringify(selector)})?.scrollIntoView({block:'center'})`);
      let quad;
      for(let attempt=0;attempt<5&&!quad;attempt++){
        const doc=await call("DOM.getDocument",{depth:-1});
        const node=await call("DOM.querySelector",{nodeId:doc.root.nodeId,selector});
        if(!node.nodeId)throw Error("Studio GUI target missing: "+selector);
        try{quad=(await call("DOM.getBoxModel",{nodeId:node.nodeId})).model.content}
        catch(error){if(attempt===4)throw Error(`Studio GUI target changed while measuring ${selector}: ${error.message}`);await delay(80)}
      }
      const x=(quad[0]+quad[2]+quad[4]+quad[6])/4,y=(quad[1]+quad[3]+quad[5]+quad[7])/4;
      await call("Input.dispatchMouseEvent",{type:"mouseMoved",x,y});
      await call("Input.dispatchMouseEvent",{type:"mousePressed",x,y,button:"left",clickCount:1});
      await call("Input.dispatchMouseEvent",{type:"mouseReleased",x,y,button:"left",clickCount:1});
    };
    const setField=async(selector,value,eventName="change")=>evaluate(`(()=>{
      const e=document.querySelector(${JSON.stringify(selector)});if(!e)throw Error('missing field '+${JSON.stringify(selector)});
      e.value=${JSON.stringify(value)};e.dispatchEvent(new Event(${JSON.stringify(eventName)},{bubbles:true}));return true;
    })()`);
    const capture=async name=>{
      const shot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
      fs.writeFileSync(path.join(reportDir,name),Buffer.from(shot.data,"base64"));
    };
    await call("Runtime.enable");await call("Page.enable");await call("DOM.enable");
    await call("Page.setDownloadBehavior",{behavior:"allow",downloadPath:downloadDir});
    await waitFor(()=>evaluate("document.readyState==='complete'&&typeof loadPackage==='function'"),
      "Studio did not load");
    await waitFor(()=>evaluate("!!manifest?.id"),"Studio starter package did not initialize");
    await evaluate("window.__s06Walk=nodes=>(nodes||[]).flatMap(node=>[node,...window.__s06Walk(node.children)])");

    await mouseClick("#newBlank");
    await waitFor(()=>evaluate("manifest?.id==='powercal-blank-skin'&&project?.pages?.length===5"),
      "New Blank project did not load");
    const initial=await evaluate(`({id:manifest.id,caps:[...manifest.capabilities],routes:project.pages.map(p=>p.route),
      sourceNames:[...sourceFiles.keys()].sort(),starter:new TextDecoder().decode(sourceFiles.get('web/app.js'))})`);
    if(initial.id!=="powercal-blank-skin"||JSON.stringify(initial.routes)!==JSON.stringify(
      ["TIMELINE","MONTH","MEMO","AI","SETTINGS"])||initial.sourceNames.includes("assets/clear"))
      throw Error("Project was not created from Blank v3");
    const sourceCode=fs.readFileSync(fixtureJs,"utf8");
    await setField("#skinId","powercal-from-blank-functional");
    await setField("#skinName","From Blank Functional QA");
    await evaluate(`document.querySelector('#capHostRead').checked=true;
      document.querySelector('#capHostRead').dispatchEvent(new Event('change',{bubbles:true}));
      document.querySelector('#advancedMode').click()`);

    const pageFor=route=>evaluate(`(()=>{
      const select=document.querySelector('#componentPage');
      const option=[...select.options].find(item=>item.textContent.endsWith('· ${route}'));
      if(!option)throw Error('required blank page not found: ${route}');
      select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));return true;
    })()`);
    const add=async(route,type,label,{statePath,hostAction}={})=>{
      await pageFor(route);
      await evaluate(`(()=>{const s=document.querySelector('#componentType');s.value=${JSON.stringify(type)};return true})()`);
      await mouseClick("#componentAdd");
      await waitFor(()=>evaluate("!!selectedComponentId"),"Component was not selected after add");
      await setField("#componentLabel",label);
      if(statePath){await setField("#componentStateBinding",statePath);}
      if(hostAction){await setField("#componentHostAction",hostAction);}
    };
    await add("TIMELINE","timeline","可视时间轴");
    await add("TIMELINE","event-card","Core 事件卡片");
    await add("TIMELINE","text","时间窗口",{statePath:"timeline.anchorMillis"});
    await add("TIMELINE","button","回到今日",{hostAction:"today"});
    await add("TIMELINE","button","创建活动",{hostAction:"createDraft"});
    // The GUI action parameter panel is part of the official catalog; set the required ACTIVITY kind.
    await evaluate(`(()=>{const input=document.querySelector('#componentActionParameters [data-host-param="kind"]');
      if(!input)throw Error('catalogued createDraft.kind parameter control missing');
      input.value='ACTIVITY';input.dispatchEvent(new Event('change',{bubbles:true}));return true})()`);
    await add("MONTH","month-grid","Core 月格",{statePath:"month.month"});
    await add("MONTH","date-button","日期选择");
    await add("MONTH","button","打开月历",{hostAction:"month"});
    await add("MEMO","scroll","Core Memo 列表");
    await add("MEMO","memo-row","Memo 完成行",{statePath:"memoCount"});
    await add("MEMO","button","新建 Memo",{hostAction:"createMemo"});
    await add("AI","scroll","AI 历史消息");
    await add("AI","chat-item","AI 消息");
    await add("AI","form-field","AI 输入框");
    await add("AI","button","AI 页面",{hostAction:"ai"});
    await add("SETTINGS","scroll","权限与提醒设置");
    await add("SETTINGS","settings-row","系统权限状态",{statePath:"hostDataReady"});
    await add("SETTINGS","button","打开设置",{hostAction:"settings"});
    await evaluate(`(()=>{const cap=document.querySelector('#capHostAction');cap.checked=true;
      cap.dispatchEvent(new Event('change',{bubbles:true}));return true})()`);

    await pageFor("TIMELINE");
    await evaluate(`(()=>{const editor=document.querySelector('#jsCode');
      if(!editor||editor.disabled)throw Error('Studio source editor is unavailable');
      editor.value=${JSON.stringify(sourceCode)};
      editor.dispatchEvent(new Event('input',{bubbles:true}));return true})()`);
    await waitFor(()=>evaluate(`sourceFiles.has('web/app.js')&&
      new TextDecoder().decode(sourceFiles.get('web/app.js')).length>1000`),
      "Studio did not persist the authored Blank runtime source");
    const authoredRuntime=await evaluate("new TextDecoder().decode(sourceFiles.get('web/app.js'))");
    if(authoredRuntime.replace(/\\r\\n/g,"\\n")!==sourceCode.replace(/\\r\\n/g,"\\n"))
      throw Error(`Studio source editor changed authored runtime bytes (expected ${sourceCode.length}, got ${authoredRuntime.length})`);
    await waitFor(()=>evaluate(`project.pages.flatMap(p=>window.__s06Walk(p.components)).length>=19`),
      "Studio component tree is missing authored feature components");
    await capture("s06-studio-created.png");

    await mouseClick("#validate");
    await waitFor(()=>evaluate(`document.querySelector('#toast').textContent.includes('验证')||
      document.querySelector('#toast').textContent.includes('构建')||
      document.querySelector('#packageInspectionOutput')?.textContent.includes('packageSha256')`),
      "Studio validation did not return");
    await mouseClick("#exportProject");
    const projectName="powercal-from-blank-functional.pcstudio-v3.json";
    await waitFor(()=>evaluate(`document.querySelector('#toast').textContent.includes('保存')`),
      "Studio project export did not complete");
    const projectPath=path.join(downloadDir,projectName);
    await waitFor(()=>fs.existsSync(projectPath),"Saved Studio project file was not downloaded");
    const beforeReload=await evaluate(`JSON.stringify({manifest,project,app:new TextDecoder().decode(sourceFiles.get('web/app.js'))})`);

    await call("Page.reload");
    await waitFor(()=>evaluate("document.readyState==='complete'&&!!manifest?.id"),"Studio did not reopen");
    await evaluate("window.__s06Walk=nodes=>(nodes||[]).flatMap(node=>[node,...window.__s06Walk(node.children)])");
    const doc=await call("DOM.getDocument",{depth:-1});
    const fileNode=await call("DOM.querySelector",{nodeId:doc.root.nodeId,selector:"#importProject"});
    await call("DOM.setFileInputFiles",{files:[projectPath],nodeId:fileNode.nodeId});
    await evaluate("document.querySelector('#importProject').dispatchEvent(new Event('change',{bubbles:true}))");
    await waitFor(()=>evaluate("manifest?.id==='powercal-from-blank-functional'&&project?.pages?.length===5"),
      "Studio did not reopen the saved blank project");
    const afterReload=await evaluate(`JSON.stringify({manifest,project,app:new TextDecoder().decode(sourceFiles.get('web/app.js'))})`);
    const old=JSON.parse(beforeReload),fresh=JSON.parse(afterReload);
    if(old.manifest.id!==fresh.manifest.id||old.project.pages.length!==fresh.project.pages.length||
       old.app!==fresh.app||old.project.pages.some((page,index)=>
         JSON.stringify(page.components)!==JSON.stringify(fresh.project.pages[index].components)))
      throw Error("Saved and reopened Studio project structures differ");

    await pageFor("TIMELINE");
    const todayNode=await evaluate(`window.__s06Walk(project.pages.find(p=>p.route==='TIMELINE').components)
      .find(node=>node.type==='button'&&node.props.label==='回到今日')?.id||''`);
    await mouseClick(`#componentTree [data-component-id='${todayNode}']`);
    await setField("#componentLabel","回到今日 · 重开后继续编辑");
    await mouseClick("#exportPack");
    const packagePath=path.join(downloadDir,"powercal-from-blank-functional.pcskin");
    await waitFor(()=>fs.existsSync(packagePath),"Studio .pcskin export was not downloaded");
    const packageBytes=fs.readFileSync(packagePath);
    const verified=await fetch(origin+"/api/validate-v3",{method:"POST",
      headers:{"Content-Type":"application/octet-stream"},body:packageBytes});
    const receipt=await verified.json();
    if(!verified.ok||receipt.ok!==true||receipt.packageSha256!==hash(packageBytes)||
       JSON.stringify(receipt.requiredCapabilities)!==JSON.stringify(["host.read","host.action"]))
      throw Error("Formal v3 verifier rejected the Studio export: "+JSON.stringify(receipt));

    const zipDoc=await call("DOM.getDocument",{depth:-1});
    const zipNode=await call("DOM.querySelector",{nodeId:zipDoc.root.nodeId,selector:"#importZip"});
    await call("DOM.setFileInputFiles",{files:[packagePath],nodeId:zipNode.nodeId});
    await evaluate("document.querySelector('#importZip').dispatchEvent(new Event('change',{bubbles:true}))");
    await waitFor(()=>evaluate(`manifest?.id==='powercal-from-blank-functional'&&
      !!sourceFiles.get('web/app.js')&&project.pages.length===5`),"Studio did not import its exported .pcskin");
    const imported=await evaluate(`({id:manifest.id,caps:[...manifest.capabilities],routes:project.pages.map(p=>p.route),
      nodes:project.pages.map(p=>window.__s06Walk(p.components).map(n=>({type:n.type,label:n.props?.label||'',
        action:n.props?.hostAction?.name||'',state:n.props?.hostStateBinding||''}))),
      appMatches:new TextDecoder().decode(sourceFiles.get('web/app.js'))===${JSON.stringify(sourceCode)}})`);
    if(imported.id!=="powercal-from-blank-functional"||!imported.appMatches||
       JSON.stringify(imported.routes)!==JSON.stringify(["TIMELINE","MONTH","MEMO","AI","SETTINGS"]))
      throw Error("Studio package import did not preserve the five-page project/runtime");
    await pageFor("TIMELINE");
    const importedToday=await evaluate(`window.__s06Walk(project.pages.find(p=>p.route==='TIMELINE').components)
      .find(node=>node.type==='button'&&node.props.label.includes('回到今日'))?.id||''`);
    await mouseClick(`#componentTree [data-component-id='${importedToday}']`);
    await setField("#componentLabel","回到今日 · 导入后再次编辑");
    await mouseClick("#validate");
    await waitFor(()=>evaluate("document.querySelector('#toast').textContent.length>0"),
      "Post-import edit validation did not complete");
    await capture("s06-studio-reopened-roundtrip.png");
    const summary={
      blankStarterId:initial.id,skinId:imported.id,capabilities:imported.caps,
      pageRoutes:imported.routes,componentTypeCounts:imported.nodes.map((nodes,index)=>({route:imported.routes[index],
        count:nodes.length,types:[...new Set(nodes.map(node=>node.type))]})),
      packageBytes:packageBytes.length,packageSha256:hash(packageBytes),formalValidation:receipt.ok,
      savedProjectSha256:hash(fs.readFileSync(projectPath)),reopenedAndReedited:true,
      appJsSha256:hash(Buffer.from(sourceCode)),projectContainsClearSource:false
    };
    const summaryPath=path.join(reportDir,"s06-studio-roundtrip.json");
    fs.writeFileSync(summaryPath,JSON.stringify(summary,null,2)+"\n");
    const out=process.env.POWERCAL_E2E_FROM_BLANK_PACKAGE_OUT;
    if(out){fs.mkdirSync(path.dirname(path.resolve(out)),{recursive:true});fs.copyFileSync(packagePath,path.resolve(out));}
    console.log(JSON.stringify({summary,summaryPath,packagePath:out?path.resolve(out):packagePath},null,2));
  } finally {
    try{socket?.close()}catch{}
    try{browser?.kill()}catch{}
    try{server.kill()}catch{}
  }
}

main().catch(error=>{console.error(error.stack||error);process.exitCode=1});
