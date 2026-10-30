/* Studio Preview/CDP replay for Clear settings using the non-writing Studio Mock. */
const {spawn}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),readline=require("node:readline");
const root=path.resolve(__dirname,"../../..");
const chrome=process.env.CHROME_PATH||"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const bundle=path.join(root,"app/src/main/assets/powercal-clear-default.pcskin");
const reportDir=path.resolve(process.env.POWERCAL_E2E_REPORT_DIR||
  path.join(root,".local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp"));
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));

async function main(){
  fs.mkdirSync(reportDir,{recursive:true});
  const profile=fs.mkdtempSync(path.join(reportDir,"studio-chrome-profile-"));
  const runId=new Date().toISOString().replace(/[:.]/g,"-");
  const server=spawn("python",["tools/skin-studio/skin_studio.py","serve","--port","0"],
    {cwd:root,stdio:["ignore","pipe","ignore"]});
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
    let port;
    for(let i=0;i<150&&!port;i++){
      if(browser.exitCode!==null)throw Error("Chrome exited "+browser.exitCode);
      try{port=fs.readFileSync(portFile,"utf8").split(/\r?\n/)[0]||null}
      catch(error){if(error.code!=="ENOENT"&&error.code!=="EBUSY")throw error}
      if(!port)await delay(100);
    }
    if(!port)throw Error("Chrome startup timed out");
    const target=await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,
      {method:"PUT"})).json();
    socket=new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve,reject)=>{socket.onopen=resolve;socket.onerror=reject});
    let nextId=0;const pending=new Map(),contexts=new Map();
    socket.onmessage=event=>{
      const message=JSON.parse(event.data);
      if(!message.id){
        if(message.method==="Runtime.executionContextCreated")
          contexts.set(message.params.context.id,message.params.context);
        else if(message.method==="Runtime.executionContextDestroyed")
          contexts.delete(message.params.executionContextId);
        else if(message.method==="Runtime.executionContextsCleared")contexts.clear();
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
      for(let i=0;i<180;i++){
        const result=await run(expression);
        if(result)return result;
        await delay(100);
      }
      throw Error(label+" timed out");
    };
    await waitFor(evaluate,"document.readyState==='complete'&&typeof loadPackage==='function'",
      "Studio page load");
    const packageBase64=fs.readFileSync(bundle).toString("base64");
    const loaded=await evaluate(`(async()=>{
      const bytes=Uint8Array.from(atob("${packageBase64}"),c=>c.charCodeAt(0));
      const response=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:bytes});
      if(!response.ok)throw Error('Canonical Clear import returned '+response.status);
      const skin=await response.json();await loadPackage(skin);
      return {id:skin.manifest.id,fileCount:Object.keys(skin.files).length};
    })()`);
    if(loaded?.id!=="powercal-clear-default")throw Error("Canonical Clear package did not load");
    await waitFor(evaluate,"document.querySelector('#previewState')?.textContent.includes('连接成功')",
      "Preview Mock connection");
    await evaluate("document.querySelector('#webPreview').scrollIntoView({block:'center'})");
    let frame;
    for(let i=0;i<150&&!frame;i++){
      const tree=await call("Page.getFrameTree");
      frame=tree.frameTree.childFrames?.find(item=>item.frame.url.includes('/preview/'))?.frame;
      if(!frame)await delay(100);
    }
    if(!frame)throw Error("Studio Preview iframe was not created");
    const frameEval=async expression=>{
      for(let attempt=0;attempt<30;attempt++){
        const context=[...contexts.values()].filter(item=>
          item.auxData?.frameId===frame.id&&item.auxData?.isDefault).reverse()[0];
        if(context){
          try{return await evaluate(expression,{contextId:context.id})}
          catch(error){
            if(!/Cannot find context with specified id|Cannot find execution context/i.test(error.message))
              throw error;
            contexts.delete(context.id);
          }
        }
        await delay(50);
      }
      throw Error("Preview execution context unavailable");
    };
    await waitFor(frameEval,"Boolean(document.querySelector('#settingsPage'))","Clear Settings DOM");
    await frameEval(`document.querySelector('.clear-tabbar [data-action="settings"]').click()`);
    await waitFor(evaluate,"mockPage==='SETTINGS'&&lastState?.page==='SETTINGS'","Mock Settings route");
    await waitFor(frameEval,`!document.querySelector('#settingsPage').hidden&&
      document.querySelector('#notificationPermissionStatus').textContent.includes('BLOCKED')`,
      "Mock permission state projection");
    const deniedText=await frameEval("document.querySelector('#notificationPermissionStatus').textContent");
    await waitFor(frameEval,`document.querySelector('#exactAlarmPermissionStatus').textContent.includes('BLOCKED')&&
      !document.querySelector('[data-action="openExactAlarmSettings"]').hidden`,
      "Mock exact-alarm permission state projection");
    const exactDeniedText=await frameEval("document.querySelector('#exactAlarmPermissionStatus').textContent");
    await waitFor(frameEval,`document.querySelector('#reminderDiagnostics').innerText.includes('Studio Mock 提醒诊断')&&
      document.querySelector('#reminderDiagnostics').innerText.includes('最近执行：MISSED')`,
      "Mock reminder diagnostics projection");
    const reminderDiagnosticText=await frameEval("document.querySelector('#reminderDiagnostics').innerText");
    await waitFor(frameEval,`document.querySelector('#systemDiagnostics').innerText.includes('下一个事件序号')&&
      document.querySelector('#systemDiagnostics').innerText.includes('#0045')&&
      document.querySelector('#systemDiagnostics').innerText.includes('v5 · powercal-v2.db')&&
      document.querySelector('#systemDiagnostics').innerText.includes('分层玻璃')&&
      document.querySelector('#systemDiagnostics').getAttribute('role')==='list'&&
      document.querySelector('#systemDiagnostics').innerText.includes('重复 #4')`,
      "Mock system diagnostics projection");
    const systemDiagnosticText=await frameEval("document.querySelector('#systemDiagnostics').innerText");
    await waitFor(frameEval,`document.querySelectorAll('#skinChoices [data-action="selectSkin"]').length===3&&
      document.querySelector('#skinChoices [data-skin-id="powercal-clear-default"]')?.disabled===true`,
      "Mock installed-skin catalogue");
    await frameEval(`document.querySelector('#skinChoices [data-skin-id="powercal-default"]').click()`);
    await waitFor(evaluate,"mockApproval?.kind==='skinActivation'&&lastState?.selectedSkinId==='powercal-clear-default'",
      "Mock Host skin confirmation");
    await evaluate("document.querySelector('#mockReject').click()");
    await waitFor(evaluate,"mockApproval===null&&lastState?.selectedSkinId==='powercal-clear-default'",
      "Mock skin selection cancellation");
    await frameEval(`document.querySelector('#skinChoices [data-skin-id="powercal-default"]').click()`);
    await waitFor(evaluate,"mockApproval?.kind==='skinActivation'","second Mock skin confirmation");
    await evaluate("document.querySelector('#mockApprove').click()");
    await waitFor(evaluate,"mockApproval===null&&lastState?.selectedSkinId==='powercal-default'",
      "Mock skin selection approval");
    await frameEval(`document.querySelector('#importSkinButton').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('不会打开文件选择器或安装皮肤')",
      "Mock skin import action");
    await frameEval(`document.querySelector('#exportClearSkinButton').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('不会打开 SAF 或写入文件')",
      "Mock Clear package export action");
    await frameEval(`document.querySelector('[data-action="exportInstalledSkin"][data-skin-id="studio-preview-fixture"]').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('已安装皮肤导出请求')",
      "Mock installed package export action");
    await frameEval(`document.querySelector('[data-action="deleteSkin"][data-skin-id="studio-preview-fixture"]').click()`);
    await waitFor(evaluate,"mockApproval?.kind==='skinDelete'","Mock installed package delete confirmation");
    await evaluate("document.querySelector('#mockReject').click()");
    await waitFor(evaluate,"mockApproval===null&&lastState?.skinChoices.some(item=>item.id==='studio-preview-fixture')",
      "Mock skin deletion cancellation");
    await frameEval(`document.querySelector('[data-action="deleteSkin"][data-skin-id="studio-preview-fixture"]').click()`);
    await waitFor(evaluate,"mockApproval?.kind==='skinDelete'","second Mock installed package delete confirmation");
    await evaluate("document.querySelector('#mockApprove').click()");
    await waitFor(evaluate,"mockApproval===null&&!lastState?.skinChoices.some(item=>item.id==='studio-preview-fixture')",
      "Mock skin deletion approval");
    await frameEval(`document.querySelector('[data-action="requestNotificationPermission"]').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('不会调用 Android 系统权限')",
      "Mock permission action handling");
    await frameEval(`document.querySelector('[data-action="openExactAlarmSettings"]').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('不会启动 Android 系统设置')",
      "Mock exact-alarm settings action");
    await frameEval(`document.querySelector('[data-action="reconcileReminders"]').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('不会调用 Android Host 或修改真实调度')",
      "Mock reminder reconciliation action");
    await frameEval(`document.querySelector('#openAiSettings').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('此页面不启动 Android Host')",
      "Mock protected AI settings route");
    await frameEval(`document.querySelector('#nativeRecoverySettings').click()`);
    await waitFor(evaluate,"document.querySelector('#bridgeLog').textContent.includes('不启动 Android Host')",
      "Mock native recovery route");
    const result={
      status:"PASS",
      scope:"Chrome/CDP Skin Studio Preview Mock; no Android permission or Native settings launched",
      package:loaded,
      page:await evaluate("mockPage"),
      deniedText,
      exactDeniedText,
      reminderDiagnosticText,
      systemDiagnosticText,
      skinChoiceIds:(await evaluate("lastState.skinChoices.map(item=>item.id)")),
      mockSelectedSkinId:await evaluate("lastState.selectedSkinId"),
      previewFixtureDeleted:await evaluate("!mockPreviewSkinInstalled"),
      mockLog:(await evaluate("document.querySelector('#bridgeLog').innerText")).split("\n").slice(0,5)
    };
    const resultPath=path.join(reportDir,`studio-mock-${runId}.json`);
    fs.writeFileSync(resultPath,JSON.stringify(result,null,2)+"\n");
    process.stdout.write(JSON.stringify({resultPath,result},null,2)+"\n");
  }finally{
    if(socket)socket.close();
    if(browser&&!browser.killed)browser.kill();
    if(!server.killed)server.kill();
  }
}

main().catch(error=>{process.stderr.write((error.stack||String(error))+"\n");process.exitCode=1});
