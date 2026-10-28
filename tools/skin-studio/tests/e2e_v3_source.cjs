/* Real browser source edit, preview and package round trip. No npm dependencies. */
const {spawn,execFileSync} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.STUDIO_E2E_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/separation-20261028/studio-component-styles"));
const delay = ms => new Promise(resolve=>setTimeout(resolve,ms));
async function main(){
  fs.mkdirSync(reportDir,{recursive:true});
  const profile=fs.mkdtempSync(path.join(reportDir,"browser-profile-"));
  const scaffoldDir=fs.mkdtempSync(path.join(reportDir,"blank-scaffold-"));
  const scaffoldPath=path.join(scaffoldDir,"agent-blank.pcskin");
  const scaffoldReceipt=JSON.parse(execFileSync("python",[
    "tools/skin-studio/skin_studio.py","scaffold-v3","--id","browser-blank-v3",
    "--name","Browser Blank Skin","--out",scaffoldPath,"--json"
  ],{cwd:root,encoding:"utf8"}));
  if(!scaffoldReceipt.ok||scaffoldReceipt.manifest.capabilities.length!==0)
    throw Error("blank scaffold did not use the least-privilege manifest");
  const scaffoldBase64=fs.readFileSync(scaffoldPath).toString("base64");
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
      for(let i=0;i<150;i++){
        if(typeof loadPackage==='function' && document.querySelector('#skinName')?.value)break;
        await new Promise(r=>setTimeout(r,100));
      }
      const data=await (await fetch('/api/template-v3')).json();
      const waitFor=async(fn,message)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(message)};
      await loadPackage(data);
      await waitFor(()=>document.querySelector('#componentTree button[data-html-id="app"]'),
        'static HTML component tree did not load');
      const nodeButton=id=>document.querySelector('#componentTree button[data-html-id="'+id+'"]');
      const markup=document.querySelector('#componentMarkup');
      nodeButton('app').click();markup.value='<section id="studio-component-one">one</section>';
      document.querySelector('#insertComponentAfter').click();
      await waitFor(()=>nodeButton('studio-component-one'),'component insert was not reflected in tree/source');
      nodeButton('app').click();markup.value='<article id="studio-component-two">two</article>';
      document.querySelector('#insertComponentAfter').click();
      await waitFor(()=>nodeButton('studio-component-two'),'second component insert was not reflected');
      nodeButton('app').click();markup.value='<button id="studio-component-attributes">attributes</button>';
      document.querySelector('#insertComponentAfter').click();
      await waitFor(()=>nodeButton('studio-component-attributes'),'attribute test node was not inserted');
      nodeButton('studio-component-attributes').click();
      const addAttribute=(name,value,isBoolean=false)=>{
        document.querySelector('#addComponentAttribute').click();
        const rows=[...document.querySelectorAll('#componentAttributeRows .componentAttributeRow')];
        const inputs=rows.at(-1).querySelectorAll('input');
        inputs[0].value=name;inputs[1].value=value;inputs[2].checked=isBoolean;
      };
      addAttribute('data-studio-author','attribute & value');
      addAttribute('aria-label','Editable node');
      addAttribute('hidden','',true);
      document.querySelector('#saveComponentAttributes').click();
      await waitFor(()=>dec.decode(sourceFiles.get('web/index.html')).includes(
        'data-studio-author="attribute &amp; value" aria-label="Editable node" hidden>'),
        'component attribute edit did not update canonical HTML source');
      nodeButton('studio-component-attributes').click();
      const styleSelect=document.querySelector('#componentStylePath');
      check(styleSelect.value.endsWith('.css'),'linked package CSS was not offered to the component style editor');
      const cssPath=styleSelect.value,authoredCssBefore=dec.decode(sourceFiles.get(cssPath));
      const addStyle=(name,value)=>{
        document.querySelector('#addComponentStyle').click();
        const rows=[...document.querySelectorAll('#componentStyleRows .componentStyleRow')];
        const inputs=rows.at(-1).querySelectorAll('input');inputs[0].value=name;inputs[1].value=value;
      };
      addStyle('color','#2563eb');addStyle('--studio-gap','calc(2px + 1vw)');
      document.querySelector('#saveComponentStyles').click();
      await waitFor(()=>dec.decode(sourceFiles.get('web/index.html')).includes('data-pcskin-studio-style="pcss-')&&
        dec.decode(sourceFiles.get(cssPath)).includes('color: #2563eb;'),
        'component style edit did not update the canonical HTML/CSS sources');
      check(dec.decode(sourceFiles.get(cssPath)).startsWith(authoredCssBefore),
        'component style operation rewrote pre-existing CSS bytes');
      nodeButton('studio-component-attributes').click();
      const styleRows=[...document.querySelectorAll('#componentStyleRows .componentStyleRow')];
      const colorRow=styleRows.find(row=>row.querySelectorAll('input')[0].value==='color');
      const customRow=styleRows.find(row=>row.querySelectorAll('input')[0].value==='--studio-gap');
      check(colorRow&&customRow,'managed component styles did not rehydrate in the editor');
      colorRow.querySelectorAll('input')[1].value='#0f766e';customRow.querySelector('button').click();
      document.querySelector('#saveComponentStyles').click();
      await waitFor(()=>dec.decode(sourceFiles.get(cssPath)).includes('color: #0f766e;')&&
        !dec.decode(sourceFiles.get(cssPath)).includes('--studio-gap:'),
        'component style update/removal did not update the canonical CSS source');
      check(dec.decode(sourceFiles.get(cssPath)).startsWith(authoredCssBefore)&&
        dec.decode(sourceFiles.get(cssPath)).split('pcskin-studio-style-begin:').length===2,
        'component styles changed authored CSS or left duplicate managed rules');
      nodeButton('app').click();markup.value='<p id="studio-style-proof">Studio 样式回归</p>';
      document.querySelector('#insertComponentFirst').click();
      await waitFor(()=>nodeButton('studio-style-proof'),'visible style proof component was not inserted');
      nodeButton('studio-style-proof').click();
      addStyle('color','#ff00ff');addStyle('font-weight','900');
      document.querySelector('#saveComponentStyles').click();
      await waitFor(()=>dec.decode(sourceFiles.get(cssPath)).includes('color: #ff00ff;'),
        'visible component style did not update its stylesheet');
      const styledMarkup=dec.decode(sourceFiles.get('web/index.html'));
      check(styledMarkup.includes('data-pcskin-studio-style="pcss-')&&
        styledMarkup.includes('id="studio-style-proof"'),
        'visible component was not bound to its scoped style rule');
      nodeButton('studio-component-one').click();
      const target=document.querySelector('#componentTarget');
      target.value=[...target.options].find(option=>option.dataset.htmlId==='studio-component-two').value;
      document.querySelector('#componentPosition').value='before';
      document.querySelector('#moveComponent').click();
      await waitFor(()=>sourceFiles.get('web/index.html')&&
        dec.decode(sourceFiles.get('web/index.html')).indexOf('studio-component-one')<
        dec.decode(sourceFiles.get('web/index.html')).indexOf('studio-component-two'),
        'component reorder did not update canonical HTML source');
      nodeButton('studio-component-two').click();markup.value='<aside id="studio-component-two">replaced</aside>';
      document.querySelector('#replaceComponent').click();
      await waitFor(()=>dec.decode(sourceFiles.get('web/index.html')).includes('<aside id="studio-component-two">'),
        'component replace did not update canonical HTML source');
      const confirmBeforeTree=window.confirm;window.confirm=()=>true;
      nodeButton('studio-component-one').click();document.querySelector('#removeComponent').click();
      await waitFor(()=>!dec.decode(sourceFiles.get('web/index.html')).includes('studio-component-one'),
        'component remove did not update canonical HTML source');
      window.confirm=confirmBeforeTree;
      const treePayload=payload(),treeSource=dec.decode(sourceFiles.get('web/index.html'));
      const treePreviewResponse=await fetch('/api/preview-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(treePayload)});
      check(treePreviewResponse.ok,'component preview failed');
      const treePreview=await treePreviewResponse.json();
      const servedTreeSource=await (await fetch(treePreview.url)).text();
      check(servedTreeSource===treeSource,'component preview did not serve the edited canonical HTML bytes');
      const servedTreeCss=await (await fetch(treePreview.url.replace('web/index.html',cssPath))).text();
      check(servedTreeCss===dec.decode(sourceFiles.get(cssPath)),
        'component style preview did not serve the edited canonical CSS bytes');
      const treeExportResponse=await fetch('/api/export-v3',{method:'POST',
        headers:{'Content-Type':'application/json'},body:JSON.stringify(treePayload)});
      check(treeExportResponse.ok,'component export failed');
      const treeImportResponse=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:await treeExportResponse.arrayBuffer()});
      check(treeImportResponse.ok,'component export could not be reimported');
      const treeImported=await treeImportResponse.json();
      check(treeImported.files['web/index.html']===treePayload.files['web/index.html'],
        'component edits were lost in package roundtrip');
      check(treeImported.files[cssPath]===treePayload.files[cssPath],
        'component style edits were lost in package roundtrip');
      data.manifest.id='restricted-source-test'; data.manifest.capabilities=['host.read'];
      data.files['web/agent-unused-90f4.txt']=btoa('unused source');
      data.files['assets/replace.bin']=btoa('old resource');
      const meta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(data.files['project.json']),c=>c.charCodeAt(0))));
      delete meta.layers;meta.thirdPartyMetadata={keep:'原样保留'};
      data.files['project.json']=btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(meta)+'\\n')));
      await loadPackage(data);
      await waitFor(()=>document.querySelector('#componentTree button[data-node-id="'+selectedComponent+'"]'),
        'component selection did not reset when another project loaded');
      check(document.querySelector('#componentTarget').value===selectedComponent,
        'move target leaked from the previous project');
      document.querySelector('#advancedMode').click();
      const select=document.querySelector('#extraSourceFile');
      check([...select.options].some(o=>o.value==='web/edit.js'),'edit.js missing');
      check([...select.options].some(o=>o.value==='project.json'),'project.json missing from source editor');
      select.value='web/edit.js';select.dispatchEvent(new Event('change'));
      const field=document.querySelector('#extraSourceCode'), before=field.value;
      check(before.includes('PowerCalEdit'),'edit.js contents missing');
      field.value=before+'\\n// browser-source-roundtrip\\n';
      field.dispatchEvent(new Event('input',{bubbles:true}));
      const jsField=document.querySelector('#jsCode');
      const clearSourceBefore=jsField.value,clearMarker='\\n// studio-source-roundtrip-chat-scroll\\n';
      jsField.value=clearSourceBefore+clearMarker;
      jsField.dispatchEvent(new Event('input',{bubbles:true}));
      const project=payload();
      check(JSON.stringify(project.manifest.capabilities)==='["host.read"]','capabilities changed');
      check(project.files['project.json']===data.files['project.json'],'untouched project metadata changed');
      check(atob(project.files['web/app.js']).endsWith(clearMarker),'Clear runtime source edit was not retained');
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
      const zip=await (await post('/api/export-v3',JSON.stringify(authored),'application/json')).arrayBuffer();
      const imported=await (await post('/api/import-v3',zip,'application/octet-stream')).json();
      check(JSON.stringify(imported.manifest.capabilities)==='["host.read"]','import capabilities changed');
      for(const [name,bytes] of Object.entries(authored.files))
        check(imported.files[name]===bytes,'round trip changed '+name);
      await loadPackage(imported);document.querySelector('#advancedMode').click();
      check(jsField.value===clearSourceBefore+clearMarker,'Clear runtime JS edit was lost after package roundtrip');
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
      await loadPackage(treeImported);
      await waitFor(()=>nodeButton('studio-component-attributes'),'attribute node missing after exported package reload');
      const blankPackage=new File([Uint8Array.from(atob('__BLANK_PACKAGE_BASE64__'),c=>c.charCodeAt(0))],
        'agent-blank.pcskin',{type:'application/zip'});
      await document.querySelector('#importZip').onchange({target:{files:[blankPackage],value:'selected'}});
      await waitFor(()=>document.querySelector('#skinName').value==='Browser Blank Skin',
        'blank v3 package was not loaded by the Studio import handler');
      check(manifest.id==='browser-blank-v3'&&JSON.stringify(manifest.capabilities)==='[]',
        'blank scaffold identity or least-privilege capabilities changed on Studio import');
      check(sourceFiles.size===4&&sourceFiles.has('project.json')&&sourceFiles.has('web/app.js'),
        'Studio did not retain every blank scaffold source file');
      await waitFor(()=>nodeButton('app'),'blank scaffold HTML tree did not load');
      nodeButton('app').click();
      markup.value='<section id="browser-authored-from-blank"><h1>From scratch</h1></section>';
      document.querySelector('#insertComponentAfter').click();
      await waitFor(()=>dec.decode(sourceFiles.get('web/index.html')).includes('browser-authored-from-blank'),
        'Studio could not structurally edit the imported blank scaffold');
      const blankPayload=payload();
      const blankPreview=await (await post('/api/preview-v3',JSON.stringify(blankPayload),'application/json')).json();
      const servedBlankHtml=await (await fetch(blankPreview.url)).text();
      check(servedBlankHtml===dec.decode(sourceFiles.get('web/index.html')),
        'blank scaffold preview did not serve the canonical edited source bytes');
      const blankZip=await post('/api/export-v3',JSON.stringify(blankPayload),'application/json');
      const blankReimport=await (await post('/api/import-v3',await blankZip.arrayBuffer(),
        'application/octet-stream')).json();
      check(blankReimport.files['web/index.html']===blankPayload.files['web/index.html'],
        'Studio export/import changed the edited blank scaffold HTML');
      check(JSON.stringify(blankReimport.manifest.capabilities)==='[]',
        'Studio roundtrip added Host capabilities to the blank scaffold');
      await loadPackage(blankReimport);
      await waitFor(()=>nodeButton('browser-authored-from-blank'),
        'edited blank scaffold did not return to the Studio component tree after reimport');
      await loadPackage(treeImported);
      await waitFor(()=>nodeButton('studio-component-attributes'),
        'Clear fixture was not restored after blank scaffold E2E');
      return {file:'web/edit.js', files:Object.keys(authored.files).length, capabilities:imported.manifest.capabilities,
        componentTree:true,componentInsert:true,componentReplace:true,componentMove:true,componentRemove:true,
        componentAttributes:true,componentStyles:true,componentStylePreviewExportRoundtrip:true,
         componentPreviewExportRoundtrip:true,componentReloadState:true,runtimeSourceEditRoundtrip:true,
        blankScaffoldImport:true,blankScaffoldEdit:true,blankScaffoldPreviewExportImport:true,
        blankScaffoldReimportedIntoStudio:true,
        projectTree:true,newText:true,newBinary:true,replacedBinary:true,delete:true,referenceGuard:true};
    })()`.replace('__BLANK_PACKAGE_BASE64__',scaffoldBase64);
    const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
    if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    console.log("BROWSER SOURCE ROUNDTRIP PASS",JSON.stringify(result.result.value));
    console.log("BROWSER BLANK SCAFFOLD PACKAGE SHA-256",scaffoldReceipt.packageSha256);
    await call("Page.enable");
    await call("Emulation.setDeviceMetricsOverride",{width:1440,height:1280,deviceScaleFactor:1,mobile:false});
    const attributeInspector=await call("Runtime.evaluate",{expression:`(()=>{
      const check=(value,message)=>{if(!value)throw Error(message)};
      const node=document.querySelector('#componentTree button[data-html-id="studio-component-attributes"]');
      check(node,'attribute node missing after package reimport');node.click();
      const selection=document.querySelector('#componentSelection').textContent;
      check(selection.includes('studio-component-attributes'),'attribute node was not selected: '+selection);
      const rows=[...document.querySelectorAll('#componentAttributeRows .componentAttributeRow')];
      const rowFor=name=>rows.find(row=>row.querySelectorAll('input')[0].value===name)?.querySelectorAll('input');
      check(rows.length===5,'reimported attribute rows missing: '+rows.length);
      check(rowFor('id')?.[1].value==='studio-component-attributes','node id did not rehydrate');
      check(rowFor('data-studio-author')?.[1].value==='attribute & value','string attribute value did not rehydrate');
      check(rowFor('aria-label')?.[1].value==='Editable node','ARIA attribute did not rehydrate');
      check(rowFor('hidden')?.[2].checked===true,'boolean attribute did not rehydrate');
      check(/^pcss-[0-9a-f]{24}$/.test(rowFor('data-pcskin-studio-style')?.[1].value||''),
        'component style marker did not rehydrate');
      const styleRows=[...document.querySelectorAll('#componentStyleRows .componentStyleRow')];
      const color=styleRows.find(row=>row.querySelectorAll('input')[0].value==='color');
      check(styleRows.length===1&&color?.querySelectorAll('input')[1].value==='#0f766e',
        'managed component style did not rehydrate after export/import: '+JSON.stringify({
          count:styleRows.length,values:styleRows.map(row=>[...row.querySelectorAll('input')].map(input=>input.value)),
          status:document.querySelector('#componentStyleStatus').textContent,
          stylesheet:document.querySelector('#componentStylePath').value}));
      return {selection,attributes:rows.map(row=>{const inputs=row.querySelectorAll('input');return {name:inputs[0].value,value:inputs[1].value,boolean:inputs[2].checked}}),
        styles:styleRows.map(row=>{const inputs=row.querySelectorAll('input');return {name:inputs[0].value,value:inputs[1].value}})};
    })()`,returnByValue:true});
    if(attributeInspector.exceptionDetails)
      throw Error(attributeInspector.exceptionDetails.exception?.description || attributeInspector.exceptionDetails.text);
    console.log("BROWSER COMPONENT ATTRIBUTE RELOAD PASS",JSON.stringify(attributeInspector.result.value));
    const componentScreenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
    fs.writeFileSync(path.join(reportDir,"studio-component-styles.png"),Buffer.from(componentScreenshot.data,"base64"));
    const setup=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<150;i++){
        if(document.querySelector('#previewState').textContent.includes('连接成功'))break;
        await new Promise(r=>setTimeout(r,100));
      }
      if(!document.querySelector('#previewState').textContent.includes('连接成功'))throw Error('preview not connected');
      const scenario=document.querySelector('#scenario');scenario.value='repeating';
      scenario.dispatchEvent(new Event('change',{bubbles:true}));
      document.querySelector('#interactMode').click();
      return true;
    })()`});
    if(setup.exceptionDetails)throw Error(setup.exceptionDetails.exception?.description || setup.exceptionDetails.text);
    const tree=await call("Page.getFrameTree");
    const child=tree.frameTree.childFrames?.find(frame=>frame.frame.url.includes("/preview/"));
    if(!child)throw Error("Preview frame not found: "+JSON.stringify(tree.frameTree));
    const world=await call("Page.createIsolatedWorld",{frameId:child.frame.id,worldName:"powercal-source-qa"});
    const styleRender=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(()=>{
        const node=document.querySelector('#studio-style-proof');
        if(!node)throw Error('styled proof node missing from runtime preview');
        return {text:node.textContent,color:getComputedStyle(node).color,fontWeight:getComputedStyle(node).fontWeight};
      })()`});
    if(styleRender.exceptionDetails)throw Error(styleRender.exceptionDetails.exception?.description || styleRender.exceptionDetails.text);
    if(styleRender.result.value.color!=="rgb(255, 0, 255)"||styleRender.result.value.fontWeight!=="900")
      throw Error("component style did not render in the real browser preview: "+JSON.stringify(styleRender.result.value));
    console.log("BROWSER COMPONENT COMPUTED STYLE PASS",JSON.stringify(styleRender.result.value));
    const settings=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        document.querySelector('.clear-tabbar [data-action="settings"]').click();
        await wait(()=>!document.querySelector('#extraOptions').hidden&&
          document.querySelector('#extraOptions h1').textContent==='设置与数据工具',
          'settings tab did not open a skin-owned landing page');
        const options=[...document.querySelectorAll('#optionGroups [data-action="invokeOption"]')];
        const imports=options.filter(button=>button.dataset.optionId.startsWith('calendar.import.'));
        const exports=options.filter(button=>button.dataset.optionId.startsWith('calendar.export.'));
        const backups=options.filter(button=>button.dataset.optionId.startsWith('data.backup.'));
        const skinOptions=options.filter(button=>button.dataset.optionId.startsWith('skin.'));
        const reminders=options.filter(button=>button.dataset.optionId.startsWith('reminders.'));
        check(imports.length===2,'settings landing must retain the two bounded Host import options');
        check(exports.length===1,'settings landing must expose the bounded Activity ICS export');
        check(exports[0].textContent.includes('仅包含 Activity；其他类型和完成状态请用原生备份'),
          'ICS export data-loss limitations must remain visible in the skin');
        check(backups.length===2,'settings landing must expose the two bounded native backup actions');
        check(backups[0].textContent.includes('明文 JSON'),'backup export sensitivity must be visible in the skin');
        check(backups[1].textContent.includes('空数据库'),'backup restore empty-store limit must be visible in the skin');
        check(skinOptions.length===2,'settings landing must expose bounded skin import/export actions');
        check(skinOptions[0].textContent.includes('导入后不会自动启用'),'skin import must explain it does not activate packages');
        check(skinOptions[1].textContent.includes('不更改当前皮肤'),'Clear package export must not be described as activation');
        const installed=document.querySelectorAll('#optionGroups [data-action="selectSkin"]');
        const removals=document.querySelectorAll('#optionGroups [data-action="deleteSkin"]');
        check(installed.length===3&&removals.length===1,'settings must render the Host-projected skin catalogue and only allow deleting user skins');
        check(reminders.length===3,'settings landing must expose the three bounded reminder Host actions');
        check(document.querySelector('#optionGroups').textContent.includes('精确闹钟：未允许；通知运行时权限：未允许'),
          'settings landing must show the Host-projected reminder permission state');
        const reminderList=document.querySelector('.reminderDiagnosticsList');
        const reconcileStatus=document.querySelector('#reminderReconcileStatus');
        check(reminderList?.querySelectorAll('li').length===6,
          'settings must show each bounded per-event reminder diagnostic');
        check(reconcileStatus?.getAttribute('role')==='status'&&
          reconcileStatus.getAttribute('aria-live')==='polite'&&
          reconcileStatus.getAttribute('aria-atomic')==='true'&&
          reconcileStatus.dataset.status==='IDLE'&&reconcileStatus.textContent.includes('尚未执行'),
          'reconcile state must use an accessible live status region');
        const reminderText=reminderList.textContent;
        for(const label of ['未设置提醒','等待处理','已安排','受阻','已取消','已触发'])
          check(reminderText.includes(label),'reminder list omitted desired state '+label);
        check(document.querySelector('#optionGroups').textContent.includes('标题已截断'),
          'truncated reminder titles must be identified');
        const renderedSettingsText=document.querySelector('#optionGroups').textContent;
        check(!/notes|private/i.test(renderedSettingsText)&&!renderedSettingsText.includes('PRIVATE_'),
          'settings must not render notes or private reminder fields');
        check(document.querySelector('#optionGroups').textContent.includes('备份与皮肤包只由可信 Android 宿主读写和校验'),
          'backup and package access boundary was not explained');
        imports[0].click();
        exports[0].click();
        return {settingsLanding:true,hostImportOptions:imports.length,hostExportOptions:exports.length,
          hostBackupActions:backups.length,
          hostSkinPackageOptions:skinOptions.length,installedSkinRows:installed.length,
          userSkinDeleteRows:removals.length,
          reminderHostActions:reminders.length,
          reminderPermissionProjection:true,perEventReminderStates:6,
          accessibleReconcileStatus:true,reminderPrivateTextAbsent:true,browserMockWroteData:false};
      })()`});
    if(settings.exceptionDetails)throw Error(settings.exceptionDetails.exception?.description || settings.exceptionDetails.text);
    const reminderContract=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const state=simulatedState(),diagnostics=state.reminderDiagnostics,result=state.reminderReconcile;
      return {page:state.page,diagnosticKeys:Object.keys(diagnostics||{}).sort(),
        itemKeys:Object.keys(diagnostics?.items?.[0]||{}).sort(),
        itemLimit:diagnostics?.itemLimit,truncated:diagnostics?.truncated,
        itemTypesValid:(diagnostics?.items||[]).every(item=>
          typeof item.displayId==='string'&&typeof item.title==='string'&&
          typeof item.titleTruncated==='boolean'&&['ALARM','ACTIVITY','DEADLINE','MEMO'].includes(item.kind)&&
          /^reminder-[0-9]{2}$/.test(item.displayId)&&item.title.length<=120&&
          ['NO_INTENT','PENDING','SCHEDULED','BLOCKED','CANCELLED','FIRED'].includes(item.desiredState)&&
          ([null,'事件已更新或删除','缺少精确闹钟权限','系统拒绝精确闹钟'].includes(item.androidHint))&&
          ([null,'FIRED','MISSED','BLOCKED','SNOOZED','SNOOZE_INEXACT','IGNORED_STALE','IGNORED_SNOOZE_STALE']
            .includes(item.lastExecution))),
        countTypesValid:['checked','scheduled','blocked','pending','failed'].every(key=>
          Number.isInteger(result?.[key])&&result[key]>=0)&&typeof result?.truncated==='boolean',
        kinds:[...new Set((diagnostics?.items||[]).map(item=>item.kind))].sort(),
        desiredStates:[...new Set((diagnostics?.items||[]).map(item=>item.desiredState))].sort(),
        reconcileKeys:Object.keys(result||{}).sort(),
        privateFieldsPresent:/notes|private/i.test(JSON.stringify({diagnostics,result}))};})()`});
    if(reminderContract.exceptionDetails)throw Error(reminderContract.exceptionDetails.exception?.description || reminderContract.exceptionDetails.text);
    const contract=reminderContract.result.value;
    const same=(a,b)=>JSON.stringify(a)===JSON.stringify(b);
    if(contract.page!=="SETTINGS"||
      !same(contract.diagnosticKeys,["itemLimit","items","truncated"])||
      !same(contract.itemKeys,["androidHint","desiredState","displayId","kind","lastExecution","title","titleTruncated"])||
      contract.itemLimit!==40||contract.truncated!==false||!contract.itemTypesValid||!contract.countTypesValid||
      !same(contract.kinds,["ACTIVITY","ALARM","DEADLINE","MEMO"])||
      !same(contract.desiredStates,["BLOCKED","CANCELLED","FIRED","NO_INTENT","PENDING","SCHEDULED"])||
      !same(contract.reconcileKeys,["blocked","checked","failed","pending","scheduled","status","truncated"])||
      contract.privateFieldsPresent)
      throw Error("Studio reminder Mock violates the bounded projection contract: "+JSON.stringify(contract));
    if(!settings.result.value.reminderPrivateTextAbsent)
      throw Error("Settings rendered notes or private reminder fields");
    const reminderSettingsScreenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
    fs.writeFileSync(path.join(reportDir,"reminder-diagnostics-settings.png"),
      Buffer.from(reminderSettingsScreenshot.data,"base64"));
    const reminderScroll=await call("Runtime.evaluate",{contextId:world.executionContextId,returnByValue:true,
      expression:`(()=>{
        const rows=[...document.querySelectorAll('.reminderDiagnosticsList li')];
        const last=rows.at(-1),tab=document.querySelector('.clear-tabbar');
        if(!last||!tab)throw Error('reminder list or fixed navigation missing');
        last.scrollIntoView({block:'end'});
        let item=last.getBoundingClientRect(),nav=tab.getBoundingClientRect();
        window.scrollBy(0,Math.max(0,item.bottom-nav.top+12));
        item=last.getBoundingClientRect();nav=tab.getBoundingClientRect();
        return {count:rows.length,itemTop:item.top,itemBottom:item.bottom,navTop:nav.top,
          fullyVisible:item.top>=0&&item.bottom<=nav.top};
      })()`});
    if(reminderScroll.exceptionDetails)throw Error(reminderScroll.exceptionDetails.exception?.description || reminderScroll.exceptionDetails.text);
    if(reminderScroll.result.value.count!==6||!reminderScroll.result.value.fullyVisible)
      throw Error('last reminder row is not reachable above the fixed navigation: '+JSON.stringify(reminderScroll.result.value));
    const reminderLastRowScreenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
    fs.writeFileSync(path.join(reportDir,"reminder-diagnostics-last-row.png"),
      Buffer.from(reminderLastRowScreenshot.data,"base64"));
    console.log('BROWSER REMINDER LIST SCROLL PASS',JSON.stringify(reminderScroll.result.value));
    const calendarMocks=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<100;i++){
        const text=document.querySelector('#bridgeLog').textContent;
        if(text.includes('invokeOption · calendar.import.file')&&text.includes('未创建或写入真实文件'))return true;
        await new Promise(r=>setTimeout(r,100));
      }
      return false;
    })()`});
    if(calendarMocks.exceptionDetails)throw Error(calendarMocks.exceptionDetails.exception?.description || calendarMocks.exceptionDetails.text);
    if(!calendarMocks.result.value)throw Error('calendar picker options did not reach their non-writing Host Mocks');
    await call("Runtime.evaluate",{contextId:world.executionContextId,returnByValue:true,expression:`(()=>{
      const options=[...document.querySelectorAll('#optionGroups [data-action="invokeOption"]')];
      options.filter(button=>button.dataset.optionId.startsWith('data.backup.')).forEach(button=>button.click());
      return true;
    })()`});
    const backupMocks=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<100;i++){
        const text=document.querySelector('#bridgeLog').textContent;
        if(text.includes('data.backup.export')&&text.includes('未读取日历数据或创建文件')&&
          text.includes('data.backup.restore')&&text.includes('未读取文件或恢复数据'))return true;
        await new Promise(r=>setTimeout(r,100));
      }
      return false;
    })()`});
    if(backupMocks.exceptionDetails)throw Error(backupMocks.exceptionDetails.exception?.description || backupMocks.exceptionDetails.text);
    if(!backupMocks.result.value)throw Error('backup actions did not reach their non-writing Host Mocks');
    await call("Runtime.evaluate",{contextId:world.executionContextId,returnByValue:true,expression:`(()=>{
      document.querySelectorAll('#optionGroups [data-action="invokeOption"]')
        .forEach(button=>{if(button.dataset.optionId.startsWith('skin.'))button.click()});
      return true;
    })()`});
    const skinPackageMocks=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<100;i++){
        const text=document.querySelector('#bridgeLog').textContent;
        if(text.includes('skin.import')&&text.includes('未读取文件或安装皮肤')&&
          text.includes('skin.exportClear')&&text.includes('未读取素材或创建文件'))return true;
        await new Promise(r=>setTimeout(r,100));
      }
      return false;
    })()`});
    if(skinPackageMocks.exceptionDetails)throw Error(skinPackageMocks.exceptionDetails.exception?.description || skinPackageMocks.exceptionDetails.text);
    if(!skinPackageMocks.result.value)throw Error('skin package picker actions did not reach their non-writing Host Mocks');
    await call("Runtime.evaluate",{contextId:world.executionContextId,returnByValue:true,expression:`(()=>{
      document.querySelector('#optionGroups [data-action="selectSkin"]').click();
      document.querySelector('#optionGroups [data-action="deleteSkin"]').click();
      return true;
    })()`});
    const skinMocks=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
      for(let i=0;i<100;i++){
        const text=document.querySelector('#bridgeLog').textContent;
        if(text.includes('请求 Host 确认启用 powercal-default')&&
          text.includes('请求 Host 确认删除 studio-demo-skin'))return {matched:true,text};
        await new Promise(r=>setTimeout(r,100));
      }
      return {matched:false,text:document.querySelector('#bridgeLog')?.textContent||'<missing bridge log>'};
    })()`});
    if(skinMocks.exceptionDetails)throw Error(skinMocks.exceptionDetails.exception?.description || skinMocks.exceptionDetails.text);
    if(!skinMocks.result.value.matched)throw Error('skin management Host Mocks missing: '+skinMocks.result.value.text);
    const reminderActions=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,20))}throw Error(m)};
        const reminders=[...document.querySelectorAll('#optionGroups [data-action="invokeOption"]')]
          .filter(button=>button.dataset.optionId.startsWith('reminders.'));
        reminders[0].click();reminders[1].click();reminders[2].click();
        await wait(()=>document.querySelector('#reminderReconcileStatus')?.dataset.status==='RUNNING',
          'reconcile did not expose RUNNING status');
        const runningFeedback=document.querySelector('#reminderReconcileStatus').textContent;
        await wait(()=>document.querySelector('#reminderReconcileStatus')?.dataset.status==='SUCCEEDED',
          'successful mock reconcile did not finish');
        const successFeedback=document.querySelector('#reminderReconcileStatus').textContent;
        if(!successFeedback.includes('阻止 1 项单独统计')||
          !successFeedback.includes('待处理 0 项')||!successFeedback.includes('失败 0 项'))
          throw Error('success must require zero pending/failed and report blocked items separately: '+successFeedback);
        return {reminderHostActions:reminders.length,runningFeedback,successFeedback};
      })()`});
    if(reminderActions.exceptionDetails)throw Error(reminderActions.exceptionDetails.exception?.description || reminderActions.exceptionDetails.text);
    const reminderActionLog=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const text=document.querySelector('#bridgeLog')?.textContent||'';
      return {exactAlarm:text.includes('未打开真实系统设置'),
        notification:text.includes('未弹真实权限对话框'),
        reconcile:text.includes('没有调用 Android 提醒调度器')&&text.includes('没有读取或写入数据库')};})()`});
    if(reminderActionLog.exceptionDetails)throw Error(reminderActionLog.exceptionDetails.exception?.description || reminderActionLog.exceptionDetails.text);
    if(!reminderActionLog.result.value.exactAlarm||!reminderActionLog.result.value.notification||
      !reminderActionLog.result.value.reconcile)
      throw Error('Reminder settings actions did not reach their explicit Studio Host Mocks: '+JSON.stringify(reminderActionLog.result.value));
    for(const [scenario,expected] of [["reminder-pending","待处理 1 项"],["reminder-failed","失败 1 项"]]){
      const selected=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
        const select=document.querySelector('#scenario');select.value=${JSON.stringify(scenario)};
        select.dispatchEvent(new Event('change',{bubbles:true}));return simulatedState();})()`});
      if(selected.exceptionDetails)throw Error(selected.exceptionDetails.exception?.description || selected.exceptionDetails.text);
      const projected=selected.result.value;
      if(projected.reminderReconcile?.status!=="FAILED"||
        (scenario==="reminder-pending"&&projected.reminderReconcile.pending!==1)||
        (scenario==="reminder-failed"&&projected.reminderReconcile.failed!==1))
        throw Error("Failure scenario has incorrect reconcile counts: "+JSON.stringify(projected.reminderReconcile));
      const visible=await call("Runtime.evaluate",{contextId:world.executionContextId,
        awaitPromise:true,returnByValue:true,expression:`(async()=>{
          for(let i=0;i<100;i++){
            const live=document.querySelector('#reminderReconcileStatus');
            if(live?.dataset.status==='FAILED'&&live.textContent.includes(${JSON.stringify(expected)}))
              return {status:live.dataset.status,text:live.textContent};
            await new Promise(r=>setTimeout(r,50));
          }
          throw Error('FAILED status feedback missing for '+${JSON.stringify(scenario)});
        })()`});
      if(visible.exceptionDetails)throw Error(visible.exceptionDetails.exception?.description || visible.exceptionDetails.text);
    }
    await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const select=document.querySelector('#scenario');select.value='reminder-truncated';
      select.dispatchEvent(new Event('change',{bubbles:true}));
      return true;
    })()`});
    const truncatedReminder=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,20))}throw Error(m)};
        const action=[...document.querySelectorAll('#optionGroups [data-action="invokeOption"]')]
          .find(button=>button.dataset.optionId==='reminders.reconcile');
        if(!action)throw Error('reconcile action missing in truncated scenario');
        action.click();
        await wait(()=>document.querySelector('#reminderReconcileStatus')?.dataset.status==='RUNNING',
          'truncated reconcile did not enter RUNNING');
        await new Promise(r=>setTimeout(r,350));
        const live=document.querySelector('#reminderReconcileStatus');
        return {status:live?.dataset.status||null,text:live?.textContent||null,
          truncatedFeedback:Boolean(live?.textContent.includes('达到处理上限')&&
            live?.textContent.includes('仍有提醒未检查'))};
      })()`});
    if(truncatedReminder.exceptionDetails)
      throw Error(truncatedReminder.exceptionDetails.exception?.description || truncatedReminder.exceptionDetails.text);
    const truncatedContract=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const result=simulatedState().reminderReconcile;
      return {valid:result?.status==='FAILED'&&result.truncated===true&&result.failed===0&&result.pending===0,
        result,scenario:document.querySelector('#scenario')?.value,
        bridgeLog:document.querySelector('#bridgeLog')?.textContent.slice(-900)};
    })()`});
    if(truncatedContract.exceptionDetails)
      throw Error(truncatedContract.exceptionDetails.exception?.description || truncatedContract.exceptionDetails.text);
    if(truncatedContract.result.value.valid!==true)throw Error('truncated Mock payload does not match the Host status contract: '+JSON.stringify(truncatedContract.result.value));
    if(!truncatedReminder.result.value.truncatedFeedback||truncatedReminder.result.value.status!=='FAILED')
      throw Error('truncated batch feedback mismatch: '+JSON.stringify({view:truncatedReminder.result.value,
        state:truncatedContract.result.value}));
    const unavailableState=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const select=document.querySelector('#scenario');select.value='reminder-unavailable';
      select.dispatchEvent(new Event('change',{bubbles:true}));return simulatedState();})()`});
    if(unavailableState.exceptionDetails)throw Error(unavailableState.exceptionDetails.exception?.description || unavailableState.exceptionDetails.text);
    if(unavailableState.result.value.page!=="SETTINGS"||
      unavailableState.result.value.reminderDiagnostics!==null||
      unavailableState.result.value.reminderReconcile!==null)
      throw Error("Studio unavailable scenario must preserve the nullable settings projection contract");
    const unavailableVisible=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<100;i++){
          const live=document.querySelector('#reminderReconcileStatus');
          const text=document.querySelector('.reminderDiagnostics')?.textContent||'';
          if(live?.dataset.status==='UNAVAILABLE'&&live.textContent.includes('尚未提供提醒同步状态')&&
            text.includes('尚未提供逐项提醒诊断'))return true;
          await new Promise(r=>setTimeout(r,50));
        }
        throw Error('null reminder projections were not shown as unavailable');
      })()`});
    if(unavailableVisible.exceptionDetails)throw Error(unavailableVisible.exceptionDetails.exception?.description || unavailableVisible.exceptionDetails.text);
    const resetReminderScenario=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const select=document.querySelector('#scenario');select.value='repeating';
      select.dispatchEvent(new Event('change',{bubbles:true}));
      const state=simulatedState();return state.page==='SETTINGS'&&state.reminderReconcile?.status==='IDLE';})()`});
    if(resetReminderScenario.exceptionDetails)throw Error(resetReminderScenario.exceptionDetails.exception?.description || resetReminderScenario.exceptionDetails.text);
    if(resetReminderScenario.result.value!==true)throw Error("Reminder E2E did not restore its baseline Studio scenario");
    await call("Runtime.evaluate",{contextId:world.executionContextId,awaitPromise:true,
      returnByValue:true,expression:`(async()=>{
        document.querySelector('#optionGroups [data-action="settings"]').click();
        document.querySelector('.clear-tabbar [data-action="timeline"]').click();
        for(let i=0;i<100;i++){
          if(!document.querySelector('#timelinePage').hidden)return true;
          await new Promise(r=>setTimeout(r,50));
        }
        throw Error('timeline route did not leave settings');
      })()`});
    const outsideSettings=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const state=simulatedState();return {page:state.page,
        hasDiagnostics:Object.prototype.hasOwnProperty.call(state,'reminderDiagnostics'),
        hasReconcile:Object.prototype.hasOwnProperty.call(state,'reminderReconcile')};})()`});
    if(outsideSettings.exceptionDetails)throw Error(outsideSettings.exceptionDetails.exception?.description || outsideSettings.exceptionDetails.text);
    if(outsideSettings.result.value.hasDiagnostics||outsideSettings.result.value.hasReconcile)
      throw Error("Reminder diagnostics must only be projected on the skin settings page");
    const settingsLog=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      const text=document.querySelector('#bridgeLog').textContent;
      return {nativeToolsMock:text.includes('Android 宿主工具页')};})()`});
    if(settingsLog.exceptionDetails)throw Error(settingsLog.exceptionDetails.exception?.description || settingsLog.exceptionDetails.text);
    if(!settingsLog.result.value.nativeToolsMock)
      throw Error("Settings actions did not reach the explicit Studio Host Mock: "+JSON.stringify(settingsLog.result.value));
    console.log("BROWSER SKIN SETTINGS MOCK PASS",JSON.stringify({...settings.result.value,
      reminderContract:contract,privateFieldsNotRendered:settings.result.value.reminderPrivateTextAbsent,
      ...reminderActions.result.value,settingsOnlyProjection:true,
      pendingFailureShown:true,exceptionFailureShown:true,
      truncatedBatchShown:truncatedReminder.result.value}));
    const conversion=await call("Runtime.evaluate",{contextId:world.executionContextId,awaitPromise:true,returnByValue:true,expression:`(async()=>{
      const check=(v,m)=>{if(!v)throw Error(m)};
      const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
      const chooserKinds=[];
      for(const kind of ['ALARM','ACTIVITY','DEADLINE','MEMO']){
        document.querySelector('[data-action="createDraft"]').click();
        await wait(()=>document.querySelector('#timelineCreateDialog[open]'),kind+' chooser missing');
        const choice=document.querySelector('[data-timeline-create-kind="'+kind+'"]');
        check(choice,'chooser omitted '+kind);
        choice.click();
        await wait(()=>!document.querySelector('#skinSheet').hidden&&
          document.querySelector('#sheetTitle')?.textContent==='待处理草稿',kind+' draft editor missing');
        check(document.querySelector('#sheetFields [data-field="kind"]')?.value===kind,
          'Studio Mock opened the wrong kind for '+kind);
        await wait(()=>document.querySelector('#sheetStatus')?.textContent==='7001',
          'Studio Mock did not return deck.create receipt for '+kind);
        document.querySelector('#sheetClose').click();
        await wait(()=>document.querySelector('#skinSheet').hidden,kind+' transient preview draft did not close');
        chooserKinds.push(kind);
      }
      document.querySelector('[data-action="createDraft"]').click();
      await wait(()=>document.querySelector('#timelineCreateDialog[open]'),'undo create chooser missing');
      document.querySelector('[data-timeline-create-kind="MEMO"]').click();
      await wait(()=>!document.querySelector('#skinSheet').hidden&&
        document.querySelector('#sheetTitle')?.textContent==='待处理草稿','undo draft editor missing');
      const undoTitle=document.querySelector('#sheetFields [data-field="title"]');
      undoTitle.value='Studio undo replay';undoTitle.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('#sheetSubtitle')?.textContent.includes('尚未暂存'),
        'draft patch did not reach the Studio Mock');
      document.querySelector('[data-do="discard"]').click();
      await wait(()=>document.querySelector('[data-do="discard"]')?.textContent.includes('再次点击'),
        'discard did not require the second explicit tap');
      document.querySelector('[data-do="discard"]').click();
      await wait(()=>document.querySelector('[data-do="undo"]'),
        'discard did not retain the undo action in Studio preview');
      document.querySelector('[data-do="undo"]').click();
      await wait(()=>!document.querySelector('[data-do="undo"]'),
        'undo did not clear the one-step undo action');
      check(!document.querySelector('#skinSheet').hidden,
        'undo unexpectedly closed the draft deck');
      check(document.querySelector('#sheetFields [data-field="title"]')?.value==='Studio undo replay',
        'undo did not restore the discarded draft contents');
      document.querySelector('#sheetClose').click();
      await wait(()=>document.querySelector('#skinSheet').hidden,'undo replay deck did not close');
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
      return {chooserKinds,deckUndo:'discard→undo restored the staged contents',
        deckConversion:'ACTIVITY→DEADLINE',formalConversion:'ACTIVITY→ALARM',key};
    })()`});
    if(conversion.exceptionDetails)throw Error(conversion.exceptionDetails.exception?.description || conversion.exceptionDetails.text);
    if(JSON.stringify(conversion.result.value.chooserKinds)!==JSON.stringify(['ALARM','ACTIVITY','DEADLINE','MEMO']))
      throw Error("Studio chooser did not preview all four kinds: "+JSON.stringify(conversion.result.value));
    console.log("BROWSER FOUR-KIND CREATE MOCK PASS",JSON.stringify(conversion.result.value.chooserKinds));
    if(conversion.result.value.deckUndo!=='discard→undo restored the staged contents')
      throw Error('Studio preview did not preserve the deck discard/undo result');
    console.log('BROWSER DECK DISCARD/UNDO MOCK PASS',conversion.result.value.deckUndo);
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

    const runDeckResolution=async(scenario,action,expectedHostText,label)=>{
      await call("Runtime.evaluate",{expression:`(()=>{const e=document.querySelector('#scenario');e.value='${scenario}';e.dispatchEvent(new Event('change',{bubbles:true}))})()`,returnByValue:true});
      const requested=await call("Runtime.evaluate",{contextId:world.executionContextId,
        awaitPromise:true,returnByValue:true,expression:`(async()=>{
          const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
          document.querySelector('[data-action="createDraft"]').click();
          await wait(()=>document.querySelector('#timelineCreateDialog[open]'),'deck resolution create chooser missing');
          document.querySelector('[data-timeline-create-kind="MEMO"]').click();
          await wait(()=>!document.querySelector('#skinSheet').hidden&&
            document.querySelector('#sheetTitle').textContent==='待处理草稿','deck resolution draft editor missing');
          document.querySelector('#sheetSave').click();
          await wait(()=>document.querySelector('[data-do="${action}"]'),'${label} state did not expose its Host review action');
          document.querySelector('[data-do="${action}"]').click();
          return true;
        })()`});
      if(requested.exceptionDetails)throw Error(requested.exceptionDetails.exception?.description || requested.exceptionDetails.text);
      const hostPrompt=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<150;i++){
          const prompt=document.querySelector('#mockApproval');
          if(prompt&&!prompt.hidden){
            const text=document.querySelector('#mockApprovalText').textContent;
            if(!text.includes('${expectedHostText}'))throw Error('${label} did not request the expected Host review: '+text);
            if(!text.includes('不会写入真实数据'))throw Error('Studio Host review mock boundary was not disclosed');
            return true;
          }
          await new Promise(r=>setTimeout(r,100));
        }
        throw Error('${label} Host review did not open');
      })()`});
      if(hostPrompt.exceptionDetails)throw Error(hostPrompt.exceptionDetails.exception?.description || hostPrompt.exceptionDetails.text);
      await call("Runtime.evaluate",{expression:"document.querySelector('#mockReject').click()",returnByValue:true});
      const cancelled=await call("Runtime.evaluate",{contextId:world.executionContextId,
        awaitPromise:true,returnByValue:true,expression:`(async()=>{
          const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
          await wait(()=>document.querySelector('#sheetStatus').textContent.includes('已取消宿主操作'),
            '${label} cancellation did not return to Clear');
          if(document.querySelector('#skinSheet').hidden)throw Error('${label} cancel discarded the draft deck');
          await wait(()=>document.querySelector('[data-do="${action}"]')===null,
            '${label} cancel did not dismiss the stale prompt');
          document.querySelector('#sheetSave').click();
          await wait(()=>document.querySelector('[data-do="${action}"]'),'${label} retry did not restore the review action');
          document.querySelector('[data-do="${action}"]').click();
          return true;
        })()`});
      if(cancelled.exceptionDetails)throw Error(cancelled.exceptionDetails.exception?.description || cancelled.exceptionDetails.text);
      const secondPrompt=await call("Runtime.evaluate",{awaitPromise:true,returnByValue:true,expression:`(async()=>{
        for(let i=0;i<150;i++){if(!document.querySelector('#mockApproval').hidden)return true;await new Promise(r=>setTimeout(r,100))}
        throw Error('${label} repeated Host review did not open');
      })()`});
      if(secondPrompt.exceptionDetails)throw Error(secondPrompt.exceptionDetails.exception?.description || secondPrompt.exceptionDetails.text);
      await call("Runtime.evaluate",{expression:"document.querySelector('#mockApprove').click()",returnByValue:true});
      const approved=await call("Runtime.evaluate",{contextId:world.executionContextId,
        awaitPromise:true,returnByValue:true,expression:`(async()=>{
          for(let i=0;i<150;i++){
            if(document.querySelector('#skinSheet').hidden)return document.querySelector('#sheetStatus').textContent;
            await new Promise(r=>setTimeout(r,100));
          }
          throw Error('${label} Host approval did not close the preview deck');
        })()`});
      if(approved.exceptionDetails)throw Error(approved.exceptionDetails.exception?.description || approved.exceptionDetails.text);
      return {scenario,label,cancelledDraftRetained:true,hostMockOnly:true};
    };
    const deckConflict=await runDeckResolution('deck-conflict','reviewConflict',
      '完整当前版本与草稿差异','版本冲突');
    const deckRestore=await runDeckResolution('deck-restore','reviewRestore',
      '确认恢复同一事件序号','删除事件恢复');
    console.log('BROWSER DECK TRUSTED CONFLICT/RESTORE MOCK PASS',JSON.stringify([deckConflict,deckRestore]));

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
    await call("Runtime.evaluate",{expression:`(()=>{const e=document.querySelector('#scenario');e.value='ai-error';e.dispatchEvent(new Event('change',{bubbles:true}))})()`,returnByValue:true});
    const chatScrollSeed=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      mockPage='AI';
      mockAiErrorMessages=Array.from({length:18},(_,index)=>({
        role:index%2===0?'assistant':'user',
        content:'历史消息 '+index+'：用于验证长列表阅读位置保持。'.repeat(3)
      }));
      postState();return mockAiErrorMessages.length;
    })()`});
    if(chatScrollSeed.exceptionDetails)throw Error(chatScrollSeed.exceptionDetails.exception?.description || chatScrollSeed.exceptionDetails.text);
    const chatScroll=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const check=(v,m)=>{if(!v)throw Error(m)};
        const wait=async(fn,m)=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        const list=document.querySelector('#chatMessages');
        list.style.height='260px';list.style.maxHeight='260px';list.style.minHeight='0';list.style.flex='none';
        await wait(()=>list.querySelectorAll('.chat-bubble').length===19,'long chat history did not load');
        list.scrollTop=220;await new Promise(r=>setTimeout(r,50));
        const before=list.scrollTop,firstVisible=[...list.querySelectorAll('.chat-bubble')]
          .find(item=>item.getBoundingClientRect().bottom>list.getBoundingClientRect().top)?.textContent;
        check(before>0&&before<list.scrollHeight-list.clientHeight-80,'test did not enter historical reading position');
        return {before,firstVisible};
      })()`});
    if(chatScroll.exceptionDetails)throw Error(chatScroll.exceptionDetails.exception?.description || chatScroll.exceptionDetails.text);
    const chatScrollStart=chatScroll.result.value;
    await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{mockMemoDone=!mockMemoDone;postState();return true})()`});
    const unrelatedUpdate=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        await new Promise(r=>setTimeout(r,50));
        const list=document.querySelector('#chatMessages');
        const visible=[...list.querySelectorAll('.chat-bubble')]
          .find(item=>item.getBoundingClientRect().bottom>list.getBoundingClientRect().top)?.textContent;
        return {scrollTop:list.scrollTop,visible};
      })()`});
    if(unrelatedUpdate.exceptionDetails)throw Error(unrelatedUpdate.exceptionDetails.exception?.description || unrelatedUpdate.exceptionDetails.text);
    if(Math.abs(unrelatedUpdate.result.value.scrollTop-chatScrollStart.before)>1||
      unrelatedUpdate.result.value.visible!==chatScrollStart.firstVisible)
      throw Error('reading history jumped after an unrelated host-state update: '+JSON.stringify(unrelatedUpdate.result.value));
    const appendHistory=await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      mockAiErrorMessages=[...mockAiErrorMessages,{role:'assistant',content:'追加的新回复'}];postState();return true;
    })()`});
    if(appendHistory.exceptionDetails)throw Error(appendHistory.exceptionDetails.exception?.description || appendHistory.exceptionDetails.text);
    const chatScrollHistorical=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        const list=document.querySelector('#chatMessages');await wait(()=>list.querySelectorAll('.chat-bubble').length===20,'new chat reply did not render');
        const visible=[...list.querySelectorAll('.chat-bubble')]
          .find(item=>item.getBoundingClientRect().bottom>list.getBoundingClientRect().top)?.textContent;
        return {scrollTop:list.scrollTop,scrollHeight:list.scrollHeight,clientHeight:list.clientHeight,visible};
      })()`});
    if(chatScrollHistorical.exceptionDetails)throw Error(chatScrollHistorical.exceptionDetails.exception?.description || chatScrollHistorical.exceptionDetails.text);
    const historical=chatScrollHistorical.result.value;
    if(Math.abs(historical.scrollTop-chatScrollStart.before)>1||historical.visible!==chatScrollStart.firstVisible)
      throw Error('reading history jumped after a new message: '+JSON.stringify({start:chatScrollStart,after:historical}));
    const chatScrollScreenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:true});
    fs.writeFileSync(path.join(reportDir,"studio-chat-scroll-history.png"),
      Buffer.from(chatScrollScreenshot.data,"base64"));
    const followBottom=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const list=document.querySelector('#chatMessages');list.scrollTop=list.scrollHeight;
        const before=list.scrollTop;
        return before;
      })()`});
    if(followBottom.exceptionDetails)throw Error(followBottom.exceptionDetails.exception?.description || followBottom.exceptionDetails.text);
    await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{mockAiErrorMessages=[...mockAiErrorMessages,
      {role:'assistant',content:'底部的新回复'}];postState()})()`});
    const bottomFollow=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<100;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        const list=document.querySelector('#chatMessages');await wait(()=>list.querySelectorAll('.chat-bubble').length===21,'bottom reply did not render');
        return {scrollTop:list.scrollTop,maximum:Math.max(0,list.scrollHeight-list.clientHeight)};
      })()`});
    if(bottomFollow.exceptionDetails)throw Error(bottomFollow.exceptionDetails.exception?.description || bottomFollow.exceptionDetails.text);
    if(bottomFollow.result.value.maximum-bottomFollow.result.value.scrollTop>1)
      throw Error('chat did not follow a new reply while already at the bottom: '+JSON.stringify(bottomFollow.result.value));
    console.log('BROWSER CHAT SCROLL ANCHOR PASS',JSON.stringify({historical,followBottom:bottomFollow.result.value}));
    await call("Runtime.evaluate",{returnByValue:true,expression:`(()=>{
      mockAiErrorMessages=[
        {role:'user',content:'请帮我概括明天的安排。'},
        {role:'assistant',content:'Studio Mock：AI 请求未完成（模拟 HTTP 503）；上下文已保留，可编辑后手动重发。'}
      ];postState();return true;
    })()`});
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
        const inputLimit=field.maxLength;
        check(inputLimit===6000,'Clear composer does not expose the native 6000-unit limit: '+inputLimit);
        const boundary='x'.repeat(6000);
        field.value=boundary;field.dispatchEvent(new Event('input',{bubbles:true}));
        document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        await wait(()=>list.textContent.includes(boundary),'Clear/Studio rejected a 6000-unit chat message');
        const afterBoundary=list.textContent;
        field.value='x'.repeat(6001);
        document.querySelector('#chatForm').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));
        await new Promise(r=>setTimeout(r,100));
        check(list.textContent===afterBoundary,'Clear/Studio accepted a message above the native limit');
        return {scenario:'AI HTTP 503 then explicit composer resend',providerCalled:false,calendarWrites:false,
          textLimitUtf16:inputLimit,acceptedAtLimit:6000,rejectedAboveLimit:true};
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
    fs.writeFileSync(path.join(reportDir,"ai-review-studio-mock.png"),Buffer.from(aiScreenshot.data,"base64"));
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
    await call("Runtime.evaluate",{expression:`(()=>{const e=document.querySelector('#scenario');
      e.value='ai-review-failure-retry';e.dispatchEvent(new Event('change',{bubbles:true}))})()`,returnByValue:true});
    const failedPublishRetry=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        await wait(()=>document.querySelectorAll('.ai-proposal-item').length===4,'retry scenario review cards missing');
        document.querySelectorAll('.ai-proposal-group')[0]
          .querySelectorAll('.ai-proposal-actions button')[0].click();
        return true;
      })()`});
    if(failedPublishRetry.exceptionDetails)
      throw Error(failedPublishRetry.exceptionDetails.exception?.description || failedPublishRetry.exceptionDetails.text);
    const firstRetryApproval=await call("Runtime.evaluate",{expression:`!document.querySelector('#mockApproval').hidden`,returnByValue:true});
    if(firstRetryApproval.result.value!==true)throw Error('first retry-scenario Host confirmation missing');
    await call("Runtime.evaluate",{expression:"document.querySelector('#mockApprove').click()",returnByValue:true});
    const failureShown=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        await wait(()=>document.querySelector('#chatMessages').textContent.includes('模拟 Host 写入失败'),
          'simulated publish failure was not shown');
        if(document.querySelectorAll('.ai-proposal-item').length!==4)
          throw Error('failed publish must retain every proposal');
        document.querySelectorAll('.ai-proposal-group')[0]
          .querySelectorAll('.ai-proposal-actions button')[0].click();
        return true;
      })()`});
    if(failureShown.exceptionDetails)
      throw Error(failureShown.exceptionDetails.exception?.description || failureShown.exceptionDetails.text);
    const secondRetryApproval=await call("Runtime.evaluate",{expression:`!document.querySelector('#mockApproval').hidden`,returnByValue:true});
    if(secondRetryApproval.result.value!==true)throw Error('explicit retry Host confirmation missing');
    await call("Runtime.evaluate",{expression:"document.querySelector('#mockApprove').click()",returnByValue:true});
    const retryResolved=await call("Runtime.evaluate",{contextId:world.executionContextId,
      awaitPromise:true,returnByValue:true,expression:`(async()=>{
        const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
        await wait(()=>document.querySelectorAll('.ai-proposal-item').length===3&&
          document.querySelector('#chatMessages').textContent.includes('没有写入任何真实日历数据'),
          'second Host confirmation did not resolve the Mock proposal');
        return {firstConfirmation:'simulated failure',proposalsRetained:4,
          secondConfirmation:'resolved',realCalendarWrites:false};
      })()`});
    if(retryResolved.exceptionDetails)
      throw Error(retryResolved.exceptionDetails.exception?.description || retryResolved.exceptionDetails.text);
    console.log("BROWSER AI REVIEW MOCK PASS",JSON.stringify(aiReview.result.value));
    console.log("BROWSER AI HOST CONFIRM MOCK PASS; no real calendar data was written");
    console.log("BROWSER AI PUBLISH FAILURE RETRY MOCK PASS",JSON.stringify(retryResolved.result.value));
    socket.close();
  }finally{
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
