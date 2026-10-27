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
      restored:"已恢复",flushed:"修改已暂存",closed:"已安全退出",
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
  const current=JSON.stringify({form,scope:editor.recurrenceScope,
    requiresScope:editor.requiresRecurrenceScope,dirty:editor.dirty,
    awaitingConfirmation:editor.awaitingConfirmation});
  const focused=!!document.activeElement?.closest?.("#sheetFields");
  const changed=mode!==next||identity!==id||(!focused&&fingerprint!==current);
  mode=next;identity=id;
  if(!changed)return;
  fingerprint=current;
  fields.replaceChildren();actions.replaceChildren();
  $("sheetTitle").textContent=mode==="event"?"编辑事件":"待处理草稿";
  $("sheetSubtitle").textContent=mode==="event"?
    "#"+(editor.eventSeq||"?")+" · "+(form?.kind||""):
    String(deck.cards?.length||0)+" 张 · "+(deck.dirty?"尚未暂存":"已暂存");
  $("sheetSave").textContent=mode==="event"?"保存":"发布";
  const requiresScope=mode==="event"&&editor.requiresRecurrenceScope===true;
  const scope=editor.recurrenceScope||"";
  $("sheetSave").disabled=!form||deck.aiLocked===true||editor.awaitingConfirmation===true||
    (requiresScope&&!scope);
  if(form&&(!requiresScope||scope))renderForm(form,scope==="SINGLE_OCCURRENCE");
  else if(requiresScope)fields.append(el("p","请先选择编辑范围，再修改字段或保存。","sheet-note"));
  if(mode==="event"){
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
    const nav=el("nav",null,"sheet-cards");
    for(const card of deck.cards||[]){
      const b=button(card.title||"未命名草稿","select","sheet-card");
      b.dataset.draftId=card.draftId;
      if(card.draftId===deck.currentId)b.classList.add("selected");
      nav.append(b);
    }
    fields.prepend(nav);
    actions.append(button("暂存","flush","sheet-secondary"));
    actions.append(button("丢弃草稿","discard","sheet-danger"));
    if(deck.lastDiscardId!=null)actions.append(button("撤销丢弃","undo","sheet-secondary"));
    actions.append(button("完成并退出","close","sheet-secondary"));
    if(deck.hasConflict)actions.append(el("p",
      "发现版本冲突，需由宿主核对审阅快照后确认覆盖","sheet-warning"));
    if(deck.hasRestore)actions.append(el("p",
      "原事件已经删除，恢复需要受保护的宿主确认","sheet-warning"));
  }
}
function onState(next){
  state=next;
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
actions.addEventListener("click",event=>{
  const b=event.target.closest("[data-do]");if(!b||busy)return;
  const deck=state.editing?.deck||{};
  const editor=state.editing?.editor||{};
  switch(b.dataset.do){
    case "scope":if(editor.sessionId&&b.dataset.scope)
      void perform("editor.scope",{sessionId:editor.sessionId,scope:b.dataset.scope});break;
    case "discardChanges":if(editor.sessionId)
      void perform("editor.discardChanges",{sessionId:editor.sessionId});break;
    case "deleteEvent":if(editor.sessionId&&!editor.awaitingConfirmation)
      void perform("editor.delete",{sessionId:editor.sessionId});break;
    case "close":void close();break;
    case "select":if(b.dataset.draftId)
      void perform("deck.select",{draftId:Number(b.dataset.draftId)});break;
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
window.PowerCalEdit={onState,onResult,routeAction,resetTransport};
})();
