// Exercise the real Studio handlers with a small DOM; packaging is tested by Python.
const fs = require("node:fs"), vm = require("node:vm");
const {TextEncoder, TextDecoder} = require("node:util");
const source = fs.readFileSync(process.argv[2], "utf8");
const fixture = JSON.parse(fs.readFileSync(0, "utf8"));
class Element {
  constructor() {
    this.value = ""; this.children = []; this.checked = false;
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
const sandbox = {document, window:{addEventListener(){}}, TextEncoder, TextDecoder,
  Uint8Array, Map, JSON, Blob, console, atob, btoa, setTimeout:()=>1, clearTimeout(){}};
vm.createContext(sandbox);
// The final bootstrap fetch is replaced with the real package fixture below.
const bootstrap = source.lastIndexOf("\n(async()=>{");
if (bootstrap < 0) throw Error("Studio bootstrap not found");
vm.runInContext(source.slice(0, bootstrap), sandbox);
vm.runInContext("bindUI()", sandbox);
sandbox.fixture = fixture;
(async()=>{
  await vm.runInContext("loadPackage(fixture)", sandbox);
  const selector = document.getElementById("extraSourceFile");
  const choices = selector.children.map(x=>x.value);
  selector.value = "web/edit.js";
  if (selector.onchange) selector.onchange();
  const editor = document.getElementById("extraSourceCode");
  const original = editor.value;
  editor.value = original + "\n// studio-source-roundtrip\n";
  if (editor.oninput) editor.oninput();
  const payload = vm.runInContext("payload()", sandbox);
  process.stdout.write(JSON.stringify({choices, original, payload}));
})().catch(error=>{console.error(error);process.exitCode=1});
