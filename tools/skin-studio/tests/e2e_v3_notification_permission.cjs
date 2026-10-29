/* Chrome/CDP replay for Clear's Host-owned notification permission entry. */
const {spawn}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),crypto=require("node:crypto");
const {pathToFileURL}=require("node:url");
const root=path.resolve(__dirname,"../../..");
const chrome=process.env.CHROME_PATH||"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const source=path.join(root,"tools/skin-studio/default-clear/source/web/index.html");
const reportDir=path.resolve(process.env.POWERCAL_E2E_REPORT_DIR||
  path.join(root,".local/android-test/reports/separation-s0-timeline-drag/notification-permission-cdp"));
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));

async function main(){
  fs.mkdirSync(reportDir,{recursive:true});
  const profile=fs.mkdtempSync(path.join(reportDir,"chrome-profile-"));
  const runId=new Date().toISOString().replace(/[:.]/g,"-");
  let browser,socket;
  try{
    browser=spawn(chrome,["--headless=new","--disable-gpu","--no-sandbox",
      "--disable-background-networking","--no-first-run","--no-default-browser-check",
      "--remote-allow-origins=*","--remote-debugging-port=0","--user-data-dir="+profile,
      pathToFileURL(source).href],{stdio:"ignore"});
    const portFile=path.join(profile,"DevToolsActivePort");
    let port;
    for(let i=0;i<150&&!port;i++){
      if(browser.exitCode!==null)throw Error("Chrome exited "+browser.exitCode);
      try{port=fs.readFileSync(portFile,"utf8").split(/\r?\n/)[0]||null}
      catch(error){if(error.code!=="ENOENT"&&error.code!=="EBUSY")throw error}
      if(!port)await delay(100);
    }
    if(!port)throw Error("Chrome startup timed out");
    const targets=await (await fetch(`http://127.0.0.1:${port}/json`)).json();
    const target=targets.find(item=>item.type==="page");
    if(!target)throw Error("No Chrome page target");
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
    const evaluate=async(expression)=>{
      const result=await call("Runtime.evaluate",{
        expression,returnByValue:true,awaitPromise:true
      });
      if(result.exceptionDetails)
        throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);
      return result.result.value;
    };
    await call("Runtime.enable");await call("Page.enable");
    const waitFor=async(expression,label)=>{
      for(let i=0;i<120;i++){
        if(await evaluate(expression))return;
        await delay(100);
      }
      throw Error(label+" timed out");
    };
    await waitFor("document.readyState==='complete'&&document.querySelector('#settingsPage')",
      "Clear source load");
    await evaluate(`(()=>{
      const pair=new MessageChannel();window.__clearActions=[];
      window.__clearHostPort=pair.port1;
      pair.port1.onmessage=event=>window.__clearActions.push(JSON.parse(event.data));
      pair.port1.start();window.postMessage('powercal-port','*',[pair.port2]);
      pair.port1.postMessage(JSON.stringify({version:1,type:'state',payload:{
        page:'TIMELINE',notificationPermission:{required:true,allowed:false},
        exactAlarmPermission:{required:true,allowed:false},
        timeline:{anchorMillis:1792800000000,zoneId:'Asia/Shanghai',hours:24,items:[]}
      }}));return true;
    })()`);

    await evaluate(`document.querySelector('.clear-tabbar [data-action="settings"]').click()`);
    await waitFor("window.__clearActions?.length===1","Settings route action");
    const route=await evaluate("window.__clearActions[0]");
    if(route?.payload?.name!=="settings")throw Error("Clear settings tab emitted the wrong route");
    await evaluate(`window.__clearHostPort.postMessage(JSON.stringify({version:1,type:'state',payload:{
      page:'SETTINGS',notificationPermission:{required:true,allowed:false},
      exactAlarmPermission:{required:true,allowed:false}
    }}))`);
    await waitFor(`!document.querySelector('#settingsPage').hidden&&
      document.querySelector('#notificationPermissionStatus').textContent.includes('BLOCKED')`,
      "Denied permission status");
    const deniedText=await evaluate("document.querySelector('#notificationPermissionStatus').textContent");
    await waitFor(`document.querySelector('#exactAlarmPermissionStatus').textContent.includes('BLOCKED')&&
      !document.querySelector('[data-action="openExactAlarmSettings"]').hidden`,
      "Denied exact-alarm status");
    const exactDeniedText=await evaluate("document.querySelector('#exactAlarmPermissionStatus').textContent");
    const screenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
    const screenshotPath=path.join(reportDir,`settings-${runId}.png`);
    fs.writeFileSync(screenshotPath,Buffer.from(screenshot.data,"base64"));
    await evaluate(`document.querySelector('[data-action="requestNotificationPermission"]').click()`);
    await waitFor("window.__clearActions?.length===2","Permission request action");
    const permissionRequest=await evaluate("window.__clearActions[1]");
    if(permissionRequest?.payload?.name!=="requestNotificationPermission")
      throw Error("Clear emitted the wrong notification permission action");
    await evaluate(`document.querySelector('[data-action="openExactAlarmSettings"]').click()`);
    await waitFor("window.__clearActions?.length===3","Exact alarm settings action");
    const exactAlarmRequest=await evaluate("window.__clearActions[2]");
    if(exactAlarmRequest?.payload?.name!=="openExactAlarmSettings")
      throw Error("Clear emitted the wrong exact alarm settings action");

    await evaluate(`window.__clearHostPort.postMessage(JSON.stringify({version:1,type:'state',payload:{
      page:'SETTINGS',notificationPermission:{required:true,allowed:true},
      exactAlarmPermission:{required:true,allowed:true}
    }}))`);
    await waitFor("document.querySelector('#notificationPermissionStatus').textContent.includes('已允许')",
      "Granted permission status");
    const grantedText=await evaluate("document.querySelector('#notificationPermissionStatus').textContent");
    await waitFor("document.querySelector('#exactAlarmPermissionStatus').textContent.includes('已允许')",
      "Granted exact-alarm status");
    const exactGrantedText=await evaluate("document.querySelector('#exactAlarmPermissionStatus').textContent");

    await evaluate(`window.__clearHostPort.postMessage(JSON.stringify({version:1,type:'state',payload:{
      page:'SETTINGS',notificationPermission:{required:false,allowed:true},
      exactAlarmPermission:{required:false,allowed:true}
    }}))`);
    await waitFor("document.querySelector('#notificationPermissionStatus').textContent.includes('无需运行时通知权限')",
      "Legacy Android permission status");
    const legacyText=await evaluate("document.querySelector('#notificationPermissionStatus').textContent");
    await waitFor(`document.querySelector('#exactAlarmPermissionStatus').textContent.includes('无需单独授权')&&
      document.querySelector('[data-action="openExactAlarmSettings"]').hidden`,
      "Legacy Android exact-alarm status");
    const exactLegacyText=await evaluate("document.querySelector('#exactAlarmPermissionStatus').textContent");

    await evaluate(`document.querySelector('[data-action="nativeSettings"]').click()`);
    await waitFor("window.__clearActions?.length===4","Native recovery route action");
    const recovery=await evaluate("window.__clearActions[3]");
    if(recovery?.payload?.name!=="nativeSettings")
      throw Error("Clear did not preserve its native settings recovery route");

    const assetPath=path.join(root,"app/src/main/assets/powercal-clear-default.pcskin");
    const result={
      status:"PASS",
      scope:"Chrome 156 Headless/CDP rendering of canonical Clear source with a protocol-level Mock Host; not Android WebView or OS permission UI",
      actions:[route.payload.name,permissionRequest.payload.name,
        exactAlarmRequest.payload.name,recovery.payload.name],
      deniedText,grantedText,legacyText,exactDeniedText,exactGrantedText,exactLegacyText,
      canonicalClearAssetSha256:crypto.createHash("sha256").update(fs.readFileSync(assetPath)).digest("hex"),
      screenshotPath:path.relative(root,screenshotPath).replaceAll("\\","/")
    };
    const resultPath=path.join(reportDir,`result-${runId}.json`);
    fs.writeFileSync(resultPath,JSON.stringify(result,null,2)+"\n");
    process.stdout.write(JSON.stringify({resultPath,result},null,2)+"\n");
  }finally{
    if(socket)socket.close();
    if(browser&&!browser.killed)browser.kill();
  }
}

main().catch(error=>{process.stderr.write((error.stack||String(error))+"\n");process.exitCode=1});
