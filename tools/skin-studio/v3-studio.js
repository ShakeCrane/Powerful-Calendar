"use strict";
/* Skin Studio v3: one canonical collection of bytes for editing, preview and export.
   No second DOM/CSS/JS renderer, no string-replacement mock preview. */
const $ = id => document.getElementById(id);
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
let projectSourceError=null, projectDraftText=null;
const REQUIRED_SOURCE_FILES=new Set(["web/index.html","web/style.css","web/app.js","project.json"]);
const TEXT_SOURCE_EXTENSIONS=/\.(cjs|css|html?|ini|js|json|jsx|kt|less|md|mjs|properties|py|scss|sh|sql|toml|tsx?|txt|xml|ya?ml|svg)$/i;
function say(message,isError=false){$("toast").textContent=message;$("toast").classList.toggle("error",isError)}
function setText(name,value){sourceFiles.set(name,enc.encode(value))}
function getText(name){return dec.decode(sourceFiles.get(name)||new Uint8Array())}
function parseProject(){
  const obj=JSON.parse(getText("project.json"));
  if(obj.projectVersion!==3 || !Array.isArray(obj.slots)) throw Error("无效的 v3 项目元数据");
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
  if(!$("fullCanvas").checked) throw Error(
    "全画布已关闭：Android 将用原生界面覆盖皮肤，无法保证预览=使用。请开启完整画布。");
  manifest.fullCanvas=true;
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
  projectSourceError=null;projectDraftText=null;
  project=parseProject();
  projectBaseline=JSON.stringify(project);
  mockEditing={editor:{open:false},deck:{open:false},lastResult:null};
  mockApproval=null;$("mockApproval").hidden=true;
  selectedLayer=project.layers[0]?.id||null;dirty=false;
  // Fork the protected factory identity before an ordinary user exports it.
  // Its HTML/CSS/JS/assets/project remain the exact bundled source.
  const factory = manifest.id==="powercal-clear-default";
  $("skinId").value=factory?"powercal-clear-custom":manifest.id;
  $("skinName").value=factory?"我的 PowerCal Clear":manifest.name;
  $("fullCanvas").checked=manifest.fullCanvas!==false;
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
       !["simple","advanced"].includes(next.mode)||!Array.isArray(next.slots)||next.slots.length>128)
      throw Error("必须是 projectVersion=3 且 slots 为数组的 JSON 对象");
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
  ["timeline","时间轴"],
  ["month","月历"],["memo","Memo"],["ai","AI"],
  ["settings","原生设置"],["openDeck","草稿卡组"],
  ["panTimeline","时间轴向后 30 分钟"],["jumpTimelineDay","跳到今天"],
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
  const anchor=mockAnchor??base,hours=24;
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
  if(scenario==="repeating")rows=[
    {...rows[0],eventSeq:8,occurrenceKey:"8:20261022T090000",
      originalOccurrenceKey:"2026-10-22T09:00",title:"重复课程 · 本次"},
    {...rows[0],eventSeq:8,occurrenceKey:"8:20261023T090000",title:"重复课程 · 明天",
      originalOccurrenceKey:"2026-10-23T09:00",
      startMillis:rows[0].startMillis+86400000,endMillis:rows[0].endMillis+86400000}
  ];
  const items=[
    {id:"calendar.import.file",label:"导入日历文件",detail:"本地优先",icon:"file",enabled:scenario!=="locked"},
    {id:"calendar.import.system",label:"从系统日历导入",detail:"静态快照",icon:"calendar",enabled:scenario!=="locked"}
  ];
  if(scenario==="future")items.push({
    id:"calendar.import.future",label:"新功能：导入课程表",
    detail:"模拟未来软件版本新增的平行选项",icon:"table",enabled:true
  });
  const start=anchor-hours*1800000;
  const chatMessages=scenario==="ai-error"
    ? [{role:"assistant",content:"你好！今天有什么安排？"},...mockAiErrorMessages]
    : [{role:"assistant",content:"你好！今天有什么安排？"},...mockChat];
  const memoEntries=scenario==="repeating"?[
    {eventSeq:31,revision:2,occurrenceKey:"2026-10-22T00:00",
      originalOccurrenceKey:"2026-10-22T00:00",title:"重复 Memo · 今天",
      completed:mockMemoDone,startMillis:base},
    {eventSeq:31,revision:2,occurrenceKey:"2026-10-23T00:00",
      originalOccurrenceKey:"2026-10-23T00:00",title:"重复 Memo · 明天",
      completed:false,startMillis:base+86400000}
  ]:[
    {eventSeq:31,revision:2,occurrenceKey:"single",title:"整理会议笔记",
      completed:mockMemoDone,startMillis:base},
    {eventSeq:44,revision:1,occurrenceKey:"single",title:"提交报告",
      completed:false,startMillis:base+7200000}
  ];
  return {bridgeVersion:1,page:mockPage,memoCount:2,aiLocked:scenario==="locked",
    reducedMotion:$("reduced").checked,pendingCount:3,
    editing:mockEditing,
    memoEntries,memoTruncated:false,chatBusy:false,chatAttachmentBusy:false,
    chatAttachmentAvailable:scenario!=="locked"&&!(scenario==="ai-review"&&mockAiProposals.length>0),
    chatMessages,
    aiProposalReview:scenario==="ai-review"&&mockAiProposals.length?{
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
    month:{zoneId:"Asia/Shanghai",month:"2026-10",
      selectedDate:"2026-10-22",gridFrom:"2026-09-28",
      itemLimit:350,truncated:false,
      items:rows.map(r=>({...r,date:"2026-10-22"}))}};
}
function postState(){
  if(!previewPort)return;
  lastState=simulatedState();
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
function resolveMockApproval(approved){
  if(!mockApproval)return;
  const request=mockApproval;mockApproval=null;$("mockApproval").hidden=true;
  $("mockApprovalText").textContent="预览模拟宿主确认，不会写入真实数据。";
  if(request.kind==="aiProposal"){
    if(request.reviewId!==mockAiReviewId){
      mockChat.push({role:"assistant",content:"预览 Mock：审核卡已过期，模拟 Host 拒绝了旧请求。"});
      postState();return;
    }
    if(approved){
      const selected=new Set(request.indices);
      mockAiProposals=mockAiProposals.filter((_,index)=>!selected.has(index));
      rotateMockAiReviewId();
      mockChat.push({role:"assistant",content:"预览 Mock：Host 确认请求已完成；没有写入任何真实日历数据。"});
    }else mockChat.push({role:"assistant",content:"预览 Mock：已取消 Host 确认，没有执行操作。"});
    postState();return;
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
    case "editor.open":
      const memoEntry=lastState?.memoEntries?.find(item=>item.eventSeq===p.eventSeq&&
        (!p.occurrenceKey||item.originalOccurrenceKey===p.occurrenceKey));
      const form={...mockForm(memoEntry?"MEMO":"ACTIVITY"),
        title:memoEntry?.title||"测试活动"};
      if(memoEntry?.originalOccurrenceKey)form.recurrenceFreq="DAILY";
      mockEditing.editor={open:true,sessionId:"00000000-0000-0000-0000-000000000001",
        eventSeq:p.eventSeq,dirty:false,requiresRecurrenceScope:!!p.occurrenceKey,
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
      mockEditing.deck={open:true,cards:[{draftId:id,title:form.title,kind:p.kind}],
        currentId:id,version:0,dirty:false,aiLocked:false,form,baseline:{...form}};
      result.status="created";result.detail=String(id);break;
    }
    case "deck.open":
      if(!deck.open){
        mockEditing.deck={open:true,cards:[],currentId:null,version:0,
          dirty:false,aiLocked:false,form:null};
      }
      result.status="opened";break;
    case "deck.patch":
      if(deck.open&&deck.currentId===p.draftId){
        const next=mockPatch(deck.form,p.field,p.value);
        if(!next)result.status="invalid";
        else {deck.form=next;deck.dirty=JSON.stringify(next)!==JSON.stringify(deck.baseline);
          deck.cards[0].title=deck.form.title;deck.cards[0].kind=deck.form.kind;
          result.status="edited"}
      }else result.status="stale";break;
    case "deck.flush":
      if(deck.open){deck.version++;deck.dirty=false;deck.baseline={...deck.form};result.status="flushed"}
      break;
    case "deck.commit":
      if(deck.open){mockEditing.deck={open:false};result.status="committed"}
      break;
    case "deck.discard":
      if(deck.open){mockEditing.deck={open:false};result.status="discarded"}
      break;
    case "deck.close":
      mockEditing.deck={open:false};result.status="closed";break;
    case "deck.undo":
      result.status="missing";break;
    case "deck.select":
      result.status="selected";break;
  }
  mockEditing.lastResult=result;
  logAction("预览编辑命令 "+p.name+
    (p.name==="deck.create"?" · "+p.kind:"")+" → "+result.status);
  try{previewPort?.postMessage(JSON.stringify({version:1,type:"result",payload:result}))}
  catch(e){logAction("编辑回执失败 "+e.message)}
  postState();
}
function onSkinAction(message){
  try{
    if(typeof message!=="string"||message.length>8192)return;
    const envelope=JSON.parse(message);
    if(envelope.version!==1||!envelope.payload)return;
    if(envelope.type==="command"){onSkinCommand(envelope.payload);return}
    if(envelope.type!=="action")return;
    const p=envelope.payload,name=p.name;
    if(!ACTIONS.some(([x])=>x===name)&&!["openEvent","toggleMemo","sendChat","resolveAiProposal",
      "pickAiFile","pickAiImage"].includes(name)){logAction("拒绝未知动作 "+String(name));return;}
    if(name==="panTimeline"){
      if(!Number.isInteger(p.deltaMinutes)||Math.abs(p.deltaMinutes)>2880)return;
      const state=lastState||simulatedState();
      state.timeline.anchorMillis+=p.deltaMinutes*60000;
      // Keep a mock camera anchor in a distinct variable so other state changes preserve it.
      mockAnchor=state.timeline.anchorMillis;
    }
    if(name==="jumpTimelineDay"&&/^\d{4}-\d{2}-\d{2}$/.test(p.date||"")){
      const date=new Date(p.date+"T04:00:00Z").getTime();
      if(Number.isFinite(date))mockAnchor=date;
    }
    if(name==="today")mockAnchor=null;
    if(name==="browseMonth"&&Number.isInteger(p.deltaMonths)&&
      p.deltaMonths!==0&&Math.abs(p.deltaMonths)<=12){
      mockAnchor=(mockAnchor??new Date("2026-10-22T04:00:00Z").getTime())+
        p.deltaMonths*30*86400000;
    }
    if(["timeline","month","memo","ai"].includes(name))mockPage=name.toUpperCase();
    if(name==="toggleMemo"){
      if(!Number.isInteger(p.eventSeq)||typeof p.completed!=="boolean"||!p.occurrenceKey)return;
      if(p.eventSeq===31)mockMemoDone=!p.completed;
    }
    if(name==="sendChat"&&typeof p.text==="string"&&p.text.length<=4000){
      if($("scenario").value==="ai-error"){
        mockAiErrorMessages.push({role:"user",content:p.text},
          {role:"assistant",content:"Studio Mock：已收到你手动重发的消息；没有调用真实 AI 或写入日历。"});
      }else mockChat.push({role:"user",content:p.text});
    }
    if(name==="pickAiFile"||name==="pickAiImage"){
      if(Object.keys(p).length!==1||mockPage!=="AI"||scenario==="locked"){
        logAction("拒绝当前状态下的附件选择请求");return;
      }
      const reply=name==="pickAiFile"?
        "预览 Mock：未打开真实选择器，也不会读取、上传或导入文件。":
        "预览 Mock：未打开真实选择器，也不会读取、上传或识别图片。";
      mockChat.push({role:"assistant",content:reply});
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
        mockChat.push({role:"assistant",content:p.decision==="stage"?
          "预览 Mock：候选已模拟写入草稿，未写入真实数据。":"预览 Mock：候选已模拟拒绝，未写入真实数据。"});
      }else if(p.decision==="supplement"){
        mockChat.push({role:"assistant",content:"请补充需要更正的字段；预览不会调用真实 AI。"});
      }else return;
      logAction("模拟宿主收到：resolveAiProposal · "+p.decision);postState();return;
    }
    if(name==="createDraft"&&
      !["ACTIVITY","ALARM","DEADLINE","MEMO"].includes(p.kind))return;
    if(name==="invokeOption"&&$("scenario").value==="locked"){
      logAction("锁定状态拒绝执行选项");return;
    }
    logAction("模拟宿主收到："+name+
      (p.id?" · "+p.id:"")+
      (name==="openEvent"?" · "+p.eventSeq:""));
    if(["today","panTimeline","jumpTimelineDay","browseMonth","timeline","month","memo","ai",
      "toggleMemo","sendChat"].includes(name))postState();
  }catch(error){logAction("Bridge 消息错误："+error.message)}
}
let mockAnchor=null,mockPage="TIMELINE",mockMemoDone=false,mockChat=[];
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
  $("skinId").onchange=change;$("skinName").onchange=change;$("fullCanvas").onchange=change;
  for(const id of ["scenario","theme","reduced"])$(id).onchange=()=>{
    if(id==="theme")$("webPreview").style.colorScheme=$("theme").value;
    mockAnchor=null;
    if(id==="scenario"&&$("scenario").value==="ai-error")mockAiErrorMessages=[
      {role:"user",content:"请帮我概括明天的安排。"},
      {role:"assistant",content:"Studio Mock：AI 请求未完成（模拟 HTTP 503）；上下文已保留，可编辑后手动重发。"}
    ];
    if(id==="scenario"&&$("scenario").value==="ai-review")resetMockAiProposalReview();
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
