/* Optional focused browser QA. Requires a running local Skin Studio and Puppeteer. */
const assert=require("node:assert/strict");
const puppeteer=require(process.env.PUPPETEER_PATH||"puppeteer");
const studioUrl=process.env.STUDIO_URL||"http://127.0.0.1:8765";

(async()=>{
  const browser=await puppeteer.launch({headless:true,
    executablePath:process.env.CHROME_PATH||
      "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    args:["--no-sandbox","--disable-gpu"]});
  try{
    const page=await browser.newPage();
    const pageErrors=[];
    page.on("pageerror",error=>{
      pageErrors.push(error.message);
      console.error("Studio page error",error.message);
    });
    await page.setViewport({width:1460,height:960,deviceScaleFactor:1});
    await page.goto(studioUrl,{waitUntil:"domcontentloaded"});
    await page.waitForFunction(()=>document.querySelector("#previewState")?.textContent
      .includes("连接成功"),{timeout:30000});
    await page.click("#interactMode");
    const frame=page.frames().find(candidate=>candidate.url().includes("/preview/"));
    assert.ok(frame,"Clear package preview frame is loaded");

    await frame.click('[data-action="openDeck"]');
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden&&
      !!document.querySelector(".deck-search"));
    await frame.$eval(".deck-search input",input=>{
      input.value="#6201";document.querySelector(".deck-search").requestSubmit();
    });
    await frame.waitForFunction(()=>document.querySelector(".deck-formal-results .deck-result")
      ?.textContent.includes("#6201"));
    assert.ok((await frame.$eval(".deck-formal-results .deck-result",button=>button.textContent))
      .includes("正式事件替换目标"),"Formatted event sequence search matches the Host contract");
    await frame.$eval(".deck-search input",input=>{
      input.value="正式事件替换目标";document.querySelector(".deck-search").requestSubmit();
    });
    await frame.waitForFunction(()=>document.querySelector(".deck-formal-results .deck-result")
      ?.textContent.includes("正式事件替换目标"));
    const formalResult=await frame.$eval(".deck-formal-results .deck-result",button=>button.textContent);
    assert.ok(formalResult.includes("正式事件替换目标"));
    await frame.click(".deck-formal-results [data-do=replace]");
    await frame.waitForFunction(()=>document.querySelector('#sheetFields [data-field="title"]')?.value===
      "正式事件替换目标");

    await frame.$eval(".deck-search input",input=>{
      input.value="示例草稿";document.querySelector(".deck-search").requestSubmit();
    });
    await frame.waitForFunction(()=>document.querySelectorAll(".sheet-cards [data-draft-id]").length===4);
    await frame.click('.sheet-cards [data-draft-id="7202"]');
    await frame.waitForFunction(()=>document.querySelector('#sheetFields [data-field="title"]')?.value===
      "示例草稿二");
    await frame.click('[data-do="jump"][data-steps="1"]');
    await frame.waitForFunction(()=>document.querySelector('#sheetFields [data-field="title"]')?.value===
      "示例草稿三"&&document.querySelector(".sheet-deck-navigation span")?.textContent==="3 / 6");

    for(const name of ["deck.search","deck.proposeReplace","deck.select","deck.jump"]){
      assert.ok(await page.evaluate(value=>document.querySelector("#bridgeLog").textContent
        .includes("预览编辑命令 "+value),name),name+" is handled by the Studio Preview Mock");
    }
    await frame.click("#sheetClose");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);

    await frame.click(".tEvent.ACTIVITY");
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden&&
      document.querySelector("#sheetTitle").textContent==="编辑事件");
    await frame.click("#sheetClose");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);

    await frame.click('[data-action="createDraft"]');
    await frame.waitForSelector("#timelineCreateDialog[open]");
    await frame.click('[data-timeline-create-kind="ACTIVITY"]');
    await frame.waitForFunction(()=>!document.querySelector("#skinSheet").hidden&&
      !!document.querySelector('#sheetFields [data-field="title"]'));
    assert.equal(await frame.$(".deck-search"),null,
      "Transient new-event preview must not expose persisted-deck search");
    await frame.$eval('#sheetFields [data-field="title"]',input=>{
      input.value="Transient checkpoint survives";
      input.dispatchEvent(new Event("change",{bubbles:true}));
    });
    await frame.click('#sheetActions [data-do="flush"]');
    await frame.waitForFunction(()=>document.querySelector("#sheetStatus").textContent
      .includes("修改已暂存"));
    assert.equal(await frame.$eval('#sheetFields [data-field="title"]',input=>input.value),
      "Transient checkpoint survives");
    assert.equal(await frame.$(".deck-search"),null,
      "Checkpointing a transient editor does not turn it into the persisted queue");
    await frame.click("#sheetClose");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);

    assert.deepEqual(pageErrors,[],"Studio Preview must complete without uncaught page errors");
    console.log("Studio Preview Mock deck search/replace/select/navigation/transient PASS");
  }finally{
    await browser.close();
  }
})().catch(error=>{console.error(error);process.exitCode=1});
