/* Studio Preview Mock browser E2E for Clear conflict and restore flows. */
const assert=require("node:assert/strict");
const fs=require("node:fs");
const path=require("node:path");
const puppeteer=require(process.env.PUPPETEER_PATH||"puppeteer");
const studioUrl=process.env.STUDIO_URL||"http://127.0.0.1:8875";
const clearWebSource=path.resolve(__dirname,"../default-clear/source/web");
const previewEditJs=fs.readFileSync(path.join(clearWebSource,"edit.js"),"utf8");
const previewStyleCss=fs.readFileSync(path.join(clearWebSource,"style.css"),"utf8");
const base64=value=>Buffer.from(value,"utf8").toString("base64");

const expectedCommandKeys={
  "deck.retired":["name","offset","requestId"],
  "deck.beginRestore":["eventSeq","expectedRevision","name","requestId"],
  "deck.keepConflict":["draftId","draftVersion","eventSeq","expectedRevision","name","requestId"],
  "deck.requestConflictReview":["draftId","draftVersion","eventSeq","expectedRevision","name","requestId"],
  "deck.keepRestore":["draftId","draftVersion","eventSeq","expectedRevision","name","requestId"],
  "deck.requestRestoreReview":["draftId","draftVersion","eventSeq","expectedRevision","name","requestId"]
};

async function waitResult(frame,requestId,status){
  await frame.waitForFunction(({requestId,status})=>
    window.__pcE2EResult?.requestId===requestId&&
    (!status||window.__pcE2EResult.status===status),
  {timeout:10000},{requestId,status});
  const result=await frame.evaluate(()=>window.__pcE2EResult);
  await frame.evaluate(()=>new Promise(resolve=>setTimeout(resolve,30)));
  return result;
}
async function assertLastCommand(frame,name){
  const command=await frame.evaluate(()=>window.__pcE2ECommands.at(-1));
  assert.equal(command.name,name,"Clear UI emits the expected Host command");
  assert.deepEqual(Object.keys(command).sort(),expectedCommandKeys[name],
    name+" payload follows the closed production Bridge schema");
  return command;
}
async function click(frame,selector){
  await frame.$eval(selector,element=>{element.focus();element.click()});
}
async function sendMockCommand(frame,name,args,requestId){
  await frame.evaluate(({name,args,requestId})=>
    window.PowerCalSkin.sendCommand(name,requestId,args),{name,args,requestId});
  const result=await waitResult(frame,requestId);
  assert.equal(result.status,"kept");
  await assertLastCommand(frame,name);
}

(async()=>{
  const browser=await puppeteer.launch({headless:true,
    executablePath:process.env.CHROME_PATH||
      "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    args:["--no-sandbox","--disable-gpu"]});
  try{
    const page=await browser.newPage(),pageErrors=[];
    page.on("pageerror",error=>{
      pageErrors.push(error.message);console.error("Studio page error",error.message);
    });
    await page.setViewport({width:1460,height:960,deviceScaleFactor:1});
    await page.goto(studioUrl,{waitUntil:"domcontentloaded"});
    await page.waitForFunction(()=>document.querySelector("#previewState")?.textContent
      .includes("连接成功"),{timeout:30000});
    const previewInfo=await page.evaluate(async({editJs,styleCss})=>{
      const reloadsBefore=[...document.querySelector("#bridgeLog").children]
        .filter(node=>node.textContent.includes("预览已重载")).length;
      const project=await fetch("/api/template-v3").then(response=>response.json());
      project.files["web/edit.js"]=editJs;project.files["web/style.css"]=styleCss;
      const response=await fetch("/api/preview-v3",{method:"POST",
        headers:{"Content-Type":"application/json"},body:JSON.stringify(project)});
      if(!response.ok)throw Error("Preview-only Clear source package failed: "+response.status);
      const preview=await response.json();document.querySelector("#webPreview").src=preview.url;
      return {url:preview.url,reloadsBefore};
    },{editJs:base64(previewEditJs),styleCss:base64(previewStyleCss)});
    await page.waitForFunction(({previewUrl,reloadsBefore})=>{
      const reloads=[...document.querySelector("#bridgeLog").children]
        .filter(node=>node.textContent.includes("预览已重载")).length;
      return document.querySelector("#webPreview")?.src===previewUrl&&reloads>reloadsBefore&&
        document.querySelector("#previewState")?.textContent.includes("连接成功");
    },{timeout:30000},{previewUrl:previewInfo.url,reloadsBefore:previewInfo.reloadsBefore});
    await page.select("#scenario","conflict-restore");
    await page.click("#interactMode");
    const frame=page.frames().find(candidate=>candidate.url().includes("/preview/"));
    assert.ok(frame,"Clear package Preview frame is loaded");

    // Read-only E2E instrumentation observes the real Clear UI's Bridge traffic.
    // It does not add controls or code to the skin package.
    await frame.evaluate(()=>{
      const edit=window.PowerCalEdit;
      const onState=edit.onState.bind(edit),onResult=edit.onResult.bind(edit);
      const sendCommand=window.PowerCalSkin.sendCommand.bind(window.PowerCalSkin);
      window.__pcE2EState=null;window.__pcE2EResult=null;window.__pcE2ECommands=[];
      edit.onState=next=>{window.__pcE2EState=next;onState(next)};
      edit.onResult=reply=>{window.__pcE2EResult=reply;onResult(reply)};
      window.PowerCalSkin.sendCommand=(name,requestId,extra={})=>{
        window.__pcE2ECommands.push({name,requestId,...extra});
        return sendCommand(name,requestId,extra);
      };
    });

    await click(frame,'[data-action="openDeck"]');
    try{
      await frame.waitForFunction(()=>window.__pcE2EState?.editing?.deck?.conflict?.eventSeq===6201,
        {timeout:10000});
    }catch(error){
      console.error("Deck-open Mock diagnostics",JSON.stringify({
        commands:await frame.evaluate(()=>window.__pcE2ECommands),
        result:await frame.evaluate(()=>window.__pcE2EResult),
        state:await frame.evaluate(()=>window.__pcE2EState?.editing?.deck),
        studioLog:await page.$eval("#bridgeLog",element=>element.textContent),
        previewText:await frame.$eval("body",element=>element.innerText.slice(-500))
      }));throw error;
    }
    assert.equal(await frame.$eval("#skinSheet",element=>!element.hidden),true);
    const conflictSection=await frame.$eval(
      '.deck-review-snapshot[aria-label="正式事件与冲突草稿的完整差异"]',
      element=>element.textContent).catch(async error=>{
        console.error("Clear conflict UI diagnostics",JSON.stringify(await frame.evaluate(()=>({
          deck:window.__pcE2EState?.editing?.deck,
          sheetHidden:document.querySelector("#skinSheet")?.hidden,
          fields:document.querySelector("#sheetFields")?.innerText,
          actions:document.querySelector("#sheetActions")?.innerText
        }))));throw error;
      });
    assert.ok(conflictSection.includes("当前正式标题")&&conflictSection.includes("冲突草稿标题"),
      "Clear displays the full current/proposed conflict snapshot");
    assert.equal(await frame.$$eval(".deck-change-row",rows=>rows.length),5,
      "Clear lists all fixture field differences");

    await click(frame,'#sheetActions [data-do="requestConflictReview"]');
    let command=await assertLastCommand(frame,"deck.requestConflictReview");
    await waitResult(frame,command.requestId,"confirmation_required");
    assert.equal(await page.$eval("#mockApproval",element=>!element.hidden),true,
      "replacement requires a separate simulated Preview Host confirmation");
    assert.ok(await frame.evaluate(()=>!!window.__pcE2EState.editing.deck.conflict),
      "requesting approval does not itself clear or replace the conflict");
    await page.click("#mockReject");
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.lastResult?.status==="cancelled");
    assert.ok(await frame.evaluate(()=>!!window.__pcE2EState.editing.deck.conflict),
      "rejecting Host review retains the conflict snapshot");

    let conflict=await frame.evaluate(()=>window.__pcE2EState.editing.deck.conflict);
    const invalidConflictId="e2e_req_conflict_approval";
    await frame.evaluate(({conflict,id})=>window.PowerCalSkin.sendCommand(
      "deck.requestConflictReview",id,{draftId:conflict.draftId,
        draftVersion:conflict.draftVersion,eventSeq:conflict.eventSeq,
        expectedRevision:conflict.currentRevision,approved:true}),{conflict,id:invalidConflictId});
    assert.equal((await waitResult(frame,invalidConflictId,"invalid")).status,"invalid",
      "the Bridge rejects a skin-supplied approval bit");
    assert.equal(await page.$eval("#mockApproval",element=>element.hidden),true,
      "skin cannot approve or open the Host review by adding an unrecognized field");

    await click(frame,'#sheetActions [data-do="requestConflictReview"]');
    command=await assertLastCommand(frame,"deck.requestConflictReview");
    await waitResult(frame,command.requestId,"confirmation_required");
    await page.click("#mockApprove");
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.lastResult?.status===
      "replaced_preview_only");
    assert.equal(await frame.evaluate(()=>window.__pcE2EState.editing.deck.conflict),null,
      "only the simulated Preview Host decision resolves the conflict fixture");
    await click(frame,"#sheetClose");
    await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);

    await click(frame,'[data-action="openDeck"]');
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.deck?.conflict?.eventSeq===6201);
    await click(frame,'#sheetActions [data-do="keepConflict"]');
    command=await assertLastCommand(frame,"deck.keepConflict");
    await waitResult(frame,command.requestId,"kept");
    const keptConflict=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(keptConflict.conflict,null);
    assert.ok(keptConflict.cards.some(card=>card.draftId===7801&&card.status==="CONFLICT"),
      "Clear's keep action dismisses the prompt while retaining the conflict draft");
    assert.equal(await frame.$(".deck-review-snapshot[aria-label='正式事件与冲突草稿的完整差异']"),null);

    let deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.retiredLoaded,true);
    assert.equal(deck.retiredOffset,0);assert.equal(deck.retiredTotal,151);
    assert.deepEqual(deck.retiredResults.map(item=>item.eventSeq),
      Array.from({length:50},(_,index)=>9051-index));
    assert.equal(await frame.$$eval(".deck-retired-results [data-do=beginRestore]",rows=>rows.length),50,
      "Clear renders the first page of deleted events");

    await click(frame,'.retired-navigation [data-do="retiredPage"]:last-child');
    command=await assertLastCommand(frame,"deck.retired");
    assert.deepEqual(Object.keys(command).sort(),expectedCommandKeys["deck.retired"]);
    await waitResult(frame,command.requestId,"loaded");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.retiredOffset,50);assert.equal(deck.retiredHasPrevious,true);
    assert.equal(deck.retiredHasNext,true);
    assert.deepEqual(deck.retiredResults.map(item=>item.eventSeq),
      Array.from({length:50},(_,index)=>9001-index));
    await click(frame,'.retired-navigation [data-do="retiredPage"]:last-child');
    command=await assertLastCommand(frame,"deck.retired");
    await waitResult(frame,command.requestId,"loaded");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.retiredOffset,100);assert.equal(deck.retiredHasPrevious,true);
    assert.equal(deck.retiredHasNext,true);
    assert.deepEqual(deck.retiredResults.map(item=>item.eventSeq),
      Array.from({length:50},(_,index)=>8951-index));
    await click(frame,'.retired-navigation [data-do="retiredPage"]:last-child');
    command=await assertLastCommand(frame,"deck.retired");
    await waitResult(frame,command.requestId,"loaded");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.retiredOffset,150);assert.equal(deck.retiredHasNext,false);
    assert.deepEqual(deck.retiredResults.map(item=>item.eventSeq),[8901]);
    await click(frame,'.retired-navigation [data-do="retiredPage"]:first-child');
    command=await assertLastCommand(frame,"deck.retired");
    await waitResult(frame,command.requestId,"loaded");
    assert.equal((await frame.evaluate(()=>window.__pcE2EState.editing.deck)).retiredOffset,100);
    await click(frame,'.retired-navigation [data-do="retiredPage"]:first-child');
    command=await assertLastCommand(frame,"deck.retired");
    await waitResult(frame,command.requestId,"loaded");
    assert.equal((await frame.evaluate(()=>window.__pcE2EState.editing.deck)).retiredOffset,50,
      "deleted-event navigation returns to the previous page");
    await click(frame,'.retired-navigation [data-do="retiredPage"]:last-child');
    command=await assertLastCommand(frame,"deck.retired");
    await waitResult(frame,command.requestId,"loaded");
    assert.equal((await frame.evaluate(()=>window.__pcE2EState.editing.deck)).retiredOffset,100);

    await click(frame,'.deck-retired-results [data-do="beginRestore"][data-event-seq="8951"]');
    command=await assertLastCommand(frame,"deck.beginRestore");
    await waitResult(frame,command.requestId,"opened");
    let restore=await frame.evaluate(()=>window.__pcE2EState.editing.deck.restore);
    assert.equal(restore.eventSeq,8951);assert.equal(restore.expectedRevision,4);
    const restoreSnapshot=await frame.$('.deck-review-snapshot[aria-label="已删除事件与恢复草稿快照"]');
    assert.ok(restoreSnapshot,"Clear displays the restore proposal");
    assert.equal(await frame.$(".deck-retired-results"),null,
      "the long deleted-event picker gives way to the full restore review");
    assert.ok(await frame.$eval('.deck-review-snapshot[aria-label="已删除事件与恢复草稿快照"]',
      element=>element.getBoundingClientRect().height>0),"restore snapshot is visible");
    for(const summary of await frame.$$('.deck-review-snapshot .deck-readonly-snapshot summary'))
      await summary.click();
    assert.match(await frame.$eval('.deck-review-snapshot[aria-label="已删除事件与恢复草稿快照"]',
      element=>element.textContent),/预览已删除事件 51/,
      "both expandable snapshots expose the selected deleted event's full title");
    const restoreButtons=await frame.$eval("#sheetActions",actions=>({
      keep:actions.querySelector('[data-do="keepRestore"]')?.dataset.target||"",
      request:actions.querySelector('[data-do="requestRestoreReview"]')?.dataset.target||""
    }));
    assert.ok(restoreButtons.keep,"Clear renders the restore keep target");
    if(process.env.E2E_SCREENSHOT_PATH)
      await page.screenshot({path:process.env.E2E_SCREENSHOT_PATH,fullPage:true});
    await click(frame,'#sheetActions [data-do="keepRestore"]');
    command=await assertLastCommand(frame,"deck.keepRestore");
    await waitResult(frame,command.requestId,"kept");
    deck=await frame.evaluate(()=>window.__pcE2EState.editing.deck);
    assert.equal(deck.restore,null);
    assert.equal(deck.conflict,null);
    assert.equal(deck.open,true);
    assert.equal(deck.retiredLoaded,true,"retired pages remain loaded after keeping a restore proposal");
    assert.equal(deck.retiredOffset,100);
    assert.equal(deck.retiredTotal,151);
    assert.equal(deck.retiredResults.length,50);
    assert.deepEqual(pageErrors,[],"rendering after keeping restore has no browser errors");
    assert.ok(await frame.$(".deck-retired-results"),"the deleted-event picker returns after keep");
    assert.ok(deck.cards.some(card=>card.eventSeq===8951),
      "keeping the restore proposal retains its draft");

    await click(frame,'.retired-navigation [data-do="retiredPage"]:first-child');
    command=await assertLastCommand(frame,"deck.retired");
    await waitResult(frame,command.requestId,"loaded");
    assert.equal((await frame.evaluate(()=>window.__pcE2EState.editing.deck)).retiredOffset,50);
    await click(frame,'.deck-retired-results [data-do="beginRestore"][data-event-seq="8952"]');
    command=await assertLastCommand(frame,"deck.beginRestore");
    await waitResult(frame,command.requestId,"opened");
    restore=await frame.evaluate(()=>window.__pcE2EState.editing.deck.restore);
    const reviewButtons=await frame.$eval("#sheetActions",actions=>({
      keep:actions.querySelector('[data-do="keepRestore"]')?.dataset.target||"",
      request:actions.querySelector('[data-do="requestRestoreReview"]')?.dataset.target||""
    }));
    assert.ok(reviewButtons.request,"Clear renders the restore review target");
    await click(frame,'#sheetActions [data-do="requestRestoreReview"]');
    command=await assertLastCommand(frame,"deck.requestRestoreReview");
    await waitResult(frame,command.requestId,"confirmation_required");
    assert.equal(await page.$eval("#mockApproval",element=>!element.hidden),true,
      "restore is a Host review request, not a skin approval");
    assert.equal(await frame.evaluate(()=>window.__pcE2EState.editing.deck.restore.eventSeq),8952,
      "requesting restore confirmation leaves the proposal pending");
    await page.click("#mockReject");
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.lastResult?.status==="cancelled");
    assert.equal(await frame.evaluate(()=>window.__pcE2EState.editing.deck.restore.eventSeq),8952,
      "cancelling Host review retains the restore proposal");

    restore=await frame.evaluate(()=>window.__pcE2EState.editing.deck.restore);
    const invalidRestoreId="e2e_req_restore_approval";
    await frame.evaluate(({restore,id})=>window.PowerCalSkin.sendCommand(
      "deck.requestRestoreReview",id,{draftId:restore.draftId,
        draftVersion:restore.draftVersion,eventSeq:restore.eventSeq,
        expectedRevision:restore.expectedRevision,approved:true}),{restore,id:invalidRestoreId});
    assert.equal((await waitResult(frame,invalidRestoreId,"invalid")).status,"invalid",
      "the Bridge rejects a skin-supplied restore approval bit");
    assert.equal(await page.$eval("#mockApproval",element=>element.hidden),true);

    await click(frame,'#sheetActions [data-do="requestRestoreReview"]');
    command=await assertLastCommand(frame,"deck.requestRestoreReview");
    await waitResult(frame,command.requestId,"confirmation_required");
    await page.click("#mockApprove");
    await frame.waitForFunction(()=>window.__pcE2EState?.editing?.lastResult?.status===
      "restored_preview_only");
    assert.equal(await frame.evaluate(()=>window.__pcE2EState.editing.deck.restore),null);
    assert.equal(await page.$eval("#bridgeLog",element=>element.textContent.includes("无持久化写入")),true,
      "simulated restore decision is explicitly non-writing");

    assert.deepEqual(pageErrors,[],"Clear conflict and restore flows have no browser errors");
    console.log("Studio Preview Mock Bridge: conflict/restore commands, pagination, closed schemas, Host review PASS");
    console.log("Clear Studio Preview UI: conflict replace/keep, retired paging/selection, restore keep/Host review PASS");
    console.log("Scope: desktop browser Preview Mock only; Android/WebView and real Store NOT TESTED");
  }finally{
    await browser.close();
  }
})().catch(error=>{console.error(error);process.exitCode=1});
