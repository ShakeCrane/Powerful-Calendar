/* Studio Preview verifies Clear's optional Android system-back UI hook. */
const assert=require("node:assert/strict");
const puppeteer=require(process.env.PUPPETEER_PATH||"puppeteer");
const URL=process.env.STUDIO_URL||"http://127.0.0.1:8875";

(async()=>{
  const browser=await puppeteer.launch({headless:true,
    executablePath:process.env.CHROME_PATH||
      "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    args:["--no-sandbox","--disable-gpu"]});
  try{
    const page=await browser.newPage();
    await page.goto(URL,{waitUntil:"domcontentloaded"});
    await page.waitForFunction(()=>
      document.querySelector("#previewState")?.textContent.includes("连接成功"),
      {timeout:30_000});
    const frame=page.frames().find(candidate=>candidate.url().includes("/preview/"));
    assert.ok(frame,"Studio must load the canonical Clear preview iframe");
    await frame.waitForFunction(()=>
      !!document.querySelector('[data-action="createDraft"]') &&
      typeof window.PowerCalSkin?.handleHostBack==="function",{timeout:15_000});

    await frame.evaluate(()=>document.querySelector('[data-action="createDraft"]').click());
    await frame.waitForSelector("#timelineCreateDialog[open]");
    assert.equal(await frame.evaluate(()=>window.PowerCalSkin.handleHostBack()),true,
      "the skin consumes Back while its chooser is open");
    await frame.waitForFunction(()=>!document.querySelector("#timelineCreateDialog")?.open);
    assert.equal(await frame.evaluate(()=>window.PowerCalSkin.handleHostBack()),false,
      "the Host must retain Back when the skin has no transient dialog");
    console.log("Studio Preview PASS: Clear Back dismissal and unhandled Host fallback");
  } finally {
    await browser.close();
  }
})().catch(error=>{
  console.error(error);
  process.exitCode=1;
});
