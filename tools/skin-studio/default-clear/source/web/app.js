(() => {"use strict";
// First-party Clear runtime. No direct storage, network, Android or calendar access.
// All reads are complete bounded host projections; all writes pass the v1 action gate.
const $=id=>document.getElementById(id);
const darkMedia=window.matchMedia("(prefers-color-scheme: dark)");
function applyTheme(){document.documentElement.classList.toggle("dark",darkMedia.matches)}
applyTheme();
darkMedia.addEventListener("change",applyTheme);
let port=null, state={}, currentPage="TIMELINE", memoFilter="open",localOptions=false;
const send=(name,extra={})=>{
  if(!port)return;
  port.postMessage(JSON.stringify({version:1,type:"action",payload:{name,...extra}}));
};
const node=(tag,cls,text)=>{
  const el=document.createElement(tag);
  if(cls)el.className=cls;
  if(text!=null)el.textContent=String(text);
  return el;
};
const dateText=(ms,zone,options)=>{
  try{return new Intl.DateTimeFormat("zh-CN",{timeZone:zone||"Asia/Shanghai",...options}).format(new Date(ms))}
  catch{return new Date(ms).toISOString().slice(0,10)}
};
const relativeTime=(ms,zone)=>dateText(ms,zone,{hour:"2-digit",minute:"2-digit",hour12:false});
function onAction(button){
  const name=button.dataset.action;if(!name||button.disabled)return;
  if(window.PowerCalEdit?.routeAction(button,state))return;
  if(name==="openEvent")send(name,{eventSeq:Number(button.dataset.eventSeq),
    expectedRevision:Number(button.dataset.revision)});
  else if(name==="createDraft")send(name,{kind:button.dataset.kind});
  else if(name==="toggleMemo")send(name,{eventSeq:Number(button.dataset.eventSeq),
    expectedRevision:Number(button.dataset.revision),
    occurrenceKey:button.dataset.occurrenceKey,completed:button.dataset.completed==="true"});
  else if(name==="invokeOption")send(name,{id:button.dataset.optionId});
  else if(name==="panTimeline")send(name,{deltaMinutes:Number(button.dataset.deltaMinutes)});
  else if(name==="jumpTimelineDay")send(name,{date:button.dataset.date});
  else if(name==="browseMonth")send(name,{deltaMonths:Number(button.dataset.deltaMonths)});
  else {if(["timeline","memo","month","ai"].includes(name))localOptions=false;send(name)}
}
document.addEventListener("click",e=>{
  const local=e.target.closest("[data-local]");
  if(local){localOptions=local.dataset.local==="options";renderPage();return}
  const toggle=e.target.closest("[data-filter]");
  if(toggle){memoFilter=toggle.dataset.filter;renderMemo();return}
  const button=e.target.closest("[data-action]");
  if(button)onAction(button);
});
$("chatForm").addEventListener("submit",e=>{
  e.preventDefault();
  const field=$("chatInput"),text=field.value.trim();
  if(!text||text.length>4000||state.aiLocked||state.chatBusy)return;
  send("sendChat",{text});field.value="";
});
window.addEventListener("message",e=>{
  if(e.data!=="powercal-port"||!e.ports?.[0])return;
  if(port)try{port.close()}catch{}
  port=e.ports[0];
  window.PowerCalEdit?.resetTransport();
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
  $("pendingCounter").textContent=Number(state.pendingCount)||"";
  $("memoCounter").textContent=Number(state.memoCount)||"";
  $("app").classList.toggle("reduced",Boolean(state.reducedMotion));
  renderPage();
}
function renderPage(){
  const page=currentPage==="TOOLS"?"TIMELINE":currentPage;
  const pages={TIMELINE:"timelinePage",MONTH:"monthPage",MEMO:"memoPage",AI:"aiPage"};
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
  renderOptions();
  window.PowerCalEdit?.onState(state);
}
function renderTimeline(){
  const t=state.timeline;
  const labels=$("timeLabels"),left=$("timelineLeft"),right=$("timelineRight"),now=$("timeNow");
  for(const el of [labels,left,right,now])el.replaceChildren();
  if(!t){$("timeRangeLabel").textContent="尚无宿主数据";return}
  $("timeRangeLabel").textContent=t.hours+" 小时";
  const from=t.visibleStartMillis,to=t.visibleEndMillis,span=Math.max(1,to-from);
  const zone=t.zoneId,axis=$("timelineSurface");
  const position=ms=>100*(ms-from)/span;
  for(let i=0;i<=Math.ceil(t.hours);i++){
    const ms=from+i*3600000;if(ms>to)break;
    const top=position(ms)+"%";
    const grid=node("div","timeGrid"),tick=node("div","timeTick",relativeTime(ms,zone));
    grid.style.top=top;tick.style.top=top;labels.append(grid,tick);
  }
  for(const item of t.items||[]){
    const ms=Number(item.startMillis);
    if(!Number.isFinite(ms))continue;
    const kind=String(item.kind||"ACTIVITY").toUpperCase();
    // Respect the host's authoritative side; keep a safe fallback for old projections.
    const side=String(item.side || (kind==="ACTIVITY"?"RIGHT":"LEFT")).toUpperCase()==="LEFT"?left:right;
    const start=Math.max(0,Math.min(100,position(ms)));
    const stop=item.endMillis==null?start+4:Math.min(100,position(Number(item.endMillis)));
    const height=Math.min(100-start,Math.max(4,stop-start));
    const card=node("button","tEvent "+kind,item.title);
    card.type="button";card.dataset.action="openEvent";
    card.dataset.eventSeq=item.eventSeq;card.dataset.revision=item.revision;
    card.style.top=start+"%";card.style.height=height+"%";
    const lane=Number(item.lane)||0;
    card.style.width=Math.min(84,Math.max(42,84-4*lane))+"%";
    if(side===right)card.style.left=lane*14+"%";
    else card.style.right=lane*14+"%";
    side.append(card);
  }
  if(t.nowMillis>=from&&t.nowMillis<to){
    const marker=node("div","now-line");marker.style.top=position(t.nowMillis)+"%";now.append(marker);
  }
  if(t.truncated){
    const warning=node("p","quiet","可见事件过多，未全部显示");
    warning.style.cssText="position:absolute;bottom:4px;right:4px";now.append(warning);
  }
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
  for(let i=0;i<42;i++){
    const date=new Date(origin+i*86400000),iso=date.toISOString().slice(0,10);
    const button=node("button","month-cell",date.getUTCDate());
    button.dataset.action="jumpTimelineDay";button.dataset.date=iso;
    if(iso===month.selectedDate)button.classList.add("selected");
    if(iso===new Date(t.nowMillis||Date.now()).toISOString().slice(0,10))button.classList.add("today");
    const count=counts.get(iso)||0;
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
    agenda.append(button);
  }
  if(month.truncated)agenda.append(node("p","quiet","本月事件超过投影上限，部分数据未显示"));
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
    const title=node("span","",item.title);
    row.append(button,title);list.append(row);
  }
  $("memoLimit").hidden=!state.memoTruncated;
}
function renderChat(){
  const list=$("chatMessages");list.replaceChildren();
  const messages=state.chatMessages||[];
  if(!messages.length)list.append(node("p","quiet","从这里开始与 AI 讨论日程"));
  for(const item of messages)list.append(node("div","chat-bubble "+(item.role==="user"?"user":"assistant"),item.content));
  const disabled=!!(state.aiLocked||state.chatBusy);
  $("chatInput").disabled=disabled;$("chatSend").disabled=disabled;
  if(disabled)list.append(node("p","quiet","AI 正在处理，暂不可发送"));
  list.scrollTop=list.scrollHeight;
}
function renderOptions(){
  const root=$("optionGroups");root.replaceChildren();
  for(const group of state.optionGroups||[]){
    root.append(node("h2","",group.label));
    for(const item of group.items||[]){
      const button=node("button","optionRow",item.label);
      button.dataset.action="invokeOption";button.dataset.optionId=item.id;
      button.disabled=item.enabled===false;
      root.append(button);
    }
  }
}
window.PowerCalSkin={receive,sendCommand:(name,requestId,extra={})=>{
  if(!port)return false;
  port.postMessage(JSON.stringify({version:1,type:"command",
    payload:{name,requestId,...extra}}));
  return true;
}};
})();