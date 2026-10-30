// Exercise the real Studio handlers with a small DOM; packaging is tested by Python.
const fs = require("node:fs"), vm = require("node:vm");
const {TextEncoder, TextDecoder} = require("node:util");
const source = fs.readFileSync(process.argv[2], "utf8");
const fixture = JSON.parse(fs.readFileSync(0, "utf8"));
class Element {
  constructor() {
    this.value = ""; this.children = []; this.checked = false;
    this.disabled = false; this.hidden = false; this.textContent = "";
    this.classList = {toggle(){}, add(){}, remove(){}};
    this.style = {}; this.dataset = {};
  }
  append(...nodes) { this.children.push(...nodes); }
  replaceChildren(...nodes) { this.children = nodes; }
  get options() { return this.children; }
  addEventListener() {}
}
const elements = new Map();
const document = {
  getElementById(id) { if (!elements.has(id)) elements.set(id, new Element()); return elements.get(id); },
  createElement() { return new Element(); }, activeElement: null,
};
let promptValue="testAccent";
const sandbox = {document, window:{addEventListener(){}}, TextEncoder, TextDecoder,
  Uint8Array, Map, JSON, Blob, console, atob, btoa, prompt:()=>promptValue, confirm:()=>true,
  setTimeout:()=>1, clearTimeout(){}};
vm.createContext(sandbox);
// The final bootstrap fetch is replaced with the real package fixture below.
const bootstrap = source.lastIndexOf("\n(async()=>{");
if (bootstrap < 0) throw Error("Studio bootstrap not found");
vm.runInContext(source.slice(0, bootstrap), sandbox);
vm.runInContext("bindUI()", sandbox);
sandbox.fixture = fixture;
(async()=>{
  await vm.runInContext("loadPackage(fixture)", sandbox);
  const capabilityBefore = vm.runInContext("payload()", sandbox);
  const capabilitySelectionBefore = ["capHostRead","capHostAction","capHostEdit"]
    .map(id=>document.getElementById(id).checked);
  document.getElementById("advancedMode").onclick();
  const selector = document.getElementById("extraSourceFile");
  const choices = selector.children.map(x=>x.value);
  selector.value = "web/edit.js";
  if (selector.onchange) selector.onchange();
  const editor = document.getElementById("extraSourceCode");
  const original = editor.value;
  editor.value = original + "\n// studio-source-roundtrip\n";
  if (editor.oninput) editor.oninput();
  const payload = vm.runInContext("payload()", sandbox);

  selector.value = "project.json";
  selector.onchange();
  const projectObject = JSON.parse(editor.value);
  projectObject.agentAuthoredComponents = [{id:"custom-panel",type:"container",children:[{id:"title",type:"text"}]}];
  editor.value = JSON.stringify(projectObject,null,2);
  editor.oninput();
  const projectPayload = vm.runInContext("payload()", sandbox);

  promptValue = "web/components/inaccessible.bin";
  document.getElementById("addTextSource").onclick();
  const rejectedTextExtension = !vm.runInContext('sourceFiles.has("web/components/inaccessible.bin")', sandbox);

  promptValue = "web/components/agent-card.js";
  document.getElementById("addTextSource").onclick();
  editor.value = "export const card = 'custom';\n";
  editor.oninput();
  selector.value = "web/agent-unused-90f4.txt";
  selector.onchange();
  document.getElementById("removeExtraSource").onclick();

  sandbox.packageFiles = [
    {name:"agent-new.bin",size:4,arrayBuffer:async()=>new Uint8Array([0,255,65,66]).buffer},
    {name:"replace.bin",size:3,arrayBuffer:async()=>new Uint8Array([255,0,128]).buffer},
  ];
  const assetInput = document.getElementById("packageAssetFile");
  await assetInput.onchange({target:{files:sandbox.packageFiles,value:"selected"}});
  const managedPayload = vm.runInContext("payload()", sandbox);

  promptValue = "testAccent";
  document.getElementById("addMaterial").onclick();
  const visualPayload = vm.runInContext("payload()", sandbox);

  selector.value = "project.json";selector.onchange();
  const validProjectText = editor.value;
  editor.value = "{ invalid json";editor.oninput();
  let invalidProjectRejected = false;
  try { vm.runInContext("payload()", sandbox); } catch { invalidProjectRejected = true; }
  selector.value = "web/edit.js";selector.onchange();
  selector.value = "project.json";selector.onchange();
  const invalidProjectDraftRestored = editor.value === "{ invalid json";
  editor.value = validProjectText;editor.oninput();
  const recoveredPayload = vm.runInContext("payload()", sandbox);
  document.getElementById("capHostRead").checked = false;
  document.getElementById("capHostAction").checked = true;
  document.getElementById("capHostEdit").checked = true;
  document.getElementById("capHostAction").onchange();
  const capabilityPayload = vm.runInContext("payload()", sandbox);
  process.stdout.write(JSON.stringify({choices, original, payload, projectPayload,
    managedPayload, visualPayload, recoveredPayload, capabilityBefore,
    capabilitySelectionBefore, capabilityPayload, invalidProjectRejected,
    invalidProjectDraftRestored, rejectedTextExtension}));
})().catch(error=>{console.error(error);process.exitCode=1});
