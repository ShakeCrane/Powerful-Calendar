(()=>{
"use strict";
// UI-only implementation. The Host Bridge owns writes, session locks and rollback.
const $=id=>document.getElementById(id),sheet=$("skinSheet");
const status=$("sheetStatus"),fields=$("sheetFields"),actions=$("sheetActions");
let state={},mode="",identity="",fingerprint="",busy=false,pendingOperations=0,saveQueued=false,seq=0,queue=Promise.resolve();
let expandedDeckId="",expandedDeck=false,expandedDeckTarget="";
let activeDeckGesture=null,suppressedDeckClick=null;
const pending=new Map();
sheet.setAttribute("aria-busy","false");
function syncBusyUi(){
  busy=pendingOperations>0;
  sheet.setAttribute("aria-busy",String(busy));
  fields.inert=busy;
  actions.inert=busy;
}
const el=(tag,content,cls)=>{
  const node=document.createElement(tag);
  if(content!=null)node.textContent=String(content);
  if(cls)node.className=cls;
  return node;
};
function resetTransport(){
  for(const fn of pending.values())fn({status:"unavailable"});
  pending.clear();queue=Promise.resolve();pendingOperations=0;syncBusyUi();
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
  pendingOperations++;
  syncBusyUi();
  const job=queue.catch(()=>({status:"failed"})).then(()=>send(name,args));
  queue=job.then(reply=>{
    const opensTargetDraft=(name==="deck.proposeReplace"&&reply.status==="opened")||
      (name==="timeline.move"&&reply.status==="staged");
    if(opensTargetDraft)
      expandedDeckTarget=/^\d+$/.test(String(reply.detail||""))?String(reply.detail):"";
    showReply(reply);
    render();
    return reply;
  }).finally(()=>{
    pendingOperations=Math.max(0,pendingOperations-1);
    syncBusyUi();
  });
  return queue;
}
function showReply(reply){
    if(["opened","searched","moved","selected"].includes(reply.status)){
      status.textContent="";
      return;
    }
    const labels={saved:"事件已保存",committed:"草稿已发布",
      committed_preview_only:"预览模拟发布流程已完成；真实日历数据未更改",
      discarded:"已丢弃，可以撤销",
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
      draftVersion:edit.deck.version,stateRevision:edit.deck.stateRevision,field,value});
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
function renderForm(form,occurrenceOnly=false,nativeLockedDeck=false){
  const appendRow=(...args)=>{
    const item=row(...args);
    if(nativeLockedDeck)item.classList.add("deck-native-095-locked-field");
    fields.append(item);
  };
  appendRow(form,"title","标题");
  if(!occurrenceOnly&&!nativeLockedDeck)appendRow(form,"kind","事件类型",null,
    [["ALARM","闹钟"],["ACTIVITY","活动"],["DEADLINE","截止"],["MEMO","备忘"]]);
  if(nativeLockedDeck&&form.kind==="MEMO")
    fields.append(el("p","备忘可完全不指定日期；也可以填连续的自然日区间。","deck-native-095-memo-hint"));
  if(["ACTIVITY","ALARM","MEMO"].includes(form.kind))
    appendRow(form,"startText",form.kind==="MEMO"?"开始日期（可选，yyyy-MM-dd）":"开始时间");
  if(["ACTIVITY","DEADLINE","MEMO"].includes(form.kind))
    appendRow(form,"endText",form.kind==="DEADLINE"?
      "截止日期或时刻":form.kind==="MEMO"?"结束日期（可选，含当天）":"结束时间");
  if(occurrenceOnly){
    fields.append(el("p","仅本次可修改标题和时间；地点、备注、提醒与重复规则保持不变。","sheet-note"));
    return;
  }
  appendRow(form,"location","地点（可选）");
  appendRow(form,"notes","备注","notes");
  appendRow(form,"enabled","启用",null,[["true","开启"],["false","关闭"]]);
  if(form.kind!=="MEMO")appendRow(form,"trackingEnabled",
    "跟踪完成状态",null,[["true","开启"],["false","关闭"]]);
  appendRow(form,"reminderMinutes","提前提醒（分钟，-1关闭）","number");
  appendRow(form,"reminderMethod","提醒方式",null,
    [["MESSAGE","消息"],["ALARM","闹钟"]]);
  appendRow(form,"reminderTiming","提醒基准",null,
    [["OFFSET","相对时间"],["DAY_START","当天开始"]]);
  appendRow(form,"recurrenceFreq","重复",null,
    [["NONE","不重复"],["DAILY","每天"],["WEEKLY","每周"],["MONTHLY","每月"]]);
  if(form.recurrenceFreq!=="NONE"){
    appendRow(form,"recurrenceInterval","重复间隔","number");
    appendRow(form,"recurrenceFrom","开始生效日期 YYYY-MM-DD");
    appendRow(form,"recurrenceUntil","结束生效日期 YYYY-MM-DD");
  }
  fields.append(el("p","来源 "+(form.source||"—")+" · 时区 "+(form.zoneId||"—"),"sheet-note"));
}
function button(title,action,cls=""){
  const b=el("button",title,cls);b.type="button";b.dataset.do=action;return b;
}
function deckKindLabel(kind){return ({ACTIVITY:"活动",ALARM:"闹钟",DEADLINE:"截止",MEMO:"备忘"})[kind]||"事件"}
function renderDeckHeader(deck,form){
  const card=(deck.cards||[]).find(item=>String(item.draftId)===String(deck.currentId));
  const header=el("div",null,"deck-card-header");
  const sequence=Number(card?.eventSeq);
  header.append(el("span",Number.isSafeInteger(sequence)&&sequence>0?
    "#"+String(sequence).padStart(4,"0"):(deck.transient?"新建":"草稿"),"deck-card-sequence"));
  header.append(el("span",deckKindLabel(form.kind)+(deck.aiLocked===true?" ▾":""),"deck-card-kind"));
  const operation=String(card?.operationLabel||({CREATE:"新建",REPLACE:"修改",CONVERT:"类型转换",DELETE:"删除"}[card?.operation])||
    (deck.transient?"新建":"待处理"));
  header.append(el("span",operation,"deck-card-operation"));
  const toggle=button(expandedDeck?"⌃":"⌄",expandedDeck?"collapseDeck":"expandDeck","deck-expand-toggle");
  toggle.setAttribute("aria-label",expandedDeck?"收起详细编辑":"展开完整编辑");
  toggle.setAttribute("title",toggle.getAttribute("aria-label"));
  header.append(toggle);
  return header;
}
function renderDeckSummary(form,deck){
  const summary=button("","expandDeck","deck-summary");
  summary.setAttribute("aria-label",deck.aiLocked===true?
    "卡组只读，轻点卡片可浏览详情":"轻点卡片，展开完整编辑");
  summary.append(el("strong",form.title?.trim()||"给这段时间起个名字","deck-summary-title"));
  const time=el("span",null,"deck-summary-time "+String(form.kind||"").toLowerCase());
  const timeText=form.kind==="ALARM"?form.startText:
    form.kind==="ACTIVITY"?[form.startText,form.endText].filter(Boolean).join("\n"):
    form.kind==="DEADLINE"?[form.endText,"截止"].filter(Boolean).join(" · "):
    form.startText||"无日期备忘";
  time.textContent=timeText||"时间待定";
  summary.append(time);
  if(form.location?.trim())summary.append(el("span",form.location,"deck-summary-location"));
  if(form.notes?.trim())summary.append(el("span",form.notes,"deck-summary-notes"));
  summary.append(el("span",deck.aiLocked===true?"卡组只读 · 可浏览详情":"轻点卡片，展开完整编辑","deck-summary-hint"));
  return summary;
}
function render(){
  const editing=state.editing||{},editor=editing.editor||{},deck=editing.deck||{};
  const next=editor.open?"event":deck.open?"deck":"";
  const id=next==="event"?"event:"+editor.sessionId:
    next==="deck"?"deck:"+deck.currentId:"";
  if(next==="deck"){
    const expandedId=String(deck.currentId??"none")+":"+String(deck.transient===true);
    if(expandedId!==expandedDeckId){expandedDeckId=expandedId;expandedDeck=deck.transient===true}
    if(expandedDeckTarget&&String(deck.currentId)===expandedDeckTarget){
      expandedDeck=true;expandedDeckTarget="";
    }
  }else{expandedDeckId="";expandedDeck=false}
  if(!next){
    const panel=sheet.querySelector(".sheet-panel");
    if(panel)panel.append(actions);
    sheet.querySelector(".sheet-head")?.append($("sheetSave"));
    sheet.removeAttribute("data-mode");sheet.setAttribute("aria-label","编辑");
    fields.classList.remove("has-deck-search");
    const hint=$("deckGestureHint");if(hint)hint.hidden=true;
    mode="";identity="";fingerprint="";visible(false);return
  }
  visible(true);
  sheet.dataset.aiLocked=String(next==="deck"&&deck.aiLocked===true);
  const form=next==="event"?editor.form:deck.form;
  const deckView=next==="deck"?{
    query:deck.searchQuery,cards:(deck.cards||[]).map(card=>[card.draftId,card.draftVersion,card.title]),
    aiLocked:deck.aiLocked===true,busy:deck.busy===true,
    formal:(deck.formalMatches||[]).map(item=>[item.eventSeq,item.revision]),
    conflict:deck.conflict,restore:deck.restore,
    retired:(deck.retiredResults||[]).map(item=>[item.eventSeq,item.revision,item.title]),
    retiredOffset:deck.retiredOffset,retiredTotal:deck.retiredTotal,
    retiredLoaded:deck.retiredLoaded,
    completion:deck.completion,lastDiscardId:deck.lastDiscardId,
    position:deck.position,count:deck.cardCount,previous:deck.canGoPrevious,next:deck.canGoNext,
    truncated:deck.truncated,transient:deck.transient===true
  }:null;
  const current=JSON.stringify({form,deck:deckView,deckExpanded:expandedDeck,scope:editor.recurrenceScope,
    aiBatch:next==="deck"&&deck.aiLocked===true?{
      phase:state.aiBatch?.phase,active:state.aiBatch?.active===true,
      canCancel:state.aiBatch?.canCancel===true,scopeCount:state.aiBatch?.scopeCount,
      remainingCount:state.aiBatch?.remainingCount,progress:state.aiBatch?.progress
    }:null,
    requiresScope:editor.requiresRecurrenceScope,dirty:editor.dirty,
    awaitingConfirmation:editor.awaitingConfirmation});
  const focused=!!document.activeElement?.matches?.(
    "#sheetFields input,#sheetFields textarea,#sheetFields select,#sheetFields [contenteditable=true]");
  const changed=mode!==next||identity!==id||(!focused&&fingerprint!==current);
  mode=next;identity=id;$('skinSheet').dataset.mode=mode;
  if(!changed)return;
  fingerprint=current;
  fields.classList.toggle("has-deck-search",mode==="deck"&&!!(deck.searchQuery||"").trim());
  const panel=sheet.querySelector(".sheet-panel");
  if(panel)panel.append(actions);
  sheet.querySelector(".sheet-head")?.append($("sheetSave"));
  fields.replaceChildren();actions.replaceChildren();
  sheet.setAttribute("aria-label",mode==="event"?"事件编辑器":"待处理草稿卡组");
  $("sheetClose").textContent=mode==="deck"?"×":"‹";
  $("sheetClose").setAttribute("aria-label",mode==="deck"?"保存并退出待处理":"保存检查点并退出");
  sheet.querySelector(".deck-ai-stop-header")?.remove();
  if(mode==="deck"&&deck.aiLocked===true&&state.aiBatch?.active===true){
    const stop=button("AI 进行中 · 停止","cancelAiBatch","deck-ai-stop-header");
    stop.dataset.action="cancelAiBatch";
    stop.disabled=state.aiBatch.canCancel!==true;
    stop.setAttribute("aria-label","AI 进行中，停止批次");
    $("sheetClose").before(stop);
  }
  $("sheetTitle").textContent=mode==="event"?"编辑事件":deck.transient===true?"新建事件":"待处理";
  const deckCount=Number.isFinite(Number(deck.cardCount))?Number(deck.cardCount):(deck.cards||[]).length;
  const emptyDeck=mode==="deck"&&deck.transient!==true&&deck.currentId==null&&
    !deck.conflict&&!deck.restore&&!deck.completion&&deck.aiLocked!==true;
  $("sheetSubtitle").textContent=mode==="event"?
    "#"+(editor.eventSeq||"?")+" · "+(form?.kind||""):
    deck.transient===true?"只在修改后暂存":
    deck.aiLocked===true?"只读":
    deck.completion?"本次完成 · 发布 "+Number(deck.completion.published||0)+
      " · 丢弃 "+Number(deck.completion.discarded||0):
      String(deckCount)+" 张";
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
      if(deck.aiLocked===true){
        const batch=state.aiBatch||{};
        const authorized=Math.max(0,Number(batch.scopeCount)||0);
        const remaining=Math.max(0,Math.min(authorized,Number(batch.remainingCount)||0));
        const banner=el("section",null,"deck-ai-progress");
        banner.setAttribute("role","status");
        banner.setAttribute("aria-live","polite");
        const line=el("div",null,"deck-ai-progress-line");
        line.append(el("span","✦","deck-ai-progress-icon"));
        line.append(el("span",`本次授权 ${authorized} 张 · 仍待处理 ${remaining} 张`));
        if(batch.active===true){
          const stop=button("停止","cancelAiBatch","deck-ai-progress-stop");
          stop.dataset.action="cancelAiBatch";
          stop.disabled=batch.canCancel!==true;
          line.append(stop);
        }
        const progress=el("div",null,"deck-ai-progress-track");
        progress.setAttribute("aria-hidden","true");
        if(state.reducedMotion===true)progress.dataset.reducedMotion="true";
        banner.append(line,progress,el("p","期间卡组为只读；已提交的结果会保留，未处理的卡片继续等待你。"));
        fields.append(banner);
      }
      const search=el("form",null,"deck-search");
      search.setAttribute("role","search");
      const searchLabel=el("label","搜索草稿和正式事件","sheet-label");
      const input=el("input");input.type="search";input.maxLength=120;
      input.autocomplete="off";input.value=deck.searchQuery||"";
      input.placeholder="查找草稿标题或事件序号";
      input.setAttribute("aria-label","搜索草稿标题或正式事件标题、序号");
      const submit=el("button","查找","sheet-secondary");submit.type="submit";
      submit.setAttribute("aria-label","查找草稿或正式事件");
      search.append(searchLabel,input,submit);
      search.addEventListener("submit",event=>{
        event.preventDefault();
        input.blur();
        void perform("deck.search",{query:input.value.trim().slice(0,120)});
      });
      fields.append(search);

      const formal=deck.formalMatches||[];
      let formalResults=null;
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
          formalResults=results;
        }
      }

      const nav=el("nav",null,"sheet-cards");
      nav.setAttribute("aria-label","待处理草稿；选择卡片继续编辑");
      for(const card of deck.cards||[]){
        const b=button("","select","sheet-card deck-search-result");
        b.append(el("span","#"+String(card.eventSeq).padStart(4,"0"),"deck-search-sequence"));
        b.append(el("span",card.title||"未命名草稿","deck-search-title"));
        b.append(el("span","草稿","deck-search-type"));
        b.dataset.draftId=card.draftId;
        if(card.draftId===deck.currentId)b.classList.add("selected");
        nav.append(b);
      }
      if(deck.cards?.length&&(deck.searchQuery||"").trim())fields.append(nav);
      if(formalResults)fields.append(formalResults);
      if(deck.searchTruncated)fields.append(el("p","仅显示前 4 条匹配草稿。","sheet-note"));
      else if(deck.truncated)fields.append(el("p","草稿列表仅显示前 80 张；使用搜索查找其他草稿。","sheet-note"));
    }
    if(form&&!deck.conflict&&!deck.restore){
      fields.append(renderDeckHeader(deck,form));
      if(!deck.transient&&!expandedDeck)fields.append(renderDeckSummary(form,deck));
      else {
        renderForm(form,false,deck.aiLocked===true);
        if(deck.aiLocked===true||deck.busy===true){
          fields.querySelectorAll(".sheet-row input,.sheet-row textarea").forEach(control=>control.readOnly=true);
          fields.querySelectorAll(".sheet-row select").forEach(control=>control.disabled=true);
        }
      }
    }
    else if(emptyDeck){
      const empty=el("section",null,"deck-empty-state");
      empty.setAttribute("aria-label","没有待处理卡片");
      empty.append(el("h3","没有待处理卡片"));
      empty.append(el("p","所有草稿都已处理。新的自然语言、截图或 AI 建议会重新出现在这里。"));
      empty.append(button("返回时间轴","close","deck-empty-return"));
      fields.append(empty);
    }else if(!transient&&deck.currentId==null&&!deck.conflict&&!deck.restore){
      const completion=deck.completion;
      const status=el("section",null,"deck-review-snapshot deck-completion");
      status.setAttribute("aria-label",completion?"本次草稿处理结果":"草稿卡组状态");
      status.append(el("h3",completion?"本次处理已完成":"当前没有待处理草稿","sheet-label"));
      if(completion){
        status.append(el("p","已发布 "+Number(completion.published||0)+
          " 张 · 已丢弃 "+Number(completion.discarded||0)+
          " 张 · 冲突 "+Number(completion.conflicts||0)+" 张","sheet-note"));
      }else status.append(el("p","你可以关闭卡组；待处理草稿会在下次打开时重新载入。","sheet-note"));
      fields.append(status);
    }else if(!transient&&!deck.cards?.length&&!(deck.formalMatches||[]).length)
      fields.append(el("p","当前没有可编辑的待处理草稿。","sheet-note"));

    if(deck.retiredLoaded===true&&Number(deck.retiredTotal)>0&&!deck.conflict&&!deck.restore){
      fields.append(el("h3","已删除事件","sheet-label"));
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

    if(!deck.conflict&&!deck.restore&&!transient&&(deck.currentId!=null||Number(deck.cardCount)>1)){
      const paging=el("nav",null,"sheet-deck-navigation");
      paging.setAttribute("aria-label","草稿导航");
      const previous=button("上一张","jump","sheet-secondary");
      previous.dataset.steps="-1";previous.disabled=deck.canGoPrevious!==true;
      previous.setAttribute("aria-label","上一张草稿");
      const count=el("span",String(deck.position||0)+" / "+String(deck.cardCount||0)+
        " · "+(deck.dirty?"未暂存":"已暂存"));
      const nextButton=button("下一张","jump","sheet-secondary");
      nextButton.dataset.steps="1";nextButton.disabled=deck.canGoNext!==true;
      nextButton.setAttribute("aria-label","下一张草稿");
      paging.append(previous,count,nextButton);actions.append(paging);
    }
    if(!deck.conflict&&!deck.restore){
      if(deck.currentId!=null){
        const discard=button("丢弃","discard","sheet-danger");
        discard.disabled=deck.aiLocked===true||deck.busy===true;
        actions.append(discard);
      }
      if(deck.lastDiscardId!=null){
        const undo=button("撤销丢弃","undo","sheet-secondary");
        undo.disabled=deck.aiLocked===true||deck.busy===true;
        actions.append(undo);
      }
      if(!emptyDeck)actions.append(button("完成并退出","close","sheet-secondary"));
    }else actions.append(button("关闭","close","sheet-secondary"));
  }
  if(mode==="deck"){
    const search=fields.querySelector(".deck-search");
    const aiProgress=fields.querySelector(".deck-ai-progress");
    const searchMode=!!(deck.searchQuery||"").trim();
    const searchResults=searchMode?
      Array.from(fields.querySelectorAll(".deck-formal-results,.sheet-cards")):[];
    const content=el("div",null,"deck-card-content "+(expandedDeck?"is-expanded":"is-collapsed"));
    for(const child of Array.from(fields.children))
      if(child!==search&&child!==aiProgress&&!searchResults.includes(child))content.append(child);
    const surface=el("div",null,"deck-card-surface");
    surface.dataset.stackDepth=String(Math.max(0,Math.min(2,deckCount-1)));
    surface.dataset.expanded=String(expandedDeck||deck.transient===true);
    surface.dataset.aiLocked=String(deck.aiLocked===true);
    surface.dataset.empty=String(emptyDeck);
    if(deck.transient!==true&&!searchMode)
      content.querySelectorAll(".sheet-cards").forEach(node=>node.hidden=true);
    surface.append(content);
    actions.append($("sheetSave"));
    surface.append(actions);
    const stage=el("div",null,"deck-stage");stage.append(surface);
    fields.replaceChildren();
    if(aiProgress)fields.append(aiProgress);
    if(search)fields.append(search);
    searchResults.forEach(result=>fields.append(result));
    fields.append(stage);
    const hint=$("deckGestureHint");if(hint)hint.hidden=deck.transient===true;
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
  if(saveQueued)return;
  const e=state.editing||{};
  let request=null;
  if(mode==="event"&&e.editor?.sessionId)
    request=perform("editor.save",{sessionId:e.editor.sessionId});
  else if(mode==="deck"&&e.deck?.currentId!=null)
    request=perform("deck.commit",{draftId:e.deck.currentId,draftVersion:e.deck.version});
  if(request){
    saveQueued=true;
    void request.then(()=>{saveQueued=false},()=>{saveQueued=false});
  }
});
async function close(){
  if(busy)return;
  const e=state.editing||{};
  if(mode==="event"&&e.editor?.sessionId)
    await perform("editor.close",{sessionId:e.editor.sessionId});
  else if(mode==="deck")await perform("deck.close");
}
$("sheetShade").addEventListener("pointerdown",beginDeckGesture,true);
fields.addEventListener("pointerdown",beginDeckGesture,true);
document.addEventListener("pointerdown",event=>{
  if(activeDeckGesture&&event.pointerId!==activeDeckGesture.pointerId)
    activeDeckGesture.blocked=true;
},true);
document.addEventListener("pointermove",moveDeckGesture,{capture:true,passive:false});
document.addEventListener("pointerup",event=>finishDeckGesture(event,false),true);
document.addEventListener("pointercancel",event=>finishDeckGesture(event,true),true);
document.addEventListener("click",event=>{
  const pendingClick=suppressedDeckClick;
  if(!pendingClick||performance.now()>pendingClick.expires)return;
  const samePointer=Number.isInteger(event.pointerId)&&event.pointerId===pendingClick.pointerId;
  const fallback=event.detail>0&&performance.now()-pendingClick.created<100&&
    pendingClick.root?.contains(event.target);
  if(!samePointer&&!fallback)return;
  suppressedDeckClick=null;
  event.preventDefault();event.stopImmediatePropagation();
},true);
function beginDeckGesture(event){
  if(mode!=="deck"||event.button!==0)return;
  if(activeDeckGesture){
    if(event.pointerId!==activeDeckGesture.pointerId)activeDeckGesture.blocked=true;
    return;
  }
  const surface=event.target.closest?.(".deck-card-surface");
  const shade=event.target.closest?.("#sheetShade");
  if(!surface&&!shade)return;
  if(surface&&event.target.closest?.(
    'input,textarea,select,[contenteditable="true"],a,[data-no-deck-swipe],button:not(.deck-summary)'))return;
  const root=surface||shade;
  activeDeckGesture={
    type:surface?"card":"outside",pointerId:event.pointerId,root,surface,
    startX:event.clientX,startY:event.clientY,x:event.clientX,y:event.clientY,
    dx:0,dy:0,claimed:false,blocked:false,
    samples:[{x:event.clientX,time:event.timeStamp}]
  };
}
function moveDeckGesture(event){
  const gesture=activeDeckGesture;
  if(!gesture||event.pointerId!==gesture.pointerId)return;
  if(!event.isPrimary){gesture.blocked=true;resetDeckGestureVisual(gesture);return}
  gesture.x=event.clientX;gesture.y=event.clientY;
  gesture.dx=gesture.x-gesture.startX;gesture.dy=gesture.y-gesture.startY;
  gesture.samples.push({x:gesture.x,time:event.timeStamp});
  while(gesture.samples.length>2&&event.timeStamp-gesture.samples[0].time>120)
    gesture.samples.shift();
  if(gesture.blocked)return;
  const horizontal=Math.abs(gesture.dx),vertical=Math.abs(gesture.dy);
  if(!gesture.claimed&&vertical>8&&vertical>horizontal){
    gesture.blocked=true;return;
  }
  if(!gesture.claimed&&horizontal>11.2&&horizontal>vertical*1.4){
    gesture.claimed=true;
    try{gesture.root.setPointerCapture(event.pointerId)}catch{}
  }
  if(!gesture.claimed)return;
  event.preventDefault();
  if(gesture.surface){
    gesture.surface.classList.add("deck-dragging");
    gesture.surface.style.setProperty("--deck-drag-x",`${Math.max(-24,Math.min(24,gesture.dx*.14))}px`);
  }
}
function resetDeckGestureVisual(gesture){
  if(!gesture?.surface)return;
  gesture.surface.classList.remove("deck-dragging");
  gesture.surface.style.setProperty("--deck-drag-x","0px");
}
function finishDeckGesture(event,cancelled){
  const gesture=activeDeckGesture;
  if(!gesture||event.pointerId!==gesture.pointerId)return;
  activeDeckGesture=null;
  resetDeckGestureVisual(gesture);
  if(cancelled||gesture.blocked||!gesture.claimed)return;
  const horizontal=Math.abs(gesture.dx),vertical=Math.abs(gesture.dy);
  const minimum=gesture.type==="card"?72:60;
  if(horizontal<minimum||horizontal<=vertical*1.25)return;
  const direction=gesture.dx<0?1:-1;
  if(gesture.type==="card"){
    const control=document.querySelector(`.sheet-deck-navigation [data-steps="${direction}"]`);
    if(!control||control.disabled)return;
    suppressedDeckClick={pointerId:event.pointerId,root:gesture.surface,
      created:performance.now(),expires:performance.now()+500};
    void perform("deck.jump",{steps:direction});
    return;
  }
  const samples=gesture.samples;
  let velocityX=0;
  if(samples.length>=2){
    const first=samples[0],last=samples[samples.length-1];
    const elapsed=last.time-first.time;
    if(elapsed>0)velocityX=((last.x-first.x)/elapsed)*1000*(window.devicePixelRatio||1);
  }
  const fling=Math.abs(velocityX)>900;
  const steps=direction*(fling?Math.max(1,Math.floor(horizontal/110)):1);
  suppressedDeckClick={pointerId:event.pointerId,root:gesture.root,
    created:performance.now(),expires:performance.now()+500};
  void perform("deck.jump",{steps:Math.max(-8,Math.min(8,steps))});
}
$("sheetClose").addEventListener("click",()=>void close());
$("sheetShade").addEventListener("click",()=>void close());
fields.addEventListener("click",event=>{
  const b=event.target.closest("[data-do]");if(!b||busy)return;
  if(b.dataset.do==="expandDeck"||b.dataset.do==="collapseDeck"){
    expandedDeck=b.dataset.do==="expandDeck";
    fingerprint="";render();
  }else if(b.dataset.do==="close")void close();
  else if(b.dataset.do==="select"&&b.dataset.draftId)
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
