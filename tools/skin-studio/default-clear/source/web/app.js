(() => {"use strict";
// First-party Clear runtime. No direct storage, network, Android or calendar access.
// All reads are complete bounded host projections; all writes pass the v1 action gate.
const $=id=>document.getElementById(id);
const darkMedia=window.matchMedia("(prefers-color-scheme: dark)");
function applyTheme(){document.documentElement.classList.toggle("dark",darkMedia.matches)}
applyTheme();
darkMedia.addEventListener("change",applyTheme);
let port=null, state={}, currentPage="TIMELINE", memoFilter="open",localOptions=false;
let aiReviewSelectionId="",aiReviewSelected=new Set();
let attachmentRequestPending=false;
let timelineCreateDialog=null;
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
  else {
    if(["timeline","memo","month","ai","settings","skinSettings"].includes(name))localOptions=false;
    // Preserve the v1 `settings` action for existing skins and protected tools. Clear's
    // tab uses a new explicit route for its customizable in-skin settings landing.
    if(name==="settings"&&button.closest(".clear-tabbar"))send("skinSettings");
    else send(name);
  }
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
  attachmentRequestPending=Boolean(state.chatAttachmentBusy);
  $("pendingCounter").textContent=Number(state.pendingCount)||"";
  $("memoCounter").textContent=Number(state.memoCount)||"";
  $("app").classList.toggle("reduced",Boolean(state.reducedMotion));
  renderPage();
}
function renderPage(){
  const skinSettings=currentPage==="SETTINGS";
  const page=currentPage==="TOOLS"||skinSettings?"TIMELINE":currentPage;
  const optionsVisible=localOptions||skinSettings;
  const createButton=document.querySelector('[data-action="createDraft"]');
  if(createButton){createButton.title="新建事件";createButton.setAttribute("aria-label","新建事件")}
  const pages={TIMELINE:"timelinePage",MONTH:"monthPage",MEMO:"memoPage",AI:"aiPage"};
  for(const [key,id] of Object.entries(pages))$(id).hidden=key!==page||optionsVisible;
  $("extraOptions").hidden=!optionsVisible;
  $("extraOptions").querySelector("h1").textContent=skinSettings?"设置与导入":"可用选项";
  const selectedPage=skinSettings?"SETTINGS":page;
  document.querySelectorAll(".clear-tabbar button").forEach(b=>{
    b.classList.toggle("current",b.dataset.action?.toUpperCase()===selectedPage)
  });
  const anchor=state.timeline?.anchorMillis||Date.now(),zone=state.timeline?.zoneId;
  $("dayTitle").textContent=dateText(anchor,zone,{month:"long",day:"numeric"});
  $("dateSubtitle").textContent=dateText(anchor,zone,{weekday:"long",year:"numeric"});
  if(!optionsVisible){
    if(page==="TIMELINE")renderTimeline();
    if(page==="MONTH")renderMonth();
    if(page==="MEMO")renderMemo();
    if(page==="AI")renderChat();
  }
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
    if(item.originalOccurrenceKey)card.dataset.originalOccurrenceKey=item.originalOccurrenceKey;
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
    if(item.originalOccurrenceKey)button.dataset.originalOccurrenceKey=item.originalOccurrenceKey;
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
  installChatAttachmentControls();
  const list=$("chatMessages");list.replaceChildren();
  const messages=state.chatMessages||[];
  if(!messages.length)list.append(node("p","quiet","从这里开始与 AI 讨论日程"));
  for(const item of messages)list.append(node("div","chat-bubble "+(item.role==="user"?"user":"assistant"),item.content));
  renderAiProposalReview(list);
  const disabled=!!(state.aiLocked||state.chatBusy);
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
  list.scrollTop=list.scrollHeight;
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
  const busy=!!(state.aiLocked||state.chatBusy);
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
  const root=$("optionGroups");root.replaceChildren();
  if(currentPage==="SETTINGS"){
    const permissions=state.reminderPermissions||{};
    const exact=permissions.exactAlarmAllowed===true?"已允许":
      permissions.exactAlarmAllowed===false?"未允许":"状态未知";
    const notifications=permissions.notificationsRuntimePermissionRequired===false?"此 Android 版本无需运行时授权":
      permissions.notificationsAllowed===true?"已允许":
      permissions.notificationsAllowed===false?"未允许":"状态未知";
    const status=node("p","",`精确闹钟：${exact}；通知运行时权限：${notifications}`);
    status.setAttribute("role","status");status.setAttribute("aria-live","polite");
    root.append(node("h2","","提醒状态"),status);
  }
  for(const group of state.optionGroups||[]){
    root.append(node("h2","",group.label));
    for(const item of group.items||[]){
      const button=node("button","optionRow",item.detail?item.label+" · "+item.detail:item.label);
      button.dataset.action="invokeOption";button.dataset.optionId=item.id;
      button.title=item.detail||"";
      if(item.detail)button.setAttribute("aria-label",item.label+"。"+item.detail);
      button.disabled=item.enabled===false;
      root.append(button);
    }
  }
  if(currentPage==="SETTINGS"){
    root.append(node("p","","权限请求由 Android 宿主处理；凭据、备份恢复和皮肤管理仍由宿主保护。"));
    const hostTools=node("button","optionRow","打开宿主工具与设置");
    hostTools.type="button";hostTools.dataset.action="settings";
    root.append(hostTools);
  }
}
window.PowerCalSkin={receive,sendCommand:(name,requestId,extra={})=>{
  if(!port)return false;
  port.postMessage(JSON.stringify({version:1,type:"command",
    payload:{name,requestId,...extra}}));
  return true;
}};
})();
