/* Dependency-free Chrome/CDP month selection replay against the canonical Clear package. */
const {spawn}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),readline=require("node:readline");
const root=path.resolve(__dirname,"../../..");
const chrome=process.env.CHROME_PATH||"C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const bundle=path.join(root,"app/src/main/assets/powercal-clear-default.pcskin");
const reportDir=path.resolve(process.env.POWERCAL_E2E_REPORT_DIR||
  path.join(root,".local/android-test/reports/separation-s0-timeline-drag/month-selection-cdp"));
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
    const waitParent=async(expression,label)=>{
      for(let i=0;i<150;i++){
        const result=await evaluate(expression);
        if(result)return result;
        await delay(100);
      }
      throw Error(label+" timed out");
    };
    await waitParent("document.readyState==='complete'&&typeof loadPackage==='function'",
      "Studio startup");
    const packageBase64=fs.readFileSync(bundle).toString("base64");
    const loaded=await evaluate(`(async()=>{
      const bytes=Uint8Array.from(atob("${packageBase64}"),c=>c.charCodeAt(0));
      const response=await fetch('/api/import-v3',{method:'POST',
        headers:{'Content-Type':'application/octet-stream'},body:bytes});
      if(!response.ok)throw Error('Canonical .pcskin import returned '+response.status);
      const skin=await response.json();
      if(skin.manifest.id!=='powercal-clear-default')throw Error('Wrong package identity');
      await loadPackage(skin);
      return {id:skin.manifest.id,fileCount:Object.keys(skin.files).length};
    })()`);
    if(loaded?.id!=="powercal-clear-default")throw Error("Canonical Clear did not load in Studio");
    await waitParent("document.querySelector('#previewState')?.textContent.includes('连接成功')",
      "Studio Preview Host connection");
    await evaluate("document.querySelector('#webPreview').scrollIntoView({block:'center'})");
    let frame;
    for(let i=0;i<150&&!frame;i++){
      const tree=await call("Page.getFrameTree");
      frame=tree.frameTree.childFrames?.find(item=>item.frame.url.includes('/preview/'))?.frame;
      if(!frame)await delay(100);
    }
    if(!frame)throw Error("Studio Preview iframe was not created");
    const world=await call("Page.createIsolatedWorld",{frameId:frame.id,worldName:"month-selection-qa"});
    const frameEval=(expression)=>evaluate(expression,{contextId:world.executionContextId});
    const waitFrame=async(expression,label)=>{
      for(let i=0;i<150;i++){
        const result=await frameEval(expression);
        if(result){return result}
        await delay(100);
      }
      throw Error(label+" timed out");
    };
    await frameEval("document.querySelector('.clear-tabbar [data-action=\\\"month\\\"]')?.click()");
    await waitFrame("!document.querySelector('#monthPage').hidden","Month navigation");
    const initial=await waitFrame(`(()=>{
      const selected=document.querySelector('.month-cell.selected');
      return selected?{month:document.querySelector('#monthTitle').textContent,
        date:selected.dataset.date,dayCount:document.querySelectorAll('.month-cell').length}:null;
    })()`,"Initial month state");
    if(initial.date!=="2026-10-22"||initial.dayCount!==31)
      throw Error("Unexpected initial month: "+JSON.stringify(initial));
    const initialCamera=await evaluate("simulatedState().timeline.anchorMillis");

    await frameEval("document.querySelector('.month-cell[data-date=\\\"2026-10-23\\\"]')?.click()");
    const selected=await waitFrame(`(()=>{
      const button=document.querySelector('.month-cell.selected');
      return button?.dataset.date==='2026-10-23'&&
        !document.querySelector('#monthPage').hidden&&
        document.querySelector('#monthAgenda h2')?.textContent==='2026-10-23'
        ?{date:button.dataset.date,ariaPressed:button.getAttribute('aria-pressed')}:null;
    })()`,"In-month date selection");
    if(await evaluate("simulatedState().timeline.anchorMillis")!==initialCamera)
      throw Error("Selecting a day changed the timeline camera");

    await frameEval("document.querySelector('#monthTitle')?.click()");
    await waitFrame("document.querySelector('#monthPickerDialog').open","Month/year picker opening");
    await frameEval(`(()=>{
      const year=document.querySelector('#monthYear');year.value='2027';
      year.dispatchEvent(new Event('input',{bubbles:true}));
      document.querySelector('#monthNumber').value='4';
      document.querySelector('#monthPickerForm button[type=submit]').click();
    })()`);
    const picked=await waitFrame(`(()=>{
      const selected=document.querySelector('.month-cell.selected');
      return selected?.dataset.date==='2027-04-01'&&
        document.querySelector('#monthTitle').textContent.startsWith('2027-04')
        ?{month:'2027-04',date:selected.dataset.date}:null;
    })()`,"Year/month selection");
    if(await evaluate("simulatedState().timeline.anchorMillis")!==initialCamera)
      throw Error("Year/month selection changed the timeline camera");

    await frameEval("document.querySelector('.month-bar [data-delta-months=\\\"1\\\"]')?.click()");
    await waitFrame("document.querySelector('#monthTitle').textContent.startsWith('2027-05')",
      "Month arrow navigation");
    await frameEval(`(()=>{
      const grid=document.querySelector('#monthGrid'),rect=grid.getBoundingClientRect();
      const startX=rect.left+rect.width*.7,y=rect.top+rect.height*.5;
      grid.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,isPrimary:true,button:0,
        pointerId:71,clientX:startX,clientY:y}));
      document.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,isPrimary:true,button:0,
        pointerId:71,clientX:startX-110,clientY:y}));
      // Real browsers dispatch a click after pointerup; the month handler consumes it after a swipe.
      grid.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,view:window}));
    })()`);
    await waitFrame("document.querySelector('#monthTitle').textContent.startsWith('2027-06')",
      "Month swipe navigation");
    await frameEval("document.querySelector('.month-cell[data-date=\\\"2027-06-19\\\"]')?.click()");
    const june=await waitFrame(`(()=>{
      const selected=document.querySelector('.month-cell.selected');
      return selected?.dataset.date==='2027-06-19'?{
        date:selected.dataset.date,ariaPressed:selected.getAttribute('aria-pressed'),
        agenda:document.querySelector('#monthAgenda h2')?.textContent}:null;
    })()`,"Post-navigation date selection");
    if(june.agenda!=="2027-06-19"||
      await evaluate("simulatedState().timeline.anchorMillis")!==initialCamera)
      throw Error("Selected date did not update agenda independently of timeline camera");

    await frameEval("document.querySelector('#monthPage [data-action=\\\"createDraft\\\"]')?.click()");
    const createDate="2027-06-19";
    const created=await waitParent(`(()=>{
      const deck=simulatedState().editing.deck;
      return deck.open?{startText:deck.form.startText,
        log:document.querySelector('#bridgeLog')?.textContent||''}:null;
    })()`,"Create-on-selected-date Mock Host projection");
    if(!created.startText.startsWith(createDate)||!created.log.includes(createDate))
      throw Error(`Month Activity creation lost the selected date ${createDate}: `+
        JSON.stringify(created));
    if(await evaluate("simulatedState().timeline.anchorMillis")!==initialCamera)
      throw Error("Month Activity create changed the timeline camera");
    await frameEval("document.querySelector('#sheetClose')?.click()");
    await waitParent("simulatedState().editing.deck.open===false","Mock deck close");

    await frameEval("document.querySelector('#viewMonthDate')?.click()");
    await waitFrame(`document.querySelector('#monthPage').hidden&&
      !document.querySelector('#timelinePage').hidden`,"Explicit timeline date action");
    const juneCamera=await evaluate("simulatedState().timeline.anchorMillis");
    if(juneCamera!==new Date("2027-06-19T04:00:00Z").getTime())
      throw Error("Explicit month date action did not set the selected timeline day");
    await frameEval("document.querySelector('.clear-tabbar [data-action=\\\"month\\\"]')?.click()");
    await waitFrame(`!document.querySelector('#monthPage').hidden&&
      document.querySelector('#monthTitle').textContent.startsWith('2027-06')`,
      "Month reopen from selected timeline day");

    const clock=await evaluate("simulatedState().timeline");
    const expectedToday=new Intl.DateTimeFormat("en-CA",{
      timeZone:clock.zoneId,year:"numeric",month:"2-digit",day:"2-digit"
    }).format(new Date(clock.nowMillis));
    await frameEval("document.querySelector('#monthPage [data-action=\\\"today\\\"]')?.click()");
    const today=await waitFrame(`(()=>{
      const selected=document.querySelector('.month-cell.selected');
      return selected?.dataset.date==='${expectedToday}'&&!document.querySelector('#monthPage').hidden
        ?{expected:'${expectedToday}',selected:selected.dataset.date}:null;
    })()`,"Month Today selection");
    if(await evaluate("simulatedState().timeline.anchorMillis")!==juneCamera)
      throw Error("Month Today changed the timeline camera");

    const beforeTimeline=await frameEval(`(()=>({
      month:document.querySelector('#monthTitle').textContent,
      selected:document.querySelector('.month-cell.selected')?.dataset.date,
      selectedRect:(()=>{const r=document.querySelector('.month-cell.selected').getBoundingClientRect();
        return {left:r.left,top:r.top,right:r.right,bottom:r.bottom}})()
    }))()`);
    const screenshot=await call("Page.captureScreenshot",{format:"png",captureBeyondViewport:false});
    fs.writeFileSync(path.join(reportDir,"month-selected-studio-preview.png"),
      Buffer.from(screenshot.data,"base64"));
    const result={package:loaded,initial,initialCamera,selected,picked,june,today,beforeTimeline,
      juneCamera,
      createDate,created,finalPage:await frameEval(`({monthHidden:document.querySelector('#monthPage').hidden,
        timelineVisible:!document.querySelector('#timelinePage').hidden})`)};
    fs.writeFileSync(path.join(reportDir,"month-selection-result.json"),
      JSON.stringify(result,null,2)+"\n");
    console.log("CLEAR MONTH SELECTION CDP PASS",JSON.stringify(result));
    socket.close();
  }finally{
    if(socket)try{socket.close()}catch{}
    if(browser)browser.kill();
    server.kill();
  }
}
main().catch(error=>{console.error(error);process.exitCode=1});
