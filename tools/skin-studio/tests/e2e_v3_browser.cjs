/* Optional browser QA. Run with PUPPETEER_PATH and Skin Studio on 127.0.0.1:8875. */
const assert=require("node:assert/strict");
const os=require("node:os"),fs=require("node:fs"),path=require("node:path");
const puppeteer=require(process.env.PUPPETEER_PATH||"puppeteer");
const URL=process.env.STUDIO_URL||"http://127.0.0.1:8875";
const TEST_TEMP_ROOT=path.resolve(__dirname,"../../..",".local","android-test");
fs.mkdirSync(TEST_TEMP_ROOT,{recursive:true});
(async()=>{
  const browserProfile=fs.mkdtempSync(path.join(os.tmpdir(),"powercal-puppeteer-"));
  let browser;
  try{
    browser=await puppeteer.launch({headless:true,userDataDir:browserProfile,
      executablePath:process.env.CHROME_PATH||
        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
      args:["--no-sandbox","--disable-gpu"]});
    const page=await browser.newPage();
    page.on("pageerror",e=>console.error("PAGE ERROR",e.message));
    await page.setViewport({width:1460,height:960,deviceScaleFactor:1});
    await page.emulateMediaFeatures([{name:"prefers-color-scheme",value:"light"}]);
    await page.goto(URL,{waitUntil:"domcontentloaded"});
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"),{timeout:30000});
    let frame=page.frames().find(f=>f.url().includes("/preview/"));
    async function loadPreviewPackage(data){
      const previous=await page.$eval("#webPreview",iframe=>iframe.src);
      await page.evaluate(async value=>{await loadPackage(value)},data);
      await page.waitForFunction(old=>{
        const iframe=document.querySelector("#webPreview");
        return iframe.src!==old&&iframe.src.includes("/preview/")&&
          document.querySelector("#previewState").textContent.includes("加载中");
      },{},previous);
      await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
      return page.frames().find(candidate=>candidate.url().includes("/preview/"));
    }
    assert.ok(frame,"live source iframe must load");
    await frame.waitForSelector(".tEvent");
    let normal=await frame.evaluate(()=>({
      items:document.querySelectorAll(".tEvent").length,
      options:document.querySelectorAll(".optionRow").length,
      ruler:document.querySelectorAll(".timeTick").length,
      css:getComputedStyle(document.documentElement).getPropertyValue("--accent").trim()
    }));
    assert.ok(normal.items>=2);assert.equal(normal.options,2);assert.ok(normal.ruler>=8);
    const sides=await frame.evaluate(()=>({
      deadline:document.querySelector("#timelineLeft .tEvent.DEADLINE")?.textContent,
      alarm:document.querySelector("#timelineLeft .tEvent.ALARM")?.textContent,
      activity:document.querySelector("#timelineRight .tEvent.ACTIVITY")?.textContent
    }));
    assert.ok(sides.deadline,"Deadline must render in left wing");
    assert.ok(sides.alarm,"Alarm must render in left wing");
    assert.ok(sides.activity,"Activity must render in right wing");
    assert.ok(["#2A62E0","#5A8DFF"].includes(normal.css),
      "Clear light/dark source palette must load into the iframe");
    assert.equal(await frame.$eval('[data-action="createDraft"]',e=>e.getAttribute("aria-label")),
      "新建事件");
    console.log("Initial WYSIWYG preview",normal);

    // The Web UI must work through the same typed command envelopes as Android,
    // not by silently opening a native Compose editor.
    await page.click("#interactMode");
    await frame.click(".tEvent.ACTIVITY");
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden);
    assert.equal(await frame.$eval("#sheetTitle",e=>e.textContent),"编辑事件");
    await frame.$eval('#sheetFields [data-field="title"]',input=>{
      input.value="浏览器协议编辑验证";
      input.dispatchEvent(new Event("change",{bubbles:true}));
    });
    await frame.click("#sheetSave");
    await frame.waitForFunction(()=>
      document.querySelector("#sheetStatus").textContent.includes("事件已保存"));
    await frame.click("#sheetClose");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);
    await frame.click(".tEvent.ACTIVITY");
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden &&
      !!document.querySelector('#sheetActions [data-do="deleteEvent"]'));
    const deleteControl=await frame.$eval('#sheetActions [data-do="deleteEvent"]',button=>({
      text:button.textContent,aria:button.getAttribute("aria-label"),disabled:button.disabled
    }));
    assert.deepEqual(deleteControl,{text:"删除正式事件",aria:"删除正式事件",disabled:false});
    await frame.click('#sheetActions [data-do="deleteEvent"]');
    await page.waitForFunction(()=>!document.querySelector("#mockApproval").hidden &&
      document.querySelector("#bridgeLog").textContent.includes(
        "editor.delete → confirmation_required"));
    assert.ok((await page.$eval("#mockApprovalText",node=>node.textContent))
      .includes("不会写入真实数据"));
    await page.click("#mockReject");
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden &&
      !!document.querySelector('#sheetActions [data-do="deleteEvent"]') &&
      !document.querySelector('#sheetActions [data-do="deleteEvent"]').disabled);
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("editor.delete → cancelled"));
    await frame.click('#sheetActions [data-do="deleteEvent"]');
    await page.waitForFunction(()=>!document.querySelector("#mockApproval").hidden &&
      document.querySelector("#bridgeLog").textContent.includes(
        "editor.delete → confirmation_required"));
    await page.click("#mockApprove");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("editor.delete → deleted"));
    console.log("Clear formal delete routes through cancellable non-writing Studio Host Mock");
    await frame.click('[data-action="createDraft"]');
    await frame.waitForSelector("#timelineCreateDialog[open]");
    await frame.click('[data-timeline-create-kind="ACTIVITY"]');
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden &&
      document.querySelector("#sheetTitle").textContent==="待处理草稿");
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("deck.create · ACTIVITY → created"));
    await frame.$eval('#sheetFields [data-field="title"]',input=>{
      input.value="Web 草稿事务";
      input.dispatchEvent(new Event("change",{bubbles:true}));
    });
    await frame.click('#sheetActions [data-do="flush"]');
    await frame.waitForFunction(()=>
      document.querySelector("#sheetStatus").textContent.includes("修改已暂存"));
    await frame.click("#sheetSave");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);
    console.log("Clear Web editor and deck command round-trip PASS");

    for(const kind of ["ALARM","ACTIVITY","DEADLINE","MEMO"]){
      await frame.click('[data-action="createDraft"]');
      await frame.waitForSelector("#timelineCreateDialog[open]");
      await frame.click('[data-timeline-create-kind="'+kind+'"]');
      await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden &&
        document.querySelector("#sheetTitle").textContent==="待处理草稿");
      await page.waitForFunction(value=>document.querySelector("#bridgeLog").textContent
        .includes("deck.create · "+value+" → created"),{},kind);
      const labels=await frame.$$eval("#sheetFields .sheet-row .sheet-label",
        nodes=>nodes.map(node=>node.textContent));
      assert.ok(labels.includes("标题"),kind+" editor should expose its title");
      if(kind==="ALARM")assert.ok(labels.includes("开始时间")&&!labels.includes("结束时间"));
      if(kind==="ACTIVITY")assert.ok(labels.includes("开始时间")&&labels.includes("结束时间"));
      if(kind==="DEADLINE")assert.ok(!labels.includes("开始时间")&&
        labels.includes("截止日期或时刻"));
      if(kind==="MEMO")assert.ok(labels.includes("开始日期")&&labels.includes("结束日期")&&
        !labels.includes("跟踪完成状态"));
      await frame.click("#sheetClose");
      await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);
    }
    console.log("Timeline create chooser routes all four kinds through deck.create");
    await page.click("#editMode");

    await page.select("#scenario","future");
    await page.waitForFunction(()=>{
      return document.querySelector("#bridgeLog").textContent.includes("预览") ||
        document.querySelector("#previewState").textContent.includes("连接成功");
    });
    await frame.waitForFunction(()=>document.querySelectorAll(".optionRow").length===3);
    const future=await frame.evaluate(()=>document.querySelectorAll(".optionRow").length);
    assert.equal(future,3,"host-added parallel option must reuse the same row style");

    await page.click("#addText");
    await page.waitForFunction(()=>document.querySelector("#layerList").textContent.includes("装饰文字"));
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForSelector("[id^='pc-layer-']");
    const layer=await frame.evaluate(()=>({
      element:document.querySelector("[id^='pc-layer-']")?.outerHTML,
      left:getComputedStyle(document.querySelector("[id^='pc-layer-']")).left
    }));
    assert.ok(layer.element.includes("装饰文字"));
    assert.equal(layer.left,"24px");
    console.log("New layer exact-file preview",layer);

    // Material controls alter the very same CSS used by the exported package.
    const priorMaterialPreview=await page.$eval("#webPreview",e=>e.src);
    await page.$eval("#materialSlots input[type=color]",e=>{
      e.value="#ddffaa";e.dispatchEvent(new Event("input",{bubbles:true}));
    });
    await page.waitForFunction(previous=>{
      const iframe=document.querySelector("#webPreview");
      return iframe.src!==previous&&document.querySelector("#previewState").textContent.includes("加载中");
    },{},priorMaterialPreview);
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForFunction(()=>getComputedStyle(document.documentElement).getPropertyValue("--bg-canvas").trim()==="#ddffaa");

    // Real visual dragging must persist geometry to CSS and survive a source reload.
    const handle=await page.$("#dragOverlay .dragHandle.selected");
    const bounds=await handle.boundingBox();
    await page.mouse.move(bounds.x+20,bounds.y+20);
    await page.mouse.down();
    await page.mouse.move(bounds.x+57,bounds.y+42,{steps:4});
    await page.mouse.up();
    await page.waitForFunction(()=>document.querySelector("#layerX").value==="61"&&
      document.querySelector("#layerY").value==="192");
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForFunction(()=>{
      const el=document.querySelector("[id^='pc-layer-']");
      return el&&getComputedStyle(el).left==="61px";
    });
    console.log("Visual drag → exported CSS updated");

    // Upload a real PNG as bytes, insert it, and verify relative assets/ URL resolution.
    const temporaryDirectory=fs.mkdtempSync(path.join(TEST_TEMP_ROOT,"skin-studio-asset-"));
    const temporary=path.join(temporaryDirectory,"powercal-e2e-pixel.png");
    const pixel=Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGNQSPj6HwAEjgJ1ti1lKgAAAABJRU5ErkJggg==","base64");
    fs.writeFileSync(temporary,pixel);
    try{
      const upload=await page.$("#assetFile");
      await upload.uploadFile(temporary);
      await page.waitForFunction(()=>document.querySelector("#assetList").textContent.includes("powercal-e2e-pixel.png"));
      await page.click("#addImage");
      await page.waitForFunction(()=>document.querySelector("#layerList").textContent.includes("装饰图片"));
      await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
      frame=page.frames().find(f=>f.url().includes("/preview/"));
      await frame.waitForFunction(()=>{
        const img=document.querySelector("img[id^='pc-layer-']");
        return !!img&&img.complete&&img.naturalWidth>0;
      });
    }finally{fs.unlinkSync(temporary);fs.rmdirSync(temporaryDirectory)}
    console.log("PNG byte-preserving asset path rendered");
    await page.select("#iconTarget","today");
    await page.select("#iconAsset","assets/powercal-e2e-pixel.png");
    await page.click("#applyIcon");
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForFunction(()=>getComputedStyle(document.querySelector('[data-action="today"]'),'::before')
      .backgroundImage.includes("powercal-e2e-pixel.png"));
    console.log("Existing action button icon binding works");

    await page.click("#interactMode");
    await frame.click('.clear-tabbar [data-action="memo"]');
    await frame.waitForFunction(()=>!document.querySelector("#memoPage").hidden);
    let before=await frame.evaluate(()=>document.querySelectorAll(".memo-row").length);
    assert.equal(before,2,"Memo must use real host-shaped entries");
    await frame.click('.memo-row [data-action="toggleMemo"]');
    await frame.waitForFunction(()=>document.querySelectorAll(".memo-row").length===1);
    await frame.click('.clear-tabbar [data-action="month"]');
    await frame.waitForFunction(()=>!document.querySelector("#monthPage").hidden);
    const month=await frame.evaluate(()=>({
      cells:document.querySelectorAll(".month-cell").length,
      agenda:document.querySelectorAll(".agenda-event").length
    }));
    assert.equal(month.cells,42);
    assert.ok(month.agenda>0);
    await frame.click('.clear-tabbar [data-action="ai"]');
    await frame.waitForFunction(()=>!document.querySelector("#aiPage").hidden);
    await page.select("#scenario","ai-review");
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForFunction(()=>document.querySelectorAll(".ai-proposal-item").length===4);
    const review=await frame.evaluate(()=>({
      heading:document.querySelector(".ai-proposal-review h2")?.textContent,
      items:[...document.querySelectorAll(".ai-proposal-item")].map(row=>({
        text:row.textContent,checked:row.querySelector("input")?.checked,
        disabled:row.querySelector("input")?.disabled
      })),
      actions:[...document.querySelectorAll(".ai-proposal-actions button")].map(button=>button.textContent)
    }));
    assert.ok(review.heading.includes("尚未写入正式日历"));
    assert.equal(review.items.length,4);
    assert.ok(review.items[0].checked&&!review.items[0].disabled);
    assert.ok(review.items[1].disabled&&review.items[1].text.includes("缺少具体时刻"));
    assert.ok(review.items[2].text.includes("调整上午课程")&&review.items[3].text.includes("删除训练提醒"));
    assert.deepEqual(review.actions,["批量同意","写入草稿","批量拒绝","补充细节",
      "批量同意","写入草稿","批量拒绝","补充细节"]);
    await frame.evaluate(()=>[...document.querySelectorAll(".ai-proposal-group")][0]
      .querySelectorAll(".ai-proposal-actions button")[3].click());
    await frame.waitForFunction(()=>document.querySelector("#chatMessages").textContent
      .includes("请补充需要更正的字段"));
    await frame.evaluate(()=>[...document.querySelectorAll(".ai-proposal-group")][0]
      .querySelectorAll(".ai-proposal-actions button")[1].click());
    await frame.waitForFunction(()=>document.querySelectorAll(".ai-proposal-item").length===3);
    await frame.waitForFunction(()=>document.querySelector("#chatMessages").textContent
      .includes("候选已模拟写入草稿"));
    await frame.evaluate(()=>[...document.querySelectorAll(".ai-proposal-group")][1]
      .querySelectorAll(".ai-proposal-actions button")[2].click());
    await frame.waitForFunction(()=>document.querySelectorAll(".ai-proposal-item").length===1);
    await frame.waitForFunction(()=>document.querySelector("#chatMessages").textContent
      .includes("候选已模拟拒绝"));
    await page.select("#scenario","future");
    await page.select("#scenario","ai-review");
    await frame.waitForFunction(()=>document.querySelectorAll(".ai-proposal-item").length===4);
    await frame.evaluate(()=>[...document.querySelectorAll(".ai-proposal-group")][0]
      .querySelectorAll(".ai-proposal-actions button")[0].click());
    await page.waitForFunction(()=>!document.querySelector("#mockApproval").hidden);
    const approvalText=await page.$eval("#mockApprovalText",element=>element.textContent);
    assert.ok(approvalText.includes("方案评审")&&approvalText.includes("这不是 Android"));
    await page.click("#mockApprove");
    await frame.waitForFunction(()=>document.querySelectorAll(".ai-proposal-item").length===3);
    await frame.waitForFunction(()=>document.querySelector("#chatMessages").textContent
      .includes("没有写入任何真实日历数据"));
    console.log("AI proposal card / explicit mock Host review PASS",review);
    await frame.$eval("#chatInput",e=>{e.value="给我看看今日安排"});
    await frame.click("#chatSend");
    await frame.waitForFunction(()=>document.querySelector("#chatMessages").textContent.includes("给我看看今日安排"));
    await frame.click('.clear-tabbar [data-action="timeline"]');
    await frame.waitForFunction(()=>!document.querySelector("#timelinePage").hidden);
    await page.click("#editMode");
    console.log("Real Bridge-shaped Memo / month / AI interactions verified");

    await page.click("#advancedMode");
    await page.$eval("#cssCode",e=>{e.value=e.value+"\n/* browser-proof */";e.dispatchEvent(new Event("input",{bubbles:true}))});
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    const source=await page.evaluate(()=>payload());
    const exportResponse=await page.evaluate(async()=>{
      const data=payload();
      const r=await fetch("/api/export-v3",{method:"POST",
        headers:{"Content-Type":"application/json"},body:JSON.stringify(data)});
      if(!r.ok)throw Error(await r.text());
      const imported=await fetch("/api/import-v3",{method:"POST",
        headers:{"Content-Type":"application/octet-stream"},body:await r.arrayBuffer()});
      if(!imported.ok)throw Error(await imported.text());
      return await imported.json();
    });
    for(const [name,b64] of Object.entries(source.files))
      assert.equal(exportResponse.files[name],b64,name+" changed after export/import");
    assert.ok(source.files["web/style.css"]);
    console.log("All",Object.keys(source.files).length,"project files match exported ZIP bytes");
    frame=await loadPreviewPackage(exportResponse);
    await page.waitForFunction(()=>document.querySelector("#layerList").textContent.includes("装饰图片"));
    const reloaded=await page.evaluate(()=>payload());
    for(const [name,encoded] of Object.entries(source.files))
      assert.equal(reloaded.files[name],encoded,"Reimport lost "+name);
    console.log("Export → import → edit: layers and assets retained");

    const parityPackage=JSON.parse(JSON.stringify(reloaded));
    const previewHtml=Buffer.from(parityPackage.files["web/index.html"],"base64").toString("utf8");
    const previewCss=Buffer.from(parityPackage.files["web/style.css"],"base64").toString("utf8");
    const previewJs=Buffer.from(parityPackage.files["web/app.js"],"base64").toString("utf8");
    assert.ok(parityPackage.files["web/fonts/InterVariable.woff2"],"Clear font fixture missing");
    parityPackage.files["web/fonts/Probe.WOFF2"]=parityPackage.files["web/fonts/InterVariable.woff2"];
    parityPackage.files["web/style.css"]=Buffer.from(previewCss+
      '\n@font-face{font-family:"ParityProbe";src:url("/web/fonts/Probe.WOFF2") format("woff2")}\n' +
      '#fontProbe{font-family:"ParityProbe"}\n').toString("base64");
    parityPackage.files["web/app.js"]=Buffer.from(previewJs+
      '\nwindow.__parityConnectProbe=fetch("/api/template-v3").then(()=>"allowed").catch(()=>"blocked");\n'
    ).toString("base64");
    const inlineProbe='<script>document.documentElement.dataset.inlineProbe="executed"</script>';
    assert.ok(previewHtml.includes("</head>"),"Preview fixture has no head closing tag");
    parityPackage.files["web/index.html"]=Buffer.from(previewHtml.replace("</head>",inlineProbe+"</head>")
      .replace("</body>",'<span id="fontProbe">Font probe</span>'+
      '<img id="rootAssetProbe" src="/assets/powercal-e2e-pixel.png">'+
      '<img id="queryAssetProbe" src="/assets/powercal-e2e-pixel.png?cache=1">'+
      '<img id="missingAssetProbe" src="/assets/missing.png"></body>')).toString("base64");
    const parityResponses=new Map();
    const parityRequests=[];
    const parityFailures=[];
    const parityBrowserErrors=[];
    page.on("console",message=>{
      if(message.type()==="error")parityBrowserErrors.push(message.text());
    });
    page.on("request",request=>{
      const url=new globalThis.URL(request.url());
      if(["/assets/powercal-e2e-pixel.png","/assets/powercal-e2e-pixel.png?cache=1",
          "/assets/missing.png","/web/fonts/Probe.WOFF2"].includes(url.pathname+url.search))
        parityRequests.push({path:url.pathname+url.search,url:request.url(),
          origin:request.headers().origin||null,referer:request.headers().referer||null,
          resourceType:request.resourceType()});
    });
    page.on("requestfailed",request=>{
      const url=new globalThis.URL(request.url());
      if(["/assets/powercal-e2e-pixel.png","/assets/powercal-e2e-pixel.png?cache=1",
          "/assets/missing.png","/web/fonts/Probe.WOFF2"].includes(url.pathname+url.search))
        parityFailures.push({url:request.url(),error:request.failure()?.errorText||null});
    });
    page.on("response",response=>{
      const url=new globalThis.URL(response.url()),key=url.pathname+url.search;
      if(["/assets/powercal-e2e-pixel.png","/assets/powercal-e2e-pixel.png?cache=1",
          "/assets/missing.png","/web/fonts/Probe.WOFF2"].includes(key)){
        response.buffer().then(body=>parityResponses.set(key,{status:response.status(),
          headers:response.headers(),body})).catch(()=>{});
      }
    });
    frame=await loadPreviewPackage(parityPackage);
    await frame.waitForFunction(()=>["rootAssetProbe","queryAssetProbe","missingAssetProbe"]
      .every(id=>document.getElementById(id)?.complete));
    const parity=await frame.evaluate(async()=>{
      await window.__parityConnectProbe;
      let fonts=[],fontError=null;
      try{fonts=await document.fonts.load('16px "ParityProbe"')}
      catch(error){fontError=error.message}
      return {
        inlineScriptRan:document.documentElement.dataset.inlineProbe==="executed",
        connectResult:await window.__parityConnectProbe,
        fontError,
        fontLoaded:fonts.some(font=>font.family==="ParityProbe"&&font.status==="loaded"),
        fontCheck:document.fonts.check('16px "ParityProbe"'),
        fontFaces:Array.from(document.fonts).map(font=>({family:font.family,status:font.status})),
        computedProbeFont:getComputedStyle(document.getElementById("fontProbe")).fontFamily,
        rootImageWidth:document.getElementById("rootAssetProbe").naturalWidth,
        queryImageWidth:document.getElementById("queryAssetProbe").naturalWidth,
        missingImageWidth:document.getElementById("missingAssetProbe").naturalWidth
      };
    });
    await new Promise(resolve=>setTimeout(resolve,100));
    console.log("Preview resource request evidence",parityRequests,
      [...parityResponses.entries()].map(([key,value])=>[key,value.status,value.headers["content-type"]]),
      {parityFailures,parityBrowserErrors,parity});
    assert.equal(parity.inlineScriptRan,false,"CSP must block inline script");
    assert.equal(parity.connectResult,"blocked","CSP must block connect-src");
    assert.ok(parity.fontLoaded,"case-insensitive WOFF2 resource must load");
    assert.ok(parity.rootImageWidth>0,"Android-style root-relative asset must load");
    assert.equal(parity.queryImageWidth,0,"query strings must be rejected like Android");
    assert.equal(parity.missingImageWidth,0,"missing package resources must fail closed");
    await new Promise(resolve=>setTimeout(resolve,100));
    const fontResponse=parityResponses.get("/web/fonts/Probe.WOFF2");
    const rootResponse=parityResponses.get("/assets/powercal-e2e-pixel.png");
    assert.equal(fontResponse?.status,200);assert.equal(fontResponse?.headers["content-type"],"font/woff2");
    assert.equal(fontResponse?.headers["access-control-allow-origin"],"null");
    assert.deepEqual(fontResponse?.body,Buffer.from(parityPackage.files["web/fonts/InterVariable.woff2"],"base64"));
    assert.equal(rootResponse?.status,200);assert.equal(rootResponse?.headers["content-type"],"image/png");
    const previewHost=new globalThis.URL(frame.url()).host;
    assert.ok(parityRequests.every(request=>new globalThis.URL(request.url).host===previewHost),
      "all package resources must resolve through this preview session's isolated host");
    assert.ok(parityFailures.some(request=>request.url.includes("powercal-e2e-pixel.png?cache=1")),
      "query-bearing image must fail in the sandboxed preview");
    assert.ok(parityFailures.some(request=>request.url.includes("/assets/missing.png")),
      "missing image must fail in the sandboxed preview");
    console.log("Preview/WebView resource parity probes PASS",parity);

    async function setCapabilities(capabilities){
      const next=JSON.parse(JSON.stringify(parityPackage));
      next.manifest.capabilities=capabilities;
      frame=await loadPreviewPackage(next);
      return frame;
    }
    const denied=await setCapabilities([]);
    assert.equal(await denied.$$eval(".tEvent",nodes=>nodes.length),0,
      "without host.read Preview must expose no application state");
    await denied.evaluate(()=>document.querySelector('.clear-tabbar [data-action="memo"]')?.click());
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent.includes("未声明 host.action"));

    const readOnly=await setCapabilities(["host.read"]);
    await readOnly.waitForSelector(".tEvent");
    await readOnly.$eval('[data-action="createDraft"]',button=>button.click());
    await readOnly.waitForSelector("#timelineCreateDialog[open]");
    await readOnly.$eval('[data-timeline-create-kind="ACTIVITY"]',button=>button.click());
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent.includes("未声明 host.action"));
    assert.equal(await page.evaluate(()=>!document.querySelector("#bridgeLog").textContent.includes(
      "预览编辑命令 deck.create")),true,
      "host.read alone must not execute deck.create");

    const editOnly=await setCapabilities(["host.edit"]);
    assert.equal(await editOnly.$$eval(".tEvent",nodes=>nodes.length),0,
      "host.edit alone must not expose state");
    await editOnly.evaluate(()=>document.querySelector('.clear-tabbar [data-action="memo"]')?.click());
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent.includes("未声明 host.action"));

    const actionOnly=await setCapabilities(["host.action"]);
    assert.equal(await actionOnly.$$eval(".tEvent",nodes=>nodes.length),0,
      "host.action alone must not expose state");
    await actionOnly.evaluate(()=>document.querySelector('.clear-tabbar [data-action="memo"]')?.click());
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent.includes("模拟宿主收到：memo"));
    assert.equal(await actionOnly.$$eval(".memo-row",nodes=>nodes.length),0,
      "host.action alone must not expose memo state");

    const readAndAction=await setCapabilities(["host.read","host.action"]);
    await readAndAction.evaluate(()=>document.querySelector('.clear-tabbar [data-action="timeline"]')?.click());
    await readAndAction.waitForSelector(".tEvent");
    await readAndAction.$eval('[data-action="createDraft"]',button=>button.click());
    await readAndAction.waitForSelector("#timelineCreateDialog[open]");
    await readAndAction.$eval('[data-timeline-create-kind="ACTIVITY"]',button=>button.click());
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent.includes(
      "预览编辑命令 deck.create · ACTIVITY"));
    console.log("Studio Mock host.read / host.action / inert host.edit matrix PASS");
    if(process.env.STUDIO_SCREENSHOT)
      await page.screenshot({path:process.env.STUDIO_SCREENSHOT,fullPage:true});
    console.log("BROWSER E2E PASS");
  }finally{
    if(browser)await browser.close();
    try{fs.rmSync(browserProfile,{recursive:true,force:true})}
    catch(error){console.warn("Browser profile retained for local cleanup:",browserProfile,error.message)}
  }
})().catch(e=>{console.error(e);process.exitCode=1});
