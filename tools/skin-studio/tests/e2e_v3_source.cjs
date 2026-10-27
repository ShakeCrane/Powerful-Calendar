/* Real browser source edit, preview and package round trip. No npm dependencies. */
const {spawn} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.join(root, ".local/android-test/reports/separation-20261027/source-editor");
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
      for(let i=0;i<150;i++){
        if(typeof loadPackage==='function' && document.querySelector('#skinName')?.value)break;
        await new Promise(r=>setTimeout(r,100));
      }
      const data=await (await fetch('/api/template-v3')).json();
      data.manifest.id='restricted-source-test'; data.manifest.capabilities=['host.read'];
      const meta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(data.files['project.json']),c=>c.charCodeAt(0))));
      delete meta.layers;meta.thirdPartyMetadata={keep:'原样保留'};
      data.files['project.json']=btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(meta)+'\\n')));
      await loadPackage(data); document.querySelector('#advancedMode').click();
      const select=document.querySelector('#extraSourceFile');
      check([...select.options].some(o=>o.value==='web/edit.js'),'edit.js missing');
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
      const preview=await (await post('/api/preview-v3',JSON.stringify(project),'application/json')).json();
      const previewText=await (await fetch(preview.url.replace('web/index.html','web/edit.js'))).text();
      check(previewText===field.value,'preview source differs');
      const zip=await (await post('/api/export-v3',JSON.stringify(project),'application/json')).arrayBuffer();
      const imported=await (await post('/api/import-v3',zip,'application/octet-stream')).json();
      check(JSON.stringify(imported.manifest.capabilities)==='["host.read"]','import capabilities changed');
      for(const [name,bytes] of Object.entries(project.files))
        check(imported.files[name]===bytes,'round trip changed '+name);
      await loadPackage(imported);document.querySelector('#advancedMode').click();
      select.value='web/edit.js';select.dispatchEvent(new Event('change'));
      check(field.value===before+'\\n// browser-source-roundtrip\\n','reimport edit missing');
      document.querySelector('#simpleMode').click();window.prompt=()=> 'customShade';
      document.querySelector('#addMaterial').click();
      const visual=payload();
      const visualMeta=JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(visual.files['project.json']),c=>c.charCodeAt(0))));
      check(visualMeta.thirdPartyMetadata.keep==='原样保留','custom metadata lost');
      check(visualMeta.slots.some(s=>s.id==='customShade'),'visual metadata not saved');
      const visualZip=await (await post('/api/export-v3',JSON.stringify(visual),'application/json')).arrayBuffer();
      const visualImported=await (await post('/api/import-v3',visualZip,'application/octet-stream')).json();
      check(visualImported.files['project.json']===visual.files['project.json'],'visual metadata roundtrip changed');
      return {file:'web/edit.js', files:Object.keys(project.files).length, capabilities:imported.manifest.capabilities};
    })()`;
    const result=await call("Runtime.evaluate",{expression,awaitPromise:true,returnByValue:true});
    if(result.exceptionDetails)throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    console.log("BROWSER SOURCE ROUNDTRIP PASS",JSON.stringify(result.result.value));
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
    await call("Page.enable");
    const tree=await call("Page.getFrameTree");
    const child=tree.frameTree.childFrames?.find(frame=>frame.frame.url.includes("/preview/"));
    if(!child)throw Error("Preview frame not found: "+JSON.stringify(tree.frameTree));
    const world=await call("Page.createIsolatedWorld",{frameId:child.frame.id,worldName:"powercal-source-qa"});
    const recurrence=await call("Runtime.evaluate",{contextId:world.executionContextId,awaitPromise:true,returnByValue:true,expression:`(async()=>{
      const check=(v,m)=>{if(!v)throw Error(m)};
      const wait=async(fn,m)=>{for(let i=0;i<150;i++){if(fn())return;await new Promise(r=>setTimeout(r,100))}throw Error(m)};
      await wait(()=>document.querySelector('[data-original-occurrence-key]'),'repeating event missing');
      const event=document.querySelector('[data-original-occurrence-key]');
      const key=event.dataset.originalOccurrenceKey;event.click();
      await wait(()=>!document.querySelector('#skinSheet').hidden,'editor not opened');
      check(document.querySelector('#sheetSave').disabled,'save enabled before scope');
      check(!document.querySelector('#sheetFields [data-field="title"]'),'fields visible before scope');
      document.querySelector('[data-do="scope"][data-scope="SINGLE_OCCURRENCE"]').click();
      await wait(()=>document.querySelector('[data-scope="SINGLE_OCCURRENCE"][aria-pressed="true"]'),'scope not selected');
      check(!document.querySelector('#sheetFields [data-field="notes"]'),'single scope exposed notes');
      const title=document.querySelector('#sheetFields [data-field="title"]');
      title.value='单次修改';title.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('[data-do="discardChanges"]'),'dirty state missing');
      check(document.querySelector('[data-scope="WHOLE_SERIES"]').disabled,'dirty scope switch enabled');
      document.querySelector('[data-do="discardChanges"]').click();
      await wait(()=>!document.querySelector('[data-do="discardChanges"]'),'discard did not clear dirty state');
      document.querySelector('[data-do="scope"][data-scope="THIS_AND_FUTURE"]').click();
      await wait(()=>document.querySelector('[data-scope="THIS_AND_FUTURE"][aria-pressed="true"]'),'future scope not selected');
      const future=document.querySelector('#sheetFields [data-field="title"]');
      future.value='后续修改';future.dispatchEvent(new Event('change',{bubbles:true}));
      await wait(()=>document.querySelector('[data-do="discardChanges"]'),'future dirty state missing');
      document.querySelector('#sheetSave').click();
      await wait(()=>document.querySelector('#sheetStatus').textContent.includes('宿主确认'),'host review not requested');
      check(!document.querySelector('#skinSheet').hidden,'review request closed before approval');
      return {key,scope:'single, discard, future'};
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
    const evidenceDir=path.join(root,".local/android-test/reports/separation-20261027/ai-proposal-review");
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
    console.log("BROWSER AI REVIEW MOCK PASS",JSON.stringify(aiReview.result.value));
    console.log("BROWSER AI HOST CONFIRM MOCK PASS; no real calendar data was written");
    socket.close();
  }finally{
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
