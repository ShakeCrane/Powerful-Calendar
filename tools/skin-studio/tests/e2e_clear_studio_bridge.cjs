/* Browser Studio authors a real Clear page/control, then exports and reopens it. */
const {spawn} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const crypto = require("node:crypto");

const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.POWERCAL_STUDIO_CLEAR_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/sep15-studio-clear-bridge-20261031"));
const outputPath = path.resolve(process.env.POWERCAL_STUDIO_CLEAR_PACKAGE_OUT ||
  path.join(reportDir, "clear-studio-authored-bridge.pcskin"));
const sourcePath = path.join(root, "app/src/main/assets/powercal-clear-default.pcskin");
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const sha256 = bytes => crypto.createHash("sha256").update(bytes).digest("hex");
const stopChild = async child => {
  if (!child || child.exitCode !== null) return;
  await new Promise(resolve => {
    const timer=setTimeout(resolve,4000);
    child.once("exit",()=>{clearTimeout(timer);resolve();});
    child.kill();
  });
};

async function main() {
  fs.mkdirSync(reportDir, {recursive:true});
  const profile = fs.mkdtempSync(path.join(reportDir, "chrome-profile-"));
  const server = spawn("python", ["tools/skin-studio/skin_studio.py", "serve", "--port", "0"],
    {cwd:root, stdio:["ignore", "pipe", "pipe"]});
  let browser, socket;
  try {
    const line = await Promise.race([
      new Promise((resolve, reject) => {
        readline.createInterface({input:server.stdout}).once("line", resolve);
        server.once("error", reject);
        server.once("exit", code => reject(Error("Studio exited " + code)));
      }),
      delay(15000).then(() => {throw Error("Studio startup timed out");})
    ]);
    const origin = line.match(/http:\/\/127\.0\.0\.1:\d+/)?.[0];
    if (!origin) throw Error("Studio did not report a local URL: " + line);
    browser = spawn(chrome, ["--headless=new", "--disable-gpu", "--no-sandbox",
      "--disable-background-networking", "--disable-site-isolation-trials",
      "--disable-features=IsolateOrigins,site-per-process", "--no-first-run",
      "--no-default-browser-check", "--remote-allow-origins=*", "--remote-debugging-port=0",
      "--user-data-dir=" + profile, "about:blank"], {stdio:"ignore"});
    const portFile = path.join(profile, "DevToolsActivePort");
    for (let i=0; i<150 && !fs.existsSync(portFile); i++) {
      if (browser.exitCode !== null) throw Error("Chrome exited " + browser.exitCode);
      await delay(100);
    }
    if (!fs.existsSync(portFile)) throw Error("Chrome DevTools startup timed out");
    const port = fs.readFileSync(portFile, "utf8").split(/\r?\n/)[0];
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,
      {method:"PUT"})).json();
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {socket.onopen=resolve; socket.onerror=reject;});
    let nextId=0;
    const pending=new Map();
    socket.onmessage=event=>{
      const message=JSON.parse(event.data);
      if (!message.id || !pending.has(message.id)) return;
      const {resolve,reject}=pending.get(message.id); pending.delete(message.id);
      message.error ? reject(Error(message.error.message)) : resolve(message.result);
    };
    const call=(method,params={})=>new Promise((resolve,reject)=>{
      const id=++nextId; pending.set(id,{resolve,reject}); socket.send(JSON.stringify({id,method,params}));
    });
    await call("Runtime.enable");
    let ready=false;
    for (let i=0; i<150 && !ready; i++) {
      const state=await call("Runtime.evaluate", {expression:
        "document.readyState==='complete' && typeof loadPackage==='function'", returnByValue:true});
      ready=state.result.value===true;
      if (!ready) await delay(100);
    }
    if (!ready) throw Error("Studio page startup timed out");

    const sourceBytes=fs.readFileSync(sourcePath);
    const originalPackageBase64=sourceBytes.toString("base64");
    const expression=`(async()=>{
      const check=(value,message)=>{if(!value)throw Error(message)};
      const decode=value=>Uint8Array.from(atob(value),c=>c.charCodeAt(0));
      const text=value=>new TextDecoder().decode(decode(value));
      const originalBytes=decode(${JSON.stringify(originalPackageBase64)});
      const importedResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:originalBytes});
      check(importedResponse.ok,'bundled Clear import failed: '+importedResponse.status);
      const original=await importedResponse.json();
      check(original.manifest.id==='powercal-clear-default','source is not bundled Clear');
      const capabilities=[...original.manifest.capabilities];
      await loadPackage(original);

      const id=document.querySelector('#skinId'), name=document.querySelector('#skinName');
      id.value='pc-clear-studio-bridge-proof'; id.dispatchEvent(new Event('change',{bubbles:true}));
      name.value='Clear Studio Bridge Proof'; name.dispatchEvent(new Event('change',{bubbles:true}));
      document.querySelector('#advancedMode').click();
      const html=document.querySelector('#htmlCode'), css=document.querySelector('#cssCode');
      check(html.value.includes('<section id="monthPage"'),'Clear month page source not loaded');
      check(html.value.includes('data-action="browseMonth"'),'existing month Bridge binding missing');
      const monthStart=html.value.indexOf('<div class="month-bar">');
      const monthBarEnd=html.value.indexOf('</div>',monthStart);
      check(monthStart>=0&&monthBarEnd>monthStart,'month page control group not found');
      const authoredButton='<button id="studioAuthoredMonthAdvance" class="studio-authored-month-advance" '+
        'type="button" data-action="browseMonth" data-delta-months="1" '+
        'aria-label="Studio 自定义下个月">Studio 下月</button>';
      html.value=html.value.slice(0,monthBarEnd)+authoredButton+html.value.slice(monthBarEnd);
      html.dispatchEvent(new Event('input',{bubbles:true}));
      css.value+='\\n#monthPage .studio-authored-month-advance{min-width:72px;padding-inline:8px}\\n';
      css.dispatchEvent(new Event('input',{bubbles:true}));

      const captureUiExport=async()=>{
        window.__powercalStudioCapturedBlob=null;
        if(!window.__powercalStudioCreateObjectUrl){
          window.__powercalStudioCreateObjectUrl=URL.createObjectURL.bind(URL);
          URL.createObjectURL=function(blob){window.__powercalStudioCapturedBlob=blob;
            return window.__powercalStudioCreateObjectUrl(blob)};
        }
        document.querySelector('#exportPack').click();
        for(let i=0;i<200&&!window.__powercalStudioCapturedBlob;i++)
          await new Promise(resolve=>setTimeout(resolve,50));
        check(window.__powercalStudioCapturedBlob instanceof Blob,
          'Studio export button did not create a package');
        const bytes=new Uint8Array(await window.__powercalStudioCapturedBlob.arrayBuffer());
        check(document.querySelector('#toast').textContent.includes('已导出'),
          'Studio did not report a successful export');
        return bytes;
      };
      const firstExport=await captureUiExport();
      const firstImportResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:firstExport});
      check(firstImportResponse.ok,'first Studio export could not be reopened');
      const firstRoundTrip=await firstImportResponse.json();
      check(firstRoundTrip.manifest.id==='pc-clear-studio-bridge-proof','Studio skin identity changed');
      check(JSON.stringify(firstRoundTrip.manifest.capabilities)===JSON.stringify(capabilities),
        'Studio changed the original closed Host capability list');
      check(text(firstRoundTrip.files['web/index.html']).includes('id="studioAuthoredMonthAdvance"'),
        'authored control was lost on Studio reopen');
      check(text(firstRoundTrip.files['web/style.css']).includes('.studio-authored-month-advance'),
        'authored style was lost on Studio reopen');

      await loadPackage(firstRoundTrip);
      const reopenedHtml=document.querySelector('#htmlCode');
      check(reopenedHtml.value.includes('Studio 下月'),'reopened source editor did not retain the control');
      reopenedHtml.value=reopenedHtml.value.replace('>Studio 下月</button>','>Studio 下一月</button>');
      reopenedHtml.dispatchEvent(new Event('input',{bubbles:true}));
      const finalBytes=await captureUiExport();
      const finalImportResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:finalBytes});
      check(finalImportResponse.ok,'final Studio export could not be reopened');
      const finalPackage=await finalImportResponse.json();
      check(finalPackage.manifest.id==='pc-clear-studio-bridge-proof','final identity mismatch');
      check(JSON.stringify(finalPackage.manifest.capabilities)===JSON.stringify(capabilities),
        'final export changed Host capabilities');
      check(finalPackage.manifest.fullCanvas===true,'final export changed Clear canvas mode');
      check(text(finalPackage.files['web/index.html']).includes(
        'id="studioAuthoredMonthAdvance" class="studio-authored-month-advance"')&&
        text(finalPackage.files['web/index.html']).includes('>Studio 下一月</button>'),
        'final HTML edit did not survive export/reimport');
      check(text(finalPackage.files['web/style.css']).includes('.studio-authored-month-advance'),
        'final CSS edit did not survive export/reimport');
      const expectedPaths=Object.keys(original.files).sort();
      check(JSON.stringify(Object.keys(finalPackage.files).sort())===JSON.stringify(expectedPaths),
        'Studio changed the Clear package file set');
      for(const [file,base64] of Object.entries(original.files)){
        if(file==='web/index.html'||file==='web/style.css')continue;
        check(finalPackage.files[file]===base64,'unmodified Clear source/resource changed: '+file);
      }
      let blobBinary='';for(const byte of finalBytes)blobBinary+=String.fromCharCode(byte);
      return {packageBase64:btoa(blobBinary),packageBytes:finalBytes.length,
        identity:finalPackage.manifest.id,name:finalPackage.manifest.name,
        capabilities:finalPackage.manifest.capabilities,fullCanvas:finalPackage.manifest.fullCanvas,
        sourceFiles:Object.keys(finalPackage.files).length,editedFiles:['web/index.html','web/style.css'],
        reopenedAndContinuedEdit:true,studioExportButton:true,
        authoredAction:{name:'browseMonth',deltaMonths:1,selector:'#studioAuthoredMonthAdvance'},
        untouchedFiles:Object.keys(original.files).filter(file=>!['web/index.html','web/style.css'].includes(file)).length};
    })()`;
    const evaluation=await call("Runtime.evaluate", {expression, awaitPromise:true, returnByValue:true});
    if (evaluation.exceptionDetails) throw Error(evaluation.exceptionDetails.exception?.description ||
      evaluation.exceptionDetails.text);
    const result=evaluation.result.value;
    const packageBytes=Buffer.from(result.packageBase64,"base64");
    if (packageBytes.length!==result.packageBytes) throw Error("export byte count mismatch");
    fs.mkdirSync(path.dirname(outputPath),{recursive:true});
    fs.writeFileSync(outputPath,packageBytes);
    const report={...result,packageBase64:undefined,sourcePath,
      sourceSha256:sha256(sourceBytes),exportPath:outputPath,exportSha256:sha256(packageBytes)};
    const reportPath=path.join(reportDir,"studio-clear-bridge-export.json");
    fs.writeFileSync(reportPath,JSON.stringify(report,null,2)+"\n");
    console.log("STUDIO CLEAR BRIDGE AUTHORING PASS",JSON.stringify(report));
  } finally {
    socket?.close();
    await stopChild(browser);
    await stopChild(server);
    if (fs.existsSync(profile)) {
      try { fs.rmSync(profile,{recursive:true,force:true}); }
      catch (error) { console.warn("Chrome profile cleanup deferred:",error.message); }
    }
  }
}

main().catch(error=>{console.error(error.stack||String(error));process.exitCode=1;});
