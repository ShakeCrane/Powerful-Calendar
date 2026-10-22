"use strict";
// Visual *design* project only. Android ABI v1 never accepts page layout or motion.
const COLORS = ["primary","onPrimary","primaryContainer","onPrimaryContainer","secondary","onSecondary","tertiary","onTertiary","background","onBackground","surface","onSurface","surfaceContainerLow","surfaceContainer","surfaceContainerHigh","outline","alarm","activity","deadline","nowLine","gridLine","glassFill","glassBorder","glassHighlight"];
const ASSETS = ["TIMELINE_BACKGROUND","DECK_BACKDROP","MONTH_BACKGROUND","COMPLETION_EMBLEM","BRAND_MARK","ALARM_MARKER","ACTIVITY_BADGE","DEADLINE_MARKER","AI_AVATAR","SETTINGS_ICON"];
const GEOM = {cardRadiusDp:[12,40,28],barRadiusDp:[8,32,20],pointLineDp:[1,5,2],fontScale:[.85,1.2,1]};
const SCENES = {
  TIMELINE:{label:"时间轴",slots:[
    ["header","顶部品牌","header",true,null],["today","今日","header",true,"GO_TODAY"],["memo","Memo 入口","header",true,"OPEN_MEMO"],["deck","待办 / 草稿","header",true,"OPEN_DECK"],["create","新建","header",true,"CREATE_DRAFT"],
    ["axis","时间轴刻度","content",true,"RESET_VIEWPORT"],["alarm","左侧闹钟","content",true,"OPEN_ALARM"],["activity","活动区间","content",true,"OPEN_ACTIVITY"],["deadline","截止点","content",true,"OPEN_DEADLINE"],["backdrop","装饰背景","background",false,null],["status","状态信息","footer",true,null]]},
  MONTH:{label:"月历",slots:[["monthHeader","月历标题","header",true,null],["monthNav","月份跳转","header",true,"SELECT_MONTH"],["monthGrid","日期网格","content",true,"SELECT_DATE"],["daySummary","当日摘要","content",true,"OPEN_EVENT"],["monthDecor","月历背景装饰","background",false,null]]},
  MEMO:{label:"Memo",slots:[["memoHeader","Memo 标题","header",true,null],["memoCreate","新建 Memo","header",true,"CREATE_MEMO"],["memoItems","Memo 清单","content",true,"OPEN_MEMO_ITEM"],["memoDone","完成操作","content",true,"TOGGLE_MEMO"],["memoDecor","背景装饰","background",false,null]]},
  AI:{label:"AI",slots:[["chatHeader","AI 标题","header",true,null],["messages","对话消息","content",true,"OPEN_MESSAGE"],["proposal","AI 建议草稿","content",true,"REVIEW_DRAFT"],["send","发送输入","footer",true,"SUBMIT_CHAT"],["aiDecor","对话背景","background",false,null]]},
  SETTINGS:{label:"设置",slots:[["settingsHeader","设置标题","header",true,null],["skinSwitch","皮肤切换","content",true,"SELECT_SKIN"],["aiSettings","AI 配置","content",true,"OPEN_AI_SETTINGS"],["backup","数据备份","content",true,"OPEN_BACKUP"],["settingsDecor","设置装饰","background",false,null]]},
  DECK:{label:"草稿卡组",slots:[["deckTitle","待办标题","header",true,null],["draftCard","草稿内容","content",true,"OPEN_DRAFT"],["diff","更改对照","content",true,"REVIEW_CHANGES"],["commit","确认发布","footer",true,"CONFIRM_DRAFT"],["cancel","取消编辑","footer",true,"CANCEL_DRAFT"],["deckDecor","卡组背景","background",false,null]]}
};
const basePalette={
  light:{primary:"#2457D6",onPrimary:"#FFFFFF",background:"#F3F5FA",onBackground:"#11151C",surface:"#FFFFFF",onSurface:"#11151C",outline:"#6B7485",alarm:"#3D5FD8",activity:"#177A63",deadline:"#A8501C",glassFill:"#CCFFFFFF"},
  dark:{primary:"#9DB8FF",onPrimary:"#0B2A66",background:"#0A0D13",onBackground:"#E6EAF2",surface:"#12161E",onSurface:"#E6EAF2",outline:"#8A93A3",alarm:"#9DB8FF",activity:"#7FD6C2",deadline:"#FFB68C",glassFill:"#B31A2130"}
};
const copy=x=>JSON.parse(JSON.stringify(x));
const $=id=>document.getElementById(id);
const views=Object.fromEntries(Object.entries(SCENES).map(([key, scene])=>[key,scene.slots.map(([id,label,zone,required,intent])=>({id,label,zone,required,intent,visible:true,motion:"none",duration:200}))]));
let design={format:"powercal-skin-studio",schemaVersion:1,compiled:{pages:copy(views)}};
let manifest={format:"powercal-skin",schemaVersion:1,id:"new-powercal-skin",name:"My PowerCal Skin",light:copy(basePalette.light),dark:copy(basePalette.dark),
  layout:Object.fromEntries(Object.entries(GEOM).map(([k,v])=>[k,v[2]])),imagesLight:{},imagesDark:{},sha256:{}};
let images={}; let currentPage="TIMELINE"; let mode="light"; let reduced=false; let selectedBlock="header";
let logCount=0;
function notify(message,error=false){$("toast").textContent=message;$("toast").classList.toggle("error",error)}
function make(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e}
function download(blob,name){const url=URL.createObjectURL(blob);const a=document.createElement("a");a.href=url;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),2000)}
async function api(path,data,binary=false) {
  const res=await fetch(path,{method:"POST",headers:{"Content-Type":binary?"application/octet-stream":"application/json"},body:binary?data:JSON.stringify(data)});
  if(!res.ok){let message="请求失败";try{message=(await res.json()).error||message}catch{};throw Error(message)}
  return path==="/api/export"||path==="/api/export-v3"?res.blob():res.json();
}
function showError(error){notify(error?.message||String(error),true)}
function rgba(hex){return hex.length===9?"#"+hex.slice(3):hex}
function updateColors(){
  const style=$("phone").style,p=manifest[mode];
  [["--p-primary","primary","#2457D6"],["--p-on-primary","onPrimary","#FFFFFF"],["--p-bg","background",mode==="dark"?"#0A0D13":"#F3F5FA"],
   ["--p-text","onBackground",mode==="dark"?"#E6EAF2":"#11151C"],["--p-card","surface",mode==="dark"?"#12161E":"#FFFFFF"],
   ["--p-outline","outline",mode==="dark"?"#8A93A3":"#C3CAD8"],["--p-alarm","alarm","#3D5FD8"],
   ["--p-activity","activity","#177A63"],["--p-deadline","deadline","#A8501C"]].forEach(([css,key,def])=>style.setProperty(css,p[key]?rgba(p[key]):def));
  style.setProperty("--p-radius",manifest.layout.cardRadiusDp+"px");
  style.setProperty("--p-soft",mode==="dark"?"#26384A":"#E5EBFC");
  style.fontSize=(14*manifest.layout.fontScale)+"px";
}
function renderPalette(){
  const root=$("palette");root.replaceChildren();
  for(const key of COLORS){
    const row=make("div",undefined,"paletteRow");
    const lbl=make("span",key),color=make("input"),value=make("input");
    color.type="color";color.value=rgba(manifest[mode][key]||"#8899aa");
    value.type="text";value.value=manifest[mode][key]||"";value.placeholder="默认 / 留空";
    const sync=v=>{if(v&& !/^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$/.test(v)){notify(key+" 颜色格式错误",true);return}
      if(v)manifest[mode][key]=v;else delete manifest[mode][key];
      value.value=v;color.value=rgba(v||"#8899aa");updateColors()};
    color.addEventListener("input",()=>sync(color.value));value.addEventListener("change",()=>sync(value.value.trim()));
    row.append(lbl,color,value);root.append(row);
  }
}
function renderGeometry(){
  $("geometry").replaceChildren();
  for(const [key,range] of Object.entries(GEOM)){
    const label=make("label",key,"gRow"),input=make("input");input.type="number";input.min=range[0];input.max=range[1];input.step=key==="fontScale"?"0.01":"0.5";input.value=manifest.layout[key]??range[2];
    input.addEventListener("change",()=>{const n=Number(input.value);if(!Number.isFinite(n)||n<range[0]||n>range[1]){notify(key+" 超出 ABI v1 限值",true);input.value=manifest.layout[key];return}manifest.layout[key]=n;updateColors()});
    label.append(input);$("geometry").append(label);
  }
}
function renderTabs(){
 $("pageTabs").replaceChildren();
 for(const [page,spec] of Object.entries(SCENES)){
  const b=make("button",spec.label,page===currentPage?"active":"");b.onclick=()=>{currentPage=page;selectedBlock=design.compiled.pages[page][0].id;renderTabs();renderStructure();renderMotion();renderScene()};
  $("pageTabs").append(b);
 }
}
function sceneBlock(slot){
 const node=make("div",undefined,"visualBlock area-"+slot.zone+" motion-"+(!reduced?slot.motion:"none"));
 node.style.setProperty("--motion-duration",slot.duration+"ms");
 node.append(make("strong",slot.label));
 node.append(make("span",slot.zone,"tag"));
 if(slot.id==="axis"){
  const axis=make("div",undefined,"ruler");
  axis.append(make("span","08:00\n09:00\n10:00\n11:00"),make("div","● 09:10 闹钟","alarm"),make("div","10:00 – 11:30 活动","activity"),make("div","◆ Deadline","deadline"));
  node.append(axis);
 }else if(slot.intent){
  const button=make("button",slot.intent);button.onclick=()=>{
   $("intentLog").prepend(make("div",++logCount+" · "+currentPage+" / "+slot.id+" → "+slot.intent+"（预览，无真实操作）"));
   if($("intentLog").children.length>20)$("intentLog").lastElementChild.remove();
  };node.append(button);
 }else node.append(make("small","可替换的纯视觉组件"));
 return node;
}
function renderScene(){
 updateColors();const root=$("scene");root.replaceChildren();
 const slots=design.compiled.pages[currentPage];
 // Fixed semantic grouping for wireframe only; a Compose renderer may lay out these freely.
 const zones=["background","header","content","overlay","footer"];
 for(const zone of zones)for(const s of slots)if(s.visible&&s.zone===zone)root.append(sceneBlock(s));
}
function renderStructure(){
 $("slots").replaceChildren();const slots=design.compiled.pages[currentPage];
 slots.forEach((slot,i)=>{
  const row=make("div",undefined,"slotRow"),left=make("div"),right=make("div",undefined,"cmd");
  left.append(make("b",slot.label),make("small",slot.id+" · "+(slot.required?"必备":"可选")));
  const up=make("button","↑"),down=make("button","↓");up.disabled=i===0;down.disabled=i===slots.length-1;
  up.onclick=()=>{[slots[i-1],slots[i]]=[slots[i],slots[i-1]];renderStructure();renderScene()};
  down.onclick=()=>{[slots[i+1],slots[i]]=[slots[i],slots[i+1]];renderStructure();renderScene()};
  const region=make("select");for(const zone of ["background","header","content","overlay","footer"]){const opt=make("option",zone);opt.value=zone;region.append(opt)}region.value=slot.zone;region.title="渲染区域";
  region.onchange=()=>{slot.zone=region.value;renderScene()};
  const toggle=make("button",slot.visible?"可见":"隐藏");toggle.disabled=slot.required;
  toggle.title=slot.required?"必要语义/功能槽不得隐藏":"切换纯装饰槽";
  toggle.onclick=()=>{slot.visible=!slot.visible;renderStructure();renderScene()};
  const select=make("button","⚙");select.title="编辑动效";select.onclick=()=>{selectedBlock=slot.id;renderMotion()};
  right.append(up,down,region,toggle,select);row.append(left,right);$("slots").append(row);
 });
}
function renderMotion(){
 const list=design.compiled.pages[currentPage];const sel=$("motionTarget");sel.replaceChildren();
 for(const slot of list){const opt=make("option",slot.label);opt.value=slot.id;sel.append(opt)}
 if(!list.some(s=>s.id===selectedBlock))selectedBlock=list[0].id;
 sel.value=selectedBlock;
 const current=list.find(s=>s.id===selectedBlock);
 $("motionPreset").value=current.motion;$("motionDuration").value=current.duration;$("durationValue").textContent=current.duration;
}
function assetStatus(){
 const group=$("assetMode").value,slot=$("assetSlot").value,path=manifest[group][slot];
 $("assetStatus").textContent=path?(path+" · 已嵌入，打包时计算 SHA-256"):"该槽尚未设置，Android 将回退原生素材";
}
async function addAsset(file){
 if(!file)return;
 try{
  if(file.size>3_000_000)throw Error("单张图片不能超过 3MB");
  const ext=file.type==="image/png"?"png":file.type==="image/webp"?"webp":null;
  if(!ext)throw Error("仅支持 PNG / WebP");
  const bytes=new Uint8Array(await file.arrayBuffer());
  const base64=await new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result.split(",")[1]);r.onerror=()=>reject(Error("无法读取"));r.readAsDataURL(new Blob([bytes],{type:file.type}))});
  const group=$("assetMode").value,slot=$("assetSlot").value;
  const path="images/"+slot.toLowerCase()+"_"+(group==="imagesLight"?"light":"dark")+"."+ext;
  const old=manifest[group][slot];delete manifest[group][slot];
  if(old&&![...Object.values(manifest.imagesLight),...Object.values(manifest.imagesDark)].includes(old))delete images[old];
  manifest[group][slot]=path;images[path]=base64;assetStatus();notify("图片已读取，导出前将在服务端校验文件头、像素、哈希");
 }catch(e){showError(e)}
}
function payload(){return{manifest:copy(manifest),images:{...images}}}
function applyManifest(obj){
 if(!obj||obj.format!=="powercal-skin"||obj.schemaVersion!==1)throw Error("不是 PowerCal 皮肤 v1");
 manifest={...obj,layout:{cardRadiusDp:28,barRadiusDp:20,pointLineDp:2,fontScale:1,...(obj.layout||{})},imagesLight:obj.imagesLight||{},imagesDark:obj.imagesDark||{},light:obj.light||{},dark:obj.dark||{}};$("skinId").value=obj.id;$("skinName").value=obj.name;
 renderPalette();renderGeometry();renderScene();assetStatus();
}
function pickProject(data){
 if(data?.format!=="powercal-skin-studio"||data.schemaVersion!==1||!data.portable||!data.compiled?.pages)throw Error("不支持的 Skin Studio 项目");
 // Import a design document as data, not executable code; rehydrate only known slots.
 const compiled={pages:{}};
 for(const [page,config] of Object.entries(SCENES)){
  const existing=data.compiled.pages[page];if(!Array.isArray(existing))throw Error("缺少页面 "+page);
  const known=config.slots;
  if(existing.length!==known.length)throw Error(page+" 槽位数量不匹配");
  const seen=new Set();
  compiled.pages[page]=existing.map((s)=>{
   const template=known.find(row=>row[0]===s.id);
   if(!template||seen.has(s.id))throw Error(page+" 未知/重复槽位");
   seen.add(s.id);
   const zone=["background","header","content","overlay","footer"].includes(s.zone)?s.zone:template[2];
   const motion=["none","fade","slide","scale"].includes(s.motion)?s.motion:"none";
   const duration=Number(s.duration);
   return{id:template[0],label:template[1],required:template[3],intent:template[4],zone,
          visible:template[3]?true:s.visible!==false,motion,duration:Number.isFinite(duration)?Math.max(0,Math.min(600,duration)):200};
  });
 }
 design={format:"powercal-skin-studio",schemaVersion:1,compiled};
 images={...data.images};applyManifest(data.portable);renderStructure();renderMotion();renderTabs();
}
function prepare(){
 manifest.id=$("skinId").value.trim();manifest.name=$("skinName").value.trim();
 return payload();
}
function hookup(){
 renderTabs();renderPalette();renderGeometry();renderStructure();renderMotion();
 for(const slot of ASSETS){const opt=make("option",slot);opt.value=slot;$("assetSlot").append(opt)}
 for(const name of ["skinId","skinName"])$(name).addEventListener("change",()=>{manifest.id=$("skinId").value;manifest.name=$("skinName").value});
 document.querySelectorAll('input[name="mode"]').forEach(r=>r.addEventListener("change",()=>{mode=r.value;renderPalette();renderScene()}));
 $("reduced").onchange=e=>{reduced=e.target.checked;renderScene()};
 $("motionTarget").onchange=e=>{selectedBlock=e.target.value;renderMotion()};
 $("motionPreset").onchange=e=>{design.compiled.pages[currentPage].find(s=>s.id===selectedBlock).motion=e.target.value;renderScene()};
 $("motionDuration").oninput=e=>{const duration=Number(e.target.value);design.compiled.pages[currentPage].find(s=>s.id===selectedBlock).duration=duration;$("durationValue").textContent=duration;renderScene()};
 $("replay").onclick=renderScene;
 $("assetMode").onchange=assetStatus;$("assetSlot").onchange=assetStatus;$("assetFile").onchange=e=>addAsset(e.target.files[0]);
 $("clearAsset").onclick=()=>{const group=$("assetMode").value,slot=$("assetSlot").value,old=manifest[group][slot];delete manifest[group][slot];if(old&&![...Object.values(manifest.imagesLight),...Object.values(manifest.imagesDark)].includes(old))delete images[old];assetStatus();notify("已移除此槽素材")};
 $("validate").onclick=async()=>{try{const res=await api("/api/validate",prepare());notify("ABI 校验通过 · "+res.assets+" 张图片 · 仍需真机验收")}catch(e){showError(e)}};
 $("exportPack").onclick=async()=>{try{const blob=await api("/api/export",prepare());download(blob,manifest.id+".pcskin");notify("已导出 .pcskin v1。页面布局、组件动效未写入 ZIP：它们属于 Compose 编译层。")}catch(e){showError(e)}};
 $("exportProject").onclick=()=>{try{const data={format:"powercal-skin-studio",schemaVersion:1,portable:prepare().manifest,images:{...images},compiled:copy(design.compiled)};
 download(new Blob([JSON.stringify(data,null,2)],{type:"application/json"}),manifest.id+".pcstudio.json");notify("已保存设计工程（包含布局、动效、内嵌图片；不能直接导入 Android）。")}catch(e){showError(e)}};
 $("importZip").onchange=async e=>{try{const file=e.target.files[0];if(!file)return;if(file.size>10_000_000)throw Error("ZIP 超过 10MB");
 const response=await api("/api/import",await file.arrayBuffer(),true);images=response.images;applyManifest(response.manifest);notify("导入成功：颜色与图片已恢复，已存在的 Compose 布局设计未覆盖。")}catch(error){showError(error)}finally{e.target.value=""}};
 $("importProject").onchange=async e=>{try{const file=e.target.files[0];if(!file)return;if(file.size>24_000_000)throw Error("设计项目过大");pickProject(JSON.parse(await file.text()));notify("设计项目已载入；请在导出前执行 ABI 校验。")}catch(error){showError(error)}finally{e.target.value=""}};
 renderScene();assetStatus();
}
hookup();

// Unified v3 project: simple slots and advanced source always describe the same runnable package.
const V3_DEFAULT={
 html:'<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="style.css"></head><body><header><b>PowerCal</b><button data-action="today">Today</button><button data-action="openDeck">Drafts</button></header><main id="events"></main><nav><button data-action="timeline">Timeline</button><button data-action="month">Month</button><button data-action="ai">AI</button><button data-action="settings">Settings</button></nav><script src="app.js"></script></body></html>',
 css:':root{--primary:#2457D6;--surface:#F3F5FA}*{box-sizing:border-box}body{margin:0;background:var(--surface);font:16px system-ui}header,nav{display:flex;gap:8px;padding:12px}main{min-height:70vh;padding:16px}button{min-height:48px;border:0;border-radius:14px;padding:8px 12px}header button{background:var(--primary);color:white}@media(prefers-reduced-motion:reduce){*{animation:none!important}}',
 js:"(()=>{'use strict';let host={postMessage:m=>console.log('mock host',m)};const send=(type,payload={})=>host.postMessage(JSON.stringify({version:1,type,payload}));window.addEventListener('message',e=>{if(e.data==='powercal-port'&&e.ports[0]){host=e.ports[0];host.onmessage=m=>receive(m.data);send('ready',{minimumBridgeVersion:1})}});document.addEventListener('click',e=>{const t=e.target.closest('[data-action]');if(!t)return;const name=t.dataset.action;if(name==='openEvent')send('action',{name,eventSeq:Number(t.dataset.eventSeq),expectedRevision:Number(t.dataset.revision)});else send('action',{name})});function receive(raw){const m=typeof raw==='string'?JSON.parse(raw):raw;if(m.version!==1||m.type!=='state')return;const root=document.getElementById('events');root.replaceChildren();for(const x of m.payload.events||[]){const b=document.createElement('button');b.dataset.action='openEvent';b.dataset.eventSeq=String(x.eventSeq);b.dataset.revision=String(x.revision);b.textContent=x.title;root.appendChild(b)}if(!root.children.length)root.textContent='No events'}window.PowerCalMockState=receive})();"
};
let v3Project={format:"powercal-skin-studio",projectVersion:3,mode:"simple",slots:[
 {id:"primary",label:"Primary",type:"color",value:"#2457D6",editable:true},
 {id:"surface",label:"Surface",type:"color",value:"#F3F5FA",editable:true}],
 layers:[{id:"root",x:0,y:0},{id:"header",x:0,y:0},{id:"events",x:0,y:0},{id:"navigation",x:0,y:0}],source:{...V3_DEFAULT}};
const b64=bytes=>{let s="";for(const x of bytes)s+=String.fromCharCode(x);return btoa(s)};
const text64=text=>b64(new TextEncoder().encode(text));
const from64=value=>new TextDecoder().decode(Uint8Array.from(atob(value),c=>c.charCodeAt(0)));
function syncV3Editors(){
 $("htmlCode").value=v3Project.source.html;$("cssCode").value=v3Project.source.css;$("jsCode").value=v3Project.source.js;
 const layer=v3Project.layers.find(x=>x.id===$("layerTree").value)||v3Project.layers[0];$("layerTree").value=layer.id;$("layerX").value=layer.x;$("layerY").value=layer.y;
 renderWebPreview();
}
function renderWebPreview(){
 const mock=`<script>window.addEventListener('message',e=>{});setTimeout(()=>window.PowerCalMockState&&window.PowerCalMockState({type:'state',payload:{events:[{title:'Mock alarm'},{title:'Mock activity'}]}}),50)<\/script>`;
 $("webPreview").srcdoc=v3Project.source.html.replace('<link rel="stylesheet" href="style.css">',`<style>${v3Project.source.css}</style>`).replace('<script src="app.js"></script>',`<script>${v3Project.source.js}<\/script>${mock}`);
}
function setStudioMode(next){v3Project.mode=next;const advanced=next==="advanced";$("advancedEditor").hidden=!advanced;$("webPreviewPanel").hidden=!advanced;notify(advanced?"高级模式：编辑完整 HTML/CSS/JS 与图层位置":"简单模式：只修改基底声明的材质槽");if(advanced)syncV3Editors()}
function v3Payload(){
 manifest.id=$("skinId").value.trim();manifest.name=$("skinName").value.trim();
 const project={projectVersion:3,mode:v3Project.mode,slots:v3Project.slots};
 return{manifest:{format:"powercal-skin",schemaVersion:3,id:manifest.id,name:manifest.name,entrypoint:"web/index.html",project:"project.json",fullCanvas:true,capabilities:["host.read","host.action","host.edit"],light:manifest.light,dark:manifest.dark,layout:manifest.layout,imagesLight:{},imagesDark:{},sha256:{}},files:{
  "project.json":text64(JSON.stringify(project,null,2)),"web/index.html":text64(v3Project.source.html),"web/style.css":text64(v3Project.source.css),"web/app.js":text64(v3Project.source.js)}};
}
$("simpleMode").onclick=()=>setStudioMode("simple");$("advancedMode").onclick=()=>setStudioMode("advanced");
$("layerTree").onchange=syncV3Editors;for(const id of ["layerX","layerY"]){$(id).onchange=()=>{const layer=v3Project.layers.find(x=>x.id===$("layerTree").value);layer[id==="layerX"?"x":"y"]=Number($(id).value)||0;v3Project.source.css+=`\n#${layer.id}{transform:translate(${layer.x}px,${layer.y}px)}`;syncV3Editors()}}
$("applyCode").onclick=()=>{v3Project.source={html:$("htmlCode").value,css:$("cssCode").value,js:$("jsCode").value};renderWebPreview();notify("已应用源码；导出后 Android v3 将运行同一份 Web 源")};
$("exportPack").onclick=async()=>{try{const blob=await api("/api/export-v3",v3Payload());download(blob,manifest.id+".pcskin");notify("已导出自包含 .pcskin v3") }catch(e){showError(e)}};
$("validate").onclick=async()=>{try{await api("/api/export-v3",v3Payload());notify("v3 ZIP、完整哈希、项目与 Web 源验证通过") }catch(e){showError(e)}};
$("exportProject").onclick=()=>{v3Project.source={html:$("htmlCode").value||v3Project.source.html,css:$("cssCode").value||v3Project.source.css,js:$("jsCode").value||v3Project.source.js};download(new Blob([JSON.stringify(v3Project,null,2)],{type:"application/json"}),manifest.id+".pcstudio-v3.json");notify("已保存独立 v3 源项目")};
$("importProject").onchange=async e=>{try{const data=JSON.parse(await e.target.files[0].text());if(data.projectVersion!==3||!data.source)throw Error("不是 v3 源项目");v3Project=data;setStudioMode(data.mode);syncV3Editors();notify("v3 源项目已载入") }catch(error){showError(error)}finally{e.target.value=""}};
$("importZip").onchange=async e=>{try{const file=e.target.files[0];const response=await api("/api/import-v3",await file.arrayBuffer(),true);manifest={...manifest,...response.manifest};$("skinId").value=manifest.id;$("skinName").value=manifest.name;const project=JSON.parse(from64(response.files["project.json"]));v3Project={...v3Project,...project,source:{html:from64(response.files["web/index.html"]),css:from64(response.files["web/style.css"]),js:from64(response.files["web/app.js"]||text64(V3_DEFAULT.js))}};setStudioMode(v3Project.mode);syncV3Editors();notify("可运行 v3 包已导入；源码和槽位均可继续编辑") }catch(error){showError(error)}finally{e.target.value=""}};
setStudioMode("simple");syncV3Editors();
