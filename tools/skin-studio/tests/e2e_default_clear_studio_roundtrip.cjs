/* Studio GUI proof for editing, saving, rebuilding and restoring canonical Clear. */
const {spawn} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const {createHash} = require("node:crypto");
const assert = require("node:assert/strict");

const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.POWERCAL_E2E_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/S-07-20261104"));
const downloadDir = path.join(reportDir, "studio-downloads-" + process.pid);
const assetPath = path.join(root, "app/src/main/assets/powercal-clear-default.pcskin");
const oldLabel = "打开 AI 配置与隐私设置";
const editedLabel = oldLabel + " · Studio 往返校验";
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const sha = bytes => createHash("sha256").update(bytes).digest("hex");
const stable = value => JSON.stringify(value, (_key, item) => {
  if (!item || typeof item !== "object" || Array.isArray(item)) return item;
  return Object.fromEntries(Object.keys(item).sort().map(key => [key, item[key]]));
});

async function main() {
  if (!fs.existsSync(chrome)) throw Error("Chrome not found: " + chrome);
  if (!fs.existsSync(assetPath)) throw Error("Canonical Clear package missing");
  fs.mkdirSync(downloadDir, {recursive:true});
  const profile = fs.mkdtempSync(path.join(reportDir, "studio-profile-"));
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
      const requestId=++id;pending.set(requestId,{resolve,reject,method});socket.send(JSON.stringify({id:requestId,method,params}));
    });
    const evaluate=async expression=>{
      const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
      if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description||result.exceptionDetails.text);
      return result.result.value;
    };
    const waitFor=async(predicate,message)=>{
      for(let n=0;n<300;n++){try{if(await predicate())return}catch{}await delay(100)}
      throw Error(message);
    };
    const mouseClick=async selector=>{
      await evaluate(`document.querySelector(${JSON.stringify(selector)})?.scrollIntoView({block:'center'})`);
      const doc=await call("DOM.getDocument",{depth:-1});
      const node=await call("DOM.querySelector",{nodeId:doc.root.nodeId,selector});
      if(!node.nodeId)throw Error("Studio GUI target missing: "+selector);
      const quad=(await call("DOM.getBoxModel",{nodeId:node.nodeId})).model.content;
      const x=(quad[0]+quad[2]+quad[4]+quad[6])/4,y=(quad[1]+quad[3]+quad[5]+quad[7])/4;
      await call("Input.dispatchMouseEvent",{type:"mouseMoved",x,y});
      await call("Input.dispatchMouseEvent",{type:"mousePressed",x,y,button:"left",clickCount:1});
      await call("Input.dispatchMouseEvent",{type:"mouseReleased",x,y,button:"left",clickCount:1});
    };
    const setField=(selector,value)=>evaluate(`(()=>{
      const e=document.querySelector(${JSON.stringify(selector)});if(!e)throw Error('missing '+${JSON.stringify(selector)});
      e.value=${JSON.stringify(value)};e.dispatchEvent(new Event('input',{bubbles:true}));
      e.dispatchEvent(new Event('change',{bubbles:true}));return true;
    })()`);
    const setPackageFile=async(selector,file)=>{
      const doc=await call("DOM.getDocument",{depth:-1});
      const node=await call("DOM.querySelector",{nodeId:doc.root.nodeId,selector});
      if(!node.nodeId)throw Error("Studio file input missing: "+selector);
      await call("DOM.setFileInputFiles",{files:[file],nodeId:node.nodeId});
      // CDP's setFileInputFiles emits the input/change sequence itself.
    };
    const capture=async name=>{
      const shot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
      fs.writeFileSync(path.join(reportDir,name),Buffer.from(shot.data,"base64"));
    };
    const waitDownload=async name=>{
      const file=path.join(downloadDir,name);
      await waitFor(()=>fs.existsSync(file)&&!fs.existsSync(file+".crdownload"),"Studio download missing: "+name);
      return file;
    };
    const snapshot=()=>evaluate(`(async()=>({
      manifest:JSON.parse(JSON.stringify(manifest)),
      projectBytes:sourceFiles.get('project.json').length,
      projectObject:JSON.parse(getText('project.json')),
      files:await Promise.all([...sourceFiles].sort(([a],[b])=>a.localeCompare(b)).map(async([name,bytes])=>({
        name,size:bytes.length,sha256:Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)))
          .map(value=>value.toString(16).padStart(2,'0')).join('')
      }))),
      indexHtml:getText('web/index.html')
    }))()`);
    const validatePackage=async file=>{
      const bytes=fs.readFileSync(file);
      const response=await fetch(origin+"/api/validate-v3",{method:"POST",
        headers:{"Content-Type":"application/octet-stream"},body:bytes});
      const receipt=await response.json();
      assert.equal(response.ok,true,JSON.stringify(receipt));
      assert.equal(receipt.ok,true,JSON.stringify(receipt));
      return {bytes,receipt};
    };
    const inspectPackage=async bytes=>{
      const response=await fetch(origin+"/api/import-v3",{method:"POST",
        headers:{"Content-Type":"application/octet-stream"},body:bytes});
      const result=await response.json();
      assert.equal(response.ok,true,JSON.stringify(result));
      assert.equal(result.ok,true,JSON.stringify(result));
      return result;
    };
    const installImportProbe=()=>evaluate(`(()=>{
      const zip=document.querySelector('#importZip'),projectInput=document.querySelector('#importProject');
      const originalZip=zip?.onchange,originalProject=projectInput?.onchange;
      if(typeof originalZip!=='function'||typeof originalProject!=='function')throw Error('Studio import handler unavailable');
      if(window.__s07ImportProbeInstalled)return true;
      window.__s07ImportCount=0;window.__s07ImportStartCount=0;window.__s07ProjectImportCount=0;
      window.__s07ImportProbeInstalled=true;
      zip.onchange=async event=>{window.__s07ImportStartCount++;try{return await originalZip.call(zip,event)}
        finally{window.__s07ImportCount++}};
      projectInput.onchange=async event=>{try{return await originalProject.call(projectInput,event)}
        finally{window.__s07ProjectImportCount++}};
      return true;
    })()`);

    await call("Runtime.enable");await call("Page.enable");await call("DOM.enable");
    await call("Page.setDownloadBehavior",{behavior:"allow",downloadPath:downloadDir});
    await waitFor(()=>evaluate("document.readyState==='complete'&&typeof loadPackage==='function'&&!!manifest?.id"),
      "Studio did not load its factory template");
    const template=await snapshot();
    const canonicalBytes=fs.readFileSync(assetPath);
    const canonical=await inspectPackage(canonicalBytes);
    assert.equal(canonical.manifest.id,"powercal-clear-default");
    assert.deepEqual(canonical.manifest.capabilities,["host.read","host.action"]);
    const canonicalFiles=Object.entries(canonical.files).sort(([a],[b])=>a.localeCompare(b))
      .map(([name,base64])=>({name,size:Buffer.from(base64,"base64").length,
        sha256:sha(Buffer.from(base64,"base64"))}));
    assert.deepEqual(template.files.map(file=>[file.name,file.size,file.sha256]),
      canonicalFiles.map(file=>[file.name,file.size,file.sha256]),
      "Studio factory template must match the canonical APK asset file-for-file");
    await installImportProbe();

    await setPackageFile("#importZip",assetPath);
    await waitFor(()=>evaluate("window.__s07ImportCount===1"),"Studio GUI import of canonical .pcskin did not complete").catch(async error=>{
      const state=await evaluate("({starts:window.__s07ImportStartCount,done:window.__s07ImportCount,id:manifest?.id,count:sourceFiles?.size,toast:document.querySelector('#toast')?.textContent,selected:document.querySelector('#importZip')?.files?.length})");
      throw Error(error.message+"; state="+JSON.stringify(state));
    });
    const imported=await snapshot();
    assert.equal(imported.files.length,9);
    assert.deepEqual(imported.files.map(file=>file.name),template.files.map(file=>file.name));
    assert.deepEqual(imported.files.map(file=>[file.name,file.size,file.sha256]),canonicalFiles.map(file=>[file.name,file.size,file.sha256]));
    assert.equal(imported.indexHtml.split(oldLabel).length-1,1,"expected one editable Settings label");
    assert.ok(imported.files.some(file=>file.name==="web/fonts/InterVariable.woff2"));

    // Studio intentionally opens a factory identity as a custom editable copy.
    // Restore the canonical identity explicitly through its GUI fields for this maintenance roundtrip.
    await setField("#skinId","powercal-clear-default");
    await setField("#skinName",canonical.manifest.name);
    await mouseClick("#advancedMode");
    const editedHtml=imported.indexHtml.replace(oldLabel,editedLabel);
    await setField("#htmlCode",editedHtml);
    await waitFor(()=>evaluate(`getText('web/index.html').includes(${JSON.stringify(editedLabel)})`),
      "Studio source editor did not apply the reversible Settings label edit");
    await capture("s07-settings-edited-in-studio.png");

    await mouseClick("#exportProject");
    const projectPath=await waitDownload("powercal-clear-default.pcstudio-v3.json");
    const savedProject=JSON.parse(fs.readFileSync(projectPath,"utf8"));
    assert.equal(savedProject.manifest.id,canonical.manifest.id);
    assert.equal(savedProject.manifest.name,canonical.manifest.name);
    await call("Page.reload");
    await waitFor(()=>evaluate("document.readyState==='complete'&&!!manifest?.id"),"Studio did not reopen after save");
    await installImportProbe();
    await setPackageFile("#importProject",projectPath);
    await waitFor(()=>evaluate("window.__s07ProjectImportCount===1"),
      "Saved Studio project did not reopen with the edit");
    assert.ok(await evaluate(`getText('web/index.html').includes(${JSON.stringify(editedLabel)})`));
    await setField("#skinId","powercal-clear-default");
    await setField("#skinName",canonical.manifest.name);
    await mouseClick("#exportPack");
    let editedPackagePath=await waitDownload("powercal-clear-default.pcskin");
    const editedPackageSaved=path.join(reportDir,"s07-edited-settings.pcskin");
    fs.copyFileSync(editedPackagePath,editedPackageSaved);
    fs.renameSync(editedPackagePath,path.join(downloadDir,"edited-settings.pcskin"));
    const editedBuilt=await validatePackage(editedPackageSaved);
    assert.equal(editedBuilt.receipt.packageSha256,sha(editedBuilt.bytes));
    assert.deepEqual(editedBuilt.receipt.requiredCapabilities,["host.read","host.action"]);
    const editedPackage=await inspectPackage(editedBuilt.bytes);
    assert.equal(editedPackage.manifest.id,"powercal-clear-default");
    assert.equal(editedPackage.manifest.name,canonical.manifest.name);
    assert.deepEqual(editedPackage.manifest.capabilities,["host.read","host.action"]);

    await installImportProbe();
    await setPackageFile("#importZip",editedPackageSaved);
    await waitFor(()=>evaluate("window.__s07ImportCount===1"),
      "Studio did not re-import its edited default package");
    await mouseClick("#advancedMode");
    const edited=await snapshot();
    assert.ok(edited.indexHtml.includes(editedLabel));
    const beforeFiles=new Map(imported.files.map(file=>[file.name,file]));
    const changedFiles=edited.files.filter(file=>{
      const before=beforeFiles.get(file.name);return !before||before.size!==file.size||before.sha256!==file.sha256;
    }).map(file=>file.name);
    assert.deepEqual(changedFiles,["web/index.html"],"only the selected Settings source should change");
    assert.deepEqual(editedPackage.manifest.capabilities,["host.read","host.action"]);
    await capture("s07-settings-edited-package-reimported.png");

    await setField("#skinId","powercal-clear-default");
    await setField("#skinName",canonical.manifest.name);
    await setField("#htmlCode",imported.indexHtml);
    await waitFor(()=>evaluate(`getText('web/index.html')===${JSON.stringify(imported.indexHtml)}`),
      "Studio could not restore the exact original Settings source");
    await mouseClick("#validate");
    await waitFor(()=>evaluate("document.querySelector('#packageInspectionOutput')?.textContent.includes('packageSha256')"),
      "Studio formal build validation did not complete");
    await mouseClick("#exportPack");
    const finalDownloaded=await waitDownload("powercal-clear-default.pcskin");
    const restoredPackagePath=path.join(reportDir,"s07-restored-default.pcskin");
    fs.copyFileSync(finalDownloaded,restoredPackagePath);
    const restoredBuilt=await validatePackage(restoredPackagePath);
    assert.deepEqual(restoredBuilt.receipt.requiredCapabilities,["host.read","host.action"]);
    const restoredPackage=await inspectPackage(restoredBuilt.bytes);
    assert.deepEqual(stable(restoredPackage.manifest),stable(canonical.manifest));
    assert.deepEqual(Object.entries(restoredPackage.files).sort(([a],[b])=>a.localeCompare(b))
      .map(([name,base64])=>[name,sha(Buffer.from(base64,"base64"))]),
      canonicalFiles.map(file=>[file.name,file.sha256]));

    await setField("#skinId","powercal-clear-default");
    await setField("#skinName",canonical.manifest.name);
    await installImportProbe();
    await setPackageFile("#importZip",restoredPackagePath);
    await waitFor(()=>evaluate("window.__s07ImportCount===2"),
      "Studio did not re-import the restored package");
    const restored=await snapshot();
    assert.deepEqual(restored.files.map(file=>[file.name,file.size,file.sha256]),
      canonicalFiles.map(file=>[file.name,file.size,file.sha256]),
      "restored package files must match the imported factory source byte-for-byte");
    await capture("s07-settings-restored-in-studio.png");

    const summary={
      canonicalPackageSha256:sha(fs.readFileSync(assetPath)),
      canonicalId:canonical.manifest.id,canonicalName:canonical.manifest.name,
      capabilities:canonical.manifest.capabilities,sourceFileCount:imported.files.length,
      studioTemplateAndImportedFilesIdentical:true,
      projectSavedAndReopened:true,
      editedPackageSha256:editedBuilt.receipt.packageSha256,
      modifiedSourceFiles:changedFiles,
      restoredPackageSha256:restoredBuilt.receipt.packageSha256,
      restoredAllSourceFileBytes:true,
      restoredManifestEqual:stable(restoredPackage.manifest)===stable(canonical.manifest),
      projectMetadataPreserved:stable(restored.projectObject)===stable(imported.projectObject),
      outputPackages:[editedPackageSaved,restoredPackagePath],
      screenshots:["s07-settings-edited-in-studio.png","s07-settings-edited-package-reimported.png",
        "s07-settings-restored-in-studio.png"]
    };
    fs.writeFileSync(path.join(reportDir,"s07-studio-roundtrip.json"),JSON.stringify(summary,null,2)+"\n");
    console.log(JSON.stringify(summary,null,2));
  } finally {
    try{socket?.close()}catch{}
    try{browser?.kill()}catch{}
    try{server.kill()}catch{}
  }
}

main().catch(error=>{console.error(error.stack||error);process.exitCode=1});
