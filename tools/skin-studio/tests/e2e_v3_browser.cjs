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
      deadline:document.querySelector("#dateDeadlineBar .dateDeadline")?.textContent,
      alarm:document.querySelector("#timelineLeft .tEvent.ALARM")?.textContent,
      activity:document.querySelector("#timelineRight .tEvent.ACTIVITY")?.textContent
    }));
    assert.ok(sides.deadline.includes("2026-10-22")&&sides.deadline.includes("无具体时刻"),
      "Date-only Deadline must render in the independent date strip without a time");
    assert.ok(sides.alarm,"Alarm must render in left wing");
    assert.ok(sides.activity,"Activity must render in right wing");
    assert.ok(["#2A62E0","#5A8DFF"].includes(normal.css),
      "Clear light/dark source palette must load into the iframe");
    assert.equal(await frame.$eval('[data-action="createDraft"]',e=>e.getAttribute("aria-label")),
      "新建事件");
    console.log("Initial WYSIWYG preview",normal);

    await page.click("#interactMode");
    const zoomLabels=await frame.$$eval('.timeline-view-controls button',buttons=>
      buttons.map(button=>button.getAttribute("aria-label")));
    assert.deepEqual(zoomLabels,["缩小时间轴，显示更多小时","放大时间轴，显示更少小时",
      "重置时间轴缩放为24小时"]);
    await frame.click('[data-action="zoomTimeline"][data-direction="in"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="16 小时");
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("zoomTimeline · in · 16 小时"));
    await frame.click('[data-action="zoomTimeline"][data-direction="out"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="24 小时");
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("zoomTimeline · out · 24 小时"));
    const validZoom=await frame.$eval('[data-action="zoomTimeline"][data-direction="in"]',button=>{
      button.dataset.direction="sideways";button.click();button.dataset.direction="in";
      return button.getAttribute("aria-label");
    });
    assert.ok(validZoom);
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("拒绝无效 zoomTimeline payload"));
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="24 小时");
    for(let i=0;i<20;i++)await frame.click('[data-action="zoomTimeline"][data-direction="in"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="0.25 小时");
    for(let i=0;i<20;i++)await frame.click('[data-action="zoomTimeline"][data-direction="out"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="48 小时");
    await frame.click('[data-action="resetTimelineZoom"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="24 小时");
    await page.waitForFunction(()=>document.querySelector("#bridgeLog").textContent
      .includes("resetTimelineZoom · 24 小时"));
    console.log("Studio Mock validates zoom/reset actions, clamps hours, and restores 24h");

    await page.select("#scenario","timeline-geometry");
    await frame.waitForFunction(()=>document.querySelectorAll("#timelineLeft .tEvent").length>=12&&
      document.querySelectorAll("#timelineRight .tEvent").length>=12);
    const geometry=await frame.evaluate(()=>{
      const surfaceElement=document.querySelector("#timelineSurface");
      const surface=surfaceElement.getBoundingClientRect();
      const event=seq=>document.querySelector(`[data-event-seq="${seq}"]`);
      const rect=element=>{const r=element.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height}};
      const activity=seq=>{
        const button=event(seq),visual=button.querySelector(".tEventVisual");
        const title=button.querySelector(".timelineTitle");
        return {button:rect(button),visual:rect(visual),start:Number(button.dataset.startMillis),
          end:Number(button.dataset.endMillis),from:Number(button.dataset.visibleStartMillis),
          to:Number(button.dataset.visibleEndMillis),surfaceY:surface.y,surfaceHeight:surface.height,
          surfaceClientHeight:surfaceElement.clientHeight,
          visualStyleHeight:visual.style.height,title:button.dataset.eventTitle,lane:Number(button.dataset.lane),
          occurrenceKey:button.dataset.occurrenceKey,aria:button.getAttribute("aria-label"),
          foldedTitle:{id:title.dataset.titleFoldId,state:title.dataset.titleFoldState,
            comparedTo:title.dataset.titleComparedTo||null,text:title.dataset.titleDisplay,
            source:title.dataset.titleSource,maxWidth:Number(title.dataset.titleMaxWidth),
            measuredRect:JSON.parse(title.dataset.titleMeasuredRect||"null"),
            screenRect:JSON.parse(title.dataset.titleScreenRect||"null"),
            pointerEvents:getComputedStyle(title).pointerEvents,ariaHidden:title.getAttribute("aria-hidden")}};
      };
      const point=seq=>{
        const button=event(seq),line=button.querySelector(".tPointLine"),r=rect(line);
        return {line:r,start:Number(button.dataset.startMillis),from:Number(button.dataset.visibleStartMillis),
          to:Number(button.dataset.visibleEndMillis),surfaceY:surface.y,surfaceHeight:surface.height,
          aria:button.getAttribute("aria-label"),geometry:button.dataset.temporalGeometry};
      };
      const left=document.querySelector("#timelineLeft"),right=document.querySelector("#timelineRight");
      return {
        oneMinute:activity(101),normal:activity(103),long:activity(102),longTitle:activity(105),
        alarm:point(201),exactDeadline:point(202),
        dateDeadline:{text:document.querySelector("#dateDeadlineBar .dateDeadline")?.textContent,
          aria:document.querySelector("#dateDeadlineBar .dateDeadline")?.getAttribute("aria-label"),
          date:document.querySelector("#dateDeadlineBar .dateDeadline")?.dataset.deadlineDate,
          inWing:!!document.querySelector('#timelineLeft [data-event-seq="204"],#timelineRight [data-event-seq="204"]')},
        left:{scrollWidth:left.scrollWidth,clientWidth:left.clientWidth,scrollLeft:left.scrollLeft,
          laneCount:Number(left.querySelector(".timelineLanes").dataset.laneCount),
          laneZero:rect(left.querySelector('.tEvent[data-lane="0"]')),
          viewport:rect(left)},
        right:{scrollWidth:right.scrollWidth,clientWidth:right.clientWidth,scrollLeft:right.scrollLeft,
          laneCount:Number(right.querySelector(".timelineLanes").dataset.laneCount),
          laneZero:rect(right.querySelector('.tEvent[data-lane="0"]')),
          viewport:rect(right)},
        events:[...document.querySelectorAll("#timelineLeft [data-event-seq],#timelineRight [data-event-seq],#dateDeadlineBar [data-event-seq]")]
          .map(button=>({seq:button.dataset.eventSeq,revision:button.dataset.revision,
            occurrenceKey:button.dataset.occurrenceKey,originalOccurrenceKey:button.dataset.originalOccurrenceKey,
            title:button.dataset.eventTitle,aria:button.getAttribute("aria-label"),tabIndex:button.tabIndex,
            geometry:button.dataset.temporalGeometry})),
        greedy:[101,301,302,303].map(seq=>activity(seq).foldedTitle),
        independentLaneTitles:[103,104,201,202,203].map(seq=>({seq,
          state:event(seq).querySelector(".timelineTitle").dataset.titleFoldState}))
      };
    });
    const expectedRect=event=>{
      const visibleStart=Math.max(event.start,event.from),visibleEnd=Math.min(event.end,event.to);
      const scale=event.surfaceHeight/(event.to-event.from);
      return {top:event.surfaceY+(visibleStart-event.from)*scale,
        height:(visibleEnd-visibleStart)*scale};
    };
    for(const event of [geometry.oneMinute,geometry.normal,geometry.long]){
      const expected=expectedRect(event);
      assert.ok(Math.abs(event.visual.y-expected.top)<1.1,
        "Activity top must use its clipped start without a minimum-height block");
      const heightTolerance=Math.max(0.02,Math.abs(expected.height)*0.005);
      assert.ok(Math.abs(event.visual.height-expected.height)<heightTolerance,
        "Activity height must equal the true clipped start/end interval");
      assert.equal(event.button.width,84,"Lane width remains fixed and readable");
      assert.ok(event.aria.includes(event.title),
        "Full Activity title remains in its accessible name");
      assert.ok(event.occurrenceKey,"Activity occurrence identity remains attached");
    }
    const [greedyA,greedyB,greedyC,greedyD]=geometry.greedy;
    assert.deepEqual([greedyA.state,greedyB.state,greedyC.state,greedyD.state],
      ["visible","folded","visible","folded"],
      "Greedy folding keeps A and C visible while hiding only their colliding followers");
    assert.equal(greedyB.comparedTo,greedyA.id,"Hidden B is compared with visible lead A");
    assert.equal(greedyC.state,"visible",
      "C remains visible after hidden B even when other non-colliding lane titles intervene");
    assert.notEqual(greedyC.comparedTo,greedyB.id,"Hidden B never becomes a later candidate's lead");
    assert.equal(greedyD.comparedTo,greedyC.id,"D compares with current visible lead C, not hidden B");
    const overlaps=(a,b)=>a.left<b.right&&b.left<a.right&&a.top<b.bottom&&b.top<a.bottom;
    assert.ok(overlaps(greedyA.screenRect,greedyB.screenRect),"A/B rendered screen rectangles collide");
    assert.ok(!overlaps(greedyA.screenRect,greedyC.screenRect),"A/C rendered screen rectangles are separate");
    assert.ok(overlaps(greedyC.screenRect,greedyD.screenRect),"C/D rendered screen rectangles collide");
    assert.ok(geometry.greedy[0].screenRect&&geometry.greedy.every(item=>item.measuredRect),
      "Collision evidence includes measured DOM text rectangles in screen coordinates");
    assert.ok(geometry.oneMinute.aria.includes("Greedy_A")&&
      (await frame.$eval('[data-event-seq="301"]',button=>button.getAttribute("aria-label"))).includes("Greedy_B"),
      "Full titles remain accessible when their rendered labels are folded");
    assert.ok(geometry.greedy.every(item=>item.pointerEvents==="none"&&item.ariaHidden==="true"),
      "Decorative title overlays do not intercept the event button or duplicate accessibility speech");
    assert.ok(geometry.independentLaneTitles.every(item=>item.state==="visible"),
      "Non-occluding lane titles remain visible at the same or nearby times");
    assert.ok(geometry.longTitle.foldedTitle.text.endsWith("…")&&
      geometry.longTitle.foldedTitle.source.length>geometry.longTitle.foldedTitle.text.length,
      "An overlong title is truncated on one line with a visible ellipsis");
    assert.ok(geometry.longTitle.foldedTitle.measuredRect.right-
      geometry.longTitle.foldedTitle.measuredRect.left<=geometry.longTitle.foldedTitle.maxWidth+0.5,
      "The measured rendered title rectangle fits the existing lane width");
    assert.ok(geometry.longTitle.aria.includes(geometry.longTitle.foldedTitle.source),
      "Truncation preserves the full accessible title");
    const greedyTimes=await Promise.all([301,302,303].map(seq=>frame.$eval(
      `[data-event-seq="${seq}"]`,element=>({start:Number(element.dataset.startMillis),
        end:Number(element.dataset.endMillis)}))));
    const intervals=[{start:geometry.oneMinute.start,end:geometry.oneMinute.end},...greedyTimes];
    assert.ok(intervals.slice(1).every((item,index)=>item.start>=intervals[index].end),
      "The folded A/B/C/D fixture has no overlapping event time intervals");
    assert.equal(geometry.long.visual.height,geometry.long.button.height,
      "The long interval is clipped to the full visible timeline height");
    for(const point of [geometry.alarm,geometry.exactDeadline]){
      const expectedY=point.surfaceY+(point.start-point.from)/(point.to-point.from)*point.surfaceHeight;
      assert.equal(point.geometry,"point");
      assert.ok(Math.abs((point.line.y+point.line.height/2)-expectedY)<1.1,
        "Point marker line must stay at the exact projected instant");
      assert.ok(point.aria,"Point remains separately accessible and clickable");
    }
    assert.ok(geometry.dateDeadline.text.includes("2026-10-22")&&
      geometry.dateDeadline.text.includes("无具体时刻"));
    assert.equal(geometry.dateDeadline.date,"2026-10-22");
    assert.equal(geometry.dateDeadline.inWing,false,"Date Deadline is outside time lanes");
    assert.equal(geometry.left.laneCount,12,"Date Deadline lane 99 does not inflate left lanes");
    assert.equal(geometry.right.laneCount,12);
    assert.ok(geometry.left.scrollWidth>geometry.left.clientWidth&&
      geometry.right.scrollWidth>geometry.right.clientWidth,"Both wings have independent overflow");
    assert.ok(geometry.left.scrollLeft>0&&geometry.right.scrollLeft===0,
      "Initial view anchors the left wing at the center and the right wing at its first lane");
    assert.ok(Math.abs((geometry.left.laneZero.x+geometry.left.laneZero.width)-
      (geometry.left.viewport.x+geometry.left.viewport.width))<1.1);
    assert.ok(Math.abs(geometry.right.laneZero.x-geometry.right.viewport.x)<1.1);
    const changedScroll=await frame.evaluate(()=>{
      const left=document.querySelector("#timelineLeft"),right=document.querySelector("#timelineRight");
      const leftStart=left.scrollLeft;
      right.scrollLeft=right.scrollWidth;
      const rightEnd=right.scrollLeft;
      const leftAfterRight=left.scrollLeft;
      left.scrollLeft=0;
      return {leftStart,leftAfterRight,leftAfterOwn:left.scrollLeft,
        rightEnd,rightAfterLeft:right.scrollLeft};
    });
    assert.ok(changedScroll.leftStart>0&&changedScroll.rightEnd>0);
    assert.equal(changedScroll.leftAfterRight,changedScroll.leftStart,
      "Right wing scrolling must not move the left wing");
    assert.equal(changedScroll.leftAfterOwn,0);
    assert.equal(changedScroll.rightAfterLeft,changedScroll.rightEnd,
      "Left wing scrolling must not move the right wing");
    await frame.waitForFunction(()=>[101,301,302,303].every(seq=>
      document.querySelector(`[data-event-seq="${seq}"] .timelineTitle`).dataset.titleFoldState==="clipped")&&
      [201,202,203].every(seq=>document.querySelector(`[data-event-seq="${seq}"] .timelineTitle`)
        .dataset.titleFoldState==="clipped"));
    await frame.evaluate(()=>{
      const left=document.querySelector("#timelineLeft"),right=document.querySelector("#timelineRight");
      left.scrollLeft=left.scrollWidth;right.scrollLeft=0;
    });
    await frame.waitForFunction(()=>{
      const read=seq=>document.querySelector(`[data-event-seq="${seq}"] .timelineTitle`).dataset.titleFoldState;
      return read(101)==="visible"&&read(301)==="folded"&&read(302)==="visible"&&read(303)==="folded"&&
        [201,202,203].every(seq=>read(seq)==="visible");
    });
    const afterHorizontalReturn=await frame.evaluate(()=>[101,301,302,303].map(seq=>{
      const title=document.querySelector(`[data-event-seq="${seq}"] .timelineTitle`);
      return [title.dataset.titleFoldState,title.dataset.titleComparedTo||null];
    }));
    assert.deepEqual(afterHorizontalReturn.map(item=>item[0]),["visible","folded","visible","folded"],
      "Horizontal wing scrolling and clipping deterministically restore the same folding decisions");
    await frame.evaluate(end=>{
      const left=document.querySelector("#timelineLeft"),right=document.querySelector("#timelineRight");
      left.scrollLeft=0;right.scrollLeft=end;
    },changedScroll.rightEnd);
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="clipped");
    const verticalScroll=await frame.evaluate(()=>{
      const axis=document.querySelector("#axisScroll"),surface=document.querySelector("#timelineSurface");
      const oldHeight=surface.style.height;
      surface.style.height=(axis.clientHeight+320)+"px";
      document.querySelector("#timelineRight").scrollLeft=0;
      axis.scrollTop=120;
      return {oldHeight,scrollTop:axis.scrollTop};
    });
    assert.equal(verticalScroll.scrollTop,120,"Timeline viewport can scroll vertically for clipping reflow");
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="visible");
    const afterVerticalScroll=await frame.evaluate(()=>[101,301,302,303].map(seq=>{
      const title=document.querySelector(`[data-event-seq="${seq}"] .timelineTitle`);
      return [title.dataset.titleFoldState,title.dataset.titleComparedTo||null];
    }));
    assert.deepEqual(afterVerticalScroll.map(item=>item[0]),["visible","folded","visible","folded"],
      "Vertical scrolling recomputes screen rectangles without changing the greedy lead sequence");
    await frame.evaluate(oldHeight=>{
      const axis=document.querySelector("#axisScroll"),surface=document.querySelector("#timelineSurface");
      surface.style.height=oldHeight;axis.scrollTop=0;
    },verticalScroll.oldHeight);
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="visible");
    await frame.evaluate(end=>{document.querySelector("#timelineRight").scrollLeft=end},changedScroll.rightEnd);
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="clipped");
    await frame.click('[data-action="zoomTimeline"][data-direction="in"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="16 小时");
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="301"] .timelineTitle')
      .dataset.titleFoldState==="clipped");
    const zoomedScroll=await frame.evaluate(()=>({
      left:document.querySelector("#timelineLeft").scrollLeft,
      right:document.querySelector("#timelineRight").scrollLeft,
      oneMinute:document.querySelector('[data-event-seq="101"] .tEventVisual').getBoundingClientRect().height,
      height:document.querySelector("#timelineSurface").getBoundingClientRect().height
    }));
    assert.equal(zoomedScroll.left,0,"Y zoom preserves independent left-wing scroll state");
    assert.equal(zoomedScroll.right,changedScroll.rightEnd,"Y zoom preserves independent right-wing scroll state");
    assert.ok(Math.abs(zoomedScroll.oneMinute-
      zoomedScroll.height*60000/(16*3600000))<1.1,
      "Y zoom changes scale while the one-minute interval remains geometrically exact");
    await frame.evaluate(()=>{document.querySelector("#timelineRight").scrollLeft=0});
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="301"] .timelineTitle')
      .dataset.titleFoldState==="folded");
    const zoomedFoldTrace=await frame.evaluate(()=>[101,301,302,303].map(seq=>{
      const title=document.querySelector(`[data-event-seq="${seq}"] .timelineTitle`);
      return [title.dataset.titleFoldState,title.dataset.titleComparedTo||null];
    }));
    assert.deepEqual(zoomedFoldTrace.map(item=>item[0]),["visible","folded","visible","folded"],
      "Zoom recalculates the same deterministic greedy lead sequence");
    await frame.click('[data-action="resetTimelineZoom"]');
    await frame.waitForFunction(()=>document.querySelector("#timeRangeLabel").textContent==="24 小时");
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="301"] .timelineTitle')
      .dataset.titleFoldState==="folded");
    await frame.evaluate(()=>{document.querySelector("#timelineRight").scrollLeft=0});
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="visible");
    for(let i=0;i<8;i++)await frame.click('[data-action="panTimeline"][data-delta-minutes="180"]');
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="clipped");
    await frame.click('[data-action="today"]');
    await frame.waitForFunction(()=>document.querySelector('[data-event-seq="101"] .timelineTitle')
      .dataset.titleFoldState==="visible");
    assert.equal(geometry.events.length,28,"All mock events, including the date Deadline, remain present");
    for(const event of geometry.events){
      assert.ok(event.aria&&event.title&&event.aria.includes(event.title),
        "Every complete event title remains in Accessibility");
      assert.ok(event.tabIndex>=0,"Every event remains in the keyboard focus order");
      assert.ok(event.occurrenceKey&&event.revision,"Every event retains occurrence key and revision");
      await frame.$eval(`[data-event-seq="${event.seq}"]`,element=>{element.focus();element.click()});
      await frame.waitForFunction(seq=>{
        const sheet=document.querySelector("#skinSheet");
        return sheet&&!sheet.hidden&&
          document.querySelector("#sheetSubtitle").textContent.includes("#"+seq+" ·");
      },{},event.seq);
      await frame.click("#sheetClose");
      await frame.waitForFunction(()=>document.querySelector("#skinSheet").hidden);
    }
    console.log("Clear timeline true interval/point/date geometry, greedy title folding, accessible targets, and independent wing scroll PASS",
      {oneMinute:geometry.oneMinute.visual,long:geometry.long.visual,
        greedy:geometry.greedy,left:geometry.left,right:geometry.right,events:geometry.events.length});
    await page.select("#scenario","normal");
    await frame.waitForSelector(".tEvent.ACTIVITY");

    // The Web UI must work through the same typed command envelopes as Android,
    // not by silently opening a native Compose editor.
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
    const clearHtml=Buffer.from(source.files["web/index.html"],"base64").toString("utf8");
    assert.ok(clearHtml.includes('data-action="zoomTimeline"')&&
      clearHtml.includes('data-action="resetTimelineZoom"'),
      "The editable canonical Clear source must contain both Host action controls");
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
