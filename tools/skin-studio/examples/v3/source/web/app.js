(() => {"use strict";
  let port=null;
  const $=id=>document.getElementById(id);
  const send=(name,extra={})=>{
    if(!port)return;
    port.postMessage(JSON.stringify({version:1,type:"action",payload:{name,...extra}}));
  };
  window.addEventListener("message",event=>{
    if(event.data==="powercal-port"&&event.ports?.[0]){
      port=event.ports[0];
      port.onmessage=e=>receive(e.data);
    }
  });
  document.addEventListener("click",event=>{
    const button=event.target.closest("[data-action]");
    if(!button||button.disabled)return;
    const name=button.dataset.action,extra={};
    if(name==="openEvent"){
      extra.eventSeq=Number(button.dataset.eventSeq);
      extra.expectedRevision=Number(button.dataset.revision);
    }
    if(name==="invokeOption")extra.id=button.dataset.optionId;
    if(name==="panTimeline")extra.deltaMinutes=Number(button.dataset.deltaMinutes);
    if(name==="jumpTimelineDay")extra.date=button.dataset.date;
    send(name,extra);
  });
  function receive(raw){
    const message=typeof raw==="string"?JSON.parse(raw):raw;
    if(message.version!==1||message.type!=="state")return;
    const state=message.payload||{};
    $("pending").textContent=state.pendingCount||"";
    $("pageTitle").textContent=state.page==="TIMELINE"
      ?"时间轴 · 最左侧刻度":String(state.page||"").toUpperCase();
    const time=state.timeline,items=time?.items||[];
    const ruler=$("ruler"),tracks=$("tracks");
    ruler.replaceChildren();tracks.replaceChildren();
    if(time){
      const from=time.visibleStartMillis,to=time.visibleEndMillis,span=Math.max(1,to-from);
      const y=ms=>100*(ms-from)/span;
      for(let i=0;i<=6;i++){
        const when=from+span*i/6;
        const tick=document.createElement("div");tick.className="tick";
        tick.style.top=(i*100/6)+"%";
        tick.textContent=new Date(when).toLocaleTimeString("zh-CN",
          {timeZone:time.zoneId||"UTC",hour:"2-digit",minute:"2-digit",hour12:false});
        ruler.append(tick);
      }
      const visible=items.filter(v=>v.startMillis!=null&&
        v.startMillis<=to&&(v.endMillis==null||v.endMillis>=from));
      const maxLane=Math.max(0,...visible.map(x=>x.lane||0));
      tracks.style.minWidth=Math.max(0,(maxLane+1)*92)+"px";
      for(const v of visible){
        const position=Math.max(0,Math.min(100,y(v.startMillis)));
        const end=v.endMillis==null?v.startMillis:v.endMillis;
        const height=Math.max(4,Math.min(100-position,y(end)-y(v.startMillis)));
        const card=document.createElement("button");
        card.className="event "+String(v.kind||"").toLowerCase();
        card.type="button";
        card.style.top=position+"%";
        card.style.height=height+"%";
        card.style.left=((v.lane||0)*92+4)+"px";card.style.width="85px";
        card.dataset.action="openEvent";
        card.dataset.eventSeq=String(v.eventSeq);
        card.dataset.revision=String(v.revision);
        card.textContent=v.title;
        tracks.append(card);
      }
      const line=document.createElement("div");line.className="nowLine";
      line.style.top=y(time.nowMillis)+"%";
      tracks.append(line);
      if(time.truncated){
        const note=document.createElement("div");note.textContent="可见项目超过显示上限";
        note.style.cssText="position:absolute;bottom:4px;right:4px;font-size:11px";
        tracks.append(note);
      }
    }
    const groups=$("optionGroups");groups.replaceChildren();
    for(const group of state.optionGroups||[]){
      const list=document.createElement("div");list.className="optionGroup";
      list.dataset.groupId=group.id;
      for(const item of group.items||[]){
        const row=document.createElement("button");row.className="optionRow";
        row.type="button";row.dataset.action="invokeOption";row.dataset.optionId=item.id;
        row.disabled=item.enabled===false;
        const label=document.createElement("span");label.textContent=item.label;
        const detail=document.createElement("small");detail.textContent=item.detail||"";
        row.append(label,detail);list.append(row);
      }
      groups.append(list);
    }
    const isTimeline=state.page==="TIMELINE";
    $("timeline").hidden=!isTimeline;
    $("otherPage").hidden=isTimeline;
    if(!isTimeline) $("otherPage").textContent="该页面业务内容由 PowerCal 宿主管理。当前 v3 Bridge 尚未开放本页的完整数据和编辑权限。";
  }
  window.PowerCalSkin={receive};
})();
