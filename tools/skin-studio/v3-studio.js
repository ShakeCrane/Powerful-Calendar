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
let manifest=null, project=null, sourceFiles=new Map(), selectedLayer=null;
let activeMode="simple", editCanvas=true, previewTimer=null, previewSeq=0;
let previewPort=null, lastState=null, lastPreviewUrl="", dirty=false;
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
  files.set("project.json",enc.encode(JSON.stringify(project,null,2)));
  return files;
}
function payload(){
  if(!manifest) throw Error("还没有加载皮肤");
  manifest.id=$("skinId").value.trim();manifest.name=$("skinName").value.trim();
  if(!$("fullCanvas").checked) throw Error(
    "全画布已关闭：Android 将用原生界面覆盖皮肤，无法保证预览=使用。请开启完整画布。");
  manifest.fullCanvas=true;
  // These two capabilities are all this v3 editor's running components require.
  manifest.capabilities=["host.read","host.action"];
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
  project=parseProject();
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
function change(){dirty=true;schedulePreview()}
function syncSourceInputs(){
  $("htmlCode").value=getText("web/index.html");
  $("cssCode").value=getText("web/style.css");
  $("jsCode").value=getText("web/app.js");
}
function switchMode(mode){
  activeMode=mode;project.mode=mode;
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
  const block=markerStart+"\n:root {\n"+properties+"\n}\n"+markerEnd;
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
      const used=project.layers.some(layer=>layer.asset===path);
      if(used){say("素材被图层引用，请先移除或更换引用",true);return;}
      sourceFiles.delete(path);renderAssets();renderLayers();change();
    };
    row.append(name,remove);root.append(row);
  }
  renderIconChoices();
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
      if(/\.(png|webp)$/i.test(name)){
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
const ACTIONS=[
  ["","无动作"],["today","返回今天"],["createMemo","新建 Memo"],
  ["createDraft","新建活动草稿"],["browseMonth","浏览月份"],
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
    {...rows[0],eventSeq:8,occurrenceKey:"8:20261022T090000",title:"重复课程 · 本次"},
    {...rows[0],eventSeq:8,occurrenceKey:"8:20261023T090000",title:"重复课程 · 明天",
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
  return {bridgeVersion:1,page:mockPage,memoCount:2,aiLocked:scenario==="locked",
    reducedMotion:$("reduced").checked,pendingCount:3,
    editing:mockEditing,
    memoEntries:[
      {eventSeq:31,revision:2,occurrenceKey:"single",title:"整理会议笔记",completed:mockMemoDone,startMillis:base},
      {eventSeq:44,revision:1,occurrenceKey:"single",title:"提交报告",completed:false,startMillis:base+7200000}
    ],memoTruncated:false,chatBusy:false,
    chatMessages:[{role:"assistant",content:"你好！今天有什么安排？"},...mockChat],
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
let mockEditing={editor:{open:false},deck:{open:false},lastResult:null};
function onSkinCommand(p){
  if(!p || !/^[A-Za-z0-9_-]{8,64}$/.test(p.requestId||""))return;
  const result={requestId:p.requestId,status:"invalid"};
  const editor=mockEditing.editor,deck=mockEditing.deck;
  switch(p.name){
    case "editor.open":
      mockEditing.editor={open:true,sessionId:"00000000-0000-0000-0000-000000000001",
        eventSeq:p.eventSeq,dirty:false,form:{...mockForm("ACTIVITY"),title:"测试活动"}};
      result.status="opened";break;
    case "editor.patch":
      if(editor.open&&editor.sessionId===p.sessionId){
        editor.form[p.field]=p.value;editor.dirty=true;result.status="edited";
      }else result.status="stale";
      break;
    case "editor.save":
      result.status=editor.open?"saved":"stale";break;
    case "editor.close":
      mockEditing.editor={open:false};result.status="closed";break;
    case "deck.create":{
      const id=7001;
      const form=mockForm(p.kind);
      mockEditing.deck={open:true,cards:[{draftId:id,title:form.title,kind:p.kind}],
        currentId:id,version:0,dirty:false,aiLocked:false,form};
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
        deck.form[p.field]=p.value;deck.dirty=true;
        deck.cards[0].title=deck.form.title;result.status="edited";
      }else result.status="stale";break;
    case "deck.flush":
      if(deck.open){deck.version++;deck.dirty=false;result.status="flushed"}
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
  logAction("预览编辑命令 "+p.name+" → "+result.status);
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
    if(!ACTIONS.some(([x])=>x===name)&&!["openEvent","toggleMemo","sendChat"].includes(name)){logAction("拒绝未知动作 "+String(name));return;}
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
    if(name==="sendChat"&&typeof p.text==="string"&&p.text.length<=4000)
      mockChat.push({role:"user",content:p.text});
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
    mockAnchor=null;postState();
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
