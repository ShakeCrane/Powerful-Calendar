/* Studio Preview Mock E2E for draft flush, recovery, review, discard, and undo. */
const assert=require("node:assert/strict");
const {spawn}=require("node:child_process");
const fs=require("node:fs");
const readline=require("node:readline");
const path=require("node:path");
const puppeteer=require(process.env.PUPPETEER_PATH||"puppeteer");
const root=path.resolve(__dirname,"../../..");
const sourceRoot=path.resolve(__dirname,"../default-clear/source/web");
const editJs=fs.readFileSync(path.join(sourceRoot,"edit.js"),"utf8");
const styleCss=fs.readFileSync(path.join(sourceRoot,"style.css"),"utf8");
const base64=value=>Buffer.from(value,"utf8").toString("base64");

async function click(frame,selector){
  await frame.$eval(selector,element=>{element.focus();element.click()});
}
async function waitForResult(frame,status){
  await frame.waitForFunction(status=>
    window.__pcE2EState?.editing?.lastResult?.status===status,
  {timeout:10000},status);
  return frame.evaluate(()=>window.__pcE2EState.editing.lastResult);
}

(async()=>{
  const reportDir=path.join(root,".local/android-test/reports/separation-s0-dk-02-20261029");
  fs.mkdirSync(reportDir,{recursive:true});
  const server=spawn("python",["tools/skin-studio/skin_studio.py","serve","--port","0"],
    {cwd:root,stdio:["ignore","pipe","pipe"]});
  let browser,startupTimer;
  try{
    const line=await Promise.race([
      new Promise((resolve,reject)=>{
        readline.createInterface({input:server.stdout}).once("line",resolve);
        server.once("error",reject);server.once("exit",code=>reject(Error("Studio exited "+code)));
      }),new Promise((_,reject)=>{
        startupTimer=setTimeout(()=>reject(Error("Studio startup timed out")),15000);
      })
    ]);
    clearTimeout(startupTimer);
    const studioUrl=line.match(/http:\/\/127\.0\.0\.1:\d+/)?.[0];
    if(!studioUrl)throw Error("No local Studio URL: "+line);
    browser=await puppeteer.launch({headless:true,
      executablePath:process.env.CHROME_PATH||
        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
      args:["--no-sandbox","--disable-gpu"]});
    const page=await browser.newPage(),pageErrors=[];
    page.on("pageerror",error=>pageErrors.push(error.message));
    await page.setViewport({width:1460,height:960,deviceScaleFactor:1});
    await page.goto(studioUrl,{waitUntil:"domcontentloaded"});
    await page.waitForFunction(()=>document.querySelector("#previewState")?.textContent
      .includes("连接成功"),{timeout:30000});
    const preview=await page.evaluate(async({edit,css})=>{
      const project=await fetch("/api/template-v3").then(response=>response.json());
      project.files["web/edit.js"]=edit;project.files["web/style.css"]=css;
      const response=await fetch("/api/preview-v3",{method:"POST",
        headers:{"Content-Type":"application/json"},body:JSON.stringify(project)});
      if(!response.ok)throw Error("Clear Preview package failed: "+response.status);
      const result=await response.json();document.querySelector("#webPreview").src=result.url;
      return result.url;
    },{edit:base64(editJs),css:base64(styleCss)});
    await page.waitForFunction(url=>document.querySelector("#webPreview")?.src===url&&
      document.querySelector("#previewState")?.textContent.includes("连接成功"),
    {timeout:30000},preview);
    await page.select("#scenario","deck-lifecycle");
    await page.click("#interactMode");
    const frame=page.frames().find(candidate=>candidate.url().includes("/preview/"));
    assert.ok(frame,"Clear Preview frame is loaded");
    await frame.evaluate(()=>{
      const edit=window.PowerCalEdit;
      const onState=edit.onState.bind(edit),onResult=edit.onResult.bind(edit);
      edit.onState=next=>{window.__pcE2EState=next;onState(next)};
      edit.onResult=reply=>{onResult(reply)};
    });

    await click(frame,'[data-action="openDeck"]');
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.deck?.currentId===7301,
      {timeout:10000});
    assert.equal(await frame.$eval("#sheetSubtitle",node=>node.textContent),"2 张 · 已暂存");

    await frame.$eval('#sheetFields [data-field="title"]',input=>{
      input.value="暂存后可恢复的标题";
      input.dispatchEvent(new Event("change",{bubbles:true}));
    });
    await waitForResult(frame,"edited");
    await click(frame,'#sheetActions [data-do="flush"]');
    await waitForResult(frame,"flushed");
    let deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.form.title,"暂存后可恢复的标题");
    assert.equal(deck.version,1);
    await click(frame,'#sheetActions [data-do="close"]');
    await waitForResult(frame,"closed");
    await click(frame,'[data-action="openDeck"]');
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.deck?.currentId===7301&&
      window.__pcE2EState.editing.deck.form.title==="暂存后可恢复的标题",{timeout:10000});
    console.log("Preview Mock deck flush → close → reopen retains the staged form and version");

    await click(frame,'#sheetActions [data-do="discard"]');
    await click(frame,'#sheetActions [data-do="discard"]');
    await waitForResult(frame,"discarded");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.currentId,7302);
    assert.equal(deck.cardCount,1);
    assert.equal(deck.lastDiscardId,7301);
    await click(frame,'#sheetActions [data-do="undo"]');
    await waitForResult(frame,"restored");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.currentId,7301);
    assert.equal(deck.cardCount,2);
    assert.equal(deck.form.title,"暂存后可恢复的标题");
    assert.equal(deck.lastDiscardId,null);
    console.log("Preview Mock discard → undo restores the same draft and queue selection");

    await click(frame,"#sheetSave");
    await waitForResult(frame,"confirmation_required");
    assert.equal(await page.$eval("#mockApproval",element=>!element.hidden),true);
    await page.click("#mockReject");
    await waitForResult(frame,"cancelled");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.currentId,7301,"cancel leaves the draft selected");
    assert.equal(deck.cardCount,2,"cancel does not remove a pending card");
    await click(frame,"#sheetSave");
    await waitForResult(frame,"confirmation_required");
    await page.click("#mockApprove");
    await waitForResult(frame,"committed_preview_only");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.currentId,7302);
    assert.equal(deck.cardCount,1);
    assert.equal(deck.completion,null);
    console.log("Preview Mock submit requires separate review; cancel retains and confirm advances locally");

    await click(frame,'#sheetActions [data-do="discard"]');
    await click(frame,'#sheetActions [data-do="discard"]');
    await waitForResult(frame,"discarded");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.currentId,null);
    assert.equal(deck.cardCount,0);
    assert.equal(deck.lastDiscardId,7302);
    assert.deepEqual(deck.completion,{published:1,discarded:1,conflicts:0});
    assert.match(await frame.$eval("#sheetFields",node=>node.innerText),/本次处理已完成/);
    assert.equal(await frame.$('[data-do="discard"]'),null,
      "an empty deck cannot offer a no-op discard action");
    assert.ok(await frame.$('[data-do="undo"]'),"session discard remains undoable");
    assert.ok(await frame.$('[data-do="close"]'),"empty deck can be closed safely");
    assert.equal(await frame.$eval("#sheetSave",element=>element.disabled),true,
      "empty deck cannot submit an unselected draft");
    await (await frame.$(".deck-completion")).screenshot({
      path:path.join(reportDir,"deck-completion-preview.png")});
    await click(frame,'#sheetActions [data-do="undo"]');
    await waitForResult(frame,"restored");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.currentId,7302);
    assert.equal(deck.cardCount,1);
    assert.equal(deck.completion,null);
    assert.deepEqual(pageErrors,[],"deck lifecycle UI has no JavaScript exceptions");
    console.log("Preview Mock terminal state exposes completion, session undo and safe empty-deck actions");
    console.log("No persistent Store or calendar write is performed by this Preview Mock E2E");
  }finally{
    if(browser)await browser.close();server.kill();
  }
})().catch(error=>{console.error(error);process.exitCode=1});
