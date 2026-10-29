"use strict";
/* Skin Studio v3: one canonical collection of bytes for editing, preview and export.
   No second DOM/CSS/JS renderer, no string-replacement mock preview. */
const $ = id => document.getElementById(id);
const timelineGeometryFixture=document.createElement("option");
timelineGeometryFixture.value="timeline-geometry";
timelineGeometryFixture.textContent="时间轴真实几何与泳道（回归夹具）";
timelineGeometryFixture.hidden=true;
$("scenario").append(timelineGeometryFixture);
const timelineDragFixture=document.createElement("option");
timelineDragFixture.value="timeline-drag";
timelineDragFixture.textContent="时间轴拖动与重复事件拒绝（回归夹具）";
timelineDragFixture.hidden=true;
$("scenario").append(timelineDragFixture);
const conflictRestoreFixture=document.createElement("option");
conflictRestoreFixture.value="conflict-restore";
conflictRestoreFixture.textContent="仅预览 Mock：冲突与已删除恢复（不写数据）";
$("scenario").append(conflictRestoreFixture);
const deckLifecycleFixture=document.createElement("option");
deckLifecycleFixture.value="deck-lifecycle";
deckLifecycleFixture.textContent="仅预览 Mock：草稿暂存、提交、丢弃与撤销（不写数据）";
deckLifecycleFixture.hidden=true;
$("scenario").append(deckLifecycleFixture);
const staleAiBatchFixture=document.createElement("option");
staleAiBatchFixture.value="locked-stale";
staleAiBatchFixture.textContent="仅预览 Mock：停止超时恢复（回归夹具）";
staleAiBatchFixture.hidden=true;
$("scenario").append(staleAiBatchFixture);
const aiReviewBatchFixture=document.createElement("option");
aiReviewBatchFixture.value="ai-review-batch";
aiReviewBatchFixture.textContent="仅预览 Mock：提案审核与批次互斥（回归夹具）";
aiReviewBatchFixture.hidden=true;
$("scenario").append(aiReviewBatchFixture);
const enc = new TextEncoder(), dec = new TextDecoder();
const escapeHtml = s => String(s).replaceAll("&","&amp;").replaceAll("<","&lt;")
  .replaceAll(">","&gt;").replaceAll('"',"&quot;").replaceAll("'","&#39;");
const bytesToBase64 = bytes => {
  let out=""; const step=8192;
  for(let i=0;i<bytes.length;i+=step)
    out+=String.fromCharCode(...bytes.subarray(i,i+step));
  return btoa(out);
};
const base64ToBytes = text => Uint8Array.from(atob(text), c=>c.charCodeAt(0));
const safeFilename = name => /^[A-Za-z0-9][A-Za-z0-9_.-]{0,90}$/.test(name);
const copy = value => JSON.parse(JSON.stringify(value));
let manifest=null, project=null, projectBaseline=null, sourceFiles=new Map(), selectedLayer=null;
let activeMode="simple", editCanvas=true, previewTimer=null, previewSeq=0;
let previewPort=null, lastState=null, lastPreviewUrl="", dirty=false;
let projectSourceError=null, projectDraftText=null, implicitFullCanvas=false, fullCanvasTouched=false;
const REQUIRED_SOURCE_FILES=new Set(["web/index.html","web/style.css","web/app.js","project.json"]);
const TEXT_SOURCE_EXTENSIONS=/\.(cjs|css|html?|ini|js|json|jsx|kt|less|md|mjs|properties|py|scss|sh|sql|toml|tsx?|txt|xml|ya?ml|svg)$/i;
function say(message,isError=false){$("toast").textContent=message;$("toast").classList.toggle("error",isError)}
function setText(name,value){sourceFiles.set(name,enc.encode(value))}
function getText(name){return dec.decode(sourceFiles.get(name)||new Uint8Array())}
function parseProject(){
  const obj=JSON.parse(getText("project.json"));
  if(!obj||typeof obj!=="object"||Array.isArray(obj)||obj.projectVersion!==3||
     (obj.slots!==undefined&&!Array.isArray(obj.slots))) throw Error("无效的 v3 项目元数据");
  if(!Array.isArray(obj.slots))obj.slots=[];
  obj.layers=Array.isArray(obj.layers)?obj.layers:[];
  return obj;
}
function prepareFiles(){
  const files=new Map(sourceFiles);
  // Keep imported metadata byte-for-byte until a Studio operation actually
  // changes the project model. parseProject adds an in-memory layers default.
  if(JSON.stringify(project)!==projectBaseline)
    files.set("project.json",enc.encode(JSON.stringify(project,null,2)));
  return files;
}
function payload(){
  if(!manifest) throw Error("还没有加载皮肤");
  if(projectSourceError)throw Error("project.json 尚未修复："+projectSourceError);
  manifest.id=$("skinId").value.trim();manifest.name=$("skinName").value.trim();
  if(implicitFullCanvas&&!fullCanvasTouched&&!$("fullCanvas").checked)
    delete manifest.fullCanvas;
  else manifest.fullCanvas=$("fullCanvas").checked;
  // Preserve the imported declaration: the editor must neither grant nor revoke
  // capabilities merely because a project was opened, previewed or exported.
  const files={};
  for(const [name,bytes] of prepareFiles()) files[name]=bytesToBase64(bytes);
  return {manifest:copy(manifest),files};
}
async function api(path,body=null,binary=false){
  const headers=body===null?{}:{"Content-Type":binary?"application/octet-stream":"application/json"};
  const response=await fetch(path,{method:body===null?"GET":"POST",headers,
    body:body===null?undefined:(binary?body:JSON.stringify(body))});
  if(!response.ok){
    const error=await response.json().catch(()=>({error:"请求失败 "+response.status}));
    throw Error(error.error||"服务器错误");
  }
  return path==="/api/export-v3"?response.blob():response.json();
}
function download(blob,name){
  const url=URL.createObjectURL(blob),a=document.createElement("a");
  a.href=url;a.download=name;document.body.append(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(url),12000);
}
async function loadPackage(data){
  if(!data.manifest||!data.files) throw Error("项目数据不完整");
  const files=new Map();
  for(const [name,encoded] of Object.entries(data.files))
    files.set(name,base64ToBytes(encoded));
  if(!files.has("web/index.html")||!files.has("web/style.css")||
     !files.has("web/app.js")||!files.has("project.json"))
    throw Error("皮肤包缺少必需 Web 源码");
  sourceFiles=files;manifest=copy(data.manifest);
  implicitFullCanvas=!Object.hasOwn(manifest,"fullCanvas");fullCanvasTouched=false;
  projectSourceError=null;projectDraftText=null;
  project=parseProject();
  projectBaseline=JSON.stringify(project);
  mockEditing={editor:{open:false},deck:{open:false},lastResult:null};
  resetMockDeckFixtures();
  mockConflictRestoreActive=$("scenario").value==="conflict-restore";
  mockApproval=null;$("mockApproval").hidden=true;
  selectedLayer=project.layers[0]?.id||null;dirty=false;
  // Fork the protected factory identity before an ordinary user exports it.
  // Its HTML/CSS/JS/assets/project remain the exact bundled source.
  const factory = manifest.id==="powercal-clear-default";
  $("skinId").value=factory?"powercal-clear-custom":manifest.id;
  $("skinName").value=factory?"我的 PowerCal Clear":manifest.name;
  $("fullCanvas").checked=manifest.fullCanvas===true;
  switchMode(project.mode==="advanced"?"advanced":"simple");
  syncSourceInputs();renderMaterials();renderAssets();renderLayers();
  schedulePreview(0);
  say(factory
    ?"已载入 APK 同源 Clear 基础皮肤。导出使用独立 ID，不覆盖受保护的出厂包。"
    :"已加载完整 v3 项目（含所有素材）。预览与导出共享同一组源文件。");
}
function change(){
  dirty=true;
  if(projectSourceError){$("previewState").textContent="project.json 有错误，修复后才能预览/导出";return}
  schedulePreview();
}
function syncSourceInputs(){
  $("htmlCode").value=getText("web/index.html");
  $("cssCode").value=getText("web/style.css");
  $("jsCode").value=getText("web/app.js");
  renderExtraSources();
}
function renderExtraSources(){
  const select=$("extraSourceFile"),previous=select.value;
  const strict=new TextDecoder("utf-8",{fatal:true});
  const paths=[...sourceFiles.keys()].filter(path=>{
    if(path==="project.json")return true;
    if(!TEXT_SOURCE_EXTENSIONS.test(path) || REQUIRED_SOURCE_FILES.has(path))return false;
    try{strict.decode(sourceFiles.get(path));return true}catch{return false}
  }).sort();
  select.replaceChildren();
  for(const path of paths){
    const option=document.createElement("option");option.value=path;option.textContent=path;
    select.append(option);
  }
  select.value=paths.includes(previous)?previous:(paths[0]||"");
  $("extraSourceCode").disabled=!select.value;
  $("extraSourceCode").value=select.value==="project.json"&&projectDraftText!==null
    ?projectDraftText:(select.value?getText(select.value):"");
  $("removeExtraSource").disabled=!select.value||REQUIRED_SOURCE_FILES.has(select.value);
}
function safePackagePath(path){
  if(typeof path!=="string"||path.length>126||path.includes("\\")||path.startsWith("/"))return false;
  const parts=path.split("/");
  return parts.length>=2&&["web","assets"].includes(parts[0])&&
    parts.slice(1).every(part=>part!=="."&&part!==".."&&/^[A-Za-z0-9][A-Za-z0-9_.-]*$/.test(part));
}
function updateProjectSource(text){
  projectDraftText=text;
  try{
    const next=JSON.parse(text);
    if(!next||typeof next!=="object"||Array.isArray(next)||next.projectVersion!==3||
       !["simple","advanced"].includes(next.mode)||
       (next.slots!==undefined&&!Array.isArray(next.slots)))
      throw Error("必须是 projectVersion=3 且 slots 为数组的 JSON 对象");
    if(!Array.isArray(next.slots))next.slots=[];
    if(next.slots.length>128)throw Error("slots 超过 128 项上限");
    if(next.layers!==undefined&&!Array.isArray(next.layers))throw Error("layers 必须为数组");
    if(!Array.isArray(next.layers))next.layers=[];
    if(next.layers.length>128)throw Error("layers 超过 128 项上限");
    const ids=new Set();
    for(const slot of next.slots){
      if(!slot||typeof slot!=="object"||typeof slot.id!=="string"||
         !/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(slot.id)||ids.has(slot.id)||
         Object.keys(slot).some(key=>!["id","label","type","value","editable"].includes(key))||
         !["color","image","font","text","number"].includes(slot.type))
        throw Error("slots 中存在非法或重复的材质定义");
      ids.add(slot.id);
    }
    const layerIds=new Set();
    for(const layer of next.layers){
      if(!layer||typeof layer!=="object"||Array.isArray(layer)||!safeId(layer.id)||layerIds.has(layer.id))
        throw Error("layers 中存在非法或重复的图层 ID");
      layerIds.add(layer.id);
    }
    project=next;projectBaseline=JSON.stringify(project);
    sourceFiles.set("project.json",enc.encode(text));
    projectSourceError=null;projectDraftText=null;
    if(!project.layers.some(layer=>layer&&layer.id===selectedLayer))selectedLayer=project.layers[0]?.id||null;
    renderMaterials();renderAssets();renderLayers();
    change();say("project.json 已解析并保留未知字段；预览与导出将使用此结构。");
  }catch(error){
    projectSourceError=error.message;
    dirty=true;$("previewState").textContent="project.json 无效，修复后才能预览/导出";
    say("project.json 暂未应用："+error.message,true);
  }
}
function referencedByPackage(path){
  const candidates=[path,path.replace(/^assets\//,"../assets/"),path.replace(/^web\//,"./"),path.split("/").pop()];
  const references=[];
  const strict=new TextDecoder("utf-8",{fatal:true});
  for(const [source,bytes] of sourceFiles){
    if(source===path||!TEXT_SOURCE_EXTENSIONS.test(source))continue;
    try{
      const text=strict.decode(bytes);
      if(candidates.some(candidate=>text.includes(candidate)))references.push(source);
    }catch{}
  }
  return references;
}
function addTextSource(){
  const path=prompt("包内相对路径（例如 web/components/event-card.js）","web/components/new-component.js");
  if(!path)return;
  if(!safePackagePath(path)||path==="project.json"||REQUIRED_SOURCE_FILES.has(path)){
    say("路径无效；只能在 web/ 或 assets/ 下创建非必需文件",true);return;
  }
  if(!TEXT_SOURCE_EXTENSIONS.test(path)){
    say("文本文件扩展名不受支持",true);return;
  }
  if(sourceFiles.has(path)){say("文件已存在："+path,true);return;}
  sourceFiles.set(path,enc.encode(""));
  renderExtraSources();$("extraSourceFile").value=path;$("extraSourceCode").value="";
  $("removeExtraSource").disabled=false;change();say("已创建文本文件："+path);
}
function removeExtraSource(){
  const path=$("extraSourceFile").value;
  if(!path||REQUIRED_SOURCE_FILES.has(path)){say("入口源码和 project.json 不能删除",true);return;}
  const references=referencedByPackage(path);
  if(references.length){say("仍被源码引用（"+references.join("、")+"），请先修改引用",true);return;}
  if(!confirm("删除包内文件 "+path+"？此操作只作用于当前未导出的工程。"))return;
  sourceFiles.delete(path);renderExtraSources();renderAssets();renderLayers();change();
  say("已删除："+path);
}
function switchMode(mode){
  // The current editor pane is UI state; switching it must not rewrite the
  // imported project's mode or force unrelated metadata through serialization.
  activeMode=mode;
  $("simpleMode").classList.toggle("active",mode==="simple");
  $("advancedMode").classList.toggle("active",mode==="advanced");
  $("slotSection").hidden=mode==="advanced";
  $("sourceEditor").hidden=mode!=="advanced";
  $("layerSection").hidden=mode==="advanced";
  if(mode==="advanced") syncSourceInputs();
  refreshOverlay();
}
function schedulePreview(delay=400){
  clearTimeout(previewTimer);
  const seq=++previewSeq;
  $("previewState").textContent="待刷新 · 源码与导出实时同步";
  previewTimer=setTimeout(async()=>{
    try{
      const next=await api("/api/preview-v3",payload());
      if(seq!==previewSeq)return;
      lastPreviewUrl=next.url;
      $("webPreview").src=next.url;
      $("previewState").textContent="同一文件集预览 · 加载中";
    }catch(error){if(seq===previewSeq){say("预览未通过校验："+error.message,true);
      $("previewState").textContent="预览失败：文件未达到 Android 包规则";}}
  },delay);
}
function renderMaterials(){
  const root=$("materialSlots");root.replaceChildren();
  for(const slot of project.slots){
    if(slot.editable===false)continue;
    const row=document.createElement("div");row.className="materialRow";
    const label=document.createElement("span");label.textContent=slot.label||slot.id;row.append(label);
    if(slot.type==="color"){
      const color=document.createElement("input");color.type="color";
      color.value=/^#[0-9a-fA-F]{6}$/.test(slot.value)?slot.value:"#2457d6";
      const text=document.createElement("input");text.type="text";text.value=slot.value||"";
      const update=v=>{
        if(!/^#[0-9a-fA-F]{6}$/.test(v)){say("颜色格式需要 #RRGGBB",true);return;}
        slot.value=v;color.value=v;text.value=v;applyMaterialSource();change();
      };
      color.oninput=()=>update(color.value);text.onchange=()=>update(text.value.trim());
      row.append(color,text);
    }else{
      const input=document.createElement("input");input.type="text";
      input.value=slot.value??"";
      input.onchange=()=>{slot.value=input.value;applyMaterialSource();change()};
      row.append(input);
    }
    root.append(row);
  }
}
function applyMaterialSource(){
  const markerStart="/* powercal-studio:materials:start */";
  const markerEnd="/* powercal-studio:materials:end */";
  const original=getText("web/style.css");
  const properties=project.slots.filter(s=>s.editable!==false &&
    ["color","number","font","text","image"].includes(s.type) &&
    /^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(s.id)).map(s=>{
      let value=String(s.value??"").replaceAll("\r","").replaceAll("\n","").trim();
      if(s.type==="color"&&!/^#[0-9a-fA-F]{6,8}$/.test(value))return null;
      if(s.type==="number"&&!/^-?\d+(?:\.\d+)?(?:px|rem|%)?$/.test(value))return null;
      if(s.type==="image"){
        if(!/^assets\/[A-Za-z0-9_./-]+$/.test(value))return null;
        value="url('../"+value+"')";
      }else if(s.type==="text"||s.type==="font"){
        // These are metadata if not explicitly bound in the authored CSS.
        return null;
      }
      return "  --"+s.id+": "+value+";";
    }).filter(Boolean).join("\n");
  // Material slots describe the light/base palette. Keep Clear's authored .dark
  // tokens authoritative so one light color edit cannot make dark text unreadable.
  const block=markerStart+"\n:root:not(.dark) {\n"+properties+"\n}\n"+markerEnd;
  const start=original.indexOf(markerStart),end=original.indexOf(markerEnd);
  const css=start>=0&&end>start
    ?original.slice(0,start)+block+original.slice(end+markerEnd.length)
    :original+"\n\n"+block+"\n";
  setText("web/style.css",css);
  if(document.activeElement!==$("cssCode"))$("cssCode").value=css;
}

function renderAssets(){
  const root=$("assetList");root.replaceChildren();
  for(const [path,bytes] of sourceFiles){
    if(!path.startsWith("assets/"))continue;
    const row=document.createElement("div");row.className="entry";
    const name=document.createElement("span");
    name.textContent=path+" · "+Math.ceil(bytes.byteLength/1024)+"KB";
    const remove=document.createElement("button");remove.textContent="删除";remove.className="danger";
    remove.onclick=()=>{
      const references=referencedByPackage(path);
      if(references.length){say("素材仍被引用（"+references.join("、")+"），请先移除引用",true);return;}
      if(!confirm("删除资源 "+path+"？此操作只作用于当前未导出的工程。"))return;
      sourceFiles.delete(path);renderAssets();renderLayers();change();
    };
    row.append(name,remove);root.append(row);
  }
  renderIconChoices();
}
async function validateRasterAsset(file,name,bytes){
  if(!/\.(png|webp)$/i.test(name))return;
  const png=name.toLowerCase().endsWith(".png");
  const header=bytes.subarray(0,png?8:4);
  const correct=png
    ?header.length===8&&header.every((v,i)=>v===[137,80,78,71,13,10,26,10][i])
    :header.length===4&&dec.decode(header)==="RIFF"&&dec.decode(bytes.subarray(8,12))==="WEBP";
  if(!correct)throw Error("图片文件头与扩展名不符："+name);
  const bitmap=await createImageBitmap(file).catch(()=>null);
  if(!bitmap)throw Error("图片无法解码："+name);
  const valid=bitmap.width>0&&bitmap.height>0&&bitmap.width<=4096&&
    bitmap.height<=4096&&bitmap.width*bitmap.height<=8_000_000;
  bitmap.close();
  if(!valid)throw Error("图片尺寸超过上限："+name);
}
async function addAssets(list){
  try{
    for(const file of Array.from(list||[])){
      const name=file.name.replace(/[^a-zA-Z0-9_.-]/g,"_");
      if(!safeFilename(name))throw Error("文件名不安全："+file.name);
      if(!/\.(png|webp|woff2|woff|ttf)$/i.test(name))
        throw Error("只允许 PNG、WebP 和字体文件："+name);
      if(file.size>3_000_000)throw Error(name+" 超过 3MB");
      const bytes=new Uint8Array(await file.arrayBuffer());
      await validateRasterAsset(file,name,bytes);
      let path="assets/"+name;
      let i=2;
      while(sourceFiles.has(path)){
        const basename=name.replace(/(\.[^.]+)$/,""),extension=name.substring(basename.length);
        path="assets/"+basename+"-"+(i++)+extension;
      }
      sourceFiles.set(path,bytes);
    }
    renderAssets();renderLayers();change();
    say("素材已保留原始字节；预览与导出引用同一文件。");
  }catch(error){say(error.message,true);}
}
async function addPackageAssets(list){
  try{
    const pending=[],seen=new Set();
    for(const file of Array.from(list||[])){
      const name=file.name;
      if(!safeFilename(name))throw Error("资源文件名必须使用安全 ASCII 字符："+name);
      if(file.size>3_000_000)throw Error(name+" 超过 3MB");
      const path="assets/"+name;
      if(seen.has(path))throw Error("选择列表中包含重复资源："+path);
      seen.add(path);
      if(sourceFiles.has(path)&&!confirm("替换现有资源 "+path+"？"))
        throw Error("已取消资源替换；本批次未应用");
      const bytes=new Uint8Array(await file.arrayBuffer());
      await validateRasterAsset(file,name,bytes);
      pending.push([path,bytes]);
    }
    if(!pending.length)return;
    const newPaths=pending.filter(([path])=>!sourceFiles.has(path)).length;
    if(sourceFiles.size+newPaths>96)throw Error("v3 包最多包含 96 个源文件");
    for(const [path,bytes] of pending)sourceFiles.set(path,bytes);
    renderAssets();renderLayers();change();
    say("资源文件已更新；导出保留原始字节。");
  }catch(error){say(error.message,true);}
}
const ACTIONS=[
  ["","无动作"],["today","返回今天"],["createMemo","新建 Memo"],
  ["createDraft","新建事件"],["browseMonth","浏览月份"],
  ["selectMonthDate","选择月历日期"],["selectMonth","选择年月"],
  ["loadMoreMemo","加载更多 Memo"],
  ["timeline","时间轴"],
  ["month","月历"],["memo","Memo"],["ai","AI"],
  ["settings","设置"],["requestNotificationPermission","请求通知权限"],
  ["nativeSettings","打开原生设置"],["openDeck","草稿卡组"],
  ["panTimeline","时间轴向后 30 分钟"],["zoomTimeline","时间轴缩放"],
  ["resetTimelineZoom","重置时间轴缩放"],["jumpTimelineDay","跳到今天"],
  ["invokeOption","导入日历文件"]
];
function renderIconChoices(){
  const iconTarget=$("iconTarget"),iconAsset=$("iconAsset");
  const previousAction=iconTarget.value,previousFile=iconAsset.value;
  iconTarget.replaceChildren();iconAsset.replaceChildren();
  for(const [id,label] of ACTIONS){
    if(!id||id==="invokeOption")continue;
    const option=document.createElement("option");option.value=id;option.textContent=label;
    iconTarget.append(option);
  }
  for(const path of sourceFiles.keys()){
    if(!path.startsWith("assets/")||!/\.(png|webp)$/i.test(path))continue;
    const option=document.createElement("option");option.value=path;option.textContent=path;
    iconAsset.append(option);
  }
  if([...iconTarget.options].some(o=>o.value===previousAction))iconTarget.value=previousAction;
  if([...iconAsset.options].some(o=>o.value===previousFile))iconAsset.value=previousFile;
}
function applyButtonIcon(){
  const action=$("iconTarget").value,asset=$("iconAsset").value;
  if(!ACTIONS.some(([name])=>name===action)||!asset||!sourceFiles.has(asset)){
    say("请选择允许的按钮动作和已导入的 PNG/WebP 素材",true);return;
  }
  const first="/* powercal-studio:icon:"+action+":start */";
  const last="/* powercal-studio:icon:"+action+":end */";
  const block=first+"\n[data-action=\""+action+"\"]::before{content:\"\";display:inline-block;"+
    "width:1.25em;height:1.25em;margin-right:.35em;vertical-align:-.22em;"+
    "background:center/contain no-repeat url('../"+asset+"');}\n"+last;
  const css=getText("web/style.css");
  setText("web/style.css",replaceMarked(css,first,last,block)??css+"\n"+block+"\n");
  $("cssCode").value=getText("web/style.css");
  change();say("按钮图标已写入实际 CSS；默认模板直接生效。");
}
function renderActions(){
  const select=$("layerAction"),root=$("actionList");
  select.replaceChildren();root.textContent=ACTIONS.map(x=>x[1]).join(" · ");
  for(const [id,label] of ACTIONS){
    const option=document.createElement("option");option.value=id;option.textContent=label;
    select.append(option);
  }
}
function safeId(id){return /^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(id)}
function layerById(){return project.layers.find(x=>x.id===selectedLayer);}
function layerMarkup(layer){
  const name=escapeHtml(layer.label||layer.id);
  const id=escapeHtml(layer.id);
  const action=layer.action||"";
  let props="";
  if(action){
    props+=' data-action="'+escapeHtml(action)+'"';
    if(action==="panTimeline")props+=' data-delta-minutes="30"';
    if(action==="zoomTimeline")props+=' data-direction="in"';
    if(action==="createDraft")props+=' data-kind="ACTIVITY"';
    if(action==="browseMonth")props+=' data-delta-months="1"';
    if(action==="jumpTimelineDay")props+=' data-date="2026-10-22"';
    if(action==="invokeOption")
      props+=' data-option-id="calendar.import.file"';
  }
  if(layer.kind==="image"){
    const path=layer.asset||"";
    const src=path.startsWith("assets/")?"../"+path:"";
    return '<img id="'+id+'" alt="'+name+'" src="'+escapeHtml(src)+'">';
  }
  if(layer.kind==="button")return '<button id="'+id+'" type="button"'+props+'>'+name+'</button>';
  return '<div id="'+id+'" aria-label="'+name+'">'+name+'</div>';
}
function startMark(id){return "<!-- powercal-studio:"+id+":start -->"}
function endMark(id){return "<!-- powercal-studio:"+id+":end -->"}
function cssStart(id){return "/* powercal-studio:layer:"+id+":start */"}
function cssEnd(id){return "/* powercal-studio:layer:"+id+":end */"}
function replaceMarked(text,start,end,replacement){
  const a=text.indexOf(start),b=text.indexOf(end);
  if(a>=0 && b>a)return text.slice(0,a)+replacement+text.slice(b+end.length);
  return null;
}
function applyLayerToSource(layer){
  if(!safeId(layer.id))throw Error("不安全的图层 ID");
  let html=getText("web/index.html");
  const wrapped=startMark(layer.id)+"\n"+layerMarkup(layer)+"\n"+endMark(layer.id);
  const replaced=replaceMarked(html,startMark(layer.id),endMark(layer.id),wrapped);
  if(replaced!==null)html=replaced;
  else if(/<\/body>/i.test(html))html=html.replace(/<\/body>/i,wrapped+"\n</body>");
  else throw Error("源 HTML 缺少 </body>，无法插入可视化图层");
  setText("web/index.html",html);
  updateLayerStyle(layer);
  if(document.activeElement!==$("htmlCode"))$("htmlCode").value=html;
}
function updateLayerStyle(layer){
  const css=getText("web/style.css"),start=cssStart(layer.id),end=cssEnd(layer.id);
  const x=Number(layer.x)||0,y=Number(layer.y)||0;
  const w=Math.max(1,Number(layer.width)||100),h=Math.max(1,Number(layer.height)||40);
  const z=Math.max(-10000,Math.min(10000,Number(layer.z)||0));
  const opacity=Math.max(0,Math.min(1,Number(layer.opacity??1)));
  const block=start+"\n#"+layer.id+"{position:fixed;box-sizing:border-box;left:"+x+
    "px;top:"+y+"px;width:"+w+"px;height:"+h+"px;z-index:"+z+
    ";opacity:"+opacity+";pointer-events:"+(layer.kind==="button"?"auto":"none")+
    ";object-fit:contain;max-width:none;}\n"+end;
  setText("web/style.css",
    replaceMarked(css,start,end,block) ?? (css+"\n"+block+"\n"));
  if(document.activeElement!==$("cssCode"))
    $("cssCode").value=getText("web/style.css");
}
function removeLayerSource(layer){
  for(const [path,a,b] of [
    ["web/index.html",startMark(layer.id),endMark(layer.id)],
    ["web/style.css",cssStart(layer.id),cssEnd(layer.id)]
  ]){
    let data=getText(path),i=data.indexOf(a),j=data.indexOf(b);
    if(i>=0 && j>i){
      data=data.slice(0,i)+data.slice(j+b.length);
      setText(path,data);
    }else{
      // Imported arbitrary source must not be destructively rewritten.
      say("已移除图层元数据；外部源码未标记的节点请在高级模式检查。");
    }
  }
}
function renderLayers(){
  const root=$("layerList");root.replaceChildren();
  for(const layer of project.layers){
    const row=document.createElement("div");row.className="entry"+(selectedLayer===layer.id?" selected":"");
    const label=document.createElement("span");
    label.textContent=(layer.kind||"node")+" · "+(layer.label||layer.id);
    const button=document.createElement("button");
    button.textContent="编辑";button.onclick=()=>selectLayer(layer.id);
    row.append(label,button);root.append(row);
  }
  const layer=layerById(),pane=$("layerEditor");pane.hidden=!layer;
  if(layer){
    $("layerLabel").value=layer.label||"";
    for(const [id,key] of [
      ["layerX","x"],["layerY","y"],["layerWidth","width"],
      ["layerHeight","height"],["layerZ","z"],["layerOpacity","opacity"]
    ])$(id).value=layer[key]??0;
    $("layerAction").value=layer.action||"";
    $("layerAsset").replaceChildren();
    const none=document.createElement("option");none.value="";none.textContent="不使用图片";$("layerAsset").append(none);
    for(const path of sourceFiles.keys()){
      if(!path.startsWith("assets/")||!/\.(png|webp)$/i.test(path))continue;
      const option=document.createElement("option");option.value=path;option.textContent=path;
      $("layerAsset").append(option);
    }
    $("layerAsset").value=layer.asset||"";
  }
  refreshOverlay();
}
function selectLayer(id){selectedLayer=id;renderLayers();}
function newLayer(kind){
  const id="pc-layer-"+Date.now().toString(36)+"-"+Math.floor(Math.random()*10000).toString(36);
  const firstAsset=[...sourceFiles.keys()].find(x=>x.startsWith("assets/")&&/\.(png|webp)$/i.test(x));
  if(kind==="image"&&!firstAsset){say("请先在左侧上传 PNG 或 WebP 素材",true);return;}
  const layer={id,kind,label:kind==="button"?"今天":kind==="image"?"装饰图片":"装饰文字",
    x:24,y:170,width:kind==="image"?160:kind==="button"?100:150,height:kind==="image"?160:48,
    z:project.layers.length+2,opacity:1,asset:kind==="image"?firstAsset:"",
    action:kind==="button"?"today":""};
  try{applyLayerToSource(layer);project.layers.push(layer);selectedLayer=id;
    renderLayers();change();}catch(error){say(error.message,true)}
}
function updateLayer(){
  const layer=layerById();if(!layer)return;
  try{
    layer.label=$("layerLabel").value;
    layer.action=$("layerAction").value;
    layer.asset=$("layerAsset").value;
    for(const [id,key] of [
      ["layerX","x"],["layerY","y"],["layerWidth","width"],
      ["layerHeight","height"],["layerZ","z"],["layerOpacity","opacity"]
    ])layer[key]=Number($(id).value);
    applyLayerToSource(layer);refreshOverlay();change();
  }catch(error){say(error.message,true)}
}
function removeLayer(){
  const layer=layerById();if(!layer)return;
  removeLayerSource(layer);
  project.layers=project.layers.filter(x=>x.id!==layer.id);
  selectedLayer=project.layers[0]?.id||null;
  syncSourceInputs();renderLayers();change();
}
function refreshOverlay(){
  const overlay=$("dragOverlay");overlay.replaceChildren();
  overlay.classList.toggle("passive",!editCanvas);
  if(!editCanvas||!project)return;
  for(const layer of project.layers){
    const element=document.createElement("div");element.className="dragHandle"+(layer.id===selectedLayer?" selected":"");
    element.style.left=(Number(layer.x)||0)+"px";
    element.style.top=(Number(layer.y)||0)+"px";
    element.style.width=Math.max(1,Number(layer.width)||100)+"px";
    element.style.height=Math.max(1,Number(layer.height)||40)+"px";
    const name=document.createElement("span");name.className="tag";
    name.textContent=layer.label||layer.id;element.append(name);
    element.onpointerdown=event=>{
      event.preventDefault();
      // Keep this DOM node attached until pointer-up; re-rendering here would
      // revoke its pointer capture and make dragging silently fail.
      selectedLayer=layer.id;
      overlay.querySelectorAll(".dragHandle").forEach(x=>x.classList.remove("selected"));
      element.classList.add("selected");
      element.setPointerCapture(event.pointerId);
      const baseX=Number(layer.x)||0,baseY=Number(layer.y)||0;
      const px=event.clientX,py=event.clientY;
      element.onpointermove=e=>{
        const dx=e.clientX-px,dy=e.clientY-py;
        element.style.left=baseX+dx+"px";element.style.top=baseY+dy+"px";
      };
      element.onpointerup=e=>{
        element.onpointermove=null;element.onpointerup=null;
        layer.x=Math.round(baseX+e.clientX-px);
        layer.y=Math.round(baseY+e.clientY-py);
        try{applyLayerToSource(layer);renderLayers();change()}
        catch(error){say(error.message,true)}
      };
    };
    overlay.append(element);
  }
}

function simulatedState(){
  const scenario=$("scenario").value,base=new Date("2026-10-22T04:00:00Z").getTime();
  const anchor=mockAnchor??base,hours=mockHours;
  let rows=[
    {eventSeq:4,revision:2,occurrenceKey:"4:single",kind:"ACTIVITY",
      title:"上午课程",side:"RIGHT",lane:0,startMillis:base-10800000,endMillis:base-5400000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:5,revision:1,occurrenceKey:"5:single",kind:"ALARM",
      title:"训练提醒",side:"LEFT",lane:0,startMillis:base+3600000,endMillis:base+3600000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:6,revision:3,occurrenceKey:"6:single",kind:"DEADLINE",
      title:"报告截止",side:"LEFT",lane:1,startMillis:base+14400000,endMillis:null,precision:"DATE",deadlineDate:"2026-10-22"}
  ];
  if(scenario==="empty")rows=[];
  if(scenario==="dense")rows=Array.from({length:12},(_,i)=>({
    eventSeq:100+i,revision:1,occurrenceKey:String(100+i)+":single",kind:"ACTIVITY",
    title:"并行事件 "+(i+1),side:"RIGHT",lane:i,
    startMillis:base-6000000,endMillis:base+4500000,precision:"INSTANT",deadlineDate:null
  }));
  if(scenario==="timeline-geometry")rows=[
    {eventSeq:101,revision:3,occurrenceKey:"101:one-minute",kind:"ACTIVITY",
      title:"Greedy_A",side:"RIGHT",lane:1,startMillis:base-7200000,
      endMillis:base-7140000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:301,revision:1,occurrenceKey:"301:greedy-b",kind:"ACTIVITY",
      title:"Greedy_B",side:"RIGHT",lane:1,startMillis:base-7140000,
      endMillis:base-7080000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:302,revision:1,occurrenceKey:"302:greedy-c",kind:"ACTIVITY",
      title:"Greedy_C",side:"RIGHT",lane:1,startMillis:base-3600000,
      endMillis:base-3540000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:303,revision:1,occurrenceKey:"303:greedy-d",kind:"ACTIVITY",
      title:"Greedy_D",side:"RIGHT",lane:1,startMillis:base-3540000,
      endMillis:base-3480000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:102,revision:2,occurrenceKey:"102:long",kind:"ACTIVITY",
      title:"跨越可见区的长活动",side:"RIGHT",lane:0,startMillis:base-72000000,
      endMillis:base+72000000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:103,revision:1,occurrenceKey:"103:parallel-a",kind:"ACTIVITY",
      title:"并行活动 A",side:"RIGHT",lane:2,startMillis:base-7200000,
      endMillis:base+10800000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:104,revision:4,occurrenceKey:"104:parallel-b",kind:"ACTIVITY",
      title:"并行活动 B",side:"RIGHT",lane:3,startMillis:base-7200000,
      endMillis:base+10800000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:201,revision:5,occurrenceKey:"201:alarm",kind:"ALARM",
      title:"时刻提醒",side:"LEFT",lane:0,startMillis:base+7200000,
      endMillis:base+7200000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:202,revision:2,occurrenceKey:"202:deadline-exact",kind:"DEADLINE",
      title:"精确截止",side:"LEFT",lane:1,startMillis:base+7200000,
      endMillis:base+7200000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:203,revision:1,occurrenceKey:"203:alarm-parallel",kind:"ALARM",
      title:"并行提醒",side:"LEFT",lane:2,startMillis:base+7200000,
      endMillis:base+7200000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:204,revision:7,occurrenceKey:"204:date-deadline",kind:"DEADLINE",
      title:"日期级截止事项",side:"LEFT",lane:99,startMillis:base,
      endMillis:null,precision:"DATE",deadlineDate:"2026-10-22"}
  ];
  if(scenario==="timeline-geometry"){
    rows.push(...Array.from({length:8},(_,index)=>({
      eventSeq:105+index,revision:1,occurrenceKey:`${105+index}:parallel`,kind:"ACTIVITY",
      title:index===0?"非常长的时间轴活动标题用于验证实际文字矩形、单行截断和完整无障碍名称":
        `并行活动 ${index+3}`,side:"RIGHT",lane:index+4,
      startMillis:base-7200000,endMillis:base+10800000,precision:"INSTANT",deadlineDate:null
    })));
    rows.push(...Array.from({length:9},(_,index)=>({
      eventSeq:205+index,revision:1,occurrenceKey:`${205+index}:point`,kind:index%2?"DEADLINE":"ALARM",
      title:`并行时刻 ${index+1}`,side:"LEFT",lane:index+3,
      startMillis:base+7200000,endMillis:base+7200000,precision:"INSTANT",deadlineDate:null
    })));
  }
  if(scenario==="timeline-drag")rows=[
    {eventSeq:501,revision:4,occurrenceKey:"501:single",kind:"ACTIVITY",
      title:"可移动活动",side:"RIGHT",lane:0,startMillis:base-10800000,
      endMillis:base-7200000,precision:"INSTANT",deadlineDate:null},
    {eventSeq:502,revision:7,occurrenceKey:"502:2026-10-22T10:00",
      originalOccurrenceKey:"2026-10-22T10:00",kind:"ACTIVITY",
      title:"重复活动 · 拒绝拖动",side:"RIGHT",lane:1,startMillis:base-7200000,
      endMillis:base-3600000,precision:"INSTANT",deadlineDate:null}
  ];
  if(scenario==="repeating")rows=[
    {...rows[0],eventSeq:8,occurrenceKey:"8:20261022T090000",
      originalOccurrenceKey:"2026-10-22T09:00",title:"重复课程 · 本次"},
    {...rows[0],eventSeq:8,occurrenceKey:"8:20261023T090000",title:"重复课程 · 明天",
      originalOccurrenceKey:"2026-10-23T09:00",
      startMillis:rows[0].startMillis+86400000,endMillis:rows[0].endMillis+86400000}
  ];
  const items=[
    {id:"calendar.import.file",label:"导入日历文件",detail:"本地优先",icon:"file",enabled:scenario!=="locked"},
    {id:"calendar.import.system",label:"从系统日历导入",detail:"静态快照",icon:"calendar",enabled:scenario!=="locked"},
    {id:"data.legacy.migrate",label:"迁移旧版 PowerCal 数据",
      detail:"Android 宿主确认后只读导入；旧数据库文件保留",icon:"migration",enabled:scenario!=="locked"}
  ];
  if(scenario==="future")items.push({
    id:"calendar.import.future",label:"新功能：导入课程表",
    detail:"模拟未来软件版本新增的平行选项",icon:"table",enabled:true
  });
  const start=anchor-hours*1800000;
  const rawChatMessages=mockChatCleared?mockChat:scenario==="ai-error"
    ? [{role:"assistant",content:"你好！今天有什么安排？"},...mockAiErrorMessages]
    : [{role:"assistant",content:"你好！今天有什么安排？"},...mockChat];
  const chatMessages=rawChatMessages.map((item,index)=>({...item,
    messageId:item.messageId||"preview-seed-"+index}));
  const projectedBatch=scenario==="ai-review-batch"?{phase:"SELECTING"}:mockAiBatch;
  const chatHistoryActionsAvailable=!mockAiLocked&&
    !["SELECTING","AWAITING_HOST_REVIEW","RUNNING","CANCEL_REQUESTED"].includes(projectedBatch?.phase)&&
    !(["aiProposal","aiBatch","aiBatchRelease"].includes(mockApproval?.kind));
  const allMemoEntries=scenario==="memo-overflow"?Array.from({length:251},(_,i)=>({
    eventSeq:1000+i,revision:1,occurrenceKey:"single",
    title:"溢出 Memo "+(i+1),completed:false,startMillis:base+i*60000
  })):scenario==="repeating"?[
    {eventSeq:31,revision:mockMemoRevision.get(31)??2,occurrenceKey:"2026-10-22T00:00",
      originalOccurrenceKey:"2026-10-22T00:00",title:"重复 Memo · 今天",
      completed:mockMemoCompletion.get("31:2026-10-22T00:00")??mockMemoDone,startMillis:base},
    {eventSeq:31,revision:mockMemoRevision.get(31)??2,occurrenceKey:"2026-10-23T00:00",
      originalOccurrenceKey:"2026-10-23T00:00",title:"重复 Memo · 明天",
      completed:mockMemoCompletion.get("31:2026-10-23T00:00")??false,startMillis:base+86400000}
  ]:[
    {eventSeq:31,revision:mockMemoRevision.get(31)??2,occurrenceKey:"single",title:"整理会议笔记",
      completed:mockMemoCompletion.get("31:single")??mockMemoDone,startMillis:base},
    {eventSeq:44,revision:mockMemoRevision.get(44)??1,occurrenceKey:"single",title:"提交报告",
      completed:mockMemoCompletion.get("44:single")??false,startMillis:base+7200000}
  ];
  const memoLimit=scenario==="memo-overflow"?Math.min(allMemoEntries.length,mockMemoVisibleLimit):allMemoEntries.length;
  const memoEntries=allMemoEntries.slice(0,memoLimit);
  const memoTruncated=memoEntries.length<allMemoEntries.length;
  return {bridgeVersion:1,page:mockPage,
    notificationPermission:mockPage==="SETTINGS"?{required:true,allowed:false}:{},
    memoCount:allMemoEntries.filter(item=>!item.completed).length,aiLocked:mockAiLocked,
    aiBatch:scenario==="ai-review-batch"?{
      phase:"SELECTING",state:null,active:false,canCancel:true,canRelease:false,
      mode:"PUBLISH",scopeCount:0,selectedCount:0,operationCounts:{},
      containsDelete:false,progress:null,report:null
    }:mockAiBatch,
    reducedMotion:$("reduced").checked,pendingCount:3,
    editing:mockEditing,
    memoEntries,memoTruncated,memoRemainingCount:allMemoEntries.length-memoEntries.length,
    chatBusy:false,chatAttachmentBusy:false,
    chatHistoryRevision:mockChatHistoryRevision,
    chatHistoryActionsAvailable,
    chatPrefillText:mockChatPrefillText,
    chatPrefillRevision:mockChatPrefillRevision,
    chatAttachmentAvailable:scenario!=="locked"&&scenario!=="locked-stale"&&
      !(["ai-review","ai-review-batch"].includes(scenario)&&mockAiProposals.length>0),
    chatMessages,
    aiProposalReview:["ai-review","ai-review-batch"].includes(scenario)&&mockAiProposals.length?{
      reviewId:mockAiReviewId,
      proposals:mockAiProposals.map((proposal,index)=>({...proposal,index})),
      warnings:["预览 Mock 数据；Android Host 会重新校验全部提案。"]
    }:null,
    events:rows.map(r=>({eventSeq:r.eventSeq,revision:r.revision,title:r.title,kind:r.kind,
      anchorMillis:r.startMillis,endMillis:r.endMillis})),
    optionGroups:[{id:"calendar.import",label:"导入",items}],
    timeline:{zoneId:"Asia/Shanghai",anchorMillis:anchor,hours,
      visibleStartMillis:start,visibleEndMillis:anchor+hours*1800000,
      nowMillis:base,itemLimit:300,truncated:false,items:rows},
    month:{zoneId:"Asia/Shanghai",month:mockMonth,
      selectedDate:mockSelectedMonthDate,gridFrom:mockMonthGridFrom(mockMonth),
      itemLimit:350,truncated:false,
      items:rows.map(r=>({...r,date:"2026-10-22"}))}};
}
function mockMonthGridFrom(value){
  const [year,month]=String(value).split("-").map(Number);
  const first=new Date(Date.UTC(year,month-1,1));
  const offset=(first.getUTCDay()+6)%7;
  first.setUTCDate(first.getUTCDate()-offset);
  return first.toISOString().slice(0,10);
}
function postState(){
  if(!previewPort)return;
  lastState=Array.isArray(manifest?.capabilities)&&manifest.capabilities.includes("host.read")
    ?simulatedState():{bridgeVersion:1};
  try{previewPort.postMessage(JSON.stringify({version:1,type:"state",payload:lastState}))}
  catch(error){say("预览通道断开："+error.message,true)}
}
function logAction(text){
  const root=$("bridgeLog"),p=document.createElement("p");
  p.textContent=text;root.prepend(p);
  while(root.childElementCount>5)root.lastElementChild.remove();
}
const mockForm=kind=>({kind,title:"",startText:"2026-10-22 09:00",
  endText:"2026-10-22 10:00",notes:"",location:"",
  enabled:true,trackingEnabled:kind==="MEMO",reminderMinutes:-1,
  reminderMethod:"MESSAGE",reminderTiming:"OFFSET",source:"local",
  zoneId:"Asia/Shanghai",recurrenceFreq:"NONE",recurrenceInterval:1,
  recurrenceFrom:null,recurrenceUntil:null});
function mockTimelineSnap(millis,hours,zone){
  const parts=Object.fromEntries(new Intl.DateTimeFormat("en-GB",{
    timeZone:zone,year:"numeric",month:"2-digit",day:"2-digit",
    hour:"2-digit",minute:"2-digit",second:"2-digit",hourCycle:"h23"
  }).formatToParts(new Date(millis)).map(part=>[part.type,part.value]));
  const minutes=hours<=6?1:hours<=10?5:hours<=16?10:hours<=26?15:hours<=36?30:60;
  const seconds=Number(parts.hour)*3600+Number(parts.minute)*60+Number(parts.second);
  const step=minutes*60,rounded=Math.round(seconds/step)*step;
  return millis-(millis%1000)+(rounded-seconds)*1000;
}
function mockLocalDateTime(millis,zone){
  const parts=Object.fromEntries(new Intl.DateTimeFormat("en-CA",{
    timeZone:zone,year:"numeric",month:"2-digit",day:"2-digit",
    hour:"2-digit",minute:"2-digit",hourCycle:"h23"
  }).formatToParts(new Date(millis)).map(part=>[part.type,part.value]));
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`;
}
const mockKinds=new Set(["ALARM","ACTIVITY","DEADLINE","MEMO"]);
function mockPatch(form,field,value){
  if(field!=="kind")return {...form,[field]:value};
  if(!mockKinds.has(value))return null;
  if(form.kind===value)return {...form};
  const start=String(form.startText||"").trim(),end=String(form.endText||"").trim();
  const next={...form,kind:value};
  if(form.kind==="ALARM"&&value==="DEADLINE"){
    next.startText="";next.endText=start;
  }else if(form.kind==="DEADLINE"&&value==="ALARM"){
    if(/^\d{4}-\d{2}-\d{2}$/.test(end)){next.startText="";next.endText=""}
    else{next.startText=end;next.endText=""}
  }else if(form.kind==="ACTIVITY"&&value==="DEADLINE"){
    next.startText="";next.endText=end;
  }else if(form.kind==="DEADLINE"&&value==="ACTIVITY"){
    next.startText="";next.endText=end;
  }else if(form.kind==="ALARM"&&value==="ACTIVITY"){
    next.startText=start;next.endText="";
  }else if(form.kind==="ACTIVITY"&&value==="ALARM"){
    next.startText=start;next.endText="";
  }
  if(value==="DEADLINE"&&/^\d{4}-\d{2}-\d{2}$/.test(next.endText))
    next.reminderMinutes=-1;
  return next;
}
let mockEditing={editor:{open:false},deck:{open:false},lastResult:null},mockApproval=null;
let mockDeckDrafts=[],mockDeckFormalEvents=[];
let mockLastDiscardId=null,mockDeckPublished=0,mockDeckDiscarded=0,mockDeckConflicts=0;
const mockRetiredEvents=Array.from({length:151},(_,index)=>{
  const eventSeq=8901+index,kind=["ACTIVITY","ALARM","DEADLINE","MEMO"][index%4];
  const date=`2026-10-${String(index%28+1).padStart(2,"0")}`;
  return {eventSeq,revision:2+index%6,title:`预览已删除事件 ${index+1}`,kind,
    updatedMillis:1800000000000+Math.floor(index/2)*1000,
    startText:kind==="ACTIVITY"||kind==="ALARM"?date+" 09:00":"",
    endText:kind==="ACTIVITY"?date+" 10:00":kind==="DEADLINE"?date:""};
}).sort((left,right)=>right.updatedMillis-left.updatedMillis||right.eventSeq-left.eventSeq);
function resetMockDeckFixtures(){
  mockLastDiscardId=null;mockDeckPublished=0;mockDeckDiscarded=0;mockDeckConflicts=0;
  mockDeckDrafts=$("scenario").value==="deck-lifecycle"?[
    {draftId:7301,eventSeq:7301,draftVersion:0,title:"生命周期草稿一",kind:"MEMO",status:"PENDING"},
    {draftId:7302,eventSeq:7302,draftVersion:0,title:"生命周期草稿二",kind:"MEMO",status:"PENDING"}
  ]:[
    {draftId:7201,eventSeq:7201,draftVersion:0,title:"示例草稿一",kind:"MEMO",status:"PENDING"},
    {draftId:7202,eventSeq:7202,draftVersion:0,title:"示例草稿二",kind:"ALARM",status:"PENDING"},
    {draftId:7203,eventSeq:7203,draftVersion:0,title:"示例草稿三",kind:"ACTIVITY",status:"PENDING"},
    {draftId:7204,eventSeq:7204,draftVersion:0,title:"示例草稿四",kind:"DEADLINE",status:"PENDING"},
    {draftId:7205,eventSeq:7205,draftVersion:0,title:"其他待处理草稿",kind:"MEMO",status:"PENDING"}
  ];
  mockDeckFormalEvents=[
    {eventSeq:6201,revision:2,title:"正式事件替换目标",kind:"ACTIVITY"},
    {eventSeq:6202,revision:5,title:"另一项正式日程",kind:"ALARM"}
  ];
}
function mockDeckProject(deck,query=""){
  const term=String(query||"").trim().toLocaleLowerCase();
  const activeDrafts=mockDeckDrafts.filter(card=>
    card.status!=="DISCARDED"&&card.status!=="COMMITTED");
  const sequenceMatches=(seq)=>String(seq).includes(term)||
    ("#"+String(seq).padStart(4,"0")).toLocaleLowerCase().includes(term);
  const draftMatches=term?activeDrafts.filter(card=>
    card.title.toLocaleLowerCase().includes(term)||sequenceMatches(card.eventSeq)):
    activeDrafts;
  const formalMatches=term?mockDeckFormalEvents.filter(record=>
    record.title.toLocaleLowerCase().includes(term)||sequenceMatches(record.eventSeq)):[];
  const limit=term?4:80;
  const index=activeDrafts.findIndex(card=>card.draftId===deck.currentId);
  deck.transient=false;
  deck.cards=draftMatches.slice(0,limit).map(card=>({...card}));
  deck.truncated=draftMatches.length>limit;
  deck.searchTruncated=!!term&&draftMatches.length>limit;
  deck.formalMatches=formalMatches.slice(0,4).map(record=>({...record}));
  deck.searchQuery=String(query||"");
  deck.cardCount=activeDrafts.length;
  deck.position=index<0?0:index+1;
  deck.canGoPrevious=index>0;
  deck.canGoNext=index>=0&&index<activeDrafts.length-1;
  deck.lastDiscardId=mockLastDiscardId;
  deck.completion=activeDrafts.length===0&&
    (mockDeckPublished+mockDeckDiscarded+mockDeckConflicts)>0
      ?{published:mockDeckPublished,discarded:mockDeckDiscarded,conflicts:mockDeckConflicts}:null;
  deck.conflict=deck.conflict||null;
  deck.restore=deck.restore||null;
  deck.hasConflict=!!deck.conflict;
  deck.hasRestore=!!deck.restore;
  const retired=$("scenario").value==="conflict-restore"?mockRetiredEvents:[];
  const retiredOffset=Math.max(0,Number(deck.retiredOffset)||0);
  deck.retiredLoaded=deck.retiredLoaded===true;
  deck.retiredResults=retired.slice(retiredOffset,retiredOffset+50)
    .map(({eventSeq,revision,title,kind,startText,endText})=>
      ({eventSeq,revision,title,kind,startText,endText}));
  deck.retiredOffset=retiredOffset;
  deck.retiredTotal=retired.length;
  deck.retiredHasPrevious=retiredOffset>0;
  deck.retiredHasNext=retiredOffset+deck.retiredResults.length<retired.length;
}
function mockConflictFixture(deck){
  const eventSeq=6201,draftId=7801,draftVersion=4,currentRevision=6;
  const current={...mockForm("ACTIVITY"),title:"当前正式标题",
    startText:"2026-10-22 09:00",endText:"2026-10-22 10:00",
    location:"原教学楼",notes:"正式记录中的说明"};
  const proposed={...current,title:"冲突草稿标题",
    startText:"2026-10-22 10:30",endText:"2026-10-22 11:30",
    location:"新教学楼",notes:"草稿中的新说明"};
  const record=mockDeckFormalEvents.find(value=>value.eventSeq===eventSeq);
  if(record){record.revision=currentRevision;record.title=current.title}
  let card=mockDeckDrafts.find(value=>value.draftId===draftId);
  if(!card){
    card={draftId,eventSeq,draftVersion,title:proposed.title,kind:proposed.kind,
      status:"CONFLICT",operation:"REPLACE",baseRevision:2};
    mockDeckDrafts.push(card);
  }
  deck.open=true;deck.transient=false;deck.currentId=draftId;deck.version=draftVersion;
  deck.form={...proposed};deck.baseline={...proposed};deck.dirty=false;
  deck.conflict={draftId,eventSeq,draftVersion,currentRevision,operation:"REPLACE",
    current,proposed,changes:[
      {label:"标题",before:current.title,after:proposed.title},
      {label:"开始时间",before:current.startText,after:proposed.startText},
      {label:"结束时间",before:current.endText,after:proposed.endText},
      {label:"地点",before:current.location,after:proposed.location},
      {label:"备注",before:current.notes,after:proposed.notes}
    ]};
  deck.restore=null;deck.hasConflict=true;deck.hasRestore=false;deck.retiredOffset=0;
}
function mockSetRetiredPage(deck,offset){
  const retired=$("scenario").value==="conflict-restore"?mockRetiredEvents:[];
  const safeOffset=Math.max(0,Math.min(retired.length,offset));
  deck.retiredOffset=safeOffset;
  deck.retiredResults=retired.slice(safeOffset,safeOffset+50)
    .map(({eventSeq,revision,title,kind,startText,endText})=>
      ({eventSeq,revision,title,kind,startText,endText}));
  deck.retiredTotal=retired.length;
  deck.retiredHasPrevious=safeOffset>0;
  deck.retiredHasNext=safeOffset+deck.retiredResults.length<retired.length;
  deck.retiredLoaded=true;
}
function mockFlushDeck(deck){
  if(deck.transient){
    if(!deck.dirty||deck.currentId==null)return;
    const currentForm={...deck.form};
    if(!deck.persistedForm){
      deck.persistedForm=currentForm;
    }else if(JSON.stringify(currentForm)!==JSON.stringify(deck.persistedForm)){
      deck.version=Number(deck.version)+1;
      deck.persistedForm=currentForm;
    }
    return;
  }
  if(!deck.dirty||deck.currentId==null)return;
  const current=mockDeckDrafts.find(card=>card.draftId===deck.currentId);
  if(current){
    current.title=String(deck.form?.title||"");
    current.kind=String(deck.form?.kind||current.kind);
    current.draftVersion=Number(deck.version)+1;
    current.form={...deck.form};
  }
  deck.version=Number(deck.version)+1;
  deck.dirty=false;deck.baseline={...deck.form};
}
function mockSelectDeckCard(deck,card){
  mockFlushDeck(deck);
  deck.currentId=card.draftId;deck.version=card.draftVersion;
  deck.form=card.form?{...card.form}:{...mockForm(card.kind),title:card.title};
  deck.baseline={...deck.form};deck.dirty=false;
  mockDeckProject(deck);
}
function resolveMockApproval(approved){
  if(!mockApproval)return;
  const request=mockApproval;mockApproval=null;$("mockApproval").hidden=true;
  $("mockApprovalText").textContent="预览模拟宿主确认，不会写入真实数据。";
  if(request.kind==="deckCommitReview"){
    const deck=mockEditing.deck;
    const card=mockDeckDrafts.find(value=>value.draftId===request.draftId&&
      value.status!=="DISCARDED"&&value.status!=="COMMITTED");
    const matches=deck.open&&deck.currentId===request.draftId&&
      deck.version===request.draftVersion&&!deck.dirty&&!!card;
    let status=approved&&matches?"committed_preview_only":approved?"stale":"cancelled";
    if(approved&&matches){
      const active=mockDeckDrafts.filter(value=>
        value.status!=="DISCARDED"&&value.status!=="COMMITTED");
      const index=active.findIndex(value=>value.draftId===card.draftId);
      card.status="COMMITTED";mockDeckPublished++;
      const remaining=mockDeckDrafts.filter(value=>
        value.status!=="DISCARDED"&&value.status!=="COMMITTED");
      const next=remaining[Math.min(index,remaining.length-1)];
      if(next){
        deck.currentId=next.draftId;deck.version=Number(next.draftVersion)||0;
        deck.form=next.form?{...next.form}:{...mockForm(next.kind),title:next.title};
        deck.baseline={...deck.form};deck.dirty=false;
      }else{
        deck.currentId=null;deck.version=0;deck.form=null;deck.baseline=null;deck.dirty=false;
      }
    }
    if(deck.open)mockDeckProject(deck,deck.searchQuery);
    mockEditing.lastResult={requestId:request.requestId,status};
    logAction("仅预览、无持久化写入：Mock Host "+
      (approved&&matches?"模拟确认发布":"取消或拒绝")+" · "+status);
    postState();return;
  }
  if(request.kind==="deckConflictReview"||request.kind==="deckRestoreReview"){
    const deck=mockEditing.deck;
    const prompt=request.kind==="deckConflictReview"?deck.conflict:deck.restore;
    const matches=prompt&&prompt.draftId===request.draftId&&
      prompt.draftVersion===request.draftVersion&&prompt.eventSeq===request.eventSeq&&
      (request.kind==="deckConflictReview"
        ?prompt.currentRevision===request.expectedRevision
        :prompt.expectedRevision===request.expectedRevision);
    const status=approved&&matches
      ?request.kind==="deckConflictReview"?"replaced_preview_only":"restored_preview_only"
      :approved?"stale":"cancelled";
    if(approved&&matches){
      if(request.kind==="deckConflictReview")deck.conflict=null;
      else deck.restore=null;
    }
    if(deck.open)mockDeckProject(deck,deck.searchQuery);
    mockEditing.lastResult={requestId:request.requestId,status};
    logAction("仅预览、无持久化写入：Mock Host "+
      (approved&&matches?"模拟确认":"取消或拒绝")+" · "+status);
    postState();return;
  }
  if(request.kind==="aiProposal"){
    if(request.reviewId!==mockAiReviewId){
      pushMockChat({role:"assistant",content:"预览 Mock：审核卡已过期，模拟 Host 拒绝了旧请求。"});
      postState();return;
    }
    if(approved){
      const selected=new Set(request.indices);
      mockAiProposals=mockAiProposals.filter((_,index)=>!selected.has(index));
      rotateMockAiReviewId();
      pushMockChat({role:"assistant",content:"预览 Mock：Host 确认请求已完成；没有写入任何真实日历数据。"});
    }else pushMockChat({role:"assistant",content:"预览 Mock：已取消 Host 确认，没有执行操作。"});
    postState();return;
  }
  if(request.kind==="aiBatch"){
    if(request.reviewId!==mockAiBatch.reviewId||mockAiBatch.phase!=="AWAITING_HOST_REVIEW"){
      pushMockChat({role:"assistant",content:"预览 Mock：批次审核已过期；没有执行操作。"});
      postState();return;
    }
    if(approved){
      const count=mockAiBatch.selectedCount||0,mode=mockAiBatch.mode;
      mockAiBatch={phase:"COMPLETED",state:"COMPLETED",active:false,canCancel:false,
        canRelease:false,mode,scopeCount:count,selectedCount:0,operationCounts:{},
        containsDelete:false,progress:null,
        report:{mode,published:mode==="PUBLISH"?count:0,
          discarded:mode==="DISCARD"?count:0,kept:0,cancelled:false}};
      pushMockChat({role:"assistant",content:"预览 Mock：批次结果已模拟；没有写入真实草稿或日历。"});
    }else{
      mockAiBatch={phase:"IDLE",state:null,active:false,canCancel:false,canRelease:false,
        mode:null,scopeCount:0,selectedCount:0,operationCounts:{},containsDelete:false,
        progress:null,report:null};
      pushMockChat({role:"assistant",content:"预览 Mock：已取消批次审核，没有执行操作。"});
    }
    logAction("Preview Mock 批次 Host 审核："+(approved?"模拟确认":"模拟取消")+
      " · 不写入真实数据");postState();return;
  }
  if(request.kind==="aiBatchRelease"){
    if(approved&&mockAiLocked&&mockAiBatch.phase==="CANCEL_REQUESTED"&&mockAiBatch.canRelease){
      const mode=mockAiBatch.mode||"PUBLISH";
      const total=mockAiBatch.scopeCount||0;
      const done=Math.min(total,Math.max(0,mockAiBatch.progress?.done||0));
      mockAiBatch={phase:"FAILED",state:"FAILED",active:false,canCancel:false,
        canRelease:false,mode,scopeCount:total,selectedCount:0,
        operationCounts:{},containsDelete:false,progress:null,
        report:{mode,published:mode==="PUBLISH"?done:0,
          discarded:mode==="DISCARD"?done:0,kept:Math.max(0,total-done),
          cancelled:true,released:true,outcomeKnown:true}};
      mockAiLocked=false;
    }
    pushMockChat({role:"assistant",content:approved?
      "预览 Mock：批次锁已模拟解除；未完成草稿保留，没有写入真实日历。":
      "预览 Mock：未解除批次锁；没有写入真实日历。"});
    logAction("Preview Mock 批次恢复确认 · "+(approved?"解除":"取消")+
      " · 不写入真实数据");postState();return;
  }
  const editor=mockEditing.editor;
  if(editor.open){
    editor.awaitingConfirmation=false;
    if(request.operation==="delete"){
      if(approved)mockEditing.editor={open:false};
    }
    else if(approved&&request.scope==="THIS_AND_FUTURE")mockEditing.editor={open:false};
    else if(approved){editor.dirty=false;editor.baseline={...editor.form};editor.scopeBaseline={...editor.form}}
  }
  mockEditing.lastResult={requestId:request.requestId,
    status:request.operation==="delete"?(approved?"deleted":"cancelled"):
      approved?"saved":"cancelled"};
  if(request.operation==="delete")
    logAction("预览编辑命令 editor.delete → "+(approved?"deleted":"cancelled"));
  logAction("仅预览：模拟宿主"+(approved?"确认":"取消"));postState();
}
function onSkinCommand(p){
  if(!p || !/^[A-Za-z0-9_-]{8,64}$/.test(p.requestId||""))return;
  const result={requestId:p.requestId,status:"invalid"};
  const editor=mockEditing.editor,deck=mockEditing.deck;
  if(mockApproval){
    result.status="locked";mockEditing.lastResult=result;
    try{previewPort?.postMessage(JSON.stringify({version:1,type:"result",payload:result}))}catch{}
    postState();return;
  }
  switch(p.name){
    case "timeline.move":{
      const keys=Object.keys(p).sort().join(",");
      if(keys!=="eventSeq,expectedRevision,name,requestId,targetMillis"||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0||
        !Number.isSafeInteger(p.targetMillis)){
        result.status="invalid";break;
      }
      if(mockPage!=="TIMELINE"){
        result.status="unsupported";result.detail="只能从时间轴移动定时事件";break;
      }
      if(editor.open||deck.open){result.status="locked";break}
      const item=lastState?.timeline?.items?.find(value=>
        value.eventSeq===p.eventSeq&&value.revision===p.expectedRevision);
      if(!item){result.status="stale";break}
      if(item.originalOccurrenceKey||item.kind==="MEMO"||
        item.kind==="DEADLINE"&&item.precision==="DATE"){
        result.status="unsupported";result.detail=item.originalOccurrenceKey
          ?"重复事件暂不支持拖动；请打开卡片选择编辑范围"
          :"此类事项没有可拖动的时间轴时刻";break;
      }
      const zone=lastState.timeline.zoneId||"Asia/Shanghai";
      const snapped=mockTimelineSnap(p.targetMillis,Number(lastState.timeline.hours),zone);
      if(snapped===Number(item.startMillis)){
        result.status="unchanged";break;
      }
      const form=mockForm(item.kind);
      form.title=String(item.title||"");form.zoneId=zone;
      const duration=Math.max(0,Number(item.endMillis)-Number(item.startMillis));
      if(item.kind==="ALARM")form.startText=mockLocalDateTime(snapped,zone);
      else if(item.kind==="ACTIVITY"){
        form.startText=mockLocalDateTime(snapped,zone);
        form.endText=mockLocalDateTime(snapped+duration,zone);
      }else form.endText=mockLocalDateTime(snapped,zone);
      const draftId=7501;
      mockEditing.deck={open:true,cards:[{draftId,eventSeq:item.eventSeq,
        draftVersion:0,title:form.title,kind:form.kind,status:"PENDING",
        operation:"REPLACE"}],currentId:draftId,version:0,dirty:false,
        aiLocked:false,form,baseline:{...form},conflict:null,restore:null,
        hasConflict:false,hasRestore:false,retiredResults:[],retiredOffset:0,
        retiredTotal:0,retiredHasPrevious:false,retiredHasNext:false,retiredLoaded:false};
      result.status="staged";result.detail=String(draftId);break;
    }
    case "editor.open":
      const memoEntry=lastState?.memoEntries?.find(item=>item.eventSeq===p.eventSeq&&
        (!p.occurrenceKey||item.originalOccurrenceKey===p.occurrenceKey));
      const form={...mockForm(memoEntry?"MEMO":"ACTIVITY"),
        title:memoEntry?.title||"测试活动"};
      if(memoEntry?.originalOccurrenceKey)form.recurrenceFreq="DAILY";
      mockEditing.editor={open:true,sessionId:"00000000-0000-0000-0000-000000000001",
        eventSeq:p.eventSeq,revision:p.expectedRevision,dirty:false,
        requiresRecurrenceScope:!!p.occurrenceKey,
        canEditSelectedOccurrence:!!p.occurrenceKey,recurrenceScope:null,
        occurrenceKey:p.occurrenceKey||null,form};
      mockEditing.editor.baseline={...mockEditing.editor.form};
      result.status="opened";break;
    case "editor.scope":
      if(editor.open&&editor.sessionId===p.sessionId&&editor.requiresRecurrenceScope&&
        !editor.dirty&&["SINGLE_OCCURRENCE","THIS_AND_FUTURE","WHOLE_SERIES"].includes(p.scope)&&
        (p.scope==="WHOLE_SERIES"||editor.canEditSelectedOccurrence)){
        editor.recurrenceScope=p.scope;
        editor.form={...editor.baseline};
        if(p.scope!=="WHOLE_SERIES"&&editor.occurrenceKey)
          editor.form.startText=editor.form.kind==="MEMO"
            ? editor.occurrenceKey.slice(0,10):editor.occurrenceKey.replace("T"," ");
        editor.scopeBaseline={...editor.form};result.status="selected";
      }else result.status="invalid";
      break;
    case "editor.discardChanges":
      if(editor.open&&editor.sessionId===p.sessionId){
        editor.form={...(editor.scopeBaseline||editor.baseline)};
        editor.dirty=false;result.status="discarded_changes";
      }else result.status="stale";
      break;
    case "editor.patch":
      if(editor.open&&editor.sessionId===p.sessionId){
        if((editor.requiresRecurrenceScope&&!editor.recurrenceScope)||
          (editor.recurrenceScope==="SINGLE_OCCURRENCE"&&
            !["title","startText","endText"].includes(p.field)))result.status="invalid";
        else {
          const next=mockPatch(editor.form,p.field,p.value);
          if(!next)result.status="invalid";
          else {editor.form=next;editor.dirty=JSON.stringify(next)!==JSON.stringify(editor.baseline);
            result.status="edited"}
        }
      }else result.status="stale";
      break;
    case "editor.save":
      if(!editor.open)result.status="stale";
      else if((editor.requiresRecurrenceScope&&!editor.recurrenceScope)||
        (editor.recurrenceScope==="THIS_AND_FUTURE"&&!editor.dirty))result.status="invalid";
      else if(editor.dirty&&(editor.requiresRecurrenceScope||editor.form.recurrenceFreq!=="NONE")){
        editor.awaitingConfirmation=true;
        mockApproval={requestId:p.requestId,scope:editor.recurrenceScope||"WHOLE_SERIES"};
        $("mockApproval").hidden=false;result.status="confirmation_required";
      } else result.status="saved";
      break;
    case "editor.delete":
      if(!editor.open||editor.sessionId!==p.sessionId||editor.awaitingConfirmation)
        result.status="stale";
      else {
        editor.awaitingConfirmation=true;
        mockApproval={requestId:p.requestId,operation:"delete"};
        $("mockApprovalText").textContent="预览 Mock：Host 将再次确认正式事件删除；不会写入真实数据。";
        $("mockApproval").hidden=false;result.status="confirmation_required";
      }
      break;
    case "editor.close":
      mockEditing.editor={open:false};result.status="closed";break;
    case "deck.create":{
      const id=7001;
      const form=mockForm(p.kind);
      if(mockPage==="MONTH"&&mockKinds.has(p.kind)){
        if(p.kind==="ACTIVITY"){
          form.startText=mockSelectedMonthDate+form.startText.slice(10);
          form.endText=mockSelectedMonthDate+form.endText.slice(10);
        }else if(p.kind==="ALARM"){
          form.startText=mockSelectedMonthDate+form.startText.slice(10);
        }else if(p.kind==="DEADLINE"){
          form.startText="";form.endText=mockSelectedMonthDate;
        }
      }
      mockEditing.deck={open:true,transient:true,cards:[{draftId:id,title:form.title,
        kind:p.kind,status:"PENDING"}],currentId:id,version:0,position:1,cardCount:1,
        canGoPrevious:false,canGoNext:false,searchQuery:"",formalMatches:[],
        truncated:false,searchTruncated:false,dirty:false,aiLocked:false,form,baseline:{...form},
        conflict:null,restore:null,hasConflict:false,hasRestore:false,retiredResults:[],
        retiredOffset:0,retiredTotal:0,retiredHasPrevious:false,retiredHasNext:false,
        retiredLoaded:false};
      result.status="created";result.detail=String(id);break;
    }
    case "deck.open":
      if(!deck.open){
        mockLastDiscardId=null;mockDeckPublished=0;mockDeckDiscarded=0;mockDeckConflicts=0;
        const initial=mockDeckDrafts.find(card=>
          card.status!=="DISCARDED"&&card.status!=="COMMITTED");
        const form=initial?(initial.form?{...initial.form}:
          {...mockForm(initial.kind),title:initial.title}):null;
        mockEditing.deck={open:true,currentId:initial?.draftId??null,version:initial?.draftVersion??0,
          dirty:false,aiLocked:false,form,baseline:{...form},truncated:false,
          searchTruncated:false,searchQuery:"",formalMatches:[],conflict:null,restore:null,
          retiredOffset:0,retiredResults:[],retiredTotal:0,
          retiredHasPrevious:false,retiredHasNext:false,retiredLoaded:false};
        mockDeckProject(mockEditing.deck);
        mockSetRetiredPage(mockEditing.deck,0);
        if($("scenario").value==="conflict-restore"){
          mockConflictFixture(mockEditing.deck);
          mockDeckProject(mockEditing.deck);
        }
      }
      result.status="opened";break;
    case "deck.search":{
      if(Object.keys(p).sort().join(",")!=="name,query,requestId"||
        typeof p.query!=="string"||p.query.length>120||/[\u0000-\u001f\u007f-\u009f]/.test(p.query)){
        result.status="invalid";break;
      }
      if(!deck.open){result.status="missing";break}
      if(deck.transient){result.status="unsupported";break}
      mockDeckProject(deck,p.query);result.status="searched";break;
    }
    case "deck.jump":{
      if(Object.keys(p).sort().join(",")!=="name,requestId,steps"||
        !Number.isInteger(p.steps)||p.steps< -1000||p.steps>1000){
        result.status="invalid";break;
      }
      if(!deck.open){result.status="missing";break}
      if(deck.transient){result.status="unsupported";break}
      if(deck.aiLocked||deck.busy){result.status="locked";break}
      const activeDrafts=mockDeckDrafts.filter(card=>
        card.status!=="DISCARDED"&&card.status!=="COMMITTED");
      const index=activeDrafts.findIndex(card=>card.draftId===deck.currentId);
      if(index<0){result.status="failed";break}
      mockFlushDeck(deck);
      const target=Math.max(0,Math.min(activeDrafts.length-1,index+p.steps));
      mockSelectDeckCard(deck,activeDrafts[target]);
      result.status="moved";break;
    }
    case "deck.proposeReplace":{
      if(Object.keys(p).sort().join(",")!=="eventSeq,expectedRevision,name,requestId"||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0){
        result.status="invalid";break;
      }
      if(!deck.open||deck.transient){result.status="missing";break}
      if(editor.open||deck.aiLocked||deck.busy){result.status="locked";break}
      const searched=deck.formalMatches?.some(record=>
        record.eventSeq===p.eventSeq&&record.revision===p.expectedRevision);
      const record=mockDeckFormalEvents.find(value=>value.eventSeq===p.eventSeq);
      if(!searched||!record||record.revision!==p.expectedRevision){result.status="stale";break}
      let card=mockDeckDrafts.find(value=>value.status!=="DISCARDED"&&
        value.status!=="COMMITTED"&&value.eventSeq===p.eventSeq&&
        ["REPLACE","CONVERT"].includes(value.operation));
      if(!card){
        const form={...mockForm(record.kind),title:record.title};
        card={draftId:Math.max(...mockDeckDrafts.map(value=>value.draftId))+1,
          eventSeq:record.eventSeq,draftVersion:0,title:record.title,kind:record.kind,
          status:"PENDING",operation:"REPLACE",baseRevision:record.revision};
        mockDeckDrafts.push(card);
      }
      deck.currentId=card.draftId;deck.version=card.draftVersion;
      deck.form={...mockForm(card.kind),title:card.title};deck.baseline={...deck.form};
      deck.dirty=false;mockDeckProject(deck);
      result.status="opened";result.detail=String(card.draftId);break;
    }
    case "deck.retired":{
      if(Object.keys(p).sort().join(",")!=="name,offset,requestId"||
        !Number.isSafeInteger(p.offset)||p.offset<0){result.status="invalid";break}
      if(!deck.open||deck.transient){result.status="missing";break}
      if(editor.open||deck.aiLocked||deck.busy||deck.dirty){result.status="locked";break}
      mockSetRetiredPage(deck,p.offset);result.status="loaded";break;
    }
    case "deck.beginRestore":{
      if(Object.keys(p).sort().join(",")!=="eventSeq,expectedRevision,name,requestId"||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0){
        result.status="invalid";break;
      }
      if(!deck.open||deck.transient){result.status="missing";break}
      if(editor.open||deck.aiLocked||deck.busy||deck.dirty){result.status="locked";break}
      const record=deck.retiredResults?.find(value=>value.eventSeq===p.eventSeq&&
        value.revision===p.expectedRevision);
      if($("scenario").value!=="conflict-restore"||deck.retiredLoaded!==true||!record){
        result.status="stale";break;
      }
      if(deck.conflict||deck.restore){result.status="locked";break}
      let card=mockDeckDrafts.find(value=>value.status!=="DISCARDED"&&
        value.status!=="COMMITTED"&&value.eventSeq===record.eventSeq&&
        value.operation==="RESTORE");
      if(!card){
        card={draftId:8000+record.eventSeq,eventSeq:record.eventSeq,draftVersion:0,
          title:record.title,kind:record.kind,status:"PENDING",operation:"RESTORE",
          baseRevision:record.revision};
        mockDeckDrafts.push(card);
      }
      const form={...mockForm(record.kind),title:record.title,
        startText:record.startText,endText:record.endText};
      deck.currentId=card.draftId;deck.version=card.draftVersion;
      deck.form=form;deck.baseline={...form};deck.dirty=false;
      deck.restore={eventSeq:record.eventSeq,draftId:card.draftId,
        draftVersion:card.draftVersion,expectedRevision:record.revision,
        message:"事件已删除。预览夹具可申请宿主确认恢复原事件身份。",
        current:{...form},proposed:{...form}};
      deck.conflict=null;mockDeckProject(deck);result.status="opened";break;
    }
    case "deck.keepConflict":
    case "deck.requestConflictReview":{
      if(Object.keys(p).sort().join(",")!==
        "draftId,draftVersion,eventSeq,expectedRevision,name,requestId"||
        !Number.isSafeInteger(p.draftId)||p.draftId<=0||
        !Number.isSafeInteger(p.draftVersion)||p.draftVersion<0||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0){
        result.status="invalid";break;
      }
      const prompt=deck.conflict;
      if(!deck.open||!prompt){result.status="missing";break}
      if(editor.open||deck.aiLocked||deck.busy){result.status="locked";break}
      if(prompt.draftId!==p.draftId||prompt.draftVersion!==p.draftVersion||
        prompt.eventSeq!==p.eventSeq||prompt.currentRevision!==p.expectedRevision){
        result.status="stale";break;
      }
      if(p.name==="deck.keepConflict"){
        deck.conflict=null;mockDeckProject(deck,deck.searchQuery);result.status="kept";
      }else{
        mockApproval={kind:"deckConflictReview",requestId:p.requestId,
          draftId:p.draftId,draftVersion:p.draftVersion,eventSeq:p.eventSeq,
          expectedRevision:p.expectedRevision};
        $("mockApprovalText").textContent=
          "预览 Mock：冲突替换需由模拟宿主受信确认；此按钮不写入真实数据。";
        $("mockApproval").hidden=false;result.status="confirmation_required";
      }
      break;
    }
    case "deck.keepRestore":
    case "deck.requestRestoreReview":{
      if(Object.keys(p).sort().join(",")!==
        "draftId,draftVersion,eventSeq,expectedRevision,name,requestId"||
        !Number.isSafeInteger(p.draftId)||p.draftId<=0||
        !Number.isSafeInteger(p.draftVersion)||p.draftVersion<0||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0){
        result.status="invalid";break;
      }
      const prompt=deck.restore;
      if(!deck.open||!prompt){result.status="missing";break}
      if(editor.open||deck.aiLocked||deck.busy){result.status="locked";break}
      if(prompt.draftId!==p.draftId||prompt.draftVersion!==p.draftVersion||
        prompt.eventSeq!==p.eventSeq||prompt.expectedRevision!==p.expectedRevision){
        result.status="stale";break;
      }
      if(p.name==="deck.keepRestore"){
        deck.restore=null;mockDeckProject(deck,deck.searchQuery);result.status="kept";
      }else{
        mockApproval={kind:"deckRestoreReview",requestId:p.requestId,
          draftId:p.draftId,draftVersion:p.draftVersion,eventSeq:p.eventSeq,
          expectedRevision:p.expectedRevision};
        $("mockApprovalText").textContent=
          "预览 Mock：恢复原事件需由模拟宿主受信确认；此按钮不写入真实数据。";
        $("mockApproval").hidden=false;result.status="confirmation_required";
      }
      break;
    }
    case "deck.patch":
      if(deck.open&&deck.currentId===p.draftId){
        const next=mockPatch(deck.form,p.field,p.value);
        if(!next)result.status="invalid";
        else {deck.form=next;deck.dirty=JSON.stringify(next)!==JSON.stringify(deck.baseline);
          const current=mockDeckDrafts.find(card=>card.draftId===deck.currentId);
          if(current){current.title=deck.form.title;current.kind=deck.form.kind}
          result.status="edited"}
      }else result.status="stale";break;
    case "deck.flush":
      if(deck.open){
        mockFlushDeck(deck);
        if(!deck.transient)mockDeckProject(deck,deck.searchQuery);
        result.status="flushed";
      }
      break;
    case "deck.commit":
      if(!deck.open||deck.currentId==null){result.status="missing";break}
      if(deck.transient){result.status="unsupported";break}
      {
        const card=mockDeckDrafts.find(value=>value.draftId===p.draftId&&
          value.status!=="DISCARDED"&&value.status!=="COMMITTED");
        if(!card||p.draftId!==deck.currentId||p.draftVersion!==deck.version){
          result.status="stale";break;
        }
        mockFlushDeck(deck);
        mockApproval={kind:"deckCommitReview",requestId:p.requestId,
          draftId:card.draftId,draftVersion:deck.version};
        $("mockApprovalText").textContent=
          "预览 Mock：发布需由模拟宿主确认；确认只改变本地预览夹具，不写入真实日历数据。";
        $("mockApproval").hidden=false;result.status="confirmation_required";
      }
      break;
    case "deck.discard":
      if(!deck.open||deck.currentId==null){result.status="missing";break}
      if(deck.transient){mockEditing.deck={...deck,open:false};result.status="discarded";break}
      {
        const active=mockDeckDrafts.filter(value=>
          value.status!=="DISCARDED"&&value.status!=="COMMITTED");
        const index=active.findIndex(value=>value.draftId===deck.currentId);
        const card=active[index];
        if(!card||p.draftId!==deck.currentId||p.draftVersion!==deck.version){
          result.status="stale";break;
        }
        mockFlushDeck(deck);card.status="DISCARDED";mockDeckDiscarded++;
        mockLastDiscardId=card.draftId;
        const remaining=mockDeckDrafts.filter(value=>
          value.status!=="DISCARDED"&&value.status!=="COMMITTED");
        const next=remaining[Math.min(index,remaining.length-1)];
        if(next){
          deck.currentId=next.draftId;deck.version=Number(next.draftVersion)||0;
          deck.form=next.form?{...next.form}:{...mockForm(next.kind),title:next.title};
          deck.baseline={...deck.form};deck.dirty=false;
        }else{
          deck.currentId=null;deck.version=0;deck.form=null;deck.baseline=null;deck.dirty=false;
        }
        mockDeckProject(deck,deck.searchQuery);result.status="discarded";
      }
      break;
    case "deck.close":
      if(deck.open)mockFlushDeck(deck);
      mockEditing.deck={...deck,open:false};result.status="closed";break;
    case "deck.undo":
      if(!deck.open||deck.transient||mockLastDiscardId==null){result.status="missing";break}
      {
        const card=mockDeckDrafts.find(value=>value.draftId===mockLastDiscardId&&
          value.status==="DISCARDED");
        if(!card){result.status="missing";break}
        const duplicate=mockDeckDrafts.some(value=>value!==card&&
          value.eventSeq===card.eventSeq&&value.status!=="DISCARDED"&&
          value.status!=="COMMITTED");
        if(duplicate){result.status="conflict";break}
        card.status="PENDING";mockDeckDiscarded=Math.max(0,mockDeckDiscarded-1);
        mockLastDiscardId=null;deck.currentId=card.draftId;
        deck.version=Number(card.draftVersion)||0;
        deck.form=card.form?{...card.form}:{...mockForm(card.kind),title:card.title};
        deck.baseline={...deck.form};deck.dirty=false;
        mockDeckProject(deck,deck.searchQuery);result.status="restored";
      }
      break;
    case "deck.select":{
      if(Object.keys(p).sort().join(",")!=="draftId,name,requestId"||
        !Number.isSafeInteger(p.draftId)){result.status="invalid";break}
      if(!deck.open){result.status="missing";break}
      if(deck.transient){result.status="missing";break}
      const card=mockDeckDrafts.find(value=>value.draftId===p.draftId&&
        value.status!=="DISCARDED"&&value.status!=="COMMITTED");
      if(!card){result.status="missing";break}
      mockSelectDeckCard(deck,card);result.status="selected";break;
    }
  }
  mockEditing.lastResult=result;
  logAction("预览编辑命令 "+p.name+
    (p.name==="deck.create"?" · "+p.kind+
      (mockPage==="MONTH"?" · "+mockSelectedMonthDate:""):"")+" → "+result.status);
  try{previewPort?.postMessage(JSON.stringify({version:1,type:"result",payload:result}))}
  catch(e){logAction("编辑回执失败 "+e.message)}
  postState();
}
function onSkinAction(message){
  try{
    if(typeof message!=="string"||message.length>8192)return;
    const envelope=JSON.parse(message);
    if(envelope.version!==1||!envelope.payload)return;
    if(!Array.isArray(manifest?.capabilities)||!manifest.capabilities.includes("host.action")){
      logAction("拒绝 Preview Host 请求：皮肤未声明 host.action");return;
    }
    if(envelope.type==="command"){onSkinCommand(envelope.payload);return}
    if(envelope.type!=="action")return;
    const p=envelope.payload,name=p.name;
    if(name==="loadMoreMemo"){
      if(Object.keys(p).length!==1||$("scenario").value!=="memo-overflow"||mockPage!=="MEMO"){
        logAction("拒绝当前状态下的 Memo 续载请求");return;
      }
      mockMemoVisibleLimit=Math.min(251,mockMemoVisibleLimit+250);
      logAction("模拟宿主收到：loadMoreMemo");postState();return;
    }
    if(!ACTIONS.some(([x])=>x===name)&&!["openEvent","toggleMemo","sendChat","resolveAiProposal",
      "pickAiFile","pickAiImage","prepareAiBatch","cancelAiBatch","releaseStuckAiBatch",
      "clearChat","rewindChat"].includes(name)){
      logAction("拒绝未知动作 "+String(name));return;
    }
    if(name==="panTimeline"){
      if(!Number.isInteger(p.deltaMinutes)||Math.abs(p.deltaMinutes)>2880)return;
      const state=lastState||simulatedState();
      state.timeline.anchorMillis+=p.deltaMinutes*60000;
      // Keep a mock camera anchor in a distinct variable so other state changes preserve it.
      mockAnchor=state.timeline.anchorMillis;
    }
    if(name==="zoomTimeline"){
      if(Object.keys(p).sort().join(",")!=="direction,name"||
        !["in","out"].includes(p.direction)||
        mockPage!=="TIMELINE"){
        logAction("拒绝无效 zoomTimeline payload 或非时间轴请求");return;
      }
      const factor=p.direction==="in"?1/1.5:1.5;
      mockHours=Math.max(0.25,Math.min(48,mockHours*factor));
    }
    if(name==="resetTimelineZoom"){
      if(Object.keys(p).join(",")!=="name"||mockPage!=="TIMELINE"){
        logAction("拒绝无效 resetTimelineZoom payload 或非时间轴请求");return;
      }
      mockHours=24;
    }
    if(name==="jumpTimelineDay"&&Object.keys(p).sort().join(",")==="date,name"&&
      (mockPage==="TIMELINE"||mockPage==="MONTH")&&/^\d{4}-\d{2}-\d{2}$/.test(p.date||"")){
      const localDate=new Date(p.date+"T00:00:00Z");
      if(Number.isFinite(localDate.getTime())&&localDate.toISOString().slice(0,10)===p.date&&
        localDate.getUTCFullYear()>=1900&&localDate.getUTCFullYear()<=2200){
        mockAnchor=new Date(p.date+"T04:00:00Z").getTime();mockPage="TIMELINE";
      }
    }
    if(name==="today"){
      if(mockPage==="MONTH"){
        mockMonth="2026-10";mockSelectedMonthDate="2026-10-22";
      }else mockAnchor=null;
    }
    if(name==="browseMonth"&&Number.isInteger(p.deltaMonths)&&
      Object.keys(p).sort().join(",")==="deltaMonths,name"&&mockPage==="MONTH"&&
      p.deltaMonths!==0&&Math.abs(p.deltaMonths)<=12){
      const [year,month]=mockMonth.split("-").map(Number);
      const next=new Date(Date.UTC(year,month-1+p.deltaMonths,1));
      if(next.getUTCFullYear()>=1900&&next.getUTCFullYear()<=2100){
        mockMonth=next.toISOString().slice(0,7);
        mockSelectedMonthDate=mockMonth+"-01";
      }
    }
    if(name==="selectMonthDate"&&(mockPage!=="MONTH"||
      Object.keys(p).sort().join(",")!=="date,name"||
      !/^\d{4}-\d{2}-\d{2}$/.test(p.date||""))){
      logAction("拒绝非月历页面或格式无效的 selectMonthDate 请求");return;
    }
    if(name==="selectMonthDate"&&
      /^\d{4}-\d{2}-\d{2}$/.test(p.date||"")&&p.date.slice(0,7)===mockMonth){
      const date=new Date(p.date+"T00:00:00Z");
      if(Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===p.date&&
        date.getUTCFullYear()>=1900&&date.getUTCFullYear()<=2100)
        mockSelectedMonthDate=p.date;
    }
    if(name==="selectMonth"&&(mockPage!=="MONTH"||
      Object.keys(p).sort().join(",")!=="month,name"||
      !/^\d{4}-(0[1-9]|1[0-2])$/.test(p.month||""))){
      logAction("拒绝非月历页面或格式无效的 selectMonth 请求");return;
    }
    if(name==="selectMonth"&&
      /^\d{4}-(0[1-9]|1[0-2])$/.test(p.month||"")){
      const year=Number(p.month.slice(0,4));
      if(year>=1900&&year<=2100){mockMonth=p.month;mockSelectedMonthDate=mockMonth+"-01"}
    }
    if(["timeline","memo","ai"].includes(name))mockPage=name.toUpperCase();
    if(name==="settings"){
      if(Object.keys(p).sort().join(",")!=="name"){
        logAction("拒绝无效 settings payload");return;
      }
      mockPage="SETTINGS";
      logAction("Studio Mock 打开设置页；通知权限状态为模拟值");postState();return;
    }
    if(name==="requestNotificationPermission"){
      if(Object.keys(p).sort().join(",")!=="name"||mockPage!=="SETTINGS"){
        logAction("拒绝非设置页或格式无效的通知权限请求");return;
      }
      logAction("Studio Mock 收到通知权限请求；不会调用 Android 系统权限或修改真实状态");
      return;
    }
    if(name==="nativeSettings"){
      if(Object.keys(p).sort().join(",")!=="name"){
        logAction("拒绝无效 nativeSettings payload");return;
      }
      logAction("Studio Mock 收到原生设置/恢复路由；此页面不启动 Android Host");
      return;
    }
    if(name==="month"){
      const anchor=mockAnchor??new Date("2026-10-22T04:00:00Z").getTime();
      const date=new Date(anchor+8*3600000).toISOString().slice(0,10);
      mockMonth=date.slice(0,7);mockSelectedMonthDate=date;mockPage="MONTH";
    }
    if(name==="toggleMemo"){
      const expectedKeys="completed,eventSeq,expectedRevision,name,occurrenceKey";
      const target=lastState?.memoEntries?.find(item=>item.eventSeq===p.eventSeq&&
        item.revision===p.expectedRevision&&item.occurrenceKey===p.occurrenceKey);
      if(Object.keys(p).sort().join(",")!==expectedKeys||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0||
        typeof p.occurrenceKey!=="string"||typeof p.completed!=="boolean"||
        !target||target.completed!==p.completed){
        logAction("Studio Mock 拒绝过期或不匹配的 Memo 槽位请求");
      }else{
        mockMemoCompletion.set(`${p.eventSeq}:${p.occurrenceKey}`,!target.completed);
        mockMemoRevision.set(p.eventSeq,target.revision+1);
        logAction("Studio Mock 切换 Memo 槽位 "+p.occurrenceKey);
      }
      postState();return;
    }
    if(name==="openEvent"){
      const hasKey=Object.prototype.hasOwnProperty.call(p,"occurrenceKey");
      const expectedKeys=hasKey?"eventSeq,expectedRevision,name,occurrenceKey":
        "eventSeq,expectedRevision,name";
      const key=p.occurrenceKey;
      const projected=[...(lastState?.memoEntries||[]),...(lastState?.month?.items||[]),
        ...(lastState?.timeline?.items||[])];
      const occurrenceMatch=hasKey&&projected.some(item=>item.eventSeq===p.eventSeq&&
        item.revision===p.expectedRevision&&item.originalOccurrenceKey===key);
      const ownerMatch=!hasKey&&lastState?.events?.some(item=>item.eventSeq===p.eventSeq&&
        item.revision===p.expectedRevision);
      if(Object.keys(p).sort().join(",")!==expectedKeys||
        !Number.isSafeInteger(p.eventSeq)||p.eventSeq<=0||
        !Number.isSafeInteger(p.expectedRevision)||p.expectedRevision<=0||
        (hasKey&&(typeof key!=="string"||!key.length||key.length>100||
          !/^[\p{L}\p{N}:_.-]+$/u.test(key)))||
        !(hasKey?occurrenceMatch:ownerMatch)){
        logAction("Studio Mock 拒绝过期或不匹配的 openEvent 请求");
      }else{
        logAction("Studio Mock accepted openEvent eventSeq="+p.eventSeq+
          " revision="+p.expectedRevision+" occurrenceKey="+(hasKey?key:"none"));
      }
      postState();return;
    }
    if(name==="clearChat"){
      if(Object.keys(p).sort().join(",")!=="historyRevision,name"||
        !Number.isSafeInteger(p.historyRevision)||p.historyRevision<0||
        mockPage!=="AI"||!simulatedState().chatHistoryActionsAvailable||
        p.historyRevision!==mockChatHistoryRevision){
        logAction("拒绝过期或当前不可用的聊天清理请求");return;
      }
      if(simulatedState().chatMessages.length===0){
        logAction("Preview Mock：聊天已经为空；清理无操作");return;
      }
      mockChat=[];mockAiErrorMessages=[];mockChatCleared=true;
      mockAiProposals=[];rotateMockAiReviewId();
      mockChatHistoryRevision++;
      logAction("Preview Mock：清空 AI 聊天历史；没有修改事件或草稿");
      postState();return;
    }
    if(name==="rewindChat"){
      const expectedKeys="editAndResend,historyRevision,messageId,name";
      const messages=simulatedState().chatMessages;
      const matches=messages.map((item,index)=>({item,index}))
        .filter(row=>row.item.messageId===p.messageId);
      if(Object.keys(p).sort().join(",")!==expectedKeys||
        typeof p.messageId!=="string"||p.messageId.length<1||p.messageId.length>100||
        !Number.isSafeInteger(p.historyRevision)||p.historyRevision<0||
        typeof p.editAndResend!=="boolean"||mockPage!=="AI"||
        !simulatedState().chatHistoryActionsAvailable||
        p.historyRevision!==mockChatHistoryRevision||matches.length!==1||
        (p.editAndResend&&matches[0]?.item.role!=="user")){
        logAction("拒绝过期或不匹配的聊天回退请求");return;
      }
      const {item,index}=matches[0];
      const keep=index+(item.role==="user"?0:1);
      if(keep===messages.length&&!p.editAndResend){
        logAction("Preview Mock：助手回复后没有后续对话；回退无操作");return;
      }
      mockChat=messages.slice(0,keep).map(message=>({...message}));
      mockAiErrorMessages=[];mockChatCleared=true;mockChatHistoryRevision++;
      if(p.editAndResend){mockChatPrefillText=item.content;mockChatPrefillRevision++;}
      logAction("Preview Mock：回退聊天历史；没有修改事件或草稿");
      postState();return;
    }
    if(name==="prepareAiBatch"){
      if(Object.keys(p).sort().join(",")!=="mode,name"||
        !["publish","discard"].includes(p.mode)||mockPage!=="AI"||mockAiLocked){
        logAction("拒绝无效或锁定状态下的批次范围选择");return;
      }
      mockAiBatch={phase:"SELECTING",state:null,active:false,canCancel:false,
        canRelease:false,mode:p.mode.toUpperCase(),scopeCount:0,selectedCount:0,
        operationCounts:{},containsDelete:false,progress:null,report:null};
      pushMockChat({role:"assistant",content:"Preview Mock：请输入“全部”模拟选择范围。最终 Host 审核不会写入真实日历。"});
      postState();return;
    }
    if(name==="cancelAiBatch"){
      if(Object.keys(p).join(",")!=="name"||mockPage!=="AI")return;
      if(mockAiBatch.phase==="SELECTING"){
        mockAiBatch={phase:"IDLE",state:null,active:false,canCancel:false,canRelease:false,
          mode:null,scopeCount:0,selectedCount:0,operationCounts:{},containsDelete:false,
          progress:null,report:null};
        pushMockChat({role:"assistant",content:"Preview Mock：已取消范围选择，未改动草稿。"});
      }else if(mockAiBatch.phase==="RUNNING"&&mockAiLocked){
        const mode=mockAiBatch.mode||"PUBLISH",count=mockAiBatch.scopeCount||3;
        mockAiBatch={phase:"CANCELLED",state:"CANCELLED",active:false,canCancel:false,
          canRelease:false,mode,scopeCount:count,selectedCount:0,operationCounts:{},
          containsDelete:false,progress:null,report:{mode,
            published:mode==="PUBLISH"?1:0,discarded:mode==="DISCARD"?1:0,
            kept:Math.max(0,count-1),cancelled:true}};
        mockAiLocked=false;
        pushMockChat({role:"assistant",content:"Preview Mock：已模拟部分完成后停止；剩余草稿保留。没有写入真实数据。"});
      }else{logAction("拒绝当前状态下的批次取消");return;}
      logAction("Preview Mock 批次取消 · 结果仅为模拟");postState();return;
    }
    if(name==="releaseStuckAiBatch"){
      if(Object.keys(p).join(",")!=="name"||mockPage!=="AI"||!mockAiLocked||
        mockAiBatch.phase!=="CANCEL_REQUESTED"||!mockAiBatch.canRelease){
        logAction("仅停止请求超时后允许批次恢复");return;
      }
      mockApproval={kind:"aiBatchRelease"};
      $("mockApprovalText").textContent="Preview Mock：模拟 Host 确认强制解除批次锁；未完成草稿保留，不写入真实数据。";
      $("mockApproval").hidden=false;postState();return;
    }
    if(name==="sendChat"&&typeof p.text==="string"&&p.text.length<=4000){
      if(mockAiBatch.phase==="SELECTING"){
        pushMockChat({role:"user",content:p.text});
        if(["全部","所有","全选","全部草稿","所有待处理"].includes(p.text.trim())){
          const reviewId="batch-review-"+Date.now().toString(36);
          const mode=mockAiBatch.mode||"PUBLISH";
          mockAiBatch={phase:"AWAITING_HOST_REVIEW",state:null,active:false,
            canCancel:false,canRelease:false,mode,scopeCount:3,selectedCount:3,
            operationCounts:{CREATE:2,REPLACE:1},containsDelete:false,progress:null,
            report:null,reviewId};
          mockApproval={kind:"aiBatch",reviewId};
          $("mockApprovalText").textContent="Preview Mock Host 正在审核 3 张草稿并模拟一次性授权；不会写入真实数据。";
          $("mockApproval").hidden=false;
        }else pushMockChat({role:"assistant",content:"Preview Mock：请回复“全部”模拟选择；没有调用真实 AI。"});
        postState();return;
      }
      mockChatPrefillText="";mockChatPrefillRevision++;
      if(mockChatCleared){
        pushMockChat({role:"user",content:p.text});
        pushMockChat({role:"assistant",content:"预览 Mock：消息已保留在本地聊天历史；没有调用真实 AI 或写入日历。"});
      }else if($("scenario").value==="ai-error"){
        mockAiErrorMessages.push({role:"user",content:p.text},
          {role:"assistant",content:"Studio Mock：已收到你手动重发的消息；没有调用真实 AI 或写入日历。"});
        mockChatHistoryRevision++;
      }else pushMockChat({role:"user",content:p.text});
    }
    if(name==="pickAiFile"||name==="pickAiImage"){
      if(Object.keys(p).length!==1||mockPage!=="AI"||scenario==="locked"){
        logAction("拒绝当前状态下的附件选择请求");return;
      }
      const reply=name==="pickAiFile"?
        "预览 Mock：未打开真实选择器，也不会读取、上传或导入文件。":
        "预览 Mock：未打开真实选择器，也不会读取、上传或识别图片。";
      pushMockChat({role:"assistant",content:reply});
      logAction(reply);postState();
      return;
    }
    if(name==="resolveAiProposal"){
      const state=simulatedState(),review=state.aiProposalReview;
      const decisions=["publish","stage","reject","supplement"];
      if(!review||!/^([0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})$/.test(p.reviewId||"")||
        p.reviewId!==review.reviewId||!decisions.includes(p.decision)||!Array.isArray(p.indices)||
        p.indices.length>100||
        p.indices.some(i=>!Number.isInteger(i)||i<0||i>=review.proposals.length)||
        new Set(p.indices).size!==p.indices.length||
        (p.decision==="supplement"?p.indices.length!==0:p.indices.length===0)){
        logAction("拒绝过期或无效 AI 审核请求");return;
      }
      const chosen=p.indices.map(i=>review.proposals[i]);
      if(chosen.some(item=>item.blocked)||
        (p.decision==="publish"&&chosen.some(item=>!item.publishable))){
        logAction("拒绝不可发布或未补全的 AI 候选");return;
      }
      if(p.decision==="publish"){
        mockApproval={kind:"aiProposal",reviewId:p.reviewId,indices:p.indices};
        $("mockApprovalText").textContent="Mock Host 正在模拟确认："+
          chosen.map(item=>`${item.operation} · ${item.title}`).join("；")+
          "。这不是 Android 权限或真实数据验收。";
        $("mockApproval").hidden=false;
        logAction("AI 正式操作请求等待 Mock Host 确认");return;
      }
      if(p.decision==="stage"||p.decision==="reject"){
        const chosen=new Set(p.indices);
        mockAiProposals=mockAiProposals.filter((_,index)=>!chosen.has(index));
        rotateMockAiReviewId();
        pushMockChat({role:"assistant",content:p.decision==="stage"?
          "预览 Mock：候选已模拟写入草稿，未写入真实数据。":"预览 Mock：候选已模拟拒绝，未写入真实数据。"});
      }else if(p.decision==="supplement"){
        pushMockChat({role:"assistant",content:"请补充需要更正的字段；预览不会调用真实 AI。"});
      }else return;
      logAction("模拟宿主收到：resolveAiProposal · "+p.decision);postState();return;
    }
    if(name==="createDraft"){
      if(Object.keys(p).sort().join(",")!=="kind,name"||
        !["ACTIVITY","ALARM","DEADLINE","MEMO"].includes(p.kind))return;
      const base=new Date("2026-10-22T04:00:00Z").getTime();
      const selectedDate=mockPage==="MONTH"?mockSelectedMonthDate:
        new Date((mockAnchor??base)+8*3600000).toISOString().slice(0,10);
      logAction(`模拟宿主收到：createDraft · ${p.kind} · ${selectedDate}（不会写入真实数据）`);
      return;
    }
    if(name==="invokeOption"&&$("scenario").value==="locked"){
      logAction("锁定状态拒绝执行选项");return;
    }
    if(name==="invokeOption"&&p.id==="data.legacy.migrate"){
      logAction("Preview Mock：迁移请求仅模拟到达；未读取、写入或删除任何数据库。Android 需经宿主确认。");
      return;
    }
    logAction("模拟宿主收到："+name+
      (name==="zoomTimeline"?" · "+p.direction+" · "+mockHours+" 小时":"")+
      (name==="resetTimelineZoom"?" · "+mockHours+" 小时":"")+
      (p.id?" · "+p.id:"")+
      (name==="openEvent"?" · "+p.eventSeq:""));
    if(["today","panTimeline","zoomTimeline","resetTimelineZoom","jumpTimelineDay","browseMonth","selectMonthDate","selectMonth","timeline","month","memo","ai",
      "toggleMemo","sendChat"].includes(name))postState();
  }catch(error){logAction("Bridge 消息错误："+error.message)}
}
let mockAnchor=null,mockHours=24,mockPage="TIMELINE",mockMemoDone=false;
const isLockedMockScenario=()=>["locked","locked-stale"].includes($("scenario").value);
const idleMockAiBatch=()=>({phase:"IDLE",state:null,active:false,canCancel:false,
  canRelease:false,mode:null,scopeCount:0,selectedCount:0,operationCounts:{},
  containsDelete:false,progress:null,report:null});
const runningMockAiBatch=()=>({phase:"RUNNING",state:"RUNNING",active:true,
  canCancel:true,canRelease:false,mode:"PUBLISH",scopeCount:3,selectedCount:0,
  operationCounts:{CREATE:3},containsDelete:false,progress:{done:1,total:3},report:null});
const staleCancelMockAiBatch=()=>({phase:"CANCEL_REQUESTED",state:"CANCEL_REQUESTED",active:true,
  canCancel:false,canRelease:true,mode:"PUBLISH",scopeCount:3,selectedCount:0,
  operationCounts:{CREATE:3},containsDelete:false,progress:{done:1,total:3},report:null});
let mockAiLocked=isLockedMockScenario();
let mockAiBatch=$("scenario").value==="locked-stale"?staleCancelMockAiBatch():
  mockAiLocked?runningMockAiBatch():idleMockAiBatch();
let mockConflictRestoreActive=$("scenario").value==="conflict-restore";
let mockMemoCompletion=new Map(),mockMemoRevision=new Map(),mockChat=[];
let mockChatHistoryRevision=0,mockChatPrefillRevision=0,mockChatPrefillText="",mockChatCleared=false;
let mockChatSequence=0;
function pushMockChat(message){
  mockChat.push({...message,messageId:message.messageId||"studio-chat-"+(++mockChatSequence)});
  mockChatHistoryRevision++;
}
let mockMonth="2026-10",mockSelectedMonthDate="2026-10-22";
let mockMemoVisibleLimit=250;
let mockAiErrorMessages=[
  {role:"user",content:"请帮我概括明天的安排。"},
  {role:"assistant",content:"Studio Mock：AI 请求未完成（模拟 HTTP 503）；上下文已保留，可编辑后手动重发。"}
];
let mockAiReviewId="41b2c6f7-6070-44b5-93ed-a922b5a447db",mockAiReviewGeneration=0;
function rotateMockAiReviewId(){
  mockAiReviewGeneration++;
  mockAiReviewId="41b2c6f7-6070-44b5-93ed-"+
    String(mockAiReviewGeneration).padStart(12,"0").slice(-12);
}
const defaultMockAiProposals=()=>[
  {operation:"CREATE",eventSeq:null,targetRevision:null,kind:"ACTIVITY",title:"方案评审",
    startText:"2026-10-22 14:00",endText:"2026-10-22 15:00",zoneId:"Asia/Shanghai",
    location:"会议室 A",notes:"核对预算和时间安排",reminderMinutes:15,reminderMethod:"MESSAGE",
    reminderTiming:"OFFSET",enabled:true,trackingEnabled:false,
    recurrence:{frequency:"NONE",interval:1,byDay:[],count:null,untilMillis:null},
    rationale:"来自已明确的日程请求",blocked:null,publishable:true},
  {operation:"CREATE",eventSeq:null,targetRevision:null,kind:"ALARM",title:"通话提醒",
    startText:"",endText:"",zoneId:"Asia/Shanghai",location:"",notes:"",
    reminderMinutes:-1,reminderMethod:"MESSAGE",reminderTiming:"OFFSET",enabled:true,
    trackingEnabled:false,recurrence:{frequency:"NONE",interval:1,byDay:[],count:null,untilMillis:null},
    rationale:"缺少具体时刻",blocked:"缺少具体时刻",publishable:false},
  {operation:"REPLACE",eventSeq:4,targetRevision:2,kind:"ACTIVITY",title:"调整上午课程",
    startText:"2026-10-22 10:30",endText:"2026-10-22 12:00",zoneId:"Asia/Shanghai",
    location:"教学楼 2",notes:"改到新教室",reminderMinutes:10,reminderMethod:"MESSAGE",
    reminderTiming:"OFFSET",enabled:true,trackingEnabled:false,
    recurrence:{frequency:"WEEKLY",interval:1,byDay:["THURSDAY"],count:8,
      untilMillis:null,activeFromDate:"2026-10-22",activeUntilDate:"2026-12-31",
      exceptions:["2026-11-05T09:00"],overrides:[]},
    rationale:"保留原事件身份并调整时间与地点",blocked:null,publishable:true},
  {operation:"DELETE",eventSeq:5,targetRevision:1,kind:"ALARM",title:"删除训练提醒",
    startText:"2026-10-22 17:00",endText:"",zoneId:"Asia/Shanghai",location:"",notes:"",
    reminderMinutes:-1,reminderMethod:"MESSAGE",reminderTiming:"OFFSET",enabled:true,
    trackingEnabled:false,recurrence:{frequency:"NONE",interval:1,byDay:[],count:null,
      untilMillis:null,exceptions:[],overrides:[]},rationale:"用户明确要求删除",blocked:null,publishable:true}
];
let mockAiProposals=defaultMockAiProposals();
function resetMockAiProposalReview(){
  mockAiProposals=defaultMockAiProposals();
  rotateMockAiReviewId();
}
function connectPreview(){
  if(previewPort){try{previewPort.close()}catch{}}
  const iframe=$("webPreview");
  if(!iframe.contentWindow)return;
  const channel=new MessageChannel();
  previewPort=channel.port1;
  previewPort.onmessage=e=>onSkinAction(e.data);
  // The iframe is sandboxed with an opaque origin. Its only host capability is
  // this one transferred MessagePort, exactly as in the Android WebView bridge.
  iframe.contentWindow.postMessage("powercal-port","*",[channel.port2]);
  lastState=null;postState();
  $("previewState").textContent="已加载源文件 · Bridge v1 模拟连接成功";
  logAction("预览已重载：文件与导出包内字节一致");
  refreshOverlay();
}
async function importZip(file){
  if(!file)return;
  if(file.size>10_000_000)throw Error("ZIP 超过 10MB");
  const next=await api("/api/import-v3",await file.arrayBuffer(),true);
  await loadPackage(next);
}
async function importProject(file){
  if(!file)return;
  if(file.size>24_000_000)throw Error("项目文件超过上限");
  const data=JSON.parse(await file.text());
  if(data.format==="powercal-v3-studio"&&data.manifest&&data.files){
    await loadPackage(data);return;
  }
  // Older .pcstudio-v3.json stored a three-file source without separate assets.
  // Convert it once; do not pretend missing external resources can be recovered.
  if(data.projectVersion===3 && data.source){
    const current=payload(),files=current.files;
    files["web/index.html"]=bytesToBase64(enc.encode(data.source.html||""));
    files["web/style.css"]=bytesToBase64(enc.encode(data.source.css||""));
    files["web/app.js"]=bytesToBase64(enc.encode(data.source.js||""));
    const meta=copy(data);delete meta.source;
    files["project.json"]=bytesToBase64(enc.encode(JSON.stringify(meta)));
    await loadPackage({manifest:current.manifest,files});
    say("已转换旧版源项目。旧格式没有携带的素材需要重新导入。");return;
  }
  throw Error("不支持的项目文件；请选择 v3 .pcskin 或 v3 Studio 项目");
}
async function exportPack(){
  const result=await api("/api/export-v3",payload());
  download(result,manifest.id+".pcskin");
  dirty=false;
  say("已导出 v3 包：使用与预览一致的 HTML/CSS/JS/素材字节，并完成哈希校验。");
}
function exportProject(){
  const object={format:"powercal-v3-studio",projectVersion:3,...payload()};
  download(new Blob([JSON.stringify(object,null,2)],{type:"application/json"}),
    manifest.id+".pcstudio-v3.json");
  dirty=false;say("已保存包含完整 Web 源码、图层与素材的 v3 编辑工程。");
}
function bindUI(){
  $("mockApprove").onclick=()=>resolveMockApproval(true);
  $("mockReject").onclick=()=>resolveMockApproval(false);
  $("simpleMode").onclick=()=>switchMode("simple");
  $("advancedMode").onclick=()=>switchMode("advanced");
  $("editMode").onclick=()=>{
    editCanvas=true;$("editMode").classList.add("active");
    $("interactMode").classList.remove("active");refreshOverlay();
  };
  $("interactMode").onclick=()=>{
    editCanvas=false;$("editMode").classList.remove("active");
    $("interactMode").classList.add("active");refreshOverlay();
  };
  $("refresh").onclick=()=>schedulePreview(0);
  $("webPreview").onload=()=>connectPreview();
  $("skinId").onchange=change;$("skinName").onchange=change;
  $("fullCanvas").onchange=()=>{fullCanvasTouched=true;change()};
  for(const id of ["scenario","theme","reduced"])$(id).onchange=()=>{
    if(id==="theme")$("webPreview").style.colorScheme=$("theme").value;
    mockAnchor=null;
    if(id==="scenario"){
      mockChat=[];mockChatCleared=false;mockChatHistoryRevision++;
      mockChatPrefillText="";mockChatPrefillRevision++;
      mockMemoVisibleLimit=250;
      mockMemoCompletion=new Map();
      mockMemoRevision=new Map();
      resetMockDeckFixtures();
      mockEditing={editor:{open:false},deck:{open:false},lastResult:null};
      mockApproval=null;$("mockApproval").hidden=true;
      mockConflictRestoreActive=$("scenario").value==="conflict-restore";
      mockAiLocked=isLockedMockScenario();
      mockAiBatch=$("scenario").value==="locked-stale"?staleCancelMockAiBatch():
        mockAiLocked?runningMockAiBatch():idleMockAiBatch();
      if($("scenario").value==="timeline-drag"){
        mockHours=24;mockPage="TIMELINE";
        mockEditing={editor:{open:false},deck:{open:false},lastResult:null};
      }
    }
    if(id==="scenario"&&$("scenario").value==="ai-error")mockAiErrorMessages=[
      {role:"user",content:"请帮我概括明天的安排。"},
      {role:"assistant",content:"Studio Mock：AI 请求未完成（模拟 HTTP 503）；上下文已保留，可编辑后手动重发。"}
    ];
    if(id==="scenario"&&["ai-review","ai-review-batch"].includes($("scenario").value))
      resetMockAiProposalReview();
    postState();
  };
  $("assetFile").onchange=async e=>{await addAssets(e.target.files);e.target.value=""};
  $("addText").onclick=()=>newLayer("text");
  $("addImage").onclick=()=>newLayer("image");
  $("addButton").onclick=()=>newLayer("button");
  $("removeLayer").onclick=removeLayer;
  for(const id of ["layerLabel","layerAsset","layerX","layerY","layerWidth",
    "layerHeight","layerZ","layerOpacity","layerAction"])$(id).onchange=updateLayer;
  for(const [id,path] of [["htmlCode","web/index.html"],["cssCode","web/style.css"],
    ["jsCode","web/app.js"]]){
    $(id).oninput=()=>{setText(path,$(id).value);change()};
  }
  $("extraSourceFile").onchange=()=>{
    const path=$("extraSourceFile").value;
    $("extraSourceCode").value=path==="project.json"&&projectDraftText!==null
      ?projectDraftText:getText(path);
    $("removeExtraSource").disabled=!path||REQUIRED_SOURCE_FILES.has(path);
  };
  $("extraSourceCode").oninput=()=>{
    const path=$("extraSourceFile").value;
    if(path==="project.json"){updateProjectSource($("extraSourceCode").value);return;}
    if(path&&sourceFiles.has(path)){setText(path,$("extraSourceCode").value);change()}
  };
  $("addTextSource").onclick=addTextSource;
  $("removeExtraSource").onclick=removeExtraSource;
  $("packageAssetFile").onchange=async event=>{
    await addPackageAssets(event.target.files);event.target.value="";
  };
  $("applyIcon").onclick=applyButtonIcon;
  $("addMaterial").onclick=()=>{
    const id=prompt("CSS 变量名（例如 accentColor）");
    if(!id)return;
    if(!/^[A-Za-z][A-Za-z0-9_.-]{0,63}$/.test(id)||project.slots.some(s=>s.id===id)){
      say("变量名不合法或已存在",true);return;
    }
    project.slots.push({id,label:id,type:"color",value:"#2457D6",editable:true});
    applyMaterialSource();renderMaterials();change();
  };
  $("importZip").onchange=async e=>{
    try{await importZip(e.target.files[0])}catch(error){say(error.message,true)}
    finally{e.target.value=""}
  };
  $("importProject").onchange=async e=>{
    try{await importProject(e.target.files[0])}catch(error){say(error.message,true)}
    finally{e.target.value=""}
  };
  $("exportProject").onclick=()=>{
    try{exportProject()}catch(error){say(error.message,true)}
  };
  $("validate").onclick=async()=>{
    try{await api("/api/preview-v3",payload());say("文件结构、体积、SHA-256、导入限制已通过；真机仍需设备验收。")}
    catch(error){say(error.message,true)}
  };
  $("exportPack").onclick=async()=>{
    try{await exportPack()}catch(error){say(error.message,true)}
  };
  window.addEventListener("beforeunload",e=>{
    if(dirty){e.preventDefault();e.returnValue="";}
  });
}
(async()=>{
  bindUI();renderActions();
  try{await loadPackage(await api("/api/template-v3"))}
  catch(error){say("模板载入失败："+error.message,true)}
})();
