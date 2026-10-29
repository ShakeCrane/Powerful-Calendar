(()=>{
"use strict";
// UI-only implementation. The Host Bridge owns writes, session locks and rollback.
const $=id=>document.getElementById(id),sheet=$("skinSheet");
const status=$("sheetStatus"),fields=$("sheetFields"),actions=$("sheetActions");
let state={},mode="",identity="",fingerprint="",busy=false,seq=0,queue=Promise.resolve();
const pending=new Map();
const el=(tag,content,cls)=>{
  const node=document.createElement(tag);
  if(content!=null)node.textContent=String(content);
  if(cls)node.className=cls;
  return node;
};
function resetTransport(){
  for(const fn of pending.values())fn({status:"unavailable"});
  pending.clear();queue=Promise.resolve();busy=false;
}
function send(name,args){
  return new Promise(resolve=>{
    const id="clear_"+Date.now().toString(36)+"_"+(++seq).toString(36);
    pending.set(id,resolve);
    if(!window.PowerCalSkin?.sendCommand?.(name,id,args)){
      pending.delete(id);resolve({status:"unavailable"});
    }
  });
}
function perform(name,args={}){
  busy=true;
  const job=queue.catch(()=>({status:"failed"})).then(()=>send(name,args));
  queue=job;
  return job.then(reply=>{
    busy=false;
    showReply(reply);
    return reply;
  });
}
function showReply(reply){
    const labels={saved:"事件已保存",committed:"草稿已发布",discarded:"已丢弃，可以撤销",
      discarded_changes:"已放弃本次修改",selected:"已选择",
      confirmation_required:"请在宿主确认此操作",cancelled:"已取消宿主操作，内容仍保留",
      restored:"已恢复",replaced:"已按审阅快照覆盖",kept:"已保留草稿，正式事件未变化",
      loaded:"已加载已删除事件",updated:"墓碑已更新，已打开最新恢复草稿",
      flushed:"修改已暂存",closed:"已安全退出",
      opened:"已打开替换草稿",searched:"搜索结果已更新",moved:"已切换草稿",
      staged:"移动修改已暂存为草稿；正式事件尚未改变",
      unchanged:"事件已位于该时间，没有创建草稿",
      unsupported:"此事项不支持时间轴拖动",
      stale:"数据已变化，请重新审查",locked:"被其他业务操作锁定",
      invalid:"输入无效，请检查完整性",conflict:"存在版本冲突，请审查差异",
      restore_required:"原事件已删除，需要受保护的恢复确认",
      unavailable:"与宿主的连接已断开",duplicate_request_id:"重复命令标识被拒绝",
      deleted:"预览已模拟删除（未写入真实数据）",failed:"未成功，数据已保留在宿主"};
    status.textContent=labels[reply.status]||reply.detail||"";
}
function onResult(reply){
  const fn=pending.get(reply.requestId);
  if(fn){pending.delete(reply.requestId);fn(reply)}
}
function visible(show){
  sheet.hidden=!show;
  document.body.classList.toggle("editing-open",show);
}
function patch(field,value){
  const edit=state.editing||{};
  if(mode==="event"&&edit.editor?.sessionId)
    void perform("editor.patch",{sessionId:edit.editor.sessionId,field,value});
  else if(mode==="deck"&&edit.deck?.currentId!=null)
    void perform("deck.patch",{draftId:edit.deck.currentId,
      draftVersion:edit.deck.version,field,value});
}
function row(form,key,label,kind="text",choices=null){
  const container=el("label",null,"sheet-row");
  container.append(el("span",label,"sheet-label"));
  let control;
  if(choices){
    control=document.createElement("select");
    for(const [value,text] of choices){
      const option=el("option",text);option.value=value;control.append(option);
    }
  }else if(kind==="notes"){
    control=document.createElement("textarea");control.rows=3;
    control.maxLength=2000;
  }else{
    control=document.createElement("input");control.type=kind;
    control.maxLength=key==="title"?240:240;
  }
  control.value=String(form[key]??"");
  control.dataset.field=key;
  control.addEventListener("change",()=>patch(key,String(control.value)));
  container.append(control);return container;
}
function renderForm(form,occurrenceOnly=false){
  fields.append(row(form,"title","标题"));
  if(!occurrenceOnly)fields.append(row(form,"kind","事件类型",null,
    [["ALARM","闹钟"],["ACTIVITY","活动"],["DEADLINE","截止"],["MEMO","备忘"]]));
  if(["ACTIVITY","ALARM","MEMO"].includes(form.kind))
    fields.append(row(form,"startText",form.kind==="MEMO"?"开始日期":"开始时间"));
  if(["ACTIVITY","DEADLINE","MEMO"].includes(form.kind))
    fields.append(row(form,"endText",form.kind==="DEADLINE"?
      "截止日期或时刻":form.kind==="MEMO"?"结束日期":"结束时间"));
  if(occurrenceOnly){
    fields.append(el("p","仅本次可修改标题和时间；地点、备注、提醒与重复规则保持不变。","sheet-note"));
    return;
  }
  fields.append(row(form,"location","地点"));
  fields.append(row(form,"notes","备注","notes"));
  fields.append(row(form,"enabled","启用",null,[["true","开启"],["false","关闭"]]));
  if(form.kind!=="MEMO")fields.append(row(form,"trackingEnabled",
    "跟踪完成状态",null,[["true","开启"],["false","关闭"]]));
  fields.append(row(form,"reminderMinutes","提前提醒（分钟，-1关闭）","number"));
  fields.append(row(form,"reminderMethod","提醒方式",null,
    [["MESSAGE","消息"],["ALARM","闹钟"]]));
  fields.append(row(form,"reminderTiming","提醒基准",null,
    [["OFFSET","相对时间"],["DAY_START","当天开始"]]));
  fields.append(row(form,"recurrenceFreq","重复",null,
    [["NONE","不重复"],["DAILY","每天"],["WEEKLY","每周"],["MONTHLY","每月"]]));
  if(form.recurrenceFreq!=="NONE"){
    fields.append(row(form,"recurrenceInterval","重复间隔","number"));
    fields.append(row(form,"recurrenceFrom","开始生效日期 YYYY-MM-DD"));
    fields.append(row(form,"recurrenceUntil","结束生效日期 YYYY-MM-DD"));
  }
  fields.append(el("p","来源 "+(form.source||"—")+" · 时区 "+(form.zoneId||"—"),"sheet-note"));
}
function button(title,action,cls=""){
  const b=el("button",title,cls);b.type="button";b.dataset.do=action;return b;
}
function render(){
  const editing=state.editing||{},editor=editing.editor||{},deck=editing.deck||{};
  const next=editor.open?"event":deck.open?"deck":"";
  const id=next==="event"?"event:"+editor.sessionId:
    next==="deck"?"deck:"+deck.currentId:"";
  if(!next){mode="";identity="";fingerprint="";visible(false);return}
  visible(true);
  const form=next==="event"?editor.form:deck.form;
  const deckView=next==="deck"?{
    query:deck.searchQuery,cards:(deck.cards||[]).map(card=>[card.draftId,card.draftVersion,card.title]),
    formal:(deck.formalMatches||[]).map(item=>[item.eventSeq,item.revision]),
    conflict:deck.conflict,restore:deck.restore,
    retired:(deck.retiredResults||[]).map(item=>[item.eventSeq,item.revision,item.title]),
    retiredOffset:deck.retiredOffset,retiredTotal:deck.retiredTotal,
    retiredLoaded:deck.retiredLoaded,
    position:deck.position,count:deck.cardCount,previous:deck.canGoPrevious,next:deck.canGoNext,
    truncated:deck.truncated,transient:deck.transient===true
  }:null;
  const current=JSON.stringify({form,deck:deckView,scope:editor.recurrenceScope,
    requiresScope:editor.requiresRecurrenceScope,dirty:editor.dirty,
    awaitingConfirmation:editor.awaitingConfirmation});
  const focused=!!document.activeElement?.matches?.(
    "#sheetFields input,#sheetFields textarea,#sheetFields select,#sheetFields [contenteditable=true]");
  const changed=mode!==next||identity!==id||(!focused&&fingerprint!==current);
  mode=next;identity=id;
  if(!changed)return;
  fingerprint=current;
  fields.replaceChildren();actions.replaceChildren();
  $("sheetTitle").textContent=mode==="event"?"编辑事件":"待处理草稿";
  const deckCount=Number.isFinite(Number(deck.cardCount))?Number(deck.cardCount):(deck.cards||[]).length;
  $("sheetSubtitle").textContent=mode==="event"?
    "#"+(editor.eventSeq||"?")+" · "+(form?.kind||""):
    String(deckCount)+" 张 · "+(deck.dirty?"尚未暂存":"已暂存");
  $("sheetSave").textContent=mode==="event"?"保存":"发布";
  const requiresScope=mode==="event"&&editor.requiresRecurrenceScope===true;
  const scope=editor.recurrenceScope||"";
  $("sheetSave").disabled=!form||deck.aiLocked===true||editor.awaitingConfirmation===true||
    !!deck.conflict||!!deck.restore||
    (requiresScope&&!scope);
  if(mode==="event"){
    if(form&&(!requiresScope||scope))renderForm(form,scope==="SINGLE_OCCURRENCE");
    else if(requiresScope)fields.append(el("p","请先选择编辑范围，再修改字段或保存。","sheet-note"));
    if(requiresScope){
      const chooser=el("nav",null,"sheet-cards");chooser.setAttribute("aria-label","重复事件编辑范围");
      for(const [value,label] of [["SINGLE_OCCURRENCE","仅本次"],
        ["THIS_AND_FUTURE","本次及以后"],["WHOLE_SERIES","整个系列"]]){
        const b=button(label,"scope","sheet-card");b.dataset.scope=value;
        b.disabled=!!editor.dirty||!!editor.awaitingConfirmation||
          (value!=="WHOLE_SERIES"&&!editor.canEditSelectedOccurrence);
        b.setAttribute("aria-pressed",String(scope===value));
        if(scope===value)b.classList.add("selected");
        chooser.append(b);
      }
      actions.append(chooser);
    }
    if(editor.dirty)actions.append(button("放弃本次修改","discardChanges","sheet-secondary"));
    actions.append(button("保存检查点并退出","close","sheet-secondary"));
    const recurring=form?.recurrenceFreq&&form.recurrenceFreq!=="NONE";
    const deleteLabel=recurring?"删除整个重复系列":"删除正式事件";
    const remove=button(deleteLabel,"deleteEvent","sheet-danger");
    remove.disabled=editor.awaitingConfirmation===true||!editor.sessionId;
    remove.setAttribute("aria-label",deleteLabel);
    actions.append(remove);
    actions.append(el("p",recurring?
      "删除将移除整个重复系列及其全部实例，并须由 Android 宿主再次确认。":
      "删除须由 Android 宿主再次确认；取消不会删除事件。","sheet-note"));
  }else{
    const transient=deck.transient===true;
    if(!transient){
      const search=el("form",null,"deck-search");
      search.setAttribute("role","search");
      const searchLabel=el("label","搜索草稿和正式事件","sheet-label");
      const input=el("input");input.type="search";input.maxLength=120;
      input.autocomplete="off";input.value=deck.searchQuery||"";
      input.setAttribute("aria-label","搜索草稿标题或正式事件标题、序号");
      const submit=el("button","查找","sheet-secondary");submit.type="submit";
      search.append(searchLabel,input,submit);
      search.addEventListener("submit",event=>{
        event.preventDefault();
        input.blur();
        void perform("deck.search",{query:input.value.trim().slice(0,120)});
      });
      fields.append(search);

      const formal=deck.formalMatches||[];
      if((deck.searchQuery||"").trim()){
        if(!deck.cards?.length&&!formal.length)
          fields.append(el("p","没有匹配的草稿或正式事件","sheet-note"));
        if(formal.length){
          const results=el("nav",null,"deck-formal-results");
          results.setAttribute("aria-label","匹配的正式事件，可生成替换草稿");
          for(const match of formal){
            const seq=Number(match.eventSeq),revision=Number(match.revision);
            const item=button("#"+String(match.eventSeq).padStart(4,"0")+" · "+
              (match.title||"未命名事件"),"replace","deck-result");
            item.dataset.eventSeq=String(match.eventSeq);item.dataset.revision=String(match.revision);
            item.disabled=!Number.isSafeInteger(seq)||seq<=0||
              !Number.isSafeInteger(revision)||revision<=0;
            results.append(item);
          }
          fields.append(results);
        }
      }

      const nav=el("nav",null,"sheet-cards");
      nav.setAttribute("aria-label","待处理草稿；选择卡片继续编辑");
      for(const card of deck.cards||[]){
        const b=button("#"+String(card.eventSeq).padStart(4,"0")+" · "+
          (card.title||"未命名草稿"),"select","sheet-card");
        b.dataset.draftId=card.draftId;
        if(card.draftId===deck.currentId)b.classList.add("selected");
        nav.append(b);
      }
      if(deck.cards?.length||!(deck.searchQuery||"").trim())fields.append(nav);
      if(deck.searchTruncated)fields.append(el("p","仅显示前 4 条匹配草稿。","sheet-note"));
      else if(deck.truncated)fields.append(el("p","草稿列表仅显示前 80 张；使用搜索查找其他草稿。","sheet-note"));
    }
    if(form&&!deck.conflict&&!deck.restore)renderForm(form);
    else if(!transient&&!deck.cards?.length&&!(deck.formalMatches||[]).length)
      fields.append(el("p","当前没有可编辑的待处理草稿。","sheet-note"));

    if(deck.retiredLoaded===true&&!deck.conflict&&!deck.restore){
      fields.append(el("h3","已删除事件","sheet-label"));
      if(!deck.retiredTotal)fields.append(el("p","没有可恢复的已删除事件。","sheet-note"));
      const retired=el("nav",null,"deck-retired-results");
      retired.setAttribute("aria-label","已删除事件；选择一项生成恢复草稿");
      for(const item of deck.retiredResults||[]){
        const title=item.title||"未命名事件";
        const detail=[item.kind,item.startText||item.endText].filter(Boolean).join(" · ");
        const b=button("#"+String(item.eventSeq).padStart(4,"0")+" · "+title+
          (detail?" · "+detail:""),"beginRestore","deck-result");
        b.dataset.eventSeq=String(item.eventSeq);b.dataset.revision=String(item.revision);
        b.setAttribute("aria-label","恢复草稿："+title+"，事件序号 "+item.eventSeq);
        retired.append(b);
      }
      if(retired.childElementCount)fields.append(retired);
      if(Number(deck.retiredTotal)>50){
        const paging=el("nav",null,"sheet-deck-navigation retired-navigation");
        paging.setAttribute("aria-label","已删除事件分页");
        const previous=button("较新","retiredPage","sheet-secondary");
        previous.dataset.offset=String(Math.max(0,Number(deck.retiredOffset)-50));
        previous.disabled=deck.retiredHasPrevious!==true;
        const count=el("span",String(Number(deck.retiredOffset)+1)+"–"+
          String(Number(deck.retiredOffset)+(deck.retiredResults||[]).length)+" / "+String(deck.retiredTotal));
        const next=button("较旧","retiredPage","sheet-secondary");
        next.dataset.offset=String(Number(deck.retiredOffset)+50);
        next.disabled=deck.retiredHasNext!==true;
        paging.append(previous,count,next);fields.append(paging);
      }
      fields.append(el("p","恢复会先创建待处理草稿；正式事件只有在 Android 宿主再次确认后才会恢复。","sheet-note"));
    }

    if(deck.conflict){
      const conflict=deck.conflict;
      const section=el("section",null,"deck-review-snapshot");
      section.setAttribute("aria-label","正式事件与冲突草稿的完整差异");
      section.append(el("h3","版本冲突 · #"+
        String(conflict.eventSeq).padStart(4,"0")+" · v"+conflict.currentRevision,"sheet-label"));
      for(const change of conflict.changes||[]){
        const row=el("div",null,"deck-change-row");
        row.append(el("strong",change.label,"deck-change-label"));
        row.append(el("span",change.before+" → "+change.after,"deck-change-values"));
        section.append(row);
      }
      section.append(readonlySnapshot("当前正式事件",conflict.current));
      section.append(readonlySnapshot("待处理草稿",conflict.proposed));
      fields.append(section);
      actions.append(button("申请宿主确认整份覆盖","requestConflictReview","sheet-danger"));
      actions.append(button("保留冲突草稿","keepConflict","sheet-secondary"));
    }
    if(deck.restore){
      const restore=deck.restore;
      const section=el("section",null,"deck-review-snapshot");
      section.setAttribute("aria-label","已删除事件与恢复草稿快照");
      section.append(el("p",restore.message||"原事件已删除，需要确认恢复。","sheet-warning"));
      section.append(readonlySnapshot("删除前的事件",restore.current));
      section.append(readonlySnapshot("恢复后内容",restore.proposed));
      fields.append(section);
      const args={draftId:Number(restore.draftId),draftVersion:Number(restore.draftVersion),
        eventSeq:Number(restore.eventSeq),expectedRevision:Number(restore.expectedRevision)};
      const request=button("申请宿主确认恢复同一事件","requestRestoreReview","sheet-danger");
      request.disabled=!Number.isSafeInteger(Number(restore.expectedRevision))||
        Number(restore.expectedRevision)<=0;
      actions.append(request);
      actions.append(button("保留恢复草稿","keepRestore","sheet-secondary"));
      request.dataset.target=JSON.stringify(args);
      const keep=actions.lastElementChild;keep.dataset.target=JSON.stringify(args);
    }

    if(!deck.conflict&&!deck.restore&&!transient&&Number(deck.cardCount)>1){
      const paging=el("nav",null,"sheet-deck-navigation");
      paging.setAttribute("aria-label","草稿导航");
      const previous=button("上一张","jump","sheet-secondary");
      previous.dataset.steps="-1";previous.disabled=deck.canGoPrevious!==true;
      const count=el("span",String(deck.position||0)+" / "+String(deck.cardCount||0));
      const nextButton=button("下一张","jump","sheet-secondary");
      nextButton.dataset.steps="1";nextButton.disabled=deck.canGoNext!==true;
      paging.append(previous,count,nextButton);actions.append(paging);
    }
    if(!deck.conflict&&!deck.restore){
      actions.append(button("暂存","flush","sheet-secondary"));
      actions.append(button("丢弃草稿","discard","sheet-danger"));
      if(deck.lastDiscardId!=null)actions.append(button("撤销丢弃","undo","sheet-secondary"));
      actions.append(button("完成并退出","close","sheet-secondary"));
    }else actions.append(button("关闭","close","sheet-secondary"));
  }
}
function readonlySnapshot(title,form){
  const details=document.createElement("details");details.className="deck-readonly-snapshot";
  const summary=el("summary",title);details.append(summary);
  if(!form){details.append(el("p","当前快照不可用，请刷新后重新检查。","sheet-note"));return details}
  const labels={kind:"类型",title:"标题",startText:"开始",endText:"结束/截止",
    location:"地点",notes:"备注",enabled:"启用",trackingEnabled:"完成跟踪",
    reminderMinutes:"提醒分钟",reminderMethod:"提醒方式",reminderTiming:"提醒时机",
    recurrenceFreq:"重复",recurrenceInterval:"重复间隔",recurrenceFrom:"重复开始",
    recurrenceUntil:"重复结束",source:"来源",zoneId:"时区"};
  const rows=document.createElement("dl");
  for(const [key,label] of Object.entries(labels)){
    if(form[key]===undefined||form[key]===null||form[key]==="")continue;
    rows.append(el("dt",label),el("dd",String(form[key])));
  }
  details.append(rows);return details;
}
function onState(next){
  const previousKind=state.editing?.editor?.form?.kind||state.editing?.deck?.form?.kind;
  state=next;
  const nextKind=next.editing?.editor?.form?.kind||next.editing?.deck?.form?.kind;
  if(previousKind&&nextKind&&previousKind!==nextKind&&
    document.activeElement?.dataset?.field==="kind")document.activeElement.blur();
  const reply=next.editing?.lastResult;
  if(reply?.requestId)showReply(reply);
  render();
}
function routeAction(b,host){
  if(!host?.editing)return false;
  const name=b.dataset.action;
  if(name==="openEvent"){
    const args={eventSeq:Number(b.dataset.eventSeq),expectedRevision:Number(b.dataset.revision)};
    if(b.dataset.originalOccurrenceKey)args.occurrenceKey=b.dataset.originalOccurrenceKey;
    void perform("editor.open",args);return true;
  }
  if(name==="openDeck"){void perform("deck.open",{draftId:null});return true}
  if(name==="createDraft"||name==="createMemo"){
    void perform("deck.create",{kind:name==="createMemo"?"MEMO":
      b.dataset.kind||"ACTIVITY"});return true;
  }
  return false;
}
async function moveTimelineEvent(item,targetMillis){
  const statusElement=document.getElementById("timelineMoveStatus");
  const announce=value=>{if(statusElement)statusElement.textContent=value};
  const eventSeq=Number(item?.eventSeq),expectedRevision=Number(item?.revision);
  if(!Number.isSafeInteger(eventSeq)||eventSeq<=0||
    !Number.isSafeInteger(expectedRevision)||expectedRevision<=0||
    !Number.isSafeInteger(targetMillis)){
    announce("移动请求无效，事件没有变化");return {status:"invalid"};
  }
  const reply=await perform("timeline.move",{eventSeq,expectedRevision,targetMillis});
  const messages={
    staged:"移动已暂存为草稿；正式事件尚未改变，请检查草稿后再发布",
    unchanged:"事件已经位于该时间，没有创建草稿",
    unsupported:reply.detail||"此事项不支持时间轴拖动",
    stale:reply.detail||"事件已变化，请刷新后重新尝试",
    locked:reply.detail||"当前有其他编辑操作，请稍后重试",
    invalid:reply.detail||"移动请求无效，事件没有变化",
    failed:reply.detail||"移动未完成，事件仍保持原样",
    unavailable:"宿主桥不可用，事件仍保持原样"
  };
  announce(messages[reply.status]||reply.detail||"移动结果："+reply.status);
  return reply;
}
$("sheetSave").addEventListener("click",()=>{
  if(busy)return;
  const e=state.editing||{};
  if(mode==="event"&&e.editor?.sessionId)
    void perform("editor.save",{sessionId:e.editor.sessionId});
  else if(mode==="deck"&&e.deck?.currentId!=null)
    void perform("deck.commit",{draftId:e.deck.currentId,draftVersion:e.deck.version});
});
async function close(){
  if(busy)return;
  const e=state.editing||{};
  if(mode==="event"&&e.editor?.sessionId)
    await perform("editor.close",{sessionId:e.editor.sessionId});
  else if(mode==="deck")await perform("deck.close");
}
$("sheetClose").addEventListener("click",()=>void close());
$("sheetShade").addEventListener("click",()=>void close());
fields.addEventListener("click",event=>{
  const b=event.target.closest("[data-do]");if(!b||busy)return;
  if(b.dataset.do==="select"&&b.dataset.draftId)
    void perform("deck.select",{draftId:Number(b.dataset.draftId)});
  else if(b.dataset.do==="retiredPage")
    void perform("deck.retired",{offset:Number(b.dataset.offset)});
  else if(b.dataset.do==="beginRestore"){
    const eventSeq=Number(b.dataset.eventSeq),expectedRevision=Number(b.dataset.revision);
    if(Number.isSafeInteger(eventSeq)&&eventSeq>0&&
      Number.isSafeInteger(expectedRevision)&&expectedRevision>0)
      void perform("deck.beginRestore",{eventSeq,expectedRevision});
  }
  else if(b.dataset.do==="replace"){
    const eventSeq=Number(b.dataset.eventSeq),expectedRevision=Number(b.dataset.revision);
    if(Number.isSafeInteger(eventSeq)&&eventSeq>0&&
      Number.isSafeInteger(expectedRevision)&&expectedRevision>0)
      void perform("deck.proposeReplace",{eventSeq,expectedRevision});
  }
});
actions.addEventListener("click",event=>{
  const b=event.target.closest("[data-do]");if(!b||busy)return;
  const deck=state.editing?.deck||{};
  const editor=state.editing?.editor||{};
  switch(b.dataset.do){
    case "scope":if(editor.sessionId&&b.dataset.scope)
      void perform("editor.scope",{sessionId:editor.sessionId,scope:b.dataset.scope});break;
    case "discardChanges":if(editor.sessionId)
      void perform("editor.discardChanges",{sessionId:editor.sessionId});break;
    case "requestConflictReview":case "keepConflict":{
      const conflict=deck.conflict;
      if(!conflict||!Number.isSafeInteger(Number(conflict.currentRevision)))break;
      const args={draftId:Number(conflict.draftId),draftVersion:Number(conflict.draftVersion),
        eventSeq:Number(conflict.eventSeq),expectedRevision:Number(conflict.currentRevision)};
      if(Object.values(args).every(Number.isSafeInteger))
        void perform(b.dataset.do==="keepConflict"?"deck.keepConflict":"deck.requestConflictReview",args);
      break;
    }
    case "requestRestoreReview":case "keepRestore":{
      let args;try{args=JSON.parse(b.dataset.target||"null")}catch{args=null}
      if(args&&Object.values(args).every(Number.isSafeInteger))
        void perform(b.dataset.do==="keepRestore"?"deck.keepRestore":"deck.requestRestoreReview",args);
      break;
    }
    case "deleteEvent":if(editor.sessionId&&!editor.awaitingConfirmation)
      void perform("editor.delete",{sessionId:editor.sessionId});break;
    case "close":void close();break;
    case "jump":{
      const steps=Number(b.dataset.steps);
      if(Number.isInteger(steps)&&steps>=-1000&&steps<=1000)
        void perform("deck.jump",{steps});
      break;
    }
    case "flush":void perform("deck.flush");break;
    case "undo":void perform("deck.undo");break;
    case "discard":
      if(b.dataset.confirm!=="yes"){
        b.dataset.confirm="yes";b.textContent="再次点击确认丢弃";
        b.classList.add("confirming");return;
      }
      void perform("deck.discard",{draftId:deck.currentId,draftVersion:deck.version});
      break;
  }
});
window.PowerCalEdit={onState,onResult,routeAction,resetTransport,moveTimelineEvent};
})();
