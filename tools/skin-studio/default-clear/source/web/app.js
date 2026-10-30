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
let timelineDrag=null,suppressTimelineClickUntil=0;
let titleFoldFrame=0,titleFoldResizeObserver=null;
let aiReviewSelectionId="",aiReviewSelected=new Set();
let attachmentRequestPending=false;
let appliedChatPrefillRevision=null;
let timelineCreateDialog=null;
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
  if(element.dataset.titleFrameHeight){
    const frameHeight=Number(element.dataset.titleFrameHeight);
    const inside=Number.isFinite(frameHeight)&&frameHeight>=measured.bottom-measured.top;
    element.dataset.titlePlacement=inside?"inside":"start-anchor";
    const frameTop=Number(element.dataset.titleFrameTop)||0;
    element.style.top=(frameTop+(inside?Math.min(3,Math.max(0,frameHeight-(measured.bottom-measured.top))):0))+"px";
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
  else if(name==="selectSkin")send(name,{id:button.dataset.skinId});
  else if(name==="exportInstalledSkin"||name==="deleteSkin")
    send(name,{id:button.dataset.skinId});
  else if(name==="panTimeline")send(name,{deltaMinutes:Number(button.dataset.deltaMinutes)});
  else if(name==="zoomTimeline")send(name,{direction:button.dataset.direction});
  else if(name==="resetTimelineZoom")send(name);
  else if(name==="jumpTimelineDay")send(name,{date:button.dataset.date});
  else if(name==="browseMonth")send(name,{deltaMonths:Number(button.dataset.deltaMonths)});
  else if(name==="selectMonthDate")send(name,{date:button.dataset.date});
  else {if(["timeline","memo","month","ai","settings","nativeSettings"].includes(name))localOptions=false;send(name)}
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
document.addEventListener("click",e=>{
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
$("monthGrid").addEventListener("click",event=>{
  if(!monthSwipeConsumed)return;
  event.preventDefault();event.stopImmediatePropagation();
  monthSwipeConsumed=false;clearTimeout(monthSwipeReset);
},true);
$("monthTitle").addEventListener("click",()=>{
  const month=state.month||{};
  const [year,number]=String(month.month||"2026-10").split("-").map(Number);
  $("monthYear").value=year;
  $("monthNumber").value=String(number);
  $("monthPickerDialog").showModal();
});
$("monthPickerCancel").addEventListener("click",()=>$("monthPickerDialog").close());
$("monthPickerForm").addEventListener("submit",event=>{
  event.preventDefault();
  const year=Number($("monthYear").value),month=Number($("monthNumber").value);
  if(!Number.isInteger(year)||year<1900||year>2100||!Number.isInteger(month)||month<1||month>12)return;
  $("monthPickerDialog").close();
  send("selectMonth",{month:`${String(year).padStart(4,"0")}-${String(month).padStart(2,"0")}`});
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
  port.onmessage=event=>receive(event.data);
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
  $("memoCounter").textContent=Number(state.memoCount)||"";
  $("app").classList.toggle("reduced",Boolean(state.reducedMotion));
  renderPage();
  if(state.hostDataReady===false)bridgeReady=false;
  else if(!bridgeReady){bridgeReady=true;flushBridgeOutbox()}
}
function renderPage(){
  const page=currentPage==="TOOLS"?"TIMELINE":currentPage;
  const createButton=document.querySelector('[data-action="createDraft"]');
  if(createButton){createButton.title="新建事件";createButton.setAttribute("aria-label","新建事件")}
  const pages={TIMELINE:"timelinePage",MONTH:"monthPage",MEMO:"memoPage",AI:"aiPage",SETTINGS:"settingsPage"};
  for(const [key,id] of Object.entries(pages))$(id).hidden=key!==page||localOptions;
  $("extraOptions").hidden=!localOptions;
  document.querySelectorAll(".clear-tabbar button").forEach(b=>{
    b.classList.toggle("current",b.dataset.action?.toUpperCase()===page)
  });
  const anchor=state.timeline?.anchorMillis||Date.now(),zone=state.timeline?.zoneId;
  $("dayTitle").textContent=dateText(anchor,zone,{month:"long",day:"numeric"});
  $("dateSubtitle").textContent=dateText(anchor,zone,{weekday:"long",year:"numeric"});
  if(page==="TIMELINE")renderTimeline();
  if(page==="MONTH")renderMonth();
  if(page==="MEMO")renderMemo();
  if(page==="AI")renderChat();
  if(page==="SETTINGS")renderSettings();
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

  const skinRoot=$("skinChoices");
  const skinStatus=$("skinSelectionStatus");
  if(skinRoot){
    skinRoot.replaceChildren();
    const choices=(Array.isArray(state.skinChoices)?state.skinChoices:[])
      .filter(item=>item&&typeof item.id==="string"&&item.id.length>0&&
        typeof item.name==="string");
    const selectedId=typeof state.selectedSkinId==="string"?state.selectedSkinId:"";
    if(skinStatus)skinStatus.textContent=choices.length?
      "切换前会由 Android 宿主再次确认。":"暂时无法读取已安装皮肤。";
    for(const item of choices){
      const row=node("div","skin-choice-row");
      row.setAttribute("role","listitem");
      const button=node("button","skin-choice-button");
      button.type="button";
      button.dataset.action="selectSkin";
      button.dataset.skinId=item.id;
      const isSelected=item.id===selectedId;
      button.setAttribute("aria-pressed",String(isSelected));
      button.disabled=isSelected;
      button.append(node("span","skin-choice-name",item.name));
      button.append(node("span","skin-choice-state",isSelected?"正在使用":"选择"));
      row.append(button);
      if(item.builtIn===true)row.append(node("span","skin-choice-meta","内置皮肤"));
      else{
        const actions=node("div","skin-choice-actions");
        const exportButton=node("button","skin-choice-action","导出");
        exportButton.type="button";
        exportButton.dataset.action="exportInstalledSkin";
        exportButton.dataset.skinId=item.id;
        exportButton.setAttribute("aria-label","导出 "+item.name);
        const deleteButton=node("button","skin-choice-action","删除");
        deleteButton.type="button";
        deleteButton.dataset.action="deleteSkin";
        deleteButton.dataset.skinId=item.id;
        deleteButton.setAttribute("aria-label","删除 "+item.name);
        actions.append(exportButton,deleteButton);
        row.append(actions);
      }
      skinRoot.append(row);
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
  if(!t){$("timeRangeLabel").textContent="尚无宿主数据";return}
  $("timeRangeLabel").textContent=t.hours+" 小时";
  const from=Number(t.visibleStartMillis),to=Number(t.visibleEndMillis),span=Math.max(1,to-from);
  const zone=t.zoneId,axis=$("timelineSurface"),height=Math.max(1,axis.clientHeight);
  if(!Number.isFinite(from)||!Number.isFinite(to)||to<=from)return;
  const position=ms=>100*(ms-from)/span;
  const kindLabel={ACTIVITY:"活动",ALARM:"提醒",DEADLINE:"截止事项"};
  const items=Array.isArray(t.items)?t.items:[];
  const isDateOnlyDeadline=item=>String(item.kind).toUpperCase()==="DEADLINE"&&
    typeof item.deadlineDate==="string"&&/^\d{4}-\d{2}-\d{2}$/.test(item.deadlineDate);
  const dateItems=items.filter(isDateOnlyDeadline);
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
      const label=`截止事项：${title}，${item.deadlineDate}，无具体时刻`;
      const button=makeEventButton(item,"DEADLINE",label);
      button.className="dateDeadline";button.dataset.temporalGeometry="date";
      button.dataset.deadlineDate=item.deadlineDate;
      button.append(node("span","dateDeadlineTitle",title),
        node("span","dateDeadlineDate",`${item.deadlineDate} · 截止，无具体时刻`));
      dates.append(button);
    }
  }
  for(let i=0;i<=Math.ceil(t.hours);i++){
    const ms=from+i*3600000;if(ms>to)break;
    const top=position(ms)+"%";
    const grid=node("div","timeGrid"),tick=node("div","timeTick",relativeTime(ms,zone));
    grid.style.top=top;tick.style.top=top;labels.append(grid,tick);
  }
  const timedItems=items.filter(item=>kindLabel[String(item.kind).toUpperCase()]&&
    !isDateOnlyDeadline(item));
  const laneWidth=84,laneGap=4;
  const laneCountFor=sideItems=>Math.max(1,...sideItems.map(item=>Math.max(0,Math.floor(Number(item.lane)||0))+1));
  for(const [container,lanes,sideName] of [
    [left,leftLanes,"LEFT"],[right,rightLanes,"RIGHT"]
  ]){
    const sideItems=timedItems.filter(item=>
      String(item.side||(String(item.kind).toUpperCase()==="ACTIVITY"?"RIGHT":"LEFT")).toUpperCase()===sideName);
    const laneCount=laneCountFor(sideItems);
    const width=Math.max(container.clientWidth,laneCount*(laneWidth+laneGap)-laneGap);
    lanes.style.width=width+"px";lanes.dataset.laneCount=String(laneCount);
  }
  for(const item of timedItems){
    const kind=String(item.kind||"ACTIVITY").toUpperCase();
    if(!kindLabel[kind])continue;
    // Respect the Host side and lane. A date-only Deadline was already moved to its date strip.
    const sideName=String(item.side||(kind==="ACTIVITY"?"RIGHT":"LEFT")).toUpperCase();
    const lanes=sideName==="LEFT"?leftLanes:rightLanes;
    const startMillis=Number(item.startMillis),lane=Math.max(0,Math.floor(Number(item.lane)||0));
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
    const ariaLabel=isActivity
      ?`${kindLabel[kind]}：${item.title}，${timeText} 至 ${relativeTime(rawEnd,zone)}`
      :`${kindLabel[kind]}：${timeText}，${item.title}`;
    const button=makeEventButton(item,kind,ariaLabel);
    button.dataset.temporalGeometry=isActivity?"interval":"point";
    button.dataset.startMillis=String(startMillis);button.dataset.endMillis=String(rawEnd);
    button.dataset.visibleStartMillis=String(from);button.dataset.visibleEndMillis=String(to);
    button.dataset.lane=String(lane);button.dataset.side=sideName;
    button.style.width=laneWidth+"px";
    if(sideName==="LEFT")button.style.right=(lane*(laneWidth+laneGap))+"px";
    else button.style.left=(lane*(laneWidth+laneGap))+"px";
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
      visual.style.top=(visualTop-hitTop)+"px";visual.style.height=visualHeight+"px";
      const title=makeTimelineTitle(item,kind,String(item.title||""),laneWidth-13);
      title.classList.add("tActivityTitle");
      title.dataset.titleFrameTop=String(visualTop-hitTop);
      title.dataset.titleFrameHeight=String(visualHeight);
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
      title.style.top=(anchorY-hitTop+(kind==="ALARM"?3:-18))+"px";
      button.append(line,title);
    }
    attachTimelineDrag(button,item,axis);
    lanes.append(button);
  }
  if(t.nowMillis>=from&&t.nowMillis<to){
    const marker=node("div","now-line");marker.style.top=position(t.nowMillis)+"%";now.append(marker);
  }
  if(t.truncated){
    const warning=node("p","quiet","可见事件过多，未全部显示");
    warning.style.cssText="position:absolute;bottom:4px;right:4px";now.append(warning);
  }
  const newLeftOverflow=left.scrollWidth>left.clientWidth+1;
  const newRightOverflow=right.scrollWidth>right.clientWidth+1;
  left.scrollLeft=(!timelineScrollInitialized||(!previousLeftOverflow&&newLeftOverflow))
    ?left.scrollWidth:previousLeftScroll;
  right.scrollLeft=(!timelineScrollInitialized||(!previousRightOverflow&&newRightOverflow))
    ?0:previousRightScroll;
  timelineScrollInitialized=true;
  recomputeTimelineTitleFolding();
}
function renderMonth(){
  const month=state.month||{},t=state.timeline||{};
  const zone=month.zoneId||t.zoneId||"Asia/Shanghai";
  const firstIso=month.gridFrom||new Date().toISOString().slice(0,10);
  $("monthTitle").textContent=(month.month||"") + " · " + zone;
  const grid=$("monthGrid");grid.replaceChildren();
  const events=month.items||[];
  const counts=new Map();
  for(const event of events)counts.set(event.date,(counts.get(event.date)||0)+1);
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
  for(let i=0;i<cellCount;i++){
    const date=new Date(origin+i*86400000),iso=date.toISOString().slice(0,10);
    if(iso.slice(0,7)!==(month.month||firstIso.slice(0,7))){
      grid.append(node("div","month-cell-empty"));continue;
    }
    const button=node("button","month-cell",date.getUTCDate());
    button.type="button";button.dataset.action="selectMonthDate";button.dataset.date=iso;
    if(iso===month.selectedDate)button.classList.add("selected");
    if(iso===todayIso)button.classList.add("today");
    button.setAttribute("aria-pressed",String(iso===month.selectedDate));
    const count=counts.get(iso)||0;
    button.setAttribute("aria-label",`${iso}，${count} 项${iso===todayIso?"，今天":""}`);
    if(count)button.append(node("span","month-dot",count>3?"•••":"•".repeat(count)));
    grid.append(button);
  }
  const agenda=$("monthAgenda");agenda.replaceChildren();
  const picked=events.filter(item=>item.date===month.selectedDate);
  agenda.append(node("h2","",month.selectedDate||""));
  if(!picked.length)agenda.append(node("p","quiet","当天暂无日程"));
  for(const item of picked){
    const button=node("button","agenda-event",item.title);
    button.dataset.action="openEvent";
    button.dataset.eventSeq=item.eventSeq;button.dataset.revision=item.revision;
    if(item.originalOccurrenceKey)button.dataset.originalOccurrenceKey=item.originalOccurrenceKey;
    agenda.append(button);
  }
  if(month.truncated)agenda.append(node("p","quiet","本月事件超过投影上限，部分数据未显示"));
  $("viewMonthDate").dataset.date=month.selectedDate||firstIso;
}
function renderMemo(){
  const list=$("memoList");list.replaceChildren();
  const completed=memoFilter==="done";
  document.querySelectorAll("[data-filter]").forEach(button=>
    button.classList.toggle("selected",button.dataset.filter===memoFilter));
  const entries=(state.memoEntries||[]).filter(x=>Boolean(x.completed)===completed);
  if(!entries.length){list.append(node("p","quiet",completed?"暂无已完成项目":"暂无待完成项目"))}
  for(const item of entries){
    const row=node("div","memo-row"+(item.completed?" done":""));
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
    row.append(button,title);list.append(row);
  }
  const remaining=Math.max(0,Number(state.memoRemainingCount)||0);
  $("memoLimit").textContent=`加载更多（还有 ${remaining} 项）`;
  $("memoLimit").hidden=!state.memoTruncated;
}
function renderChat(){
  installChatAttachmentControls();
  const list=$("chatMessages");list.replaceChildren();
  const messages=state.chatMessages||[];
  if(!messages.length)list.append(node("p","quiet","从这里开始与 AI 讨论日程"));
  for(const item of messages){
    const bubble=node("article","chat-bubble "+(item.role==="user"?"user":"assistant"));
    bubble.append(node("p","chat-message-content",item.content));
    const actions=node("div","chat-message-actions");
    const addAction=(label,accessible,edit)=>{
      const button=node("button","",label);button.type="button";
      button.setAttribute("aria-label",accessible);
      button.disabled=state.chatHistoryActionsAvailable!==true||typeof item.messageId!=="string";
      button.addEventListener("click",()=>{
        if(!button.disabled)rewindChat(item.messageId,edit);
      });
      actions.append(button);
    };
    if(item.role==="user"){
      addAction("编辑并重新发送","编辑这条消息并移除后续对话",true);
      addAction("撤回及后续","撤回这条消息及后续对话",false);
    }else addAction("回退到此条","保留这条回复并移除后续对话",false);
    bubble.append(actions);list.append(bubble);
  }
  renderAiBatchControls();
  renderAiProposalReview(list);
  const batchPhase=String(state.aiBatch?.phase||"IDLE");
  const disabled=!!(state.aiLocked||state.chatBusy||batchPhase==="AWAITING_HOST_REVIEW");
  $("chatInput").disabled=disabled;$("chatSend").disabled=disabled;
  const reviewPending=!!state.aiProposalReview?.proposals?.length;
  const attachmentUnavailable=state.chatAttachmentAvailable===false;
  for(const button of document.querySelectorAll(".chat-attachment-controls button"))
    button.disabled=disabled||Boolean(state.chatAttachmentBusy)||attachmentRequestPending||
      reviewPending||attachmentUnavailable;
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
  style.textContent=`.chat-attachment-controls{display:flex;flex-direction:column;gap:4px;flex:0 0 auto;align-self:center}
    .chat-attachment-controls button{font:inherit;font-size:11px;min-height:28px;padding:4px 8px;
      border:1px solid var(--sep);border-radius:12px;background:var(--bg-elevated);color:var(--label)}
    .chat-attachment-controls button:disabled{opacity:.45}`;
  document.head.append(style);
  const controls=node("div","chat-attachment-controls");controls.id="chatAttachmentControls";
  for(const [name,label,accessible] of [
    ["pickAiImage","图片","选择图片附件"],["pickAiFile","文件","选择日历或文档附件"]
  ]){
    const button=node("button","",label);button.type="button";
    button.setAttribute("aria-label",accessible);
    button.addEventListener("click",()=>{
      if(!port||button.disabled)return;
      attachmentRequestPending=true;renderChat();send(name);
    });
    controls.append(button);
  }
  composer.insertBefore(controls,$("chatSend"));
}
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
  active.close();
  return true;
}
window.PowerCalSkin={receive,handleHostBack,
  sendCommand:(name,requestId,extra={})=>
    postBridgeMessage({version:1,type:"command",payload:{name,requestId,...extra}})};
})();
