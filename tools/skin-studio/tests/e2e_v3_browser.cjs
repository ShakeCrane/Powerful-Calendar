/* Optional browser QA. Run with PUPPETEER_PATH and Skin Studio on 127.0.0.1:8875. */
const assert=require("node:assert/strict");
const os=require("node:os"),fs=require("node:fs"),path=require("node:path");
const puppeteer=require(process.env.PUPPETEER_PATH||"puppeteer");
const URL=process.env.STUDIO_URL||"http://127.0.0.1:8875";
(async()=>{
  const browser=await puppeteer.launch({headless:true,
    executablePath:process.env.CHROME_PATH||
      "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
    args:["--no-sandbox","--disable-gpu"]});
  try{
    const page=await browser.newPage();
    page.on("pageerror",e=>console.error("PAGE ERROR",e.message));
    await page.setViewport({width:1460,height:960,deviceScaleFactor:1});
    await page.goto(URL,{waitUntil:"domcontentloaded"});
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"),{timeout:30000});
    let frame=page.frames().find(f=>f.url().includes("/preview/"));
    assert.ok(frame,"live source iframe must load");
    await frame.waitForSelector(".event");
    let normal=await frame.evaluate(()=>({
      items:document.querySelectorAll(".event").length,
      options:document.querySelectorAll(".optionRow").length,
      ruler:document.querySelectorAll(".tick").length,
      css:getComputedStyle(document.documentElement).getPropertyValue("--primary").trim()
    }));
    assert.ok(normal.items>=2);assert.equal(normal.options,2);assert.equal(normal.ruler,7);
    console.log("Initial WYSIWYG preview",normal);

    await page.select("#scenario","future");
    await page.waitForFunction(()=>{
      return document.querySelector("#bridgeLog").textContent.includes("预览") ||
        document.querySelector("#previewState").textContent.includes("连接成功");
    });
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
    await page.$eval("#materialSlots input[type=color]",e=>{
      e.value="#ddffaa";e.dispatchEvent(new Event("input",{bubbles:true}));
    });
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForFunction(()=>getComputedStyle(document.documentElement).getPropertyValue("--surface").trim()==="#ddffaa");

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
    const temporary=path.join(os.tmpdir(),"powercal-e2e-pixel.png");
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
    }finally{fs.unlinkSync(temporary)}
    console.log("PNG byte-preserving asset path rendered");
    await page.select("#iconTarget","today");
    await page.select("#iconAsset","assets/powercal-e2e-pixel.png");
    await page.click("#applyIcon");
    await page.waitForFunction(()=>document.querySelector("#previewState").textContent.includes("连接成功"));
    frame=page.frames().find(f=>f.url().includes("/preview/"));
    await frame.waitForFunction(()=>getComputedStyle(document.querySelector('[data-action="today"]'),'::before')
      .backgroundImage.includes("powercal-e2e-pixel.png"));
    console.log("Existing action button icon binding works");

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
    await page.evaluate(async data=>{await loadPackage(data)},exportResponse);
    await page.waitForFunction(()=>document.querySelector("#layerList").textContent.includes("装饰图片"));
    const reloaded=await page.evaluate(()=>payload());
    for(const [name,encoded] of Object.entries(source.files))
      assert.equal(reloaded.files[name],encoded,"Reimport lost "+name);
    console.log("Export → import → edit: layers and assets retained");
    if(process.env.STUDIO_SCREENSHOT)
      await page.screenshot({path:process.env.STUDIO_SCREENSHOT,fullPage:true});
    console.log("BROWSER E2E PASS");
  }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
