(() => {"use strict";
// First-party Clear runtime. No direct storage, network, Android or calendar access.
// All reads are complete bounded host projections; all writes pass the v1 action gate.
const $=id=>document.getElementById(id);
const darkMedia=window.matchMedia("(prefers-color-scheme: dark)");
function applyTheme(){document.documentElement.classList.toggle("dark",darkMedia.matches)}
applyTheme();
darkMedia.addEventListener("change",applyTheme);
let port=null, bridgeReady=false, state={}, currentPage="TIMELINE", memoFilter="open",localOptions=false;
const bridgeOutbox=[];
const MAX_PENDING_BRIDGE_MESSAGES=64;
let timelineScrollInitialized=false;
let timelineDrag=null,timelineCameraPan=null,suppressTimelineClickUntil=0;
let titleFoldFrame=0,titleFoldResizeObserver=null;
let aiReviewSelectionId="",aiReviewSelected=new Set();
let attachmentRequestPending=false;
let appliedChatPrefillRevision=null;
let activeChatMessagePress=null;
let timelineCreateDialog=null;
let profileEditorSaveRevision=-1,profileEditorDirty=false;
function closeChatMessageMenus(){
  for(const menu of document.querySelectorAll(".chat-message-actions:not([hidden])"))menu.hidden=true;
  for(const trigger of document.querySelectorAll('.chat-message-menu-trigger[aria-expanded="true"]'))
    trigger.setAttribute("aria-expanded","false");
}
function cancelChatMessagePress(pointerId){
  if(!activeChatMessagePress||activeChatMessagePress.pointerId!==pointerId)return;
  clearTimeout(activeChatMessagePress.timer);activeChatMessagePress=null;
}
document.addEventListener("pointermove",event=>{
  const press=activeChatMessagePress;
  if(press&&press.pointerId===event.pointerId&&
    Math.hypot(event.clientX-press.x,event.clientY-press.y)>12)
    cancelChatMessagePress(event.pointerId);
},{passive:true});
document.addEventListener("pointerup",event=>cancelChatMessagePress(event.pointerId),{passive:true});
document.addEventListener("pointercancel",event=>cancelChatMessagePress(event.pointerId),{passive:true});
function postBridgeMessage(message){
  const encoded=JSON.stringify(message);
  if(!port||!bridgeReady){
    if(bridgeOutbox.length>=MAX_PENDING_BRIDGE_MESSAGES)return false;
    bridgeOutbox.push(encoded);
    return true;
  }
  try{port.postMessage(encoded);return true}catch{return false}
}
function flushBridgeOutbox(){
  if(!port||!bridgeReady)return;
  while(bridgeOutbox.length){
    const encoded=bridgeOutbox.shift();
    try{port.postMessage(encoded)}catch{
      bridgeOutbox.unshift(encoded);
      bridgeReady=false;
      return;
    }
  }
}
const send=(name,extra={})=>{
  return postBridgeMessage({version:1,type:"action",payload:{name,...extra}});
};
const node=(tag,cls,text)=>{
  const el=document.createElement(tag);
  if(cls)el.className=cls;
  if(text!=null)el.textContent=String(text);
  return el;
};
function titleGraphemes(value){
  if(typeof Intl.Segmenter==="function")
    return [...new Intl.Segmenter(undefined,{granularity:"grapheme"}).segment(value)]
      .map(part=>part.segment);
  return Array.from(value);
}
function renderedTextRect(element){
  const range=document.createRange();
  range.selectNodeContents(element);
  const rects=[...range.getClientRects()].filter(rect=>rect.width>0&&rect.height>0);
  if(!rects.length)return null;
  return{
    left:Math.min(...rects.map(rect=>rect.left)),
    top:Math.min(...rects.map(rect=>rect.top)),
    right:Math.max(...rects.map(rect=>rect.right)),
    bottom:Math.max(...rects.map(rect=>rect.bottom))
  };
}
function intersectScreenRects(first,second){
  if(!first||!second)return null;
  const rect={left:Math.max(first.left,second.left),top:Math.max(first.top,second.top),
    right:Math.min(first.right,second.right),bottom:Math.min(first.bottom,second.bottom)};
  return rect.right>rect.left&&rect.bottom>rect.top?rect:null;
}
function elementClientRect(element){
  if(!element)return null;
  const bounds=element.getBoundingClientRect();
  const left=bounds.left+element.clientLeft,top=bounds.top+element.clientTop;
  const rect={left,top,right:left+element.clientWidth,bottom:top+element.clientHeight};
  return rect.right>rect.left&&rect.bottom>rect.top?rect:null;
}
function fitTimelineTitle(element){
  const source=String(element.dataset.titleSource||"").replace(/[\r\n]+/g," ");
  const maxWidth=Math.max(0,Number(element.dataset.titleMaxWidth)||0);
  element.style.visibility="hidden";
  element.style.width="max-content";
  element.textContent=source;
  let measured=renderedTextRect(element);
  let shown=source;
  if(measured&&measured.right-measured.left>maxWidth){
    const parts=titleGraphemes(source);
    let low=0,high=parts.length,best="";
    while(low<=high){
      const middle=(low+high)>>>1,candidate=parts.slice(0,middle).join("")+"…";
      element.textContent=candidate;
      const rect=renderedTextRect(element);
      if(rect&&rect.right-rect.left<=maxWidth){best=candidate;low=middle+1}
      else high=middle-1;
    }
    shown=best;
    element.textContent=shown;
    measured=renderedTextRect(element);
  }
  element.dataset.titleDisplay=shown;
  if(!measured||maxWidth<=0){
    element.style.width="0px";
    element.style.visibility="hidden";
    return null;
  }
  const width=measured.right-measured.left;
  element.style.width=width+"px";
  if(element.dataset.titleFrameTop){
    element.dataset.titlePlacement="start-anchor";
    const frameTop=Number(element.dataset.titleFrameTop)||0;
    element.style.top=frameTop+"px";
  }
  return renderedTextRect(element);
}
function recomputeTimelineTitleFolding(){
  const timeline=document.querySelector("#timelineSurface");
  const viewport=document.querySelector("#axisScroll");
  const folding=window.PowerCalTitleFolding;
  if(!timeline||!viewport||!folding?.fold)return;
  const viewportRect=elementClientRect(viewport);
  const screenRect={left:0,top:0,right:window.innerWidth,bottom:window.innerHeight};
  const candidates=[],elementsById=new Map();
  for(const element of timeline.querySelectorAll("[data-title-fold-id]")){
    const wing=element.closest(".timelineWing");
    const clip=intersectScreenRects(elementClientRect(wing),intersectScreenRects(viewportRect,screenRect));
    const measured=fitTimelineTitle(element);
    const visibleRect=intersectScreenRects(measured,clip);
    element.dataset.titleMeasuredRect=measured?JSON.stringify(measured):"";
    if(!visibleRect){
      element.classList.add("title-fold-hidden");
      element.dataset.titleFoldState="clipped";
      element.dataset.titleComparedTo="";
      element.dataset.titleScreenRect="";
      continue;
    }
    const button=element.closest(".tEvent");
    const stableId=element.dataset.titleFoldId;
    candidates.push({stableId,eventSeq:Number(button?.dataset.eventSeq)||0,
      regionId:"timeline-visible",rect:visibleRect});
    elementsById.set(stableId,element);
  }
  const result=folding.fold(candidates);
  for(const decision of result.decisions){
    const element=elementsById.get(decision.candidate.stableId);
    if(!element)continue;
    element.classList.toggle("title-fold-hidden",!decision.visible);
    element.dataset.titleFoldState=decision.visible?"visible":"folded";
    element.dataset.titleComparedTo=decision.comparedTo||"";
    element.dataset.titleScreenRect=JSON.stringify(decision.candidate.rect);
    element.style.visibility=decision.visible?"visible":"hidden";
  }
}
function scheduleTimelineTitleFolding(){
  if(titleFoldFrame)return;
  titleFoldFrame=requestAnimationFrame(()=>{
    titleFoldFrame=0;
    recomputeTimelineTitleFolding();
  });
}
for(const id of ["axisScroll","timelineLeft","timelineRight"]){
  $(id)?.addEventListener("scroll",scheduleTimelineTitleFolding,{passive:true});
}
window.addEventListener("resize",scheduleTimelineTitleFolding,{passive:true});
if(typeof ResizeObserver==="function"){
  titleFoldResizeObserver=new ResizeObserver(scheduleTimelineTitleFolding);
  for(const id of ["axisScroll","timelineLeft","timelineRight"]){
    const element=$(id);if(element)titleFoldResizeObserver.observe(element);
  }
}
if(document.fonts?.ready)document.fonts.ready.then(scheduleTimelineTitleFolding).catch(()=>{});
const dateText=(ms,zone,options)=>{
  try{return new Intl.DateTimeFormat("zh-CN",{timeZone:zone||"Asia/Shanghai",...options}).format(new Date(ms))}
  catch{return new Date(ms).toISOString().slice(0,10)}
};
const memoLocalDate=(ms,zone)=>{
  const value=Number(ms);
  if(!Number.isFinite(value)||Number.isNaN(new Date(value).getTime()))return "无日期";
  try{
    const parts=Object.fromEntries(new Intl.DateTimeFormat("en-US",{
      timeZone:zone,year:"numeric",month:"2-digit",day:"2-digit"
    }).formatToParts(new Date(value)).map(part=>[part.type,part.value]));
    if(parts.year&&parts.month&&parts.day)return `${parts.year}-${parts.month}-${parts.day}`;
  }catch{}
  return "无日期";
};
const relativeTime=(ms,zone)=>dateText(ms,zone,{hour:"2-digit",minute:"2-digit",hour12:false});
const proposalDateText=(ms,zone)=>{
  const value=Number(ms);if(!Number.isFinite(value))return "无效日期";
  if(Number.isNaN(new Date(value).getTime()))return "无效日期";
  try{return dateText(value,zone,{year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit",hour12:false})}
  catch{return "无效日期"}
};
function showTimelineCreateDialog(sourceButton){
  if(!timelineCreateDialog){
    const dialog=document.createElement("dialog");
    dialog.id="timelineCreateDialog";
    dialog.setAttribute("aria-labelledby","timelineCreateTitle");
    dialog.setAttribute("aria-describedby","timelineCreateDescription");
    Object.assign(dialog.style,{boxSizing:"border-box",width:"min(92vw, 25rem)",
      maxWidth:"92vw",padding:"1.25rem",border:"1px solid rgba(127,127,127,.35)",
      borderRadius:"1rem",background:"var(--bg-panel, #fff)",color:"var(--fg, #171717)",
      boxShadow:"0 1rem 3rem rgba(0,0,0,.24)"});
    const title=document.createElement("h2");
    title.id="timelineCreateTitle";title.textContent="新建事件";
    const description=document.createElement("p");
    description.id="timelineCreateDescription";
    description.textContent="选择类型。时间将按当前时间轴位置预填。";
    const choices=document.createElement("div");
    Object.assign(choices.style,{display:"grid",gridTemplateColumns:"1fr 1fr",gap:".65rem"});
    for(const [kind,label] of [["ALARM","提醒"],["ACTIVITY","活动"],
      ["DEADLINE","截止事项"],["MEMO","Memo"]]){
      const button=document.createElement("button");
      button.type="button";button.dataset.timelineCreateKind=kind;
      button.textContent=label;
      Object.assign(button.style,{minHeight:"3rem",padding:".65rem",font:"inherit",
        border:"1px solid rgba(127,127,127,.4)",borderRadius:".7rem",
        background:"var(--bg-control, transparent)",color:"inherit",cursor:"pointer"});
      button.addEventListener("click",()=>{
        dialog.close();
        const proxy=document.createElement("button");
        proxy.dataset.action="createDraft";proxy.dataset.kind=kind;
        if(!window.PowerCalEdit?.routeAction(proxy,state)){
          $("timelineCreateDescription").textContent="宿主编辑服务暂不可用，请稍后重试。";
          dialog.showModal();
        }
      });
      choices.append(button);
    }
    const footer=document.createElement("div");
    Object.assign(footer.style,{display:"flex",justifyContent:"flex-end",marginTop:"1rem"});
    const cancel=document.createElement("button");
    cancel.type="button";cancel.textContent="取消";
    Object.assign(cancel.style,{minHeight:"2.75rem",padding:".5rem 1rem",font:"inherit",
      border:"0",borderRadius:".7rem",background:"transparent",color:"inherit",cursor:"pointer"});
    cancel.addEventListener("click",()=>dialog.close());
    footer.append(cancel);dialog.append(title,description,choices,footer);
    dialog.addEventListener("close",()=>sourceButton?.focus());
    dialog.addEventListener("click",event=>{if(event.target===dialog)dialog.close()});
    document.body.append(dialog);timelineCreateDialog=dialog;
  }
  if(typeof timelineCreateDialog.showModal==="function")timelineCreateDialog.showModal();
  else timelineCreateDialog.setAttribute("open","");
  timelineCreateDialog.querySelector("[data-timeline-create-kind]")?.focus();
}
function onAction(button){
  const name=button.dataset.action;if(!name||button.disabled)return;
  if(name==="createDraft"&&(currentPage==="TIMELINE"||currentPage==="TOOLS")){
    showTimelineCreateDialog(button);return;
  }
  if(window.PowerCalEdit?.routeAction(button,state))return;
  if(name==="openEvent"){
    const payload={eventSeq:Number(button.dataset.eventSeq),
      expectedRevision:Number(button.dataset.revision)};
    if(button.dataset.originalOccurrenceKey)
      payload.occurrenceKey=button.dataset.originalOccurrenceKey;
    send(name,payload);
  }
  else if(name==="createDraft")send(name,{kind:button.dataset.kind});
  else if(name==="toggleMemo")send(name,{eventSeq:Number(button.dataset.eventSeq),
    expectedRevision:Number(button.dataset.revision),
    occurrenceKey:button.dataset.occurrenceKey,completed:button.dataset.completed==="true"});
  else if(name==="loadMoreMemo")send(name);
  else if(name==="invokeOption")send(name,{id:button.dataset.optionId});
  else if(name==="panTimeline")send(name,{deltaMinutes:Number(button.dataset.deltaMinutes)});
  else if(name==="zoomTimeline")send(name,{direction:button.dataset.direction});
  else if(name==="resetTimelineZoom")send(name);
  else if(name==="jumpTimelineDay")send(name,{date:button.dataset.date});
  else if(name==="browseMonth")send(name,{deltaMonths:Number(button.dataset.deltaMonths)});
  else if(name==="selectMonthDate")send(name,{date:button.dataset.date});
  else {if(["timeline","memo","month","ai","settings","nativeSettings","openAiSettings"].includes(name))localOptions=false;send(name)}
}
function cancelTimelineDrag(){
  const gesture=timelineDrag;
  if(!gesture)return;
  clearTimeout(gesture.timer);
  gesture.button.classList.remove("timeline-dragging");
  gesture.button.style.transform=gesture.originalTransform;
  timelineDrag=null;
  scheduleTimelineTitleFolding();
}
function startTimelineDrag(button,item,surface,source,pointerId,x,y){
  if(timelineDrag||!Number.isFinite(Number(item.startMillis)))return;
  const gesture={button,item,surface,source,pointerId,startX:x,startY:y,
    deltaY:0,active:false,timer:0,originalTransform:button.style.transform};
  timelineDrag=gesture;
  gesture.timer=setTimeout(()=>{
    if(timelineDrag!==gesture||!button.isConnected)return;
    gesture.active=true;
    button.classList.add("timeline-dragging");
    const status=$("timelineMoveStatus");
    if(status)status.textContent=`正在移动：${String(item.title||"未命名事项")}`;
  },500);
}
function updateTimelineDrag(x,y,event){
  const gesture=timelineDrag;
  if(!gesture)return;
  const dx=x-gesture.startX,dy=y-gesture.startY;
  if(!gesture.active){
    if(Math.hypot(dx,dy)>10){
      if(gesture.source==="touch")suppressTimelineClickUntil=Date.now()+900;
      cancelTimelineDrag();
    }
    return;
  }
  gesture.deltaY=dy;
  if(event?.cancelable)event.preventDefault();
  gesture.button.style.transform=`translateY(${dy}px)`;
  scheduleTimelineTitleFolding();
}
function finishTimelineDrag(x,y,event){
  const gesture=timelineDrag;
  if(!gesture)return;
  updateTimelineDrag(x,y,event);
  const shouldMove=gesture.active&&gesture.button.isConnected&&Math.abs(gesture.deltaY)>2;
  const item=gesture.item;
  const deltaY=gesture.deltaY;
  const surfaceHeight=gesture.surface.getBoundingClientRect().height;
  const hours=Number(state.timeline?.hours);
  const anchor=Number(item.startMillis);
  cancelTimelineDrag();
  if(!shouldMove)return;
  suppressTimelineClickUntil=Date.now()+900;
  const targetMillis=Math.round(anchor+deltaY*hours*3_600_000/Math.max(1,surfaceHeight));
  if(!Number.isSafeInteger(targetMillis)){
    const status=$("timelineMoveStatus");
    if(status)status.textContent="移动位置超出支持范围";
    return;
  }
  void window.PowerCalEdit?.moveTimelineEvent(item,targetMillis);
}
function attachTimelineDrag(button,item,surface){
  button.addEventListener("pointerdown",event=>{
    if(event.pointerType==="touch"||event.button!==0)return;
    startTimelineDrag(button,item,surface,"pointer",event.pointerId,event.clientX,event.clientY);
  });
  button.addEventListener("touchstart",event=>{
    if(event.touches.length!==1)return;
    const touch=event.touches[0];
    startTimelineDrag(button,item,surface,"touch",touch.identifier,touch.clientX,touch.clientY);
  },{passive:true});
  button.addEventListener("contextmenu",event=>{
    if(timelineDrag?.button===button&&timelineDrag.active)event.preventDefault();
  });
}
document.addEventListener("pointermove",event=>{
  if(timelineDrag?.source==="pointer"&&timelineDrag.pointerId===event.pointerId)
    updateTimelineDrag(event.clientX,event.clientY,event);
},{passive:false});
document.addEventListener("pointerup",event=>{
  if(timelineDrag?.source==="pointer"&&timelineDrag.pointerId===event.pointerId)
    finishTimelineDrag(event.clientX,event.clientY,event);
});
document.addEventListener("pointercancel",event=>{
  if(timelineDrag?.source==="pointer"&&timelineDrag.pointerId===event.pointerId)
    cancelTimelineDrag();
});
document.addEventListener("touchmove",event=>{
  const gesture=timelineDrag;
  if(gesture?.source!=="touch")return;
  const touch=[...event.touches].find(value=>value.identifier===gesture.pointerId);
  if(touch)updateTimelineDrag(touch.clientX,touch.clientY,event);
},{passive:false});
document.addEventListener("touchend",event=>{
  const gesture=timelineDrag;
  if(gesture?.source!=="touch")return;
  const touch=[...event.changedTouches].find(value=>value.identifier===gesture.pointerId);
  if(touch)finishTimelineDrag(touch.clientX,touch.clientY,event);
  else cancelTimelineDrag();
});
document.addEventListener("touchcancel",()=>{
  if(timelineDrag?.source==="touch")cancelTimelineDrag();
});
function canStartTimelineCameraPan(target){
  if(!(target instanceof Element))return false;
  return !target.closest("button,a,input,select,textarea,[contenteditable='true'],"+
    "[data-action],[data-local],.tEvent,[role='button'],dialog");
}
const TIMELINE_CAMERA_TOUCH_SLOP_CSS_PX=8;
function updateTimelineCameraPan(event){
  const gesture=timelineCameraPan;
  if(!gesture||gesture.pointerId!==event.pointerId)return;
  const dx=event.clientX-gesture.startX,dy=event.clientY-gesture.startY;
  const deltaY=dy-gesture.lastDy;
  gesture.lastDy=dy;
  if(!gesture.active){
    if(Math.abs(dx)>Math.abs(dy)*1.2){timelineCameraPan=null;return}
    if(Math.abs(dy)>Math.abs(dx)*1.2&&event.cancelable)event.preventDefault();
    gesture.verticalTravel+=Math.abs(deltaY);
    if(gesture.verticalTravel<TIMELINE_CAMERA_TOUCH_SLOP_CSS_PX)return;
    if(Math.abs(dy)<=Math.abs(dx)*1.2){timelineCameraPan=null;return}
    gesture.active=true;
    try{gesture.axis.setPointerCapture(event.pointerId)}catch{}
    gesture.appliedDy=deltaY;
  }else{
    gesture.appliedDy+=deltaY;
  }
  if(event.cancelable)event.preventDefault();
  const desiredMinutes=Math.round(-gesture.appliedDy/gesture.height*gesture.hours*60);
  let remaining=desiredMinutes-gesture.sentMinutes;
  while(remaining!==0){
    const deltaMinutes=Math.max(-2880,Math.min(2880,remaining));
    if(!send("panTimeline",{deltaMinutes}))break;
    gesture.sentMinutes+=deltaMinutes;
    remaining=desiredMinutes-gesture.sentMinutes;
  }
}
$("axisScroll")?.addEventListener("pointerdown",event=>{
  if((event.pointerType!=="touch"&&event.pointerType!=="")||!event.isTrusted||!event.isPrimary||event.button!==0||
      currentPage!=="TIMELINE"||localOptions||timelineCameraPan||!canStartTimelineCameraPan(event.target))return;
  const axis=$("axisScroll"),hours=Number(state.timeline?.hours),height=axis?.clientHeight||0;
  if(!axis||!Number.isFinite(hours)||hours<=0||height<=0)return;
  timelineCameraPan={axis,pointerId:event.pointerId,startX:event.clientX,startY:event.clientY,
    hours,height,lastDy:0,verticalTravel:0,appliedDy:0,sentMinutes:0,active:false};
});
document.addEventListener("pointermove",event=>{
  updateTimelineCameraPan(event);
},{passive:false});
document.addEventListener("pointerup",event=>{
  updateTimelineCameraPan(event);
  if(timelineCameraPan?.pointerId===event.pointerId)timelineCameraPan=null;
});
document.addEventListener("pointercancel",event=>{
  if(timelineCameraPan?.pointerId===event.pointerId)timelineCameraPan=null;
});
document.addEventListener("click",e=>{
  if(!e.target.closest(".chat-message-action-container"))closeChatMessageMenus();
  const local=e.target.closest("[data-local]");
  if(local){localOptions=local.dataset.local==="options";renderPage();return}
  const toggle=e.target.closest("[data-filter]");
  if(toggle){memoFilter=toggle.dataset.filter;renderMemo();return}
  const button=e.target.closest("[data-action]");
  if(button?.classList.contains("tEvent")&&Date.now()<suppressTimelineClickUntil){
    e.preventDefault();e.stopPropagation();return;
  }
  if(button)onAction(button);
});
const axisResetHit=$("axisResetHit");
let lastAxisResetTap=null,lastAxisResetSentAt=0;
axisResetHit?.addEventListener("pointerup",event=>{
  if(event.pointerType!=="touch"&&event.pointerType!=="pen")return;
  const now=performance.now(),point={x:event.clientX,y:event.clientY};
  if(lastAxisResetTap&&now-lastAxisResetTap.at<=420&&
      Math.hypot(point.x-lastAxisResetTap.x,point.y-lastAxisResetTap.y)<=28){
    send("resetTimelineZoom");lastAxisResetSentAt=performance.now();lastAxisResetTap=null;
  }else lastAxisResetTap={at:now,...point};
  event.preventDefault();event.stopPropagation();
});
axisResetHit?.addEventListener("dblclick",event=>{
  event.preventDefault();event.stopPropagation();lastAxisResetTap=null;
  if(performance.now()-lastAxisResetSentAt<500)return;
  lastAxisResetSentAt=performance.now();
  send("resetTimelineZoom");
});
axisResetHit?.addEventListener("keydown",event=>{
  if(event.key==="Enter"||event.key===" "){
    event.preventDefault();send("resetTimelineZoom");
  }
});
let monthSwipeStart=null,monthSwipeConsumed=false,monthSwipeReset=0;
$("monthGrid").addEventListener("pointerdown",event=>{
  if(event.isPrimary&&event.button===0)
    monthSwipeStart={x:event.clientX,y:event.clientY,pointerId:event.pointerId};
});
document.addEventListener("pointerup",event=>{
  const start=monthSwipeStart;
  if(!start||start.pointerId!==event.pointerId)return;
  monthSwipeStart=null;
  const dx=event.clientX-start.x,dy=event.clientY-start.y;
  if(Math.abs(dx)<72||Math.abs(dx)<=Math.abs(dy))return;
  monthSwipeConsumed=true;
  clearTimeout(monthSwipeReset);
  monthSwipeReset=setTimeout(()=>monthSwipeConsumed=false,500);
  send("browseMonth",{deltaMonths:dx<0?1:-1});
});
document.addEventListener("pointercancel",event=>{
  if(monthSwipeStart?.pointerId===event.pointerId)monthSwipeStart=null;
});
$("monthGrid").addEventListener("click",event=>{
  if(!monthSwipeConsumed)return;
  event.preventDefault();event.stopImmediatePropagation();
  monthSwipeConsumed=false;clearTimeout(monthSwipeReset);
},true);
$("monthYearTitle").addEventListener("click",()=>{
  const [year]=String(state.month?.month||"2026-10").split("-").map(Number);
  $("monthYear").value=year;
  updateMonthYearSubmit();
  openMonthSelectionDialog($("monthYearPickerDialog"));
});
$("monthNumberTitle").addEventListener("click",()=>{
  const [year]=String(state.month?.month||"2026-10").split("-").map(Number);
  $("monthPickerHeading").textContent=`选择月份 · ${year} 年`;
  openMonthSelectionDialog($("monthPickerDialog"));
});
function openMonthSelectionDialog(dialog){
  dialog.showModal();
  dialog.focus({preventScroll:true});
}
function updateMonthYearSubmit(){
  const year=Number($("monthYear").value);
  $("monthYearPickerSubmit").disabled=!Number.isInteger(year)||year<1900||year>2100;
}
$("monthYear").addEventListener("input",updateMonthYearSubmit);
$("monthYearPickerDialog").addEventListener("click",event=>{
  if(event.target===$("monthYearPickerDialog"))$("monthYearPickerDialog").close();
});
$("monthPickerDialog").addEventListener("click",event=>{
  if(event.target===$("monthPickerDialog"))$("monthPickerDialog").close();
});
$("monthPickerCancel").addEventListener("click",()=>$("monthPickerDialog").close());
$("monthYearPickerCancel").addEventListener("click",()=>$("monthYearPickerDialog").close());
$("monthYearPickerForm").addEventListener("submit",event=>{
  event.preventDefault();
  const year=Number($("monthYear").value);
  if(!Number.isInteger(year)||year<1900||year>2100)return;
  const [,month]=String(state.month?.month||"2026-10").split("-").map(Number);
  $("monthYearPickerDialog").close();
  send("selectMonth",{month:`${String(year).padStart(4,"0")}-${String(month).padStart(2,"0")}`});
});
document.querySelectorAll("#monthPickerForm [data-month-number]").forEach(button=>
  button.addEventListener("click",()=>{
    const [year]=String(state.month?.month||"2026-10").split("-").map(Number);
    const month=Number(button.dataset.monthNumber);
    if(!Number.isInteger(month)||month<1||month>12)return;
    $("monthPickerDialog").close();
    send("selectMonth",{month:`${String(year).padStart(4,"0")}-${String(month).padStart(2,"0")}`});
  }));
function closeProfileEditor(){
  const dialog=$("profileEditorDialog");
  if(dialog.open)dialog.close();
  profileEditorDirty=false;
  send("closeProfileEditor");
}
function saveProfileEditor(){
  const displayName=$("profileDisplayName").value;
  if(displayName.length>80)return;
  if(send("saveProfileEditor",{displayName}))
    $("profileEditorStatus").textContent="正在保存…";
}
$("profileDisplayName").addEventListener("input",()=>{
  profileEditorDirty=true;
  $("profileEditorStatus").textContent="";
});
$("profileSaveButton").addEventListener("click",saveProfileEditor);
$("profileEditorForm").addEventListener("submit",event=>{
  event.preventDefault();saveProfileEditor();
});
$("profileCancelButton").addEventListener("click",closeProfileEditor);
$("profileEditorDialog").addEventListener("cancel",event=>{
  event.preventDefault();closeProfileEditor();
});
$("chatForm").addEventListener("submit",e=>{
  e.preventDefault();
  const field=$("chatInput"),text=field.value.trim();
  if(!text||text.length>4000||state.aiLocked||state.chatBusy)return;
  send("sendChat",{text});field.value="";
});
$("chatClear").addEventListener("click",()=>{
  if($("chatClear").disabled)return;
  send("clearChat",{historyRevision:Number(state.chatHistoryRevision)||0});
});
function rewindChat(messageId,editAndResend){
  if(state.chatHistoryActionsAvailable!==true||typeof messageId!=="string")return;
  send("rewindChat",{messageId,historyRevision:Number(state.chatHistoryRevision)||0,editAndResend});
}
for(const button of document.querySelectorAll("[data-ai-batch]"))button.addEventListener("click",()=>{
  if(button.disabled)return;
  send("prepareAiBatch",{mode:button.dataset.aiBatch});
});
$("aiBatchCancel").addEventListener("click",()=>send("cancelAiBatch"));
$("aiBatchRelease").addEventListener("click",()=>send("releaseStuckAiBatch"));
window.addEventListener("message",e=>{
  if(e.data!=="powercal-port"||!e.ports?.[0])return;
  if(port){
    try{port.close()}catch{}
    port=null;bridgeReady=false;bridgeOutbox.length=0;
    window.PowerCalEdit?.resetTransport();
  }
  port=e.ports[0];
  lastModalVisibility=null;
  port.onmessage=event=>receive(event.data);
  publishModalVisibility();
});
let lastModalVisibility=null;
function publishModalVisibility(){
  if(!port)return;
  const modalOpen=document.querySelector('dialog[open]')!==null;
  if(lastModalVisibility===modalOpen)return;
  lastModalVisibility=modalOpen;
  try{port.postMessage(JSON.stringify({version:1,type:"presentation",payload:{modalOpen}}))}catch{}
}
const modalVisibilityObserver=new MutationObserver(publishModalVisibility);
modalVisibilityObserver.observe(document.documentElement,{
  subtree:true,childList:true,attributes:true,attributeFilter:["open"]
});
function receive(raw){
  let parsed;
  try{parsed=typeof raw==="string"?JSON.parse(raw):raw}catch{return}
  if(parsed?.version!==1)return;
  if(parsed.type==="result"){
    window.PowerCalEdit?.onResult(parsed.payload||{});
    return;
  }
  if(parsed.type!=="state")return;
  state=parsed.payload||{};currentPage=String(state.page||"TIMELINE").toUpperCase();
  attachmentRequestPending=Boolean(state.chatAttachmentBusy);
  $("pendingCounter").textContent=Number(state.pendingCount)||"";
  $("aiPendingCount").textContent=`待处理 ${Number(state.pendingCount)||0}`;
  $("memoCounter").textContent=Number(state.memoCount)||"";
  const hostNotice=typeof state.hostNotice==="string"?state.hostNotice.trim():"";
  $("hostNotice").textContent=hostNotice;
  $("hostNotice").hidden=hostNotice.length===0;
  $("app").classList.toggle("reduced",Boolean(state.reducedMotion));
  renderPage();
  if(state.hostDataReady===false)bridgeReady=false;
  else if(!bridgeReady){bridgeReady=true;flushBridgeOutbox()}
}
function renderPage(){
  const page=currentPage==="TOOLS"?"TIMELINE":currentPage;
  document.body.dataset.page=page.toLowerCase();
  const createButton=document.querySelector('[data-action="createDraft"]');
  if(createButton){createButton.title="新建事件";createButton.setAttribute("aria-label","新建事件")}
  const pages={TIMELINE:"timelinePage",MONTH:"monthPage",MEMO:"memoPage",AI:"aiPage",SETTINGS:"settingsPage"};
  for(const [key,id] of Object.entries(pages))$(id).hidden=key!==page||localOptions;
  $("extraOptions").hidden=!localOptions;
  document.querySelectorAll(".clear-tabbar button").forEach(b=>{
    const selected=page!=="MEMO"&&b.dataset.action?.toUpperCase()===page;
    b.classList.toggle("current",selected);
    if(selected)b.setAttribute("aria-current","page");else b.removeAttribute("aria-current");
  });
  const anchor=state.timeline?.anchorMillis||Date.now(),zone=state.timeline?.zoneId;
  $("dayTitle").textContent=dateText(anchor,zone,{year:"numeric",month:"long",day:"numeric",weekday:"short"}).replace(/日(?=周)/,"日 ");
  $("dateSubtitle").textContent="";
  const memoTitle=$("memoTitle"),memoSubtitle=$("memoSubtitle");
  if(memoTitle)memoTitle.textContent=memoFilter==="done"?"Memo · 已完成":"Memo · 未完成";
  if(memoSubtitle)memoSubtitle.textContent=`统一待办中心 · ${Number(state.memoCount)||0} 项 · 勾选状态可撤销`;
  if(page==="TIMELINE")renderTimeline();
  if(page==="MONTH")renderMonth();
  if(page==="MEMO")renderMemo();
  if(page==="AI")renderChat();
  if(page==="SETTINGS")renderSettings();
  renderProfileEditor();
  renderOptions();
  window.PowerCalEdit?.onState(state);
}
function renderSettings(){
  const status=state.notificationPermission;
  const label=$("notificationPermissionStatus");
  const button=document.querySelector('[data-action="requestNotificationPermission"]');
  if(label&&button){
    if(!status||typeof status.required!=="boolean"||typeof status.allowed!=="boolean"){
      label.textContent="暂时无法读取通知权限状态。";
      button.textContent="重新读取通知权限";
    }else if(!status.required){
      label.textContent="当前 Android 版本无需运行时通知权限。";
      button.textContent="打开系统通知设置";
    }else if(status.allowed){
      label.textContent="通知权限已允许；提醒仍可能受系统闹钟或后台限制影响。";
      button.textContent="通知权限已允许";
    }else{
      label.textContent="通知权限未允许；提醒触发会被记录为 BLOCKED。";
      button.textContent="请求通知权限";
    }
  }

  const exactStatus=state.exactAlarmPermission;
  const exactLabel=$("exactAlarmPermissionStatus");
  const exactButton=document.querySelector('[data-action="openExactAlarmSettings"]');
  if(exactLabel&&exactButton){
    if(!exactStatus||typeof exactStatus.required!=="boolean"||
      typeof exactStatus.allowed!=="boolean"){
      exactLabel.textContent="暂时无法读取精确闹钟权限状态。";
      exactButton.hidden=true;
    }else if(!exactStatus.required){
      exactLabel.textContent="当前 Android 版本无需单独授权精确闹钟。";
      exactButton.hidden=true;
    }else if(exactStatus.allowed){
      exactLabel.textContent="精确闹钟访问已允许；后台投递仍可能受设备厂商限制。";
      exactButton.textContent="精确闹钟设置";
      exactButton.hidden=false;
    }else{
      exactLabel.textContent="精确闹钟访问未允许；需要精确时刻的提醒会保持 BLOCKED。";
      exactButton.textContent="打开精确闹钟设置";
      exactButton.hidden=false;
    }
  }

  const reconcile=state.reminderReconcile;
  const reconcileLabel=$("reminderReconcileStatus");
  const reconcileButton=$("reconcileReminders");
  if(reconcileLabel&&reconcileButton){
    const reconcileStatus=reconcile&&typeof reconcile.status==="string"?reconcile.status:"IDLE";
    reconcileLabel.textContent=reconcile&&typeof reconcile.message==="string"?
      reconcile.message:"暂时无法读取提醒重建状态。";
    reconcileLabel.dataset.status=reconcileStatus.toLowerCase();
    reconcileButton.disabled=reconcileStatus==="RUNNING";
    reconcileButton.textContent=reconcileStatus==="RUNNING"?
      "正在重建提醒…":"按当前数据重建提醒调度";
  }

  const diagnosticsRoot=$("reminderDiagnostics");
  if(diagnosticsRoot){
    diagnosticsRoot.replaceChildren();
    const diagnostics=(Array.isArray(state.reminderDiagnostics)?
      state.reminderDiagnostics:[]).filter(item=>item&&typeof item==="object").slice(0,40);
    if(!diagnostics.length){
      const empty=node("p","quiet","还没有启用的提醒。");
      empty.setAttribute("role","listitem");
      diagnosticsRoot.append(empty);
    }
    const scheduleLabels={
      PENDING:"等待调度",SCHEDULED:"已交给系统",BLOCKED:"受阻，请检查权限",
      CANCELLED:"已取消",FIRED:"已触发"
    };
    for(const item of diagnostics){
      const row=node("div","reminder-diagnostic");
      row.setAttribute("role","listitem");
      const displaySeq=typeof item.displaySeq==="string"?item.displaySeq:"#????";
      row.append(node("strong","reminder-diagnostic-title",
        `${displaySeq} · ${String(item.title||"提醒")}`));
      const intent=item.intent&&typeof item.intent==="object"?item.intent:null;
      const desired=intent&&typeof intent.desiredState==="string"?intent.desiredState:"";
      const status=node("p","reminder-diagnostic-meta",
        "调度："+(scheduleLabels[desired]||"无待执行调度"));
      if(desired)status.dataset.state=desired.toLowerCase();
      row.append(status);
      if(intent&&typeof intent.androidHint==="string"&&intent.androidHint){
        row.append(node("p","reminder-diagnostic-meta",intent.androidHint));
      }
      if(typeof item.lastExecutionResult==="string"&&item.lastExecutionResult){
        row.append(node("p","reminder-diagnostic-meta","最近执行："+item.lastExecutionResult));
      }
      diagnosticsRoot.append(row);
    }
  }

  const systemRoot=$("systemDiagnostics");
  if(systemRoot){
    systemRoot.replaceChildren();
    const diagnostics=state.systemDiagnostics&&typeof state.systemDiagnostics==="object"?
      state.systemDiagnostics:null;
    if(!diagnostics||Array.isArray(diagnostics)||!Object.keys(diagnostics).length){
      const empty=node("p","quiet","诊断信息暂不可用。");
      empty.setAttribute("role","listitem");
      systemRoot.append(empty);
    }else{
      const addRow=(label,value)=>{
        const row=node("div","system-diagnostic");
        row.setAttribute("role","listitem");
        row.append(node("span","system-diagnostic-label",label));
        row.append(node("span","system-diagnostic-value",value));
        systemRoot.append(row);
      };
      const formalCount=Number(diagnostics.formalCount);
      addRow("正式事件",`${Number.isFinite(formalCount)?formalCount:"?"} 条`);
      addRow("下一个事件序号",typeof diagnostics.nextSequence==="string"?
        diagnostics.nextSequence:"未知");
      addRow("已分配序号",`${typeof diagnostics.identityCount==="string"?
        diagnostics.identityCount:"未知"} 个（含退役）`);
      const dbVersion=Number(diagnostics.databaseVersion);
      const dbName=typeof diagnostics.databaseName==="string"?diagnostics.databaseName:"未知";
      addRow("数据库版本",`v${Number.isFinite(dbVersion)?dbVersion:"?"} · ${dbName}`);
      addRow("视觉效果",diagnostics.blurEnabled===true?"分层玻璃":"简化实色界面");
      addRow("减少动画",diagnostics.reduceMotion===true?"已遵循系统设置":"未启用");
      const recurrences=Array.isArray(diagnostics.recurrences)?
        diagnostics.recurrences.filter(item=>item&&typeof item==="object").slice(0,3):[];
      for(const recurrence of recurrences){
        if(typeof recurrence.label==="string"&&typeof recurrence.value==="string")
          addRow(recurrence.label,recurrence.value);
      }
    }
  }
}
function timelineLaneAssignments(items,from,to){
  const bySide=new Map([["LEFT",[]],["RIGHT",[]]]);
  for(const item of items){
    const kind=String(item.kind||"").toUpperCase();
    if(!["ALARM","ACTIVITY","DEADLINE"].includes(kind))continue;
    const start=Number(item.startMillis);
    const isActivity=kind==="ACTIVITY";
    const end=isActivity?Number(item.endMillis):start+1;
    if(!Number.isFinite(start)||!Number.isFinite(end)||end<=start)continue;
    if(isActivity ? !(start<to&&end>from) : !(start>=from&&start<to))continue;
    const side=kind==="ACTIVITY"?"RIGHT":"LEFT";
    bySide.get(side).push({item,start,end});
  }
  const lanes=new Map();
  for(const rows of bySide.values()){
    rows.sort((a,b)=>a.start-b.start||
      (Number(a.item.eventSeq)||0)-(Number(b.item.eventSeq)||0)||
      String(a.item.occurrenceKey||a.item.originalOccurrenceKey||"").localeCompare(
        String(b.item.occurrenceKey||b.item.originalOccurrenceKey||"")));
    const laneEnds=[];
    for(const row of rows){
      let lane=laneEnds.findIndex(end=>end<=row.start);
      if(lane<0){lane=laneEnds.length;laneEnds.push(row.end)}
      else laneEnds[lane]=row.end;
      lanes.set(row.item,lane);
    }
  }
  return lanes;
}

function renderTimeline(){
  cancelTimelineDrag();
  const t=state.timeline;
  const labels=$("timeLabels"),left=$("timelineLeft"),right=$("timelineRight"),now=$("timeNow");
  const leftLanes=left.querySelector(".timelineLanes"),rightLanes=right.querySelector(".timelineLanes");
  const dates=$("dateDeadlineBar");
  const previousLeftScroll=left.scrollLeft,previousRightScroll=right.scrollLeft;
  const previousLeftOverflow=left.scrollWidth>left.clientWidth+1;
  const previousRightOverflow=right.scrollWidth>right.clientWidth+1;
  for(const el of [labels,leftLanes,rightLanes,now,dates])el.replaceChildren();
  dates.hidden=true;
  dates.classList.remove("is-empty");
  dates.style.removeProperty("--date-deadline-empty-height");
  dates.style.removeProperty("--date-deadline-empty-font-size");
  dates.style.removeProperty("--date-deadline-empty-line-height");
  if(!t){$("timeRangeLabel").textContent="尚无宿主数据";$("timelineEmpty").hidden=false;return}
  $("timeRangeLabel").textContent=t.hours+" 小时 / 屏";
  const from=Number(t.visibleStartMillis),to=Number(t.visibleEndMillis),span=Math.max(1,to-from);
  const zone=t.zoneId,axis=$("timelineSurface");
  if(!Number.isFinite(from)||!Number.isFinite(to)||to<=from)return;
  const position=ms=>100*(ms-from)/span;
  const kindLabel={ACTIVITY:"活动",ALARM:"提醒",DEADLINE:"截止事项"};
  const items=Array.isArray(t.items)?t.items:[];
  $("timelineEmpty").hidden=t.hasRecords===true||items.length!==0;
  const isDateOnlyDeadline=item=>String(item.kind).toUpperCase()==="DEADLINE"&&
    typeof item.deadlineDate==="string"&&/^\d{4}-\d{2}-\d{2}$/.test(item.deadlineDate);
  const dateItems=items.filter(isDateOnlyDeadline);
  const hasDateOnlyDeadlines=t.hasDateOnlyDeadlines===true;
  const hasVisibleDateOnlyDeadlines=t.hasVisibleDateOnlyDeadlines===true;
  const makeEventButton=(item,kind,ariaLabel)=>{
    const button=node("button","tEvent "+kind);
    button.type="button";button.dataset.action="openEvent";
    button.dataset.eventSeq=String(item.eventSeq);button.dataset.revision=String(item.revision);
    button.dataset.kind=kind;button.dataset.eventTitle=String(item.title||"");
    if(typeof item.occurrenceKey==="string")button.dataset.occurrenceKey=item.occurrenceKey;
    if(typeof item.originalOccurrenceKey==="string")
      button.dataset.originalOccurrenceKey=item.originalOccurrenceKey;
    button.setAttribute("aria-label",ariaLabel);button.title=ariaLabel;
    return button;
  };
  const makeTimelineTitle=(item,kind,text,maxWidth)=>{
    const occurrence=String(item.occurrenceKey||item.originalOccurrenceKey||
      `${item.startMillis}:${item.revision??0}`);
    const stableId=[String(item.eventSeq??0),kind,occurrence].join(":");
    const title=node("span","timelineTitle",text);
    title.setAttribute("aria-hidden","true");
    title.dataset.titleFoldId=stableId;
    title.dataset.titleSource=String(text??"");
    title.dataset.titleMaxWidth=String(maxWidth);
    return title;
  };
  if(dateItems.length){
    dates.hidden=false;
    for(const item of dateItems){
      const title=String(item.title||"截止事项");
      const label=`截止事项：${title}，${item.deadlineDate}，无具体时刻；长按并上下拖动可更改日期`;
      const button=makeEventButton(item,"DEADLINE",label);
      button.className="dateDeadline";button.dataset.temporalGeometry="date";
      button.dataset.deadlineDate=item.deadlineDate;
      button.dataset.startMillis=String(item.startMillis);
      const flag=document.createElementNS("http://www.w3.org/2000/svg","svg");
      flag.setAttribute("viewBox","0 0 24 24");flag.setAttribute("aria-hidden","true");
      flag.setAttribute("focusable","false");flag.classList.add("dateDeadlineFlag");
      const flagPath=document.createElementNS("http://www.w3.org/2000/svg","path");
      flagPath.setAttribute("d","M14.4 6 14 4H5v17h2v-7h5.6l.4 2h7V6z");
      flag.append(flagPath);
      const content=node("span","dateDeadlineContent");
      content.append(node("span","dateDeadlineTitle",title),
        node("span","dateDeadlineDate",`${item.deadlineDate} · 截止，无具体时刻`));
      button.append(flag,content);
      attachTimelineDrag(button,item,axis);
      dates.append(button);
    }
  }else if(hasDateOnlyDeadlines){
    dates.hidden=false;
    if(!hasVisibleDateOnlyDeadlines){
      const scale=Number(t.systemFontScale);
      const fontScale=Number.isFinite(scale)&&scale>0?scale:1;
      dates.classList.add("is-empty");
      dates.style.setProperty("--date-deadline-empty-height",`${64*fontScale}px`);
      dates.style.setProperty("--date-deadline-empty-font-size",`${12*fontScale}px`);
      dates.style.setProperty("--date-deadline-empty-line-height",`${16*fontScale}px`);
      dates.append(node("span","dateDeadlineEmpty","可见日期内没有日期级截止"));
    }
  }
  // Showing date-only deadlines changes the flex space available to the true-time
  // viewport. Measure only after the date strip has reached its visible layout.
  const height=Math.max(1,axis.clientHeight);
  const tickMinutes=t.hours<=0.75?1:t.hours<=2?5:t.hours<=4?10:t.hours<=6?30:t.hours<=12?60:t.hours<=26?120:240;
  const tickStep=tickMinutes*60000;
  const zonedParts=ms=>Object.fromEntries(new Intl.DateTimeFormat("en-CA",{
    timeZone:zone||"Asia/Shanghai",year:"numeric",month:"2-digit",day:"2-digit",
    hour:"2-digit",minute:"2-digit",hourCycle:"h23"
  }).formatToParts(new Date(ms)).map(part=>[part.type,part.value]));
  const localOffset=ms=>{
    const p=zonedParts(ms);
    return Date.UTC(Number(p.year),Number(p.month)-1,Number(p.day),Number(p.hour),Number(p.minute))-
      Math.floor(ms/60000)*60000;
  };
  const parts=zonedParts(from),minuteOfDay=Number(parts.hour)*60+Number(parts.minute);
  const flooredMinute=minuteOfDay-(minuteOfDay%tickMinutes);
  const wallTick=Date.UTC(Number(parts.year),Number(parts.month)-1,Number(parts.day),
    Math.floor(flooredMinute/60),flooredMinute%60);
  let firstTick=wallTick-localOffset(from);
  while(firstTick>from)firstTick-=tickStep;
  let dayCursor=Date.UTC(Number(parts.year),Number(parts.month)-1,Number(parts.day));
  const dayCount=Math.min(400,Math.ceil((to-from)/86400000)+3);
  for(let day=0;day<dayCount;day++,dayCursor+=86400000){
    const noonGuess=dayCursor+12*3600000;
    const dayStart=dayCursor-localOffset(noonGuess),y=position(dayStart)*height/100;
    if(y < -30 || y > height+30)continue;
    const marker=node("div","timeline-day-separator");marker.style.top=y+"px";
    marker.append(node("span","timeline-day-label",dateText(dayStart,zone,
      {month:"long",day:"numeric",weekday:"short"}).replace(/日(?=周)/,"日 ")));
    labels.append(marker);
  }
  let lastTickLabelBottom=Number.NEGATIVE_INFINITY;
  const axisViewport=axis.getBoundingClientRect();
  for(let ms=firstTick,count=0;ms<=to&&count<400;ms+=tickStep,count++){
    const y=position(ms)*height/100;
    if(y < -24 || y > height+24)continue;
    const grid=node("div","timeGrid"),tick=node("div","timeTick",relativeTime(ms,zone));
    grid.style.top=y+"px";tick.style.top=y+"px";labels.append(grid,tick);
    const tickRect=tick.getBoundingClientRect();
    if(tickRect.top<axisViewport.top||tickRect.bottom>axisViewport.bottom||
        tickRect.top<lastTickLabelBottom+4){
      tick.remove();
    }else{
      lastTickLabelBottom=tickRect.bottom;
    }
  }
  const timedItems=items.filter(item=>kindLabel[String(item.kind).toUpperCase()]);
  const laneWidth=84,laneGap=4;
  // Geometry and presentation order belong to this Skin. The Host supplies only
  // true event bounds and stable identities; minimum hit targets never affect lanes.
  const laneByItem=timelineLaneAssignments(timedItems,from,to);
  for(const [container,lanes,sideName] of [
    [left,leftLanes,"LEFT"],[right,rightLanes,"RIGHT"]
  ]){
    const sideItems=timedItems.filter(item=>
      (String(item.kind).toUpperCase()==="ACTIVITY"?"RIGHT":"LEFT")===sideName);
    const laneCount=Math.max(1,...sideItems.map(item=>(laneByItem.get(item)??0)+1));
    const width=Math.max(container.clientWidth,laneCount*(laneWidth+laneGap)-laneGap);
    lanes.style.width=width+"px";lanes.dataset.laneCount=String(laneCount);
  }
  for(const item of timedItems){
    const kind=String(item.kind||"ACTIVITY").toUpperCase();
    if(!kindLabel[kind])continue;
    // A date-only Deadline was already moved to its date strip; all other side/lane
    // placement is derived here from Skin presentation rules and true event bounds.
    const sideName=kind==="ACTIVITY"?"RIGHT":"LEFT";
    const lanes=sideName==="LEFT"?leftLanes:rightLanes;
    const startMillis=Number(item.startMillis),lane=laneByItem.get(item)??0;
    if(!Number.isFinite(startMillis))continue;
    const isActivity=kind==="ACTIVITY";
    let actualStart,actualEnd,anchorY;
    if(isActivity){
      const endMillis=Number(item.endMillis);
      if(!Number.isFinite(endMillis)||endMillis<=startMillis||startMillis>=to||endMillis<=from)continue;
      actualStart=Math.max(from,startMillis);actualEnd=Math.min(to,endMillis);
    }else{
      if(startMillis<from||startMillis>=to)continue;
      actualStart=startMillis;actualEnd=startMillis;anchorY=(startMillis-from)/span*height;
    }
    const rawEnd=isActivity?Number(item.endMillis):startMillis;
    const timeText=relativeTime(startMillis,zone);
    const dateOnlyDeadline=kind==="DEADLINE"&&isDateOnlyDeadline(item);
    const ariaLabel=isActivity
      ?`${kindLabel[kind]}：${item.title}，${timeText} 至 ${relativeTime(rawEnd,zone)}`
      :dateOnlyDeadline
        ?`${kindLabel[kind]}：${item.title}，${item.deadlineDate}，截止，无具体时刻`
        :`${kindLabel[kind]}：${timeText}，${item.title}`;
    const button=makeEventButton(item,kind,ariaLabel);
    button.dataset.temporalGeometry=isActivity?"interval":"point";
    button.dataset.startMillis=String(startMillis);button.dataset.endMillis=String(rawEnd);
    button.dataset.visibleStartMillis=String(from);button.dataset.visibleEndMillis=String(to);
    button.dataset.lane=String(lane);button.dataset.side=sideName;
    button.style.width=laneWidth+"px";
    if(sideName==="LEFT")button.style.right=(lane*(laneWidth+laneGap))+"px";
    else button.style.left=(lane*(laneWidth+laneGap))+"px";
    let subpixelVisual=null;
    if(isActivity){
      const visualTop=(actualStart-from)/span*height;
      const visualBottom=(actualEnd-from)/span*height;
      const visualHeight=Math.max(0,visualBottom-visualTop);
      const hitHeight=Math.min(height,Math.max(44,visualHeight));
      const hitTop=Math.max(0,Math.min(height-hitHeight,
        (visualTop+visualBottom)/2-hitHeight/2));
      button.style.top=hitTop+"px";button.style.height=hitHeight+"px";
      const visual=node("div","tEventVisual ACTIVITY");
      visual.setAttribute("aria-hidden","true");
      if(visualHeight<2){visual.classList.add("subpixel-interval");subpixelVisual=visual;}
      visual.style.top=(visualTop-hitTop)+"px";visual.style.height=visualHeight+"px";
      const title=makeTimelineTitle(item,kind,String(item.title||""),laneWidth-6);
      title.classList.add("tActivityTitle");
      title.dataset.titleFrameTop=String(visualTop-hitTop);
      title.style.top=(visualTop-hitTop)+"px";
      button.append(visual,title);
    }else{
      const hitHeight=Math.min(height,44);
      const hitTop=Math.max(0,Math.min(height-hitHeight,anchorY-hitHeight/2));
      button.style.top=hitTop+"px";button.style.height=hitHeight+"px";
      const line=node("span","tPointLine "+kind);
      line.setAttribute("aria-hidden","true");line.dataset.anchorMillis=String(startMillis);
      line.style.top=(anchorY-hitTop-0.75)+"px";
      const title=makeTimelineTitle(item,kind,`${timeText} · ${item.title}`,laneWidth-6);
      title.classList.add("tPointLabel",kind);
      title.style.top=(anchorY-hitTop+(kind==="ALARM"?2:-26))+"px";
      button.append(line,title);
    }
    attachTimelineDrag(button,item,axis);
    lanes.append(button);
    if(subpixelVisual){
      const bounds=subpixelVisual.getBoundingClientRect(),dpr=window.devicePixelRatio||1;
      const paintStart=Math.ceil(bounds.top*dpr),paintEnd=Math.ceil(bounds.bottom*dpr);
      const paintWidth=Math.round(bounds.width*dpr),paintBorderWidth=Math.round(2*dpr);
      subpixelVisual.style.setProperty("--native-paint-top",
        (paintStart/dpr-bounds.top)+"px");
      subpixelVisual.style.setProperty("--native-paint-height",
        (Math.max(1,paintEnd-paintStart)/dpr)+"px");
      subpixelVisual.style.setProperty("--native-paint-width",
        (paintWidth/dpr)+"px");
      subpixelVisual.style.setProperty("--native-paint-content-width",
        (Math.max(0,paintWidth-paintBorderWidth)/dpr)+"px");
      subpixelVisual.style.setProperty("--native-paint-border-width",
        (paintBorderWidth/dpr)+"px");
      subpixelVisual.style.setProperty("--native-paint-left-shift",
        (Math.floor(bounds.left*dpr)/dpr-bounds.left)+"px");
    }
  }
  if(t.nowMillis>=from&&t.nowMillis<to){
    const marker=node("div","now-line");marker.style.top=position(t.nowMillis)+"%";
    marker.append(node("span","now-label","现在 "+relativeTime(t.nowMillis,zone)));
    now.append(marker);
  }
  if(t.truncated){
    const warning=node("p","quiet","可见事件过多，未全部显示");
    warning.style.cssText="position:absolute;bottom:4px;right:4px";now.append(warning);
  }
  const newLeftOverflow=left.scrollWidth>left.clientWidth+1;
  const newRightOverflow=right.scrollWidth>right.clientWidth+1;
  left.scrollLeft=(!timelineScrollInitialized||(!previousLeftOverflow&&newLeftOverflow))
    ?0:previousLeftScroll;
  right.scrollLeft=(!timelineScrollInitialized||(!previousRightOverflow&&newRightOverflow))
    ?0:previousRightScroll;
  timelineScrollInitialized=true;
  positionNowBadge(axis);
  recomputeTimelineTitleFolding();
}
function positionNowBadge(axis){
  const label=axis.querySelector(".now-label");
  if(!label)return;
  const bounds=axis.getBoundingClientRect(),obstacles=[...document.querySelectorAll(
    ".timeTick,.tEventVisual,.timelineTitle,.timeline-empty,.timeline-day-label")];
  const offsets=[0];
  for(let distance=8;distance<=Math.min(180,bounds.height/2);distance+=8)offsets.push(-distance,distance);
  for(const offset of offsets){
    label.style.top=offset+"px";
    const rect=label.getBoundingClientRect();
    if(rect.top<bounds.top||rect.bottom>bounds.bottom)continue;
    const collides=obstacles.some(element=>{
      const other=element.getBoundingClientRect();
      return other.width>0&&other.height>0&&rect.left<other.right+2&&other.left-2<rect.right&&
        rect.top<other.bottom+2&&other.top-2<rect.bottom;
    });
    if(!collides)return;
  }
  label.style.top="0px";
}
function renderMonth(){
  const month=state.month||{},t=state.timeline||{};
  const zone=month.zoneId||t.zoneId||"Asia/Shanghai";
  const firstIso=month.gridFrom||new Date().toISOString().slice(0,10);
  const grid=$("monthGrid");grid.replaceChildren();
  const events=month.items||[];
  const counts=new Map();
  for(const event of events){
    const dates=Array.isArray(event.visibleDateKeys)&&event.visibleDateKeys.length
      ?event.visibleDateKeys:[event.date];
    for(const date of dates)counts.set(date,(counts.get(date)||0)+1);
  }
  for(const wd of ["一","二","三","四","五","六","日"])grid.append(node("div","month-weekday",wd));
  const origin=Date.parse(firstIso+"T00:00:00Z");
  const [shownYear,shownMonth]=String(month.month||firstIso.slice(0,7)).split("-").map(Number);
  const daysInMonth=new Date(Date.UTC(shownYear,shownMonth,0)).getUTCDate();
  const offset=(new Date(Date.UTC(shownYear,shownMonth-1,1)).getUTCDay()+6)%7;
  const cellCount=Math.ceil((offset+daysInMonth)/7)*7;
  const nowParts=Object.fromEntries(new Intl.DateTimeFormat("en-CA",{
    timeZone:zone,year:"numeric",month:"2-digit",day:"2-digit"
  }).formatToParts(new Date(t.nowMillis||Date.now())).map(part=>[part.type,part.value]));
  const todayIso=`${nowParts.year}-${nowParts.month}-${nowParts.day}`;
  const monthToday=$("monthToday");
  if(monthToday)monthToday.dataset.date=todayIso;
  const [yearValue,monthValue]=String(month.month||firstIso.slice(0,7)).split("-").map(Number);
  const title=$("monthTitle");
  title.dataset.month=String(month.month||firstIso.slice(0,7));
  title.setAttribute("aria-label",`月份 ${yearValue} 年 ${monthValue} 月`);
  $("monthYearTitle").textContent=`${yearValue} 年`;
  $("monthYearTitle").setAttribute("aria-label",`选择年份，当前 ${yearValue} 年`);
  $("monthNumberTitle").textContent=`${monthValue} 月`;
  $("monthNumberTitle").setAttribute("aria-label",`选择月份，当前 ${monthValue} 月`);
  $("monthPickerHeading").textContent=`选择月份 · ${yearValue} 年`;
  document.querySelectorAll("#monthPickerForm [data-month-number]").forEach(button=>{
    const selected=Number(button.dataset.monthNumber)===monthValue;
    button.setAttribute("aria-pressed",String(selected));
  });
  for(let i=0;i<cellCount;i++){
    const date=new Date(origin+i*86400000),iso=date.toISOString().slice(0,10);
    if(iso.slice(0,7)!==(month.month||firstIso.slice(0,7))){
      grid.append(node("div","month-cell-empty"));continue;
    }
    const button=node("button","month-cell");
    button.type="button";button.dataset.action="selectMonthDate";button.dataset.date=iso;
    if(iso===month.selectedDate)button.classList.add("selected");
    if(iso===todayIso)button.classList.add("today");
    button.setAttribute("aria-pressed",String(iso===month.selectedDate));
    const count=counts.get(iso)||0;
    button.setAttribute("aria-label",`${iso}，${count} 项${iso===todayIso?"，今天":""}`);
    button.append(node("span","month-day-number",date.getUTCDate()));
    button.append(node("span","month-count",count?`${count} 项`:""));
    grid.append(button);
  }
  const agenda=$("monthAgenda");agenda.replaceChildren();
  const selectedDate=String(month.selectedDate||firstIso);
  const picked=events.filter(item=>Array.isArray(item.visibleDateKeys)&&item.visibleDateKeys.length
    ?item.visibleDateKeys.includes(selectedDate):item.date===selectedDate);
  const selectedParts=selectedDate.split("-").map(Number);
  const weekdayLabel=new Intl.DateTimeFormat(undefined,{weekday:"long",timeZone:"UTC"})
    .format(new Date(Date.UTC(selectedParts[0],selectedParts[1]-1,selectedParts[2])));
  const agendaHeading=node("h2","",`${selectedParts[1]}月${selectedParts[2]}日 ${weekdayLabel}`);
  agendaHeading.dataset.date=selectedDate;
  agenda.append(agendaHeading);
  if(!picked.length)agenda.append(node("p","quiet","这一天还很从容。"));
  for(const item of picked){
    const kindClass=String(item.kind||"").toLowerCase();
    const button=node("button",`agenda-event ${kindClass}`);
    button.dataset.action="openEvent";
    button.dataset.eventSeq=item.eventSeq;button.dataset.revision=item.revision;
    if(item.originalOccurrenceKey)button.dataset.originalOccurrenceKey=item.originalOccurrenceKey;
    button.append(node("span","agenda-event-title",`${item.kindLabel||"事件"} · ${item.title||""}`));
    if(item.summary)button.append(node("span","agenda-event-summary",item.summary));
    if(item.sequenceLabel)button.append(node("span","agenda-event-sequence",
      item.sequenceLabel+(state.aiLocked?" · AI 处理中，仅查看":"")));
    button.setAttribute("aria-label",[
      item.kindLabel,item.title,item.summary,item.sequenceLabel
    ].filter(Boolean).join("，"));
    agenda.append(button);
  }
  if(month.truncated)agenda.append(node("p","quiet","本月事件超过投影上限，部分数据未显示"));
  $("viewMonthDate").dataset.date=month.selectedDate||firstIso;
}
function renderMemo(){
  const list=$("memoList");list.replaceChildren();
  const completed=memoFilter==="done";
  const entries=(state.memoEntries||[]).filter(x=>Boolean(x.completed)===completed);
  document.querySelectorAll(".memo-mode-row [data-filter]").forEach(button=>{
    const active=button.dataset.filter===memoFilter;
    button.hidden=active;
  });
  $("memoTitle").textContent=completed?"Memo · 已完成":"Memo · 未完成";
  $("memoSubtitle").textContent=`统一待办中心 · ${entries.length} 项（重复任务展示近一年及未来七日）`;
  if(!entries.length){list.append(node("p","quiet","目前没有待处理事项"))}
  for(const item of entries){
    const row=node("div","memo-row"+(item.completed?" done":""));
    row.dataset.eventSeq=item.eventSeq;
    const button=node("button","",item.completed?"✓":"");
    button.type="button";button.setAttribute("aria-label",item.completed?"恢复未完成":"完成 Memo");
    button.dataset.action="toggleMemo";button.dataset.eventSeq=item.eventSeq;
    button.dataset.revision=item.revision;button.dataset.occurrenceKey=item.occurrenceKey;
    button.dataset.completed=String(item.completed);
    button.disabled=!!state.aiLocked;
    const title=node("button","memo-title",item.title);
    title.type="button";
    title.setAttribute("aria-label","编辑事项："+item.title);
    title.dataset.action="openEvent";title.dataset.eventSeq=item.eventSeq;
    title.dataset.revision=item.revision;
    if(typeof item.originalOccurrenceKey==="string")
      title.dataset.originalOccurrenceKey=item.originalOccurrenceKey;
    const content=node("div","memo-content");
    const kindLabel={ALARM:"闹钟",ACTIVITY:"活动",DEADLINE:"截止",MEMO:"备忘"}[item.kind]||String(item.kind||"事件");
    const dateLabel=item.startMillis==null?"无日期":memoLocalDate(item.startMillis,item.zoneId);
    const meta=node("span","memo-meta",`${kindLabel} · ${dateLabel}${item.repeating?" · 重复":""}`);
    const sequence=node("span","memo-sequence",`#${String(item.eventSeq).padStart(4,"0")}`);
    row.addEventListener("click",event=>{
      if(event.target.closest("[data-action]"))return;
      title.click();
    });
    content.append(title,meta);row.append(button,content,sequence);list.append(row);
  }
  const remaining=Math.max(0,Number(state.memoRemainingCount)||0);
  $("memoLimit").textContent=`加载更多（还有 ${remaining} 项）`;
  $("memoLimit").hidden=!state.memoTruncated;
}
function renderChat(){
  installChatAttachmentControls();
  const list=$("chatMessages");list.replaceChildren();
  closeChatMessageMenus();
  const messages=state.chatMessages||[];
  $("chatEmptyHint").hidden=messages.length>0;
  for(const item of messages){
    const bubble=node("article","chat-bubble "+(item.role==="user"?"user":"assistant"));
    const header=node("div","chat-message-head");
    header.append(node("span","chat-message-role",item.role==="user"?"我":"PowerCal AI"));
    const actionContainer=node("div","chat-message-action-container");
    const trigger=node("button","chat-message-menu-trigger","⋮");trigger.type="button";
    trigger.setAttribute("aria-label",item.role==="user"?"我的消息操作":"PowerCal AI 消息操作");
    trigger.setAttribute("aria-haspopup","menu");trigger.setAttribute("aria-expanded","false");
    const actions=node("div","chat-message-actions");actions.hidden=true;actions.setAttribute("role","menu");
    actions.setAttribute("aria-label",item.role==="user"?"我的消息操作":"PowerCal AI 消息操作");
    const messageId=typeof item.messageId==="string"?item.messageId:"";
    const historyRevision=Number(state.chatHistoryRevision)||0;
    const closeActions=()=>{actions.hidden=true;trigger.setAttribute("aria-expanded","false")};
    const openActions=()=>{
      closeChatMessageMenus();actions.hidden=false;trigger.setAttribute("aria-expanded","true");
    };
    trigger.addEventListener("click",event=>{
      event.stopPropagation();
      const shouldOpen=actions.hidden;closeChatMessageMenus();
      if(shouldOpen)openActions();
    });
    bubble.addEventListener("pointerdown",event=>{
      if(event.pointerType==="mouse")return;
      if(activeChatMessagePress){
        clearTimeout(activeChatMessagePress.timer);activeChatMessagePress=null;
      }
      const press={pointerId:event.pointerId,x:event.clientX,y:event.clientY,timer:0};
      press.timer=window.setTimeout(()=>{
        if(activeChatMessagePress!==press||!bubble.isConnected)return;
        activeChatMessagePress=null;openActions();
      },520);
      activeChatMessagePress=press;
    });
    bubble.addEventListener("contextmenu",event=>{event.preventDefault();openActions()});
    const addMenuAction=(label,accessible,disabled,onClick)=>{
      const button=node("button","",label);button.type="button";button.setAttribute("role","menuitem");
      button.setAttribute("aria-label",accessible);button.disabled=disabled;
      button.addEventListener("click",event=>{
        event.stopPropagation();closeActions();onClick();
      });
      actions.append(button);
    };
    addMenuAction("从此消息创建事件草稿","从此消息创建事件草稿",
      !messageId||!!state.aiLocked||!!state.chatBusy,
      ()=>send("createDraftFromChatMessage",{messageId,historyRevision,source:"MESSAGE_CONTENT"}));
    if(item.role==="user")addMenuAction("编辑并重新发送","编辑这条消息并移除后续对话",
      state.chatHistoryActionsAvailable!==true||!messageId,
      ()=>rewindChat(messageId,true));
    addMenuAction(item.role==="user"?"撤回此消息及后续对话":"回退到此消息",
      item.role==="user"?"撤回这条消息及后续对话":"回退到此消息",
      state.chatHistoryActionsAvailable!==true||!messageId,
      ()=>rewindChat(messageId,false));
    addMenuAction("复制文字","复制消息文字",!messageId,
      ()=>send("copyChatMessage",{messageId,historyRevision}));
    actionContainer.append(trigger,actions);header.append(actionContainer);bubble.append(header);
    bubble.append(node("p","chat-message-content",item.content));
    if(item.role!=="user"&&item.proposalAvailable===true){
      const proposal=node("button","chat-message-proposal","从此消息创建事件草稿");
      proposal.type="button";proposal.disabled=!messageId||!!state.aiLocked||!!state.chatBusy;
      proposal.addEventListener("click",()=>send("createDraftFromChatMessage",{
        messageId,historyRevision,source:"PROPOSAL_SOURCE"}));
      bubble.append(proposal);
    }
    list.append(bubble);
  }
  renderAiBatchControls();
  renderAiProposalReview(list);
  const batchPhase=String(state.aiBatch?.phase||"IDLE");
  const disabled=!!(state.aiLocked||state.chatBusy||batchPhase==="AWAITING_HOST_REVIEW");
  $("chatInput").disabled=disabled;
  const reviewPending=!!state.aiProposalReview?.proposals?.length;
  const attachmentUnavailable=state.chatAttachmentAvailable===false;
  const cacheBusy=["SELECTING","AWAITING_HOST_REVIEW","RUNNING","CANCEL_REQUESTED"]
    .includes(batchPhase);
  $("chatClearCache").disabled=disabled||cacheBusy||Boolean(state.chatAttachmentBusy)||
    attachmentRequestPending||reviewPending;
  $("chatAttachmentTrigger").disabled=disabled||Boolean(state.chatAttachmentBusy)||
    attachmentRequestPending||reviewPending||attachmentUnavailable;
  for(const button of document.querySelectorAll(".chat-attachment-controls button"))
    button.disabled=disabled||Boolean(state.chatAttachmentBusy)||attachmentRequestPending||
      reviewPending||attachmentUnavailable;
  if(disabled||state.chatAttachmentBusy||attachmentRequestPending||reviewPending||attachmentUnavailable){
    $("chatAttachmentControls").hidden=true;
    $("chatAttachmentTrigger").setAttribute("aria-expanded","false");
  }
  if(disabled)list.append(node("p","quiet","AI 正在处理，暂不可发送"));
  else if(state.chatAttachmentBusy||attachmentRequestPending)
    list.append(node("p","quiet","请先完成系统附件选择或确认"));
  else if(reviewPending)
    list.append(node("p","quiet","请先处理当前提案审核，再添加新附件"));
  else if(attachmentUnavailable)
    list.append(node("p","quiet","请先完成当前 AI 操作，再选择附件"));
  const clear=$("chatClear");
  clear.disabled=state.chatHistoryActionsAvailable!==true||messages.length===0;
  const prefillRevision=String(state.chatPrefillRevision??"");
  if(prefillRevision!==appliedChatPrefillRevision){
    $("chatInput").value=String(state.chatPrefillText||"");
    appliedChatPrefillRevision=prefillRevision;
  }
  updateChatSendState();
  list.scrollTop=list.scrollHeight;
}
function renderAiBatchControls(){
  const batch=state.aiBatch||{},phase=String(batch.phase||"IDLE");
  const proposalPending=!!state.aiProposalReview?.proposals?.length;
  const reviewActive=["SELECTING","AWAITING_HOST_REVIEW"].includes(phase);
  const disabled=!!(state.aiLocked||state.chatBusy||proposalPending||reviewActive);
  for(const button of document.querySelectorAll("[data-ai-batch]"))
    button.disabled=disabled||Number(state.pendingCount)<=0;
  const cancel=$("aiBatchCancel"),release=$("aiBatchRelease");
  cancel.hidden=!(phase==="SELECTING"||phase==="RUNNING"||phase==="CANCEL_REQUESTED");
  cancel.disabled=phase==="SELECTING"?!!state.chatBusy:!batch.canCancel;
  cancel.textContent=phase==="SELECTING"?"取消范围选择":
    phase==="CANCEL_REQUESTED"?"正在停止…":"停止批次";
  release.hidden=!batch.canRelease;
  release.disabled=!batch.canRelease;
  const status=$("aiBatchStatus"),report=batch.report||{};
  let text="";
  if(phase==="SELECTING")text=batch.mode==="DISCARD"?
    "请描述要丢弃的待处理草稿范围，或输入“全部”。尚未授权，也没有写入。":
    "请描述要发布的待处理草稿范围，或输入“全部”。尚未授权，也没有写入。";
  else if(phase==="AWAITING_HOST_REVIEW")text=`已选定 ${batch.selectedCount||0} 张草稿。PowerCal Host 正在显示完整清单；只有在 Host 审核窗口明确授权后才会执行。`;
  else if(phase==="RUNNING"){
    const progress=batch.progress;
    text=`AI 批次正在运行，共 ${batch.scopeCount||0} 张草稿。已完成结果会保留。`+
      (progress?` 进度 ${progress.done}/${progress.total}。`:"");
  }else if(phase==="CANCEL_REQUESTED")text="已请求停止 AI 批次；当前操作结束后停止，已完成结果保留。若超过 10 分钟仍无响应，可在设置中恢复。";
  else if(phase==="INTERRUPTED")text="上次 AI 批次已中断并解锁；未完成草稿仍待处理，需重新审核后再运行。";
  else if(phase==="COMPLETED"||phase==="CANCELLED"||phase==="FAILED"){
    const prefix=phase==="COMPLETED"?"AI 批次完成":phase==="CANCELLED"?"AI 批次已停止":"AI 批次结束，存在失败或恢复结果";
    text=report.outcomeKnown===false?
      `${prefix}：强制解除时当前执行结果数量无法确定，请检查正式事件和待处理草稿。`:
      `${prefix}：发布 ${report.published||0}，丢弃 ${report.discarded||0}，保留 ${report.kept||0}。`;
    text+=(report.released?" 已强制解除并作废旧授权。":"")+
      (report.failureSummary?` ${report.failureSummary}`:"");
  }
  status.hidden=!text;status.textContent=text;
}
function installChatAttachmentControls(){
  const composer=$("chatForm").querySelector(".composer");
  if(!composer||$("chatAttachmentControls"))return;
  const style=node("style");
  style.textContent=`.composer{position:relative}
    .composer .chat-attachment-trigger{display:grid;place-items:center;flex:0 0 44px;width:44px;height:44px;
      min-height:44px;padding:0;border:0;border-radius:50%;background:transparent;color:var(--label);font-size:28px}
    .chat-attachment-controls{position:absolute;z-index:20;left:0;bottom:calc(100% + 8px);display:flex;
      flex-direction:column;gap:2px;min-width:min(250px,76vw);padding:6px;border:1px solid var(--sep);
      border-radius:14px;background:var(--bg-elevated);box-shadow:var(--shadow-lift)}
    .chat-attachment-controls[hidden]{display:none!important}
    .chat-attachment-controls button{font:inherit;font-size:14px;min-height:44px;padding:8px 12px;
      border:0;border-radius:9px;background:transparent;color:var(--label);text-align:left}
    .chat-attachment-controls button:disabled,.chat-attachment-trigger:disabled{opacity:.45}`;
  document.head.append(style);
  const trigger=node("button","chat-attachment-trigger","＋");trigger.id="chatAttachmentTrigger";
  trigger.type="button";trigger.setAttribute("aria-label","添加图片或文件");
  trigger.setAttribute("aria-haspopup","menu");trigger.setAttribute("aria-expanded","false");
  const options=()=>Array.from(controls.querySelectorAll("button:not(:disabled)"));
  const close=(returnFocus=false)=>{
    controls.hidden=true;trigger.setAttribute("aria-expanded","false");
    if(returnFocus)trigger.focus();
  };
  trigger.addEventListener("click",()=>{
    if(controls.hidden){
      controls.hidden=false;trigger.setAttribute("aria-expanded","true");
      requestAnimationFrame(()=>options()[0]?.focus());
    }else close(true);
  });
  const controls=node("div","chat-attachment-controls");controls.id="chatAttachmentControls";
  controls.hidden=true;controls.setAttribute("role","menu");
  controls.setAttribute("aria-label","选择 AI 附件类型");
  for(const [name,label,accessible] of [
    ["pickAiImage","图片","选择图片附件"],["pickAiFile","文件","选择日历或文档附件"]
  ]){
    const button=node("button","",label);button.type="button";
    button.setAttribute("aria-label",accessible);
    button.setAttribute("role","menuitem");
    button.addEventListener("click",()=>{
      if(!port||button.disabled)return;
      close();
      attachmentRequestPending=true;renderChat();send(name);
    });
    controls.append(button);
  }
  controls.addEventListener("keydown",event=>{
    const enabled=options(),index=enabled.indexOf(document.activeElement);
    let next=-1;
    if(event.key==="ArrowDown")next=(index+1)%enabled.length;
    else if(event.key==="ArrowUp")next=(index-1+enabled.length)%enabled.length;
    else if(event.key==="Home")next=0;
    else if(event.key==="End")next=enabled.length-1;
    else if(event.key==="Escape"){event.preventDefault();close(true);return}
    if(next>=0&&enabled.length){event.preventDefault();enabled[next].focus()}
  });
  document.addEventListener("pointerdown",event=>{
    if(!controls.hidden&&!controls.contains(event.target)&&!trigger.contains(event.target))close();
  },true);
  document.addEventListener("keydown",event=>{
    if(!controls.hidden&&event.key==="Escape"){event.preventDefault();close(true)}
  });
  composer.insertBefore(trigger,$("chatInput"));
  composer.insertBefore(controls,$("chatSend"));
  const overflow=$("aiOverflow");
  overflow?.addEventListener("click",event=>{
    if(event.target.closest("button"))overflow.open=false;
  });
  document.addEventListener("pointerdown",event=>{
    if(overflow?.open&&!overflow.contains(event.target))overflow.open=false;
  },true);
  document.addEventListener("keydown",event=>{
    if(!overflow?.open||event.key!=="Escape")return;
    event.preventDefault();overflow.open=false;overflow.querySelector("summary")?.focus();
  });
}
function updateChatSendState(){
  const batchPhase=String(state.aiBatch?.phase||"IDLE");
  $("chatSend").disabled=!!(state.aiLocked||state.chatBusy||batchPhase==="AWAITING_HOST_REVIEW")||
    !$("chatInput").value.trim();
}
$("chatInput").addEventListener("input",updateChatSendState);
function installAiProposalStyles(){
  if($("aiProposalStyles"))return;
  const style=node("style");style.id="aiProposalStyles";
  style.textContent=`
    .ai-proposal-review{display:flex;flex-direction:column;gap:12px;padding:12px;border:1px solid var(--sep);
      border-radius:18px;background:var(--bg-grouped);color:var(--label)}
    .ai-proposal-review h2,.ai-proposal-review h3{margin:0;font-size:15px}
    .ai-proposal-review p{margin:3px 0;color:var(--label-2);font-size:13px;white-space:pre-wrap;overflow-wrap:anywhere}
    .ai-proposal-group{display:flex;flex-direction:column;gap:8px}
    .ai-proposal-item{display:flex;gap:9px;align-items:flex-start;padding:10px;border-radius:13px;
      background:var(--bg-elevated);border:1px solid var(--sep)}
    .ai-proposal-item input{margin-top:3px;accent-color:var(--accent-fill)}
    .ai-proposal-item-body{min-width:0;flex:1;overflow-wrap:anywhere}
    .ai-proposal-title{font-weight:650}
    .ai-proposal-actions{display:grid;grid-template-columns:1fr 1fr;gap:7px}
    .ai-proposal-actions button{min-height:40px;border:1px solid var(--sep);border-radius:12px;padding:7px;
      background:var(--bg-elevated);color:var(--label);font:inherit}
    .ai-proposal-actions button.primary{background:var(--accent-fill);color:var(--on-accent)}
    .ai-proposal-actions button:disabled{opacity:.48}
    .ai-proposal-blocked{color:var(--danger)!important}
  `;
  document.head.append(style);
}
function renderAiProposalReview(list){
  const review=state.aiProposalReview;
  if(!review||!Array.isArray(review.proposals)||review.proposals.length===0){
    aiReviewSelectionId="";aiReviewSelected.clear();return;
  }
  installAiProposalStyles();
  if(aiReviewSelectionId!==review.reviewId){
    aiReviewSelectionId=review.reviewId;
    aiReviewSelected=new Set(review.proposals.filter(item=>!item.blocked).map(item=>item.index));
  }
  const root=node("section","ai-proposal-review");root.setAttribute("role","region");
  root.setAttribute("aria-label","AI 日程提案审核");
  root.append(node("h2","","审核 AI 候选 · 尚未写入正式日历"));
  const batchPhase=String(state.aiBatch?.phase||"IDLE");
  const busy=!!(state.aiLocked||state.chatBusy||
    ["SELECTING","AWAITING_HOST_REVIEW","RUNNING","CANCEL_REQUESTED"].includes(batchPhase));
  for(const [isCreate,label] of [[true,"新增事项"],[false,"修改或删除"]]){
    const entries=review.proposals.filter(item=>(item.operation==="CREATE")===isCreate);
    if(!entries.length)continue;
    const group=node("section","ai-proposal-group");group.setAttribute("role","group");
    group.append(node("h3","",label));
    for(const item of entries){
      const row=node("label","ai-proposal-item");
      const checkbox=document.createElement("input");checkbox.type="checkbox";
      checkbox.checked=aiReviewSelected.has(item.index);checkbox.disabled=busy||!!item.blocked;
      checkbox.setAttribute("aria-label",`选择第 ${item.index+1} 项 ${item.title||"未命名"}`);
      checkbox.addEventListener("change",()=>{
        if(checkbox.checked)aiReviewSelected.add(item.index);else aiReviewSelected.delete(item.index);
        renderChat();
      });
      row.append(checkbox);
      const body=node("div","ai-proposal-item-body");
      const target=item.eventSeq==null?"新建":`#${item.eventSeq} · 版本 ${item.targetRevision??"未知"}`;
      body.append(node("div","ai-proposal-title",`${item.index+1}. ${item.title||"未命名"} · ${item.kind} · ${item.operation} · ${target}`));
      const recurrence=item.recurrence||{};
      const recurrenceSummary=[recurrence.frequency||"NONE",
        `间隔 ${recurrence.interval??1}`,
        ...(recurrence.byDay||[]),
        recurrence.count?`共 ${recurrence.count} 次`:"",
        recurrence.untilMillis?`截止 ${proposalDateText(recurrence.untilMillis,item.zoneId)}`:"",
        recurrence.activeFromDate?`生效起 ${recurrence.activeFromDate}`:"",
        recurrence.activeUntilDate?`生效止 ${recurrence.activeUntilDate}`:""]
        .filter(Boolean).join(" · ");
      const details=[
        `开始：${item.startText||"未指定"}`,`结束/截止：${item.endText||"未指定"}`,
        `时区：${item.zoneId||"未指定"}`,`地点：${item.location||"无"}`,
        `提醒：${item.reminderMinutes<0?"关闭":item.reminderMinutes+" 分钟"} · ${item.reminderMethod} · ${item.reminderTiming}`,
        `重复：${recurrenceSummary}`,
        `备注：${item.notes||"无"}`
      ];
      for(const text of details)body.append(node("p","",text));
      if(item.titleTruncated||item.locationTruncated)
        body.append(node("p","", "标题或地点较长；完整内容会在 Host 确认界面显示。"));
      for(const exception of recurrence.exceptions||[])
        body.append(node("p","",`跳过实例：${exception}`));
      for(const override of recurrence.overrides||[])
        body.append(node("p","",`实例覆盖：${override.key} · ${proposalDateText(override.startMillis,item.zoneId)} – `+
          `${override.endMillis==null?"无结束时刻":proposalDateText(override.endMillis,item.zoneId)}${override.title?` · ${override.title}`:""}`));
      for(const key of item.completedKeys||[])body.append(node("p","",`已完成槽位：${key}`));
      if(item.recurrenceEditScope)body.append(node("p","",`编辑范围：${item.recurrenceEditScope}`));
      if(item.editOccurrenceKey)body.append(node("p","",`目标实例：${item.editOccurrenceKey}`));
      if(item.source)body.append(node("p","",`来源：${item.source}`));
      if(item.rationale)body.append(node("p","",`AI 说明：${item.rationale}`));
      if(item.notesTruncated||item.rationaleTruncated||item.completedKeysTruncated||
        recurrence.exceptionsTruncated||recurrence.overridesTruncated)
        body.append(node("p","", "部分长文本或实例列表在卡片中截断；Host 确认界面会展示完整操作快照。"));
      if(item.operation==="DELETE")
        body.append(node("p","ai-proposal-blocked", "正式删除不可撤销；确认时 Host 会显示当前完整事件快照。"));
      if(item.blocked)body.append(node("p","ai-proposal-blocked",`不可操作：${item.blocked}`));
      else if(!item.publishable)body.append(node("p","", "信息尚不完整；可以先写入草稿，补全后再发布。"));
      row.append(body);group.append(row);
    }
    const selected=entries.filter(item=>aiReviewSelected.has(item.index)&&!item.blocked);
    const indices=selected.map(item=>item.index).sort((a,b)=>a-b);
    const actions=node("div","ai-proposal-actions");
    const button=(text,decision,enabled,primary=false)=>{
      const control=node("button",primary?"primary":"",text);control.type="button";
      control.disabled=busy||!enabled;
      control.addEventListener("click",()=>send("resolveAiProposal",{
        reviewId:review.reviewId,decision,indices:decision==="supplement"?[]:indices
      }));
      actions.append(control);
    };
    button("批量同意", "publish", selected.length>0&&selected.every(item=>item.publishable), true);
    button("写入草稿", "stage", selected.length>0);
    button("批量拒绝", "reject", selected.length>0);
    button("补充细节", "supplement", true);
    group.append(actions);root.append(group);
  }
  for(const warning of (review.warnings||[]))root.append(node("p","",`提示：${warning}`));
  list.append(root);
}
function renderProfileEditor(){
  const dialog=$("profileEditorDialog"),profile=state.profileEditor;
  const input=$("profileDisplayName"),status=$("profileEditorStatus");
  if(!profile||typeof profile.displayName!=="string"){
    if(dialog.open)dialog.close();
    profileEditorDirty=false;profileEditorSaveRevision=-1;
    return;
  }
  const revision=Number(profile.saveRevision)||0;
  if(!dialog.open){
    input.value=profile.displayName;
    profileEditorDirty=false;profileEditorSaveRevision=revision;
    status.textContent=profile.saved?"已保存":"";
    dialog.showModal();
    $("profileEditorHeading").focus({preventScroll:true});
  }else if(revision!==profileEditorSaveRevision){
    input.value=profile.displayName;
    profileEditorDirty=false;profileEditorSaveRevision=revision;
    status.textContent=profile.saved?"已保存":"";
  }else if(!profileEditorDirty){
    status.textContent=profile.saved?"已保存":"";
  }
}
function renderOptions(){
  for(const id of ["optionGroups","settingsOptionGroups"]){
    const root=$(id);
    if(!root)continue;
    root.replaceChildren();
    for(const group of state.optionGroups||[]){
      root.append(node("h2","",group.label));
      for(const item of group.items||[]){
        const button=node("button","optionRow");
        button.append(node("span","optionTitle",item.label));
        if(item.detail)button.append(node("span","optionDescription",item.detail));
        button.dataset.action="invokeOption";button.dataset.optionId=item.id;
        button.disabled=item.enabled===false;
        root.append(button);
      }
    }
  }
}
function handleHostBack(){
  const dialogs=document.querySelectorAll("dialog[open]");
  const active=dialogs[dialogs.length-1];
  if(!active)return false;
  if(active.id==="profileEditorDialog"){closeProfileEditor();return true}
  active.close();
  return true;
}
window.PowerCalSkin={receive,handleHostBack,
  sendCommand:(name,requestId,extra={})=>
    postBridgeMessage({version:1,type:"command",payload:{name,requestId,...extra}})};
})();
