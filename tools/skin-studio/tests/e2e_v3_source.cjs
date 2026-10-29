/* Real browser source edit, preview and package round trip. No npm dependencies. */
const {spawn} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.POWERCAL_E2E_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/separation-20261027/source-editor"));
const canonicalClearFiles=Object.fromEntries(["index.html","style.css","app.js","edit.js"].map(name=>[
  "web/"+name,fs.readFileSync(path.join(root,"tools/skin-studio/default-clear/source/web",name)).toString("base64")
]));
const androidDeliveryFixture=fs.readFileSync(path.join(root,
  "app/src/androidTest/assets/skin-delivery-fixture.pcskin")).toString("base64");
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
      "--disable-background-networking","--disable-site-isolation-trials",
      "--disable-features=IsolateOrigins,site-per-process","--no-first-run","--no-default-browser-check",
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
      const decodeBase64=value=>Uint8Array.from(atob(value),c=>c.charCodeAt(0));
      const sha256=async bytes=>[...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))]
        .map(value=>value.toString(16).padStart(2,'0')).join('');
      const canonical=value=>Array.isArray(value)?value.map(canonical):
        value&&typeof value==='object'?Object.fromEntries(Object.keys(value).sort()
          .map(key=>[key,canonical(value[key])])):value;
      const androidArchive=Uint8Array.from(atob("${androidDeliveryFixture}"),c=>c.charCodeAt(0));
      const androidImport=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:androidArchive});
      check(androidImport.ok,'Android-installed Studio fixture import failed: '+androidImport.status);
      const installedPackage=await androidImport.json();
      check(installedPackage.manifest.id==='pc-delivery-fixture','installed fixture identity changed');
      const installedMetadata={...installedPackage.manifest};delete installedMetadata.sha256;
      const installedFiles={...installedPackage.files};
      await loadPackage(installedPackage);
      const noEditPayload=payload();
      const noEditPayloadMetadata={...noEditPayload.manifest};delete noEditPayloadMetadata.sha256;
      check(JSON.stringify(canonical(noEditPayloadMetadata))===JSON.stringify(canonical(installedMetadata)),
        'no-edit browser import changed Manifest metadata or capabilities');
      const sourcePaths=Object.keys(installedFiles).sort();
      check(JSON.stringify(Object.keys(noEditPayload.files).sort())===JSON.stringify(sourcePaths),
        'no-edit browser import changed the package file set');
      const noEditPreviewResponse=await fetch('/api/preview-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(noEditPayload)});
      check(noEditPreviewResponse.ok,'no-edit browser preview failed');
      const noEditPreview=await noEditPreviewResponse.json();
      const noEditFileSha256={};
      for(const [name,base64] of Object.entries(installedFiles)){
        const expectedHash=await sha256(decodeBase64(base64));
        check(noEditPayload.files[name]===base64,'no-edit browser changed file bytes: '+name);
        check(noEditPayload.manifest.sha256[name]===expectedHash,
          'no-edit browser payload hash mismatch: '+name);
        noEditFileSha256[name]=expectedHash;
        if(name==='project.json')continue;
        const response=await fetch(noEditPreview.url.replace('web/index.html',name));
        check(response.ok,'no-edit preview did not serve '+name);
        const previewHash=await sha256(new Uint8Array(await response.arrayBuffer()));
        check(previewHash===expectedHash,'no-edit preview bytes differ from package: '+name);
      }
      const noEditZipResponse=await fetch('/api/export-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(noEditPayload)});
      check(noEditZipResponse.ok,'no-edit browser export failed');
      const noEditReimportResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:await noEditZipResponse.arrayBuffer()});
      check(noEditReimportResponse.ok,'no-edit browser export could not be re-imported');
      const noEditReimport=await noEditReimportResponse.json();
      const noEditReimportMetadata={...noEditReimport.manifest};delete noEditReimportMetadata.sha256;
      check(JSON.stringify(canonical(noEditReimportMetadata))===JSON.stringify(canonical(installedMetadata)),
        'no-edit browser export/reimport changed Manifest metadata or capabilities');
      check(JSON.stringify(Object.keys(noEditReimport.files).sort())===JSON.stringify(sourcePaths),
        'no-edit browser export/reimport changed the complete file set');
      for(const [name,base64] of Object.entries(installedFiles)){
        const actualHash=await sha256(decodeBase64(noEditReimport.files[name]));
        check(noEditReimport.files[name]===base64&&actualHash===noEditFileSha256[name],
          'no-edit browser export/reimport changed '+name);
        check(noEditReimport.manifest.sha256[name]===actualHash,
          'no-edit reimport Manifest hash mismatch: '+name);
      }
      document.querySelector('#advancedMode').click();
      const installedEditor=document.querySelector('#jsCode');
      const installedBefore=installedEditor.value;
      check(installedBefore.includes('studio-js-delivery-fixture-v1'),
        'installed fixture source contents differ');
      installedEditor.value=installedBefore.replace('studio-js-delivery-fixture-v1',
        'studio-js-delivery-fixture-v2');
      installedEditor.dispatchEvent(new Event('input',{bubbles:true}));
      const installedPayload=payload();
      check(installedPayload.files['web/app.js']!==installedFiles['web/app.js'],
        'installed-package source edit was not applied');
      const installedZip=await (await fetch('/api/export-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(installedPayload)})).arrayBuffer();
      const installedReimportResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:installedZip});
      check(installedReimportResponse.ok,'installed-package export reimport failed: '+
        installedReimportResponse.status);
      const installedReimport=await installedReimportResponse.json();
      const reimportedMetadata={...installedReimport.manifest};delete reimportedMetadata.sha256;
      check(JSON.stringify(reimportedMetadata)===JSON.stringify(installedMetadata),
        'installed package metadata or capabilities changed');
      check(installedReimport.files['web/app.js']===installedPayload.files['web/app.js'],
        'edited installed-package source was lost on reimport');
      for(const [name,bytes] of Object.entries(installedFiles))if(name!=='web/app.js')
        check(installedReimport.files[name]===bytes,'unmodified installed-package file changed: '+name);

      const partial=await (await fetch('/api/template-v3')).json();
      partial.manifest.id='partial-canvas-test';partial.manifest.fullCanvas=false;
      await loadPackage(partial);
      check(!document.querySelector('#fullCanvas').checked,'imported partial canvas flag was changed');
      const partialPayload=payload();
      check(partialPayload.manifest.fullCanvas===false,'partial canvas payload was rejected or changed');
      const partialExport=await fetch('/api/export-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(partialPayload)});
      check(partialExport.ok,'partial canvas export failed: '+partialExport.status);
      const partialZip=await partialExport.arrayBuffer();
      const partialImport=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:partialZip});
      check(partialImport.ok,'partial canvas reimport failed: '+partialImport.status);
      check((await partialImport.json()).manifest.fullCanvas===false,
        'partial canvas declaration changed during Studio export/reimport');

      const implicitCanvas=await (await fetch('/api/template-v3')).json();
      implicitCanvas.manifest.id='implicit-canvas-test';
      delete implicitCanvas.manifest.fullCanvas;
      const projectBytes=Uint8Array.from(atob(implicitCanvas.files['project.json']),c=>c.charCodeAt(0));
      const implicitProject=JSON.parse(new TextDecoder().decode(projectBytes));
      delete implicitProject.slots;
      const rewrittenProject=new TextEncoder().encode(JSON.stringify(implicitProject)+'\\n');
      implicitCanvas.files['project.json']=btoa(String.fromCharCode(...rewrittenProject));
      const digest=new Uint8Array(await crypto.subtle.digest('SHA-256',rewrittenProject));
      implicitCanvas.manifest.sha256['project.json']=[...digest].map(v=>v.toString(16).padStart(2,'0')).join('');
      const implicitZipResponse=await fetch('/api/export-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(implicitCanvas)});
      check(implicitZipResponse.ok,'Android-compatible implicit metadata fixture failed validation');
      const implicitImportedResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:await implicitZipResponse.arrayBuffer()});
      check(implicitImportedResponse.ok,'implicit metadata fixture import failed');
      const implicitImported=await implicitImportedResponse.json();
      await loadPackage(implicitImported);
      check(!document.querySelector('#fullCanvas').checked,
        'omitted fullCanvas must resolve to Android false semantics');
      const optionalPaths=[...document.querySelector('#extraSourceFile').options].map(option=>option.value);
      check(optionalPaths.includes('project.json'),
        'project.json with omitted slots should remain editable');
      const implicitPayload=payload();
      check(!Object.hasOwn(implicitPayload.manifest,'fullCanvas'),
        'Studio must preserve an omitted fullCanvas field');
      check(implicitPayload.files['project.json']===implicitImported.files['project.json'],
        'Studio must preserve project.json bytes when slots is omitted and unchanged');
      const projectSelector=document.querySelector('#extraSourceFile');
      projectSelector.value='project.json';projectSelector.dispatchEvent(new Event('change'));
      const optionalProjectEditor=document.querySelector('#extraSourceCode');
      const optionalProject=JSON.parse(optionalProjectEditor.value);
      optionalProject.vendorProjectField='retained-after-edit';
      optionalProjectEditor.value=JSON.stringify(optionalProject,null,2);
      optionalProjectEditor.dispatchEvent(new Event('input',{bubbles:true}));
      check(projectSourceError===null,'project without slots could not be structurally edited');
      const optionalProjectEdited=payload();
      const optionalProjectResult=JSON.parse(atob(optionalProjectEdited.files['project.json']));
      check(optionalProjectResult.vendorProjectField==='retained-after-edit'&&
        !Object.hasOwn(optionalProjectResult,'slots'),
        'edited project custom field or omitted optional slots were lost');
      const implicitExport=await fetch('/api/export-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(optionalProjectEdited)});
      check(implicitExport.ok,'implicit fullCanvas/project metadata export failed');
      const implicitRoundtrip=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:await implicitExport.arrayBuffer()});
      check(implicitRoundtrip.ok,'implicit fullCanvas/project metadata reimport failed');
      const implicitResult=await implicitRoundtrip.json();
      const reimportedProject=JSON.parse(atob(implicitResult.files['project.json']));
      check(!Object.hasOwn(implicitResult.manifest,'fullCanvas')&&
        reimportedProject.vendorProjectField==='retained-after-edit'&&
        !Object.hasOwn(reimportedProject,'slots'),
        'implicit manifest default or edited project fields changed during reimport');
      for(let i=0;i<150;i++){
        if(typeof loadPackage==='function' && document.querySelector('#skinName')?.value)break;
        await new Promise(r=>setTimeout(r,100));
      }
      // Continue editing the package that was imported, exported and re-imported above.
      // This ties the combined CRUD flow to an actual .pcskin package, not only a template object.
      const data=JSON.parse(JSON.stringify(installedReimport));
      Object.assign(data.files,${JSON.stringify(canonicalClearFiles)});
      data.manifest.id='restricted-source-test'; data.manifest.capabilities=['host.read'];
      data.files['web/agent-unused-90f4.txt']=btoa('unused source');
      data.files['assets/replace.bin']=btoa('old resource');
      const meta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(data.files['project.json']),c=>c.charCodeAt(0))));
      delete meta.layers;meta.thirdPartyMetadata={keep:'原样保留'};
      data.files['project.json']=btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(meta)+'\\n')));
      await loadPackage(data);document.querySelector('#advancedMode').click();
      const select=document.querySelector('#extraSourceFile');
      check([...select.options].some(o=>o.value==='web/edit.js'),'edit.js missing');
      check([...select.options].some(o=>o.value==='project.json'),'project.json missing from source editor');
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
      select.value='project.json';select.dispatchEvent(new Event('change'));
      const projectField=document.querySelector('#extraSourceCode');
      const editedMeta=JSON.parse(projectField.value);
      editedMeta.agentAuthoredComponents=[{id:'custom-panel',type:'container',children:[{id:'heading',type:'text'}]}];
      projectField.value=JSON.stringify(editedMeta,null,2);
      projectField.dispatchEvent(new Event('input',{bubbles:true}));
      const promptBefore=window.prompt,confirmBefore=window.confirm;
      window.prompt=()=> 'web/components/agent-card.js';window.confirm=()=>true;
      document.querySelector('#addTextSource').click();
      check([...select.options].some(o=>o.value==='web/components/agent-card.js'),'new source path missing');
      field.value="export const card = 'browser';\\n";
      field.dispatchEvent(new Event('input',{bubbles:true}));
      select.value='web/agent-unused-90f4.txt';select.dispatchEvent(new Event('change'));
      document.querySelector('#removeExtraSource').click();
      check(!sourceFiles.has('web/agent-unused-90f4.txt'),'unused source was not deleted');
      const packageInput=document.querySelector('#packageAssetFile');
      await packageInput.onchange({target:{files:[
        new File([new Uint8Array([0,255,65,66])],'agent-new.bin'),
        new File([new Uint8Array([255,0,128])],'replace.bin')
      ],value:'selected'}});
      check([...sourceFiles.get('assets/replace.bin')].join(',')==='255,0,128','binary replacement bytes changed');
      check([...sourceFiles.get('assets/agent-new.bin')].join(',')==='0,255,65,66','new binary resource bytes changed');
      const cssField=document.querySelector('#cssCode');
      cssField.value+="\\n.asset-ref{background:url('../assets/replace.bin')}\\n";
      cssField.dispatchEvent(new Event('input',{bubbles:true}));
      const assetRow=[...document.querySelectorAll('#assetList .entry')]
        .find(row=>row.textContent.includes('assets/replace.bin'));
      check(assetRow,'replacement asset missing from file list');assetRow.querySelector('button').click();
      check(sourceFiles.has('assets/replace.bin'),'referenced binary asset was deleted');
      select.value='web/edit.js';select.dispatchEvent(new Event('change'));
      document.querySelector('#removeExtraSource').click();
      check(sourceFiles.has('web/edit.js'),'referenced source file was deleted');
      window.prompt=promptBefore;window.confirm=confirmBefore;
      const authored=payload();
      check(JSON.stringify(authored.manifest.capabilities)==='["host.read"]',
        'authoring changed capabilities: '+JSON.stringify(authored.manifest.capabilities));
      const authoredMeta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(authored.files['project.json']),c=>c.charCodeAt(0))));
      check(authoredMeta.agentAuthoredComponents[0].id==='custom-panel','project structure edit was lost');
      check(atob(authored.files['web/components/agent-card.js']).includes('browser'),'new source file edit was lost');
      check(Array.from(atob(authored.files['assets/agent-new.bin']),c=>c.charCodeAt(0)).join(',')==='0,255,65,66',
        'new binary asset bytes changed');
      check(Array.from(atob(authored.files['assets/replace.bin']),c=>c.charCodeAt(0)).join(',')==='255,0,128',
        'replaced binary asset bytes changed');
      check(!Object.hasOwn(authored.files,'web/agent-unused-90f4.txt'),'deleted source file returned');
      const preview=await (await post('/api/preview-v3',JSON.stringify(authored),'application/json')).json();
      const previewText=await (await fetch(preview.url.replace('web/index.html','web/components/agent-card.js'))).text();
      check(previewText.includes('browser'),'preview source differs');
      const authoredCss=new TextDecoder().decode(decodeBase64(authored.files['web/style.css']));
      check(authoredCss.includes("url('../assets/replace.bin')"),
        'authored stylesheet resource reference is missing');
      check(Object.hasOwn(authored.files,'assets/replace.bin'),
        'referenced binary resource is missing from the complete package');
      const previewAssetResponse=await fetch(preview.url.replace('web/index.html','assets/replace.bin'));
      check(previewAssetResponse.ok,'preview did not serve the referenced binary resource');
      const previewAssetBytes=new Uint8Array(await previewAssetResponse.arrayBuffer());
      const authoredAssetBytes=decodeBase64(authored.files['assets/replace.bin']);
      check(await sha256(previewAssetBytes)===await sha256(authoredAssetBytes),
        'preview served bytes that differ from the referenced package asset');
      const zip=await (await post('/api/export-v3',JSON.stringify(authored),'application/json')).arrayBuffer();
      const imported=await (await post('/api/import-v3',zip,'application/octet-stream')).json();
      check(JSON.stringify(imported.manifest.capabilities)==='["host.read"]','import capabilities changed');
      const expectedManifest={...authored.manifest};delete expectedManifest.sha256;
      const actualManifest={...imported.manifest};delete actualManifest.sha256;
      check(JSON.stringify(canonical(actualManifest))===JSON.stringify(canonical(expectedManifest)),
        'export/reimport changed Manifest fields or package capabilities');
      const expectedPaths=Object.keys(authored.files).sort();
      const actualPaths=Object.keys(imported.files).sort();
      check(JSON.stringify(actualPaths)===JSON.stringify(expectedPaths),
        'export/reimport changed the complete package file set');
      const fileSha256={};
      for(const [name,bytes] of Object.entries(authored.files))
      {
        check(imported.files[name]===bytes,'round trip changed '+name);
        const expectedHash=await sha256(decodeBase64(bytes));
        const actualHash=await sha256(decodeBase64(imported.files[name]));
        check(actualHash===expectedHash,'round-trip SHA-256 mismatch: '+name);
        check(imported.manifest.sha256[name]===actualHash,
          're-imported Manifest hash does not match its file bytes: '+name);
        fileSha256[name]=actualHash;
      }
      check(!Object.hasOwn(imported.files,'web/agent-unused-90f4.txt'),
        'deleted source returned after export/reimport');
      check(new TextDecoder().decode(decodeBase64(imported.files['web/style.css']))
        .includes("url('../assets/replace.bin')"),
        'resource reference was lost after export/reimport');
      check(await sha256(decodeBase64(imported.files['assets/replace.bin']))===
        await sha256(authoredAssetBytes),'navigable asset changed after export/reimport');
      await loadPackage(imported);document.querySelector('#advancedMode').click();
      select.value='web/edit.js';select.dispatchEvent(new Event('change'));
      check(field.value===before+'\\n// browser-source-roundtrip\\n','reimport edit missing');
      select.value='project.json';select.dispatchEvent(new Event('change'));
      check(JSON.parse(projectField.value).agentAuthoredComponents[0].id==='custom-panel',
        'reimport project structure edit missing');
      document.querySelector('#simpleMode').click();window.prompt=()=> 'customShade';
      document.querySelector('#addMaterial').click();
      const visual=payload();
      const visualMeta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(visual.files['project.json']),c=>c.charCodeAt(0))));
      check(visualMeta.thirdPartyMetadata.keep==='原样保留','custom metadata lost');
      check(visualMeta.slots.some(s=>s.id==='customShade'),'visual metadata not saved');
      const visualZip=await (await post('/api/export-v3',JSON.stringify(visual),'application/json')).arrayBuffer();
      const visualImported=await (await post('/api/import-v3',visualZip,'application/octet-stream')).json();
      check(visualImported.files['project.json']===visual.files['project.json'],'visual metadata roundtrip changed');
      return {installedPackageReopenEditExportReimport:true,installedPackageId:'pc-delivery-fixture',
        noEditBrowserImportPreviewExportReimport:true,noEditPreviewedFiles:Object.keys(installedFiles).length-1,
        noEditFileSha256,
        fullCanvasFalseAndOmittedPreserved:true,optionalProjectSlotsPreserved:true,
        file:'web/edit.js', files:Object.keys(authored.files).length,
        capabilities:imported.manifest.capabilities,projectTree:true,newText:true,
        newBinary:true,replacedBinary:true,delete:true,referenceGuard:true,
        resourceReferencePreviewed:true,fileSha256};
    })()`;
    const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
    if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    console.log("BROWSER SOURCE ROUNDTRIP PASS",JSON.stringify(result.result.value));
    const setup=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      const previewPackage=payload();
      if(JSON.stringify(previewPackage.manifest.capabilities)!=='["host.read"]')
        throw Error('source roundtrip fixture unexpectedly gained write capabilities');
      // This second fixture exercises editing in the Mock Bridge. Keep the source
      // roundtrip fixture read-only, then grant only the capabilities this preview needs.
      previewPackage.manifest.capabilities=['host.read','host.action','host.edit'];
      await loadPackage(previewPackage);
      for(let i=0;i<150;i++){
        if(document.querySelector('#previewState').textContent.includes('连接成功'))break;
        await new Promise(r=>setTimeout(r,100));
      }
      if(!document.querySelector('#previewState').textContent.includes('连接成功'))throw Error('preview not connected');
      // Edit the actual AI screen source in Studio, preview it, then export and re-import
      // the same complete Clear package before exercising its batch controls below.
      document.querySelector('#advancedMode').click();
      const sourceEditor=document.querySelector('#jsCode');
      const aiSourceMarker='// studio-ai-batch-roundtrip-probe';
      if(sourceEditor.value.includes(aiSourceMarker))throw Error('AI source roundtrip marker already exists');
      sourceEditor.value+='\\n'+aiSourceMarker+'\\n';
      sourceEditor.dispatchEvent(new Event('input',{bubbles:true}));
      const editedSource=payload();
      const built=await fetch('/api/export-v3',{method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify(editedSource)});
      if(!built.ok)throw Error('Studio could not build edited AI source package: '+built.status);
      const archive=await built.arrayBuffer();
      const reimport=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:archive});
      if(!reimport.ok)throw Error('Studio could not re-import edited AI source package: '+reimport.status);
      const reimported=await reimport.json();
      const decodedAiSource=new TextDecoder().decode(Uint8Array.from(atob(reimported.files['web/app.js']),c=>c.charCodeAt(0)));
      if(!decodedAiSource.includes(aiSourceMarker))throw Error('AI source edit did not survive package re-import');
      await loadPackage(reimported);
      for(let i=0;i<150;i++){
        if(document.querySelector('#previewState').textContent.includes('连接成功'))break;
        await new Promise(r=>setTimeout(r,100));
      }
      if(!document.querySelector('#previewState').textContent.includes('连接成功'))
        throw Error('re-imported AI source package did not preview');
      const scenario=document.querySelector('#scenario');scenario.value='repeating';
      scenario.dispatchEvent(new Event('change',{bubbles:true}));
      document.querySelector('#interactMode').click();
      return true;
    })()`});
    if(setup.exceptionDetails)throw Error(setup.exceptionDetails.exception?.description || setup.exceptionDetails.text);
    await call("Page.enable");
    const tree=await call("Page.getFrameTree");
    const child=tree.frameTree.childFrames?.find(frame=>frame.frame.url.includes("/preview/"));
    if(!child)throw Error("Preview frame not found: "+JSON.stringify(tree.frameTree));
    const world=await call("Page.createIsolatedWorld",{frameId:child.frame.id,worldName:"powercal-source-qa"});
    const conversion=await call("Runtime.evaluate",{contextId:world.executionContextId,awaitPromise:true,returnByValue:true,expression:`(async()=>{
      const check=(v,m)=>{if(!v)throw Error(m)};
      const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
      document.querySelector('[data-action="createDraft"]').click();
      await wait(()=>document.querySelector('#timelineCreateDialog[open]'),'create chooser missing');
      document.querySelector('[data-timeline-create-kind="ACTIVITY"]').click();
      await wait(()=>!document.querySelector('#skinSheet').hidden&&
        document.querySelector('#sheetTitle').textContent==='待处理草稿','draft editor missing');
      const draftKind=document.querySelector('#sheetFields [data-field="kind"]');
      check(draftKind&&[...draftKind.options].map(option=>option.value).join(',')===
        'ALARM,ACTIVITY,DEADLINE,MEMO','draft type selector missing choices');
      draftKind.value='DEADLINE';draftKind.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('#sheetFields [data-field="kind"]')?.value==='DEADLINE'&&
        !document.querySelector('#sheetFields [data-field="startText"]'),'draft conversion did not update fields');
      check(document.querySelector('#sheetFields [data-field="endText"]').value==='2026-10-22 10:00',
        'Activity-to-Deadline preview did not retain the interval end');
      document.querySelector('#sheetClose').click();
      await wait(()=>document.querySelector('#skinSheet').hidden,'transient preview draft did not close');

      await wait(()=>document.querySelector('[data-original-occurrence-key]'),'repeating event missing');
      const event=document.querySelector('[data-original-occurrence-key]');
      const key=event.dataset.originalOccurrenceKey;event.click();
      await wait(()=>!document.querySelector('#skinSheet').hidden,'editor not opened');
      check(document.querySelector('#sheetSave').disabled,'save enabled before scope');
      check(!document.querySelector('#sheetFields [data-field="title"]'),'fields visible before scope');
      document.querySelector('[data-do="scope"][data-scope="SINGLE_OCCURRENCE"]').click();
      await wait(()=>document.querySelector('[data-scope="SINGLE_OCCURRENCE"][aria-pressed="true"]'),'scope not selected');
      check(!document.querySelector('#sheetFields [data-field="kind"]'),'single occurrence exposed type conversion');
      check(!document.querySelector('#sheetFields [data-field="notes"]'),'single scope exposed notes');
      const title=document.querySelector('#sheetFields [data-field="title"]');
      title.value='单次修改';title.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('[data-do="discardChanges"]'),'dirty state missing');
      check(document.querySelector('[data-scope="WHOLE_SERIES"]').disabled,'dirty scope switch enabled');
      document.querySelector('[data-do="discardChanges"]').click();
      await wait(()=>!document.querySelector('[data-do="discardChanges"]'),'discard did not clear dirty state');
      document.querySelector('[data-do="scope"][data-scope="WHOLE_SERIES"]').click();
      await wait(()=>document.querySelector('[data-scope="WHOLE_SERIES"][aria-pressed="true"]'),
        'whole-series scope not selected');
      const kind=document.querySelector('#sheetFields [data-field="kind"]');
      check(kind&&kind.value==='ACTIVITY','whole-series editor omitted type selector');
      const startBefore=document.querySelector('#sheetFields [data-field="startText"]').value;
      kind.value='ALARM';kind.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('#sheetFields [data-field="kind"]')?.value==='ALARM'&&
        !document.querySelector('#sheetFields [data-field="endText"]'),'kind patch did not rerender authoritative form');
      check(document.querySelector('#sheetFields [data-field="startText"]').value===startBefore,
        'Activity-to-Alarm conversion changed the start anchor');
      document.querySelector('#sheetSave').click();
      await wait(()=>document.querySelector('#sheetStatus').textContent.includes('宿主确认'),
        'formal conversion did not request mock Host confirmation');
      return {deckConversion:'ACTIVITY→DEADLINE',formalConversion:'ACTIVITY→ALARM',key};
    })()`});
    if(conversion.exceptionDetails)throw Error(conversion.exceptionDetails.exception?.description || conversion.exceptionDetails.text);
    const legacyOption=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        document.querySelector('.clear-tabbar [data-action="month"]').click();
        await wait(()=>!document.querySelector('#monthPage').hidden,'Month page did not open');
        document.querySelector('[data-local="options"]').click();
        await wait(()=>!document.querySelector('#extraOptions').hidden,'Month import options did not open');
        const option=document.querySelector('[data-action="invokeOption"][data-option-id="data.legacy.migrate"]');
        if(!option)throw Error('legacy migration option missing from Host option projection');
        option.click();
        return {monthImportOptions:true,legacyMigrationOption:true,clicked:true};
      })()`});
    if(legacyOption.exceptionDetails)throw Error(legacyOption.exceptionDetails.exception?.description || legacyOption.exceptionDetails.text);
    const icsExportOption=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        await wait(()=>!document.querySelector('#extraOptions').hidden,'data options did not remain open');
        const option=document.querySelector('[data-action="invokeOption"][data-option-id="calendar.export.ics"]');
        if(!option||!option.textContent.includes('导出 ICS'))throw Error('ICS export option missing');
        const detail=option.querySelector('.optionDescription')?.textContent||'';
        for(const term of ['活动启用/停用状态','活动提醒','闹钟','截止','备忘','原生备份'])
          if(!detail.includes(term))throw Error('ICS loss warning missing: '+term);
        option.click();
        return {visible:true,lossWarningVisible:true,clicked:true};
      })()`});
    if(icsExportOption.exceptionDetails)throw Error(icsExportOption.exceptionDetails.exception?.description || icsExportOption.exceptionDetails.text);
    const icsExportMock=await call("Runtime.evaluate",{expression:`(async()=>{
      for(let i=0;i<150;i++){
        if(document.querySelector('#bridgeLog').textContent.includes('ICS 导出请求仅模拟到达')){
          const log=document.querySelector('#bridgeLog').textContent;
          if(!log.includes('未创建文件或读取真实事件'))throw Error('ICS export Mock must disclose its non-writing boundary');
          return {hostRequestReceived:true,fileCreated:false,calendarRead:false};
        }
        await new Promise(r=>setTimeout(r,100));
      }
      throw Error('ICS export option did not reach Preview Mock Host');
    })()`,awaitPromise:true,returnByValue:true});
    if(icsExportMock.exceptionDetails)throw Error(icsExportMock.exceptionDetails.exception?.description || icsExportMock.exceptionDetails.text);
    console.log("BROWSER ICS EXPORT OPTION MOCK PASS",JSON.stringify(icsExportOption.result.value),JSON.stringify(icsExportMock.result.value));
    const backupExportOption=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        await new Promise(r=>setTimeout(r,100));
        const option=document.querySelector('[data-action="invokeOption"][data-option-id="data.backup.export"]');
        if(!option||!option.textContent.includes('导出原生备份'))throw Error('native backup option missing');
        const detail=option.querySelector('.optionDescription')?.textContent||'';
        for(const term of ['一致性快照','SHA-256','序号水位','迁移记录','AI 任务收据','不含 API 密钥','明文敏感数据','有界回读','未验证'])
          if(!detail.includes(term))throw Error('backup warning missing: '+term);
        option.click();
        return {visible:true,integrityAndPlaintextWarningVisible:true,clicked:true};
      })()`});
    if(backupExportOption.exceptionDetails)throw Error(backupExportOption.exceptionDetails.exception?.description || backupExportOption.exceptionDetails.text);
    const backupExportMock=await call("Runtime.evaluate",{expression:`(async()=>{
      for(let i=0;i<150;i++){
        if(document.querySelector('#bridgeLog').textContent.includes('原生备份导出请求仅模拟到达')){
          const log=document.querySelector('#bridgeLog').textContent;
          if(!log.includes('未创建文件或读取数据库'))throw Error('backup export Mock must disclose its non-writing boundary');
          return {hostRequestReceived:true,fileCreated:false,databaseRead:false,databaseWritten:false};
        }
        await new Promise(r=>setTimeout(r,100));
      }
      throw Error('native backup option did not reach Preview Mock Host');
    })()`,awaitPromise:true,returnByValue:true});
    if(backupExportMock.exceptionDetails)throw Error(backupExportMock.exceptionDetails.exception?.description || backupExportMock.exceptionDetails.text);
    console.log("BROWSER BACKUP EXPORT OPTION MOCK PASS",JSON.stringify(backupExportOption.result.value),JSON.stringify(backupExportMock.result.value));
    const backupRestoreOption=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const option=document.querySelector('[data-action="invokeOption"][data-option-id="data.backup.restore"]');
        if(!option||!option.textContent.includes('恢复原生备份'))throw Error('native backup restore option missing');
        const detail=option.querySelector('.optionDescription')?.textContent||'';
        for(const term of ['空数据库','SHA-256 完整性','哈希不证明来源可信','重新核对提醒调度',
          '权限状态','二次确认','旧授权','旧执行记录'])
          if(!detail.includes(term))throw Error('backup restore warning missing: '+term);
        option.click();
        return {visible:true,emptyDatabaseAndIntegrityLimitsVisible:true,clicked:true};
      })()`});
    if(backupRestoreOption.exceptionDetails)throw Error(backupRestoreOption.exceptionDetails.exception?.description || backupRestoreOption.exceptionDetails.text);
    const backupRestoreMock=await call("Runtime.evaluate",{expression:`(async()=>{
      for(let i=0;i<150;i++){
        if(document.querySelector('#bridgeLog').textContent.includes('原生备份恢复请求仅模拟到达')){
          const log=document.querySelector('#bridgeLog').textContent;
          if(!log.includes('未读取、写入或覆盖任何数据库'))throw Error('backup restore Mock must disclose its non-writing boundary');
          return {hostRequestReceived:true,fileRead:false,databaseRead:false,databaseWritten:false};
        }
        await new Promise(r=>setTimeout(r,100));
      }
      throw Error('native backup restore option did not reach Preview Mock Host');
    })()`,awaitPromise:true,returnByValue:true});
    if(backupRestoreMock.exceptionDetails)throw Error(backupRestoreMock.exceptionDetails.exception?.description || backupRestoreMock.exceptionDetails.text);
    console.log("BROWSER BACKUP RESTORE OPTION MOCK PASS",JSON.stringify(backupRestoreOption.result.value),JSON.stringify(backupRestoreMock.result.value));
    const legacyMock=await call("Runtime.evaluate",{expression:`(async()=>{
      for(let i=0;i<150;i++){
        if(document.querySelector('#bridgeLog').textContent.includes('迁移请求仅模拟到达')){
          const log=document.querySelector('#bridgeLog').textContent;
          if(!log.includes('未读取、写入或删除任何数据库'))throw Error('migration preview did not disclose non-writing behavior');
          return {hostRequestReceived:true,databaseRead:false,databaseWrite:false,databaseDelete:false};
        }
        await new Promise(r=>setTimeout(r,100));
      }
      throw Error('migration option did not reach Preview Mock Host');
    })()`,awaitPromise:true,returnByValue:true});
    if(legacyMock.exceptionDetails)throw Error(legacyMock.exceptionDetails.exception?.description || legacyMock.exceptionDetails.text);
    console.log("BROWSER LEGACY MIGRATION OPTION MOCK PASS",JSON.stringify(legacyOption.result.value),JSON.stringify(legacyMock.result.value));
    const cancelled=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<150;i++){
        const approval=document.querySelector('#mockApproval');
        if(approval&&!approval.hidden){
          if(!document.querySelector('#mockApprovalText').textContent.includes('不会写入真实数据'))
            throw Error('preview confirmation must be explicitly non-writing');
          document.querySelector('#mockReject').click();return true;
        }
        await new Promise(r=>setTimeout(r,100));
      }
      throw Error('mock Host confirmation did not open');
    })()`});
    if(cancelled.exceptionDetails)throw Error(cancelled.exceptionDetails.exception?.description || cancelled.exceptionDetails.text);
    const recurrence=await call("Runtime.evaluate",{contextId:world.executionContextId,awaitPromise:true,returnByValue:true,expression:`(async()=>{
      const check=(v,m)=>{if(!v)throw Error(m)};
      const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
      await wait(()=>document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作'),
        'mock Host cancellation did not return');
      check(!document.querySelector('#skinSheet').hidden&&
        document.querySelector('#sheetFields [data-field="kind"]').value==='ALARM',
        'cancelled conversion review lost the pending form');
      document.querySelector('[data-do="discardChanges"]').click();
      await wait(()=>!document.querySelector('[data-do="discardChanges"]'),
        'discard after cancelled conversion did not restore baseline');
      document.querySelector('[data-do="scope"][data-scope="THIS_AND_FUTURE"]').click();
      await wait(()=>document.querySelector('[data-scope="THIS_AND_FUTURE"][aria-pressed="true"]'),'future scope not selected');
      const future=document.querySelector('#sheetFields [data-field="title"]');
      future.value='后续修改';future.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('[data-do="discardChanges"]'),'future dirty state missing');
      document.querySelector('#sheetSave').click();
      await wait(()=>document.querySelector('#sheetStatus').textContent.includes('宿主确认'),'host review not requested');
      check(!document.querySelector('#skinSheet').hidden,'review request closed before approval');
      return {deckConversion:'ACTIVITY→DEADLINE',formalConversion:'ACTIVITY→ALARM',
        scope:'single hides conversion; whole-series requests cancellable Mock review; future'};
    })()`});
    if(recurrence.exceptionDetails)throw Error(recurrence.exceptionDetails.exception?.description || recurrence.exceptionDetails.text);
    const approval=await call("Runtime.evaluate",{expression:"!document.querySelector('#mockApproval').hidden && (document.querySelector('#mockApprove').click(),true)",returnByValue:true});
    if(approval.result.value!==true)throw Error("Mock host confirmation missing");
    const closed=await call("Runtime.evaluate",{contextId:world.executionContextId,awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<150;i++){if(document.querySelector('#skinSheet').hidden)return true;await new Promise(r=>setTimeout(r,100))}
      throw Error('approved future save did not close');
    })()`});
    if(closed.exceptionDetails)throw Error(closed.exceptionDetails.exception?.description || closed.exceptionDetails.text);
    console.log("BROWSER RECURRENCE MOCK PASS",JSON.stringify(recurrence.result.value));

    const memoOpen=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        document.querySelector('.clear-tabbar [data-action="memo"]').click();
        await wait(()=>!document.querySelector('#memoPage').hidden,'Memo page did not open');
        const rows=[...document.querySelectorAll('.memo-row')];
        check(rows.length===2,'repeating Memo fixture missing');
        let row=rows[0];
        const open=row.querySelector('[data-action="openEvent"]');
        const toggle=row.querySelector('[data-action="toggleMemo"]');
        check(open&&open.tagName==='BUTTON','Memo title lacks an accessible editor button');
        check(open.dataset.eventSeq==='31'&&open.dataset.revision==='2'&&
          open.dataset.originalOccurrenceKey==='2026-10-22T00:00',
          'Memo opener did not preserve the host-projected original occurrence');
        check(toggle&&toggle!==open&&toggle.dataset.occurrenceKey==='2026-10-22T00:00',
          'completion control was merged with the editor action');
        open.click();
        await wait(()=>!document.querySelector('#skinSheet').hidden&&
          !!document.querySelector('[data-do="scope"][data-scope="SINGLE_OCCURRENCE"]'),
          'Memo editor did not open with a selectable occurrence scope');
        document.querySelector('[data-do="scope"][data-scope="SINGLE_OCCURRENCE"]').click();
        await wait(()=>document.querySelector('[data-scope="SINGLE_OCCURRENCE"][aria-pressed="true"]'),
          'Memo occurrence scope did not become active');
        check(document.querySelector('#sheetSubtitle').textContent.includes('MEMO')&&
          !document.querySelector('#sheetFields [data-field="kind"]'),
          'Memo row did not open a same-kind editor with recurrence conversion disabled');
        check(document.querySelector('#sheetFields [data-field="startText"]').value==='2026-10-22',
          'editor did not retain the exact selected Memo date');
        document.querySelector('#sheetClose').click();
        await wait(()=>document.querySelector('#skinSheet').hidden,'Memo editor did not close');
        row=document.querySelector('.memo-row');
        row.querySelector('[data-action="toggleMemo"]').click();
        await wait(()=>document.querySelectorAll('.memo-row').length===1,
          'Memo completion toggle did not update its independent row');
        check(document.querySelector('#skinSheet').hidden,
          'completion toggle incorrectly reopened the editor');
        return {eventSeq:31,revision:2,originalOccurrenceKey:'2026-10-22T00:00',
          editorKind:'MEMO',selectedDate:'2026-10-22',completionRemainsSeparate:true};
      })()`});
    if(memoOpen.exceptionDetails)throw Error(memoOpen.exceptionDetails.exception?.description || memoOpen.exceptionDetails.text);
    console.log("BROWSER MEMO OPEN / COMPLETION SEPARATION PASS",JSON.stringify(memoOpen.result.value));

    await call("Runtime.evaluate",{expression:
      "(()=>{const e=document.querySelector('#scenario');e.value='memo-overflow';e.dispatchEvent(new Event('change',{bubbles:true}))})()",
      returnByValue:true});
    const memoOverflow=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        await wait(()=>document.querySelectorAll('#memoList .memo-row').length===250,
          'overflow scenario did not expose the first 250 Memo entries');
        const button=document.querySelector('#memoLimit');
        check(button.tagName==='BUTTON'&&button.type==='button'&&
          button.dataset.action==='loadMoreMemo'&&button.getAttribute('aria-controls')==='memoList',
          'Memo continuation is not an accessible button bound to loadMoreMemo');
        check(!button.hidden&&button.textContent.includes('还有 1 项'),
          'continuation button did not show the host-projected remaining count');
        button.click();
        await wait(()=>document.querySelectorAll('#memoList .memo-row').length===251,
          'loadMoreMemo did not expose the final Memo entry');
        check(button.hidden,'continuation button remained visible after truncation cleared');
        return {scenario:'memo-overflow',visibleBefore:250,remainingBefore:1,
          action:'loadMoreMemo',visibleAfter:251,truncatedAfter:false,
          buttonHiddenAfter:true};
      })()`});
    if(memoOverflow.exceptionDetails)throw Error(memoOverflow.exceptionDetails.exception?.description || memoOverflow.exceptionDetails.text);
    const memoActionAccepted=await call("Runtime.evaluate",{expression:
      "document.querySelector('#bridgeLog').textContent.includes('模拟宿主收到：loadMoreMemo')",
      returnByValue:true});
    if(memoActionAccepted.result.value!==true)throw Error("no-argument loadMoreMemo action was not accepted by the Studio Host Mock");
    console.log("BROWSER MEMO LOAD-MORE MOCK PASS",JSON.stringify(memoOverflow.result.value));

    const aiPage=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        document.querySelector('.clear-tabbar [data-action="ai"]').click();
        await wait(()=>!document.querySelector('#aiPage').hidden,'AI page did not open');
        return true;
      })()`});
    if(aiPage.exceptionDetails)throw Error(aiPage.exceptionDetails.exception?.description || aiPage.exceptionDetails.text);
    const aiAttachments=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        const controls=[...document.querySelectorAll('#chatAttachmentControls button')];
        check(controls.length===2,'AI chat must expose image and file attachment actions');
        check(controls.every(button=>!button.disabled),'attachments should be enabled without a pending review');
        check(controls.map(b=>b.getAttribute('aria-label')).join('|')==='选择图片附件|选择日历或文档附件',
          'attachment controls need distinct accessible labels');
        controls[1].click();
        await wait(()=>document.querySelector('#chatMessages').textContent.includes('未打开真实选择器，也不会读取、上传或导入文件'),
          'file action did not use the explicit non-reading Studio Mock');
        const afterFile=[...document.querySelectorAll('#chatAttachmentControls button')];
        afterFile[0].click();
        await wait(()=>document.querySelector('#chatMessages').textContent.includes('未打开真实选择器，也不会读取、上传或识别图片'),
          'image action did not use the explicit non-reading Studio Mock');
        return {actions:['pickAiFile','pickAiImage'],realFilesRead:false,realCalendarWrites:false};
      })()`});
    if(aiAttachments.exceptionDetails)throw Error(aiAttachments.exceptionDetails.exception?.description || aiAttachments.exceptionDetails.text);
    console.log("BROWSER AI attachment Host-action Mock PASS",JSON.stringify(aiAttachments.result.value));
    const aiHistory=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<120;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        const list=document.querySelector('#chatMessages'),field=document.querySelector('#chatInput');
        const send=async text=>{
          field.value=text;field.dispatchEvent(new Event('input',{bubbles:true}));
          document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
          await wait(()=>[...list.querySelectorAll('.chat-message-content')].some(node=>node.textContent===text),
            'chat message was not appended: '+text);
        };
        const first='历史第一条请求',second='历史第二条请求';
        await send(first);await send(second);
        const firstBubble=[...list.querySelectorAll('.chat-bubble.user')]
          .find(bubble=>bubble.querySelector('.chat-message-content')?.textContent===first);
        check(firstBubble,'first user message is missing before rewind');
        firstBubble.querySelector('.chat-message-actions button').click();
        await wait(()=>field.value===first&&!list.textContent.includes(second),
          'edit-and-resend did not prefill the selected user message and remove later turns');
        const edited='修改后的第一条请求';
        field.value=edited;field.dispatchEvent(new Event('input',{bubbles:true}));
        document.querySelector('.clear-tabbar [data-action="timeline"]').click();
        await wait(()=>!document.querySelector('#timelinePage').hidden,
          'timeline navigation did not publish an unrelated Host state update');
        document.querySelector('.clear-tabbar [data-action="ai"]').click();
        await wait(()=>!document.querySelector('#aiPage').hidden,
          'AI chat did not return after unrelated navigation');
        check(field.value===edited,'unrelated page state overwrote text typed after the Host prefill');
        await send(edited);
        const later='回退到助手回复之后的请求';await send(later);
        const retainedReply=[...list.querySelectorAll('.chat-bubble.assistant')]
          .find(bubble=>bubble.querySelector('.chat-message-content')?.textContent===
            '预览 Mock：消息已保留在本地聊天历史；没有调用真实 AI 或写入日历。');
        check(retainedReply,'deterministic post-rewind Mock reply is missing');
        const unsentDraft='暂不发送的输入草稿';
        field.value=unsentDraft;field.dispatchEvent(new Event('input',{bubbles:true}));
        retainedReply.querySelector('.chat-message-actions button').click();
        await wait(()=>field.value===unsentDraft&&!list.textContent.includes(later)&&
          list.textContent.includes('没有调用真实 AI 或写入日历'),
          'assistant rewind changed the composer or failed to remove later turns');
        const clear=document.querySelector('#chatClear');
        check(!clear.disabled,'clear history is unavailable for a non-empty chat');
        clear.click();
        await wait(()=>list.querySelectorAll('.chat-bubble').length===0&&
          list.textContent.includes('从这里开始与 AI 讨论日程')&&clear.disabled&&field.value===unsentDraft,
          'clear history changed the composer or failed to leave the chat empty');
        return {userEditAndResend:true,assistantRewind:true,clear:true,
          pendingComposerPreserved:true,providerCalled:false,calendarWrites:false};
      })()`});
    if(aiHistory.exceptionDetails)throw Error(aiHistory.exceptionDetails.exception?.description || aiHistory.exceptionDetails.text);
    const aiHistoryMockBoundary=await call("Runtime.evaluate",{returnByValue:true,
      expression:"document.querySelector('#bridgeLog').textContent.includes('清空 AI 聊天历史；没有修改事件或草稿')"});
    if(aiHistoryMockBoundary.result.value!==true)
      throw Error("Studio Host Mock did not disclose that history actions leave calendar data untouched");
    console.log("BROWSER AI history edit/rewind/clear Mock PASS",JSON.stringify(aiHistory.result.value));
    await call("Runtime.evaluate",{expression:`(()=>{const e=document.querySelector('#scenario');e.value='ai-error';e.dispatchEvent(new Event('change',{bubbles:true}))})()`,returnByValue:true});
    const aiErrorResend=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        const list=document.querySelector('#chatMessages');
        const prompt='请帮我概括明天的安排。';
        await wait(()=>list.textContent.includes('模拟 HTTP 503')&&list.textContent.includes(prompt),
          'AI failure recovery Mock did not load');
        const field=document.querySelector('#chatInput');field.value=prompt;
        field.dispatchEvent(new Event('input',{bubbles:true}));
        check(!document.querySelector('#chatSend').disabled,'manual resend is unavailable after a provider error');
        document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        await wait(()=>list.textContent.includes('已收到你手动重发的消息'),
          'manual resend did not reach the Studio Host Mock');
        check(field.value==='','successful send did not clear the composer');
        check(list.textContent.split(prompt).length-1===2, 'the same prompt must appear once as the failed turn and once as the explicit resend');
        check(list.textContent.includes('没有调用真实 AI 或写入日历'),'Mock boundary was not disclosed');
        return {scenario:'AI HTTP 503 then explicit composer resend',providerCalled:false,calendarWrites:false};
      })()`});
    if(aiErrorResend.exceptionDetails)throw Error(aiErrorResend.exceptionDetails.exception?.description || aiErrorResend.exceptionDetails.text);
    console.log("BROWSER AI error/manual resend Mock PASS",JSON.stringify(aiErrorResend.result.value));
    await call("Runtime.evaluate",{expression:`(()=>{const e=document.querySelector('#scenario');e.value='ai-review';e.dispatchEvent(new Event('change',{bubbles:true}))})()`,returnByValue:true});
    const aiCardsReady=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<150;i++){
          if(document.querySelectorAll('.ai-proposal-item').length===4)return true;
          await new Promise(r=>setTimeout(r,100));
        }
        throw Error('AI review cards missing before screenshot');
      })()`});
    if(aiCardsReady.exceptionDetails)throw Error(aiCardsReady.exceptionDetails.exception?.description || aiCardsReady.exceptionDetails.text);
    const pendingReviewAttachments=await call("Runtime.evaluate",{contextId:world.executionContextId,
      returnByValue:true,expression:`(()=>({disabled:[...document.querySelectorAll('#chatAttachmentControls button')]
        .map(button=>button.disabled)}))()`});
    if(pendingReviewAttachments.exceptionDetails)throw Error(pendingReviewAttachments.exceptionDetails.exception?.description || pendingReviewAttachments.exceptionDetails.text);
    if(!pendingReviewAttachments.result.value.disabled.length||
      pendingReviewAttachments.result.value.disabled.some(value=>!value))
      throw Error("attachment buttons remained enabled while AI proposals need review");
    const aiColors=await call("Runtime.evaluate",{contextId:world.executionContextId,returnByValue:true,
      expression:`(()=>{const review=document.querySelector('.ai-proposal-review');
        const title=review?.querySelector('h2');const item=review?.querySelector('.ai-proposal-item');
        const rootStyle=getComputedStyle(document.documentElement);
        return {theme:document.documentElement.className,labelToken:rootStyle.getPropertyValue('--label').trim(),
          rootColor:rootStyle.color,bodyColor:getComputedStyle(document.body).color,
          reviewColor:getComputedStyle(review).color,
          reviewBackground:getComputedStyle(review).backgroundColor,titleColor:getComputedStyle(title).color,
          itemColor:getComputedStyle(item).color,itemBackground:getComputedStyle(item).backgroundColor};})()`});
    if(aiColors.exceptionDetails)throw Error(aiColors.exceptionDetails.exception?.description || aiColors.exceptionDetails.text);
    await call("Emulation.setDeviceMetricsOverride",{width:1280,height:1000,deviceScaleFactor:1,mobile:false});
    await call("Runtime.evaluate",{expression:
      "(()=>{const frame=document.querySelector('#webPreview');frame.style.height='1600px';frame.scrollIntoView({block:'center'})})()"});
    await call("Runtime.evaluate",{contextId:world.executionContextId,expression:
      "document.querySelector('.ai-proposal-review')?.scrollIntoView({block:'start'})"});
    const previewRect=await call("Runtime.evaluate",{expression:
      "(()=>{const r=document.querySelector('#webPreview').getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}})()",
      returnByValue:true});
    const clip=previewRect.result.value;
    const aiScreenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true,
      clip:{...clip,scale:1}});
    const evidenceDir=path.join(reportDir,"ai-proposal-review");
    fs.mkdirSync(evidenceDir,{recursive:true});
    fs.writeFileSync(path.join(evidenceDir,"ai-review-studio-mock.png"),Buffer.from(aiScreenshot.data,"base64"));
    console.log("AI review computed colors",JSON.stringify(aiColors.result.value));
    const aiReview=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        await wait(()=>document.querySelectorAll('.ai-proposal-item').length===4,'AI review cards missing');
        const groups=[...document.querySelectorAll('.ai-proposal-group')];
        check(groups.length===2,'AI proposals should be grouped by operation');
        check([...document.querySelectorAll('.ai-proposal-actions button')].length===8,'four decisions per group required');
        groups[0].querySelectorAll('.ai-proposal-actions button')[3].click();
        await wait(()=>document.querySelector('#chatMessages').textContent.includes('请补充需要更正的字段'),
          'supplement decision did not reach Mock Host');
        document.querySelectorAll('.ai-proposal-group')[0]
          .querySelectorAll('.ai-proposal-actions button')[1].click();
        await wait(()=>document.querySelectorAll('.ai-proposal-item').length===3,'stage decision did not resolve one card');
        await wait(()=>document.querySelector('#chatMessages').textContent.includes('模拟写入草稿'),
          'stage result was not surfaced');
        const nextGroups=[...document.querySelectorAll('.ai-proposal-group')];
        nextGroups[1].querySelectorAll('.ai-proposal-actions button')[2].click();
        await wait(()=>document.querySelectorAll('.ai-proposal-item').length===1,'reject decision did not resolve selected cards');
        await wait(()=>document.querySelector('#chatMessages').textContent.includes('模拟拒绝'),
          'reject result was not surfaced');
        return {groups:groups.length,decisions:['supplement','stage','reject']};
      })()`});
    if(aiReview.exceptionDetails)throw Error(aiReview.exceptionDetails.exception?.description || aiReview.exceptionDetails.text);
    await call("Runtime.evaluate",{expression:`(()=>{const e=document.querySelector('#scenario');e.value='future';e.dispatchEvent(new Event('change',{bubbles:true}));e.value='ai-review';e.dispatchEvent(new Event('change',{bubbles:true}))})()`,returnByValue:true});
    const publish=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<150;i++){if(document.querySelectorAll('.ai-proposal-item').length===4)break;await new Promise(r=>setTimeout(r,100))}
        const group=document.querySelectorAll('.ai-proposal-group')[0];
        if(!document.querySelector('[data-ai-batch="publish"]').disabled||
          !document.querySelector('[data-ai-batch="discard"]').disabled)
          throw Error('batch actions remained enabled while proposal cards need review');
        const action=group.querySelectorAll('.ai-proposal-actions button')[0];
        if(action.disabled)throw Error('publish action unexpectedly disabled');
        action.click();
        return true;
      })()`});
    if(publish.exceptionDetails)throw Error(publish.exceptionDetails.exception?.description || publish.exceptionDetails.text);
    const aiApproval=await call("Runtime.evaluate",{expression:`!document.querySelector('#mockApproval').hidden &&
      document.querySelector('#mockApprovalText').textContent.includes('这不是 Android')`,returnByValue:true});
    if(aiApproval.result.value!==true)throw Error("AI Mock Host confirmation was not explicit");
    await call("Runtime.evaluate",{expression:"document.querySelector('#mockApprove').click()",returnByValue:true});
    const published=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<150;i++){
          if(document.querySelectorAll('.ai-proposal-item').length===3&&
            document.querySelector('#chatMessages').textContent.includes('没有写入任何真实日历数据'))return true;
          await new Promise(r=>setTimeout(r,100));
        }
        throw Error('Mock Host confirmation result did not return to Clear');
      })()`});
    if(published.exceptionDetails)throw Error(published.exceptionDetails.exception?.description || published.exceptionDetails.text);
    await call("Runtime.evaluate",{expression:"(()=>{const s=document.querySelector('#scenario');s.value='ai-review-batch';s.dispatchEvent(new Event('change',{bubbles:true}))})()",returnByValue:true});
    const batchProposalGate=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<150;i++){
          if(document.querySelectorAll('.ai-proposal-item').length===4&&
            document.querySelector('#aiBatchCancel').textContent==='取消范围选择')break;
          await new Promise(r=>setTimeout(r,100));
        }
        const proposalControls=[...document.querySelectorAll('.ai-proposal-item input, .ai-proposal-actions button')];
        if(!proposalControls.length||proposalControls.some(control=>!control.disabled))
          throw Error('proposal controls remained enabled during batch selection');
        const batchControls=[...document.querySelectorAll('[data-ai-batch]')];
        if(batchControls.some(control=>!control.disabled))
          throw Error('batch controls remained enabled while proposal cards were present');
        return {proposalControlsDisabled:true,batchControlsDisabled:true};
      })()`});
    if(batchProposalGate.exceptionDetails)throw Error(batchProposalGate.exceptionDetails.exception?.description || batchProposalGate.exceptionDetails.text);
    await call("Runtime.evaluate",{expression:"(()=>{const s=document.querySelector('#scenario');s.value='normal';s.dispatchEvent(new Event('change',{bubbles:true}))})()",returnByValue:true});
    const batchStart=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,contextId:world.executionContextId,expression:"(async()=>{\n  const check=(v,m)=>{if(!v)throw Error(m)};\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  const publish=document.querySelector('[data-ai-batch=\"publish\"]');\n  check(publish&&!publish.disabled,'batch publish action missing or disabled');\n  publish.click();\n  await wait(()=>!document.querySelector('#aiBatchCancel').hidden,'batch selection did not render');\n  const field=document.querySelector('#chatInput');field.value='全部';\n  document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));\n  await wait(()=>document.querySelector('#aiBatchStatus').textContent.includes('PowerCal Host'),\n    'batch selection did not request the Host review');\n  check(!document.querySelector('#aiBatchStatus').hidden,'Host review state is not visible');\n  return true;\n})()"});
    if(batchStart.exceptionDetails)throw Error(batchStart.exceptionDetails.exception?.description || batchStart.exceptionDetails.text);
    const batchFirstReview=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  await wait(()=>{const d=document.querySelector('#mockApproval');return d&&!d.hidden&&\n    document.querySelector('#mockApprovalText').textContent.includes('Preview Mock Host')},\n    'batch selection did not reach the Preview Host review');\n  document.querySelector('#mockReject').click();\n  return true;\n})()"});
    if(batchFirstReview.exceptionDetails)throw Error(batchFirstReview.exceptionDetails.exception?.description || batchFirstReview.exceptionDetails.text);
    const batchRetry=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,contextId:world.executionContextId,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  const status=document.querySelector('#aiBatchStatus');\n  await wait(()=>status.hidden,'cancelled review did not clear batch selection state');\n  const publish=document.querySelector('[data-ai-batch=\"publish\"]');\n  publish.click();\n  await wait(()=>!document.querySelector('#aiBatchCancel').hidden,'second batch selection did not start');\n  const field=document.querySelector('#chatInput');field.value='全部';\n  document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));\n  await wait(()=>status.textContent.includes('PowerCal Host'),'second batch review was not projected');\n  return true;\n})()"});
    if(batchRetry.exceptionDetails)throw Error(batchRetry.exceptionDetails.exception?.description || batchRetry.exceptionDetails.text);
    const batchApprove=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  await wait(()=>{const d=document.querySelector('#mockApproval');return d&&!d.hidden&&\n    document.querySelector('#mockApprovalText').textContent.includes('Preview Mock Host')},\n    'second batch did not reach the Preview Host review');\n  document.querySelector('#mockApprove').click();\n  return true;\n})()"});
    if(batchApprove.exceptionDetails)throw Error(batchApprove.exceptionDetails.exception?.description || batchApprove.exceptionDetails.text);
    const batchLifecycle=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,contextId:world.executionContextId,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  const status=document.querySelector('#aiBatchStatus');\n  await wait(()=>!status.hidden&&!status.textContent.includes('PowerCal Host')&&\n    status.textContent.includes('\\u6279\\u6b21\\u5b8c\\u6210'),'Mock batch completion was not returned');\n  const messages=document.querySelector('#chatMessages').textContent;\n  if(!messages.includes('\\u6ca1\\u6709\\u5199\\u5165\\u771f\\u5b9e\\u8349\\u7a3f\\u6216\\u65e5\\u5386'))\n    throw Error('Mock completion did not disclose that no calendar or draft data was written');\n  return {reviewCancelled:true,confirmedMockBatch:true,selected:3,providerCalled:false,realCalendarWrites:false};\n})()"});
    if(batchLifecycle.exceptionDetails)throw Error(batchLifecycle.exceptionDetails.exception?.description || batchLifecycle.exceptionDetails.text);
    await call("Runtime.evaluate",{expression:"(()=>{const s=document.querySelector('#scenario');s.value='locked';s.dispatchEvent(new Event('change',{bubbles:true}))})()",returnByValue:true});
    const batchPartialStop=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,contextId:world.executionContextId,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  const status=document.querySelector('#aiBatchStatus');\n  await wait(()=>status.textContent.includes('\\u6b63\\u5728\\u8fd0\\u884c'),'locked scenario did not project an active batch');\n  if(!status.textContent.includes('进度 1/3'))throw Error('live batch progress was not rendered');const stop=document.querySelector('#aiBatchCancel');\n  if(stop.disabled)throw Error('active batch cancellation is disabled');\n  stop.click();\n  await wait(()=>status.textContent.includes('\\u53d1\\u5e03 1')&&status.textContent.includes('\\u4fdd\\u7559 2'),\n    'partial cancellation counts were not projected');\n  return {liveProgressVisible:true,cancelledAfterPartialResult:true,partialCountsVisible:true,realCalendarWrites:false};\n})()"});
    if(batchPartialStop.exceptionDetails)throw Error(batchPartialStop.exceptionDetails.exception?.description || batchPartialStop.exceptionDetails.text);
    await call("Runtime.evaluate",{expression:"(()=>{const s=document.querySelector('#scenario');s.value='repeating';s.dispatchEvent(new Event('change',{bubbles:true}));s.value='locked-stale';s.dispatchEvent(new Event('change',{bubbles:true}))})()",returnByValue:true});
    const batchReleaseRequest=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,contextId:world.executionContextId,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  const status=document.querySelector('#aiBatchStatus');\n  await wait(()=>status.textContent.includes('已请求停止'),'stale cancellation scenario was not projected');\n  const release=document.querySelector('#aiBatchRelease');\n  if(release.hidden||release.disabled)throw Error('stale cancellation recovery action is unavailable');\n  release.click();\n  return true;\n})()"});
    if(batchReleaseRequest.exceptionDetails)throw Error(batchReleaseRequest.exceptionDetails.exception?.description || batchReleaseRequest.exceptionDetails.text);
    const batchReleaseConfirm=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  await wait(()=>{const d=document.querySelector('#mockApproval');return d&&!d.hidden&&\n    document.querySelector('#mockApprovalText').textContent.includes('\\u5f3a\\u5236\\u89e3\\u9664\\u6279\\u6b21\\u9501')},\n    'release action bypassed or failed to request Mock Host confirmation');\n  document.querySelector('#mockApprove').click();\n  return true;\n})()"});
    if(batchReleaseConfirm.exceptionDetails)throw Error(batchReleaseConfirm.exceptionDetails.exception?.description || batchReleaseConfirm.exceptionDetails.text);
    const batchStop=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,contextId:world.executionContextId,expression:"(async()=>{\n  const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};\n  const status=document.querySelector('#aiBatchStatus');\n  await wait(()=>status.textContent.includes('\\u5f3a\\u5236\\u89e3\\u9664\\u5e76\\u4f5c\\u5e9f\\u65e7\\u6388\\u6743'),\n    'force-release outcome was not projected');\n  if(!status.textContent.includes('发布 1')||!status.textContent.includes('保留 2'))\n    throw Error('Preview Mock failed to report its known partial result counts');\n  return {forceReleaseHostConfirmation:true,oldAuthorizationInvalidated:true,realCalendarWrites:false};\n})()"});
    if(batchStop.exceptionDetails)throw Error(batchStop.exceptionDetails.exception?.description || batchStop.exceptionDetails.text);
    console.log("BROWSER AI REVIEW MOCK PASS",JSON.stringify(aiReview.result.value));
    console.log("BROWSER AI HOST CONFIRM MOCK PASS; no real calendar data was written");
    console.log("BROWSER AI BATCH LIFECYCLE MOCK PASS",JSON.stringify(batchPartialStop.result.value),JSON.stringify(batchLifecycle.result.value),JSON.stringify(batchStop.result.value));
    socket.close();
  }finally{
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
