/* End-to-end blank v3 project creation, save, Studio restart, reopen and build. */
const {spawn,spawnSync} = require("node:child_process");
const fs = require("node:fs"), path = require("node:path"), readline = require("node:readline");
const root = path.resolve(__dirname, "../../..");
const chrome = process.env.CHROME_PATH || "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";
const reportDir = path.resolve(process.env.POWERCAL_E2E_REPORT_DIR ||
  path.join(root, ".local/android-test/reports/S-01-20261104"));
const downloadDir = path.join(reportDir, "blank-project-downloads-" + process.pid);
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
  fs.mkdirSync(downloadDir, {recursive: true});
  const profile = fs.mkdtempSync(path.join(reportDir, "blank-project-browser-"));
  const server = spawn("python", ["tools/skin-studio/skin_studio.py", "serve", "--port", "0"],
    {cwd: root, stdio: ["ignore", "pipe", "pipe"]});
  let browser, socket;
  try {
    const firstLine = await Promise.race([
      new Promise((resolve, reject) => {
        readline.createInterface({input: server.stdout}).once("line", resolve);
        server.once("error", reject);
        server.once("exit", code => reject(Error("Studio exited " + code)));
      }),
      delay(15000).then(() => {throw Error("Studio startup timed out");})
    ]);
    const origin = firstLine.match(/http:\/\/127\.0\.0\.1:\d+/)?.[0];
    if (!origin) throw Error("No Studio URL: " + firstLine);
    browser = spawn(chrome, ["--headless=new", "--disable-gpu", "--no-sandbox",
      "--disable-background-networking", "--disable-site-isolation-trials",
      "--disable-features=IsolateOrigins,site-per-process", "--no-first-run",
      "--no-default-browser-check", "--remote-allow-origins=*",
      "--remote-debugging-port=0", "--user-data-dir=" + profile, "about:blank"],
      {stdio: "ignore"});
    const activePort = path.join(profile, "DevToolsActivePort");
    for (let n = 0; n < 150 && !fs.existsSync(activePort); n++) {
      if (browser.exitCode !== null) throw Error("Browser exited " + browser.exitCode);
      await delay(100);
    }
    if (!fs.existsSync(activePort)) throw Error("Browser startup timed out");
    const port = fs.readFileSync(activePort, "utf8").split(/\r?\n/)[0];
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,
      {method: "PUT"})).json();
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });
    let sequence = 0;
    const pending = new Map();
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const {resolve, reject} = pending.get(message.id);
        pending.delete(message.id);
        message.error ? reject(Error(message.error.message)) : resolve(message.result);
      }
    };
    const call = (method, params = {}) => new Promise((resolve, reject) => {
      const id = ++sequence;
      pending.set(id, {resolve, reject});
      socket.send(JSON.stringify({id, method, params}));
    });
    const evaluate = async expression => {
      const result = await call("Runtime.evaluate", {
        expression, awaitPromise: true, returnByValue: true
      });
      if (result.exceptionDetails)
        throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
      return result.result.value;
    };
    const waitFor = async (predicate, message) => {
      for (let i = 0; i < 240; i++) {
        try { if (await predicate()) return; } catch {}
        await delay(100);
      }
      throw Error(message);
    };
    await call("Runtime.enable");
    await call("Page.enable");
    await call("DOM.enable");
    await call("Page.setDownloadBehavior", {behavior: "allow", downloadPath: downloadDir});
    await waitFor(async () => evaluate("document.readyState==='complete' && typeof loadPackage==='function'"),
      "Studio initial page did not become ready");
    await waitFor(async () => evaluate("!!manifest && !!document.querySelector('#skinName')?.value"),
      "Initial Studio package did not load");

    await evaluate("document.querySelector('#newBlank').click();true");
    await waitFor(async () => evaluate("manifest?.id==='powercal-blank-skin'"),
      "New blank project did not load through the Studio button");
    const initial = await evaluate(`(()=>{
      const p=JSON.parse(new TextDecoder().decode(sourceFiles.get('project.json')));
      return {id:manifest.id,capabilities:manifest.capabilities,pages:p.pages,
        files:[...sourceFiles.keys()].sort(),hasClear:/Clear/i.test(new TextDecoder().decode(sourceFiles.get('web/index.html')))};
    })()`);
    const routes = ["TIMELINE", "MONTH", "MEMO", "AI", "SETTINGS"];
    if (initial.id !== "powercal-blank-skin" || JSON.stringify(initial.capabilities) !== '["host.read"]' ||
        JSON.stringify(initial.pages.map(page => page.route)) !== JSON.stringify(routes) ||
        initial.pages.length !== 5 || initial.hasClear || initial.files.includes("assets/clear"))
      throw Error("Blank Studio project is incomplete or contains Clear content: " + JSON.stringify(initial));
    const allIds = initial.pages.flatMap(page => [page.id, ...page.components.map(node => node.id)]);
    if (new Set(allIds).size !== allIds.length || initial.pages.some(page =>
      page.components.length !== 1 || page.components[0].type !== "container" ||
      page.components[0].children.length !== 0))
      throw Error("Blank page/component IDs or empty roots are invalid");

    await evaluate(`(()=>{
      const name=document.querySelector('#skinName');name.value='Durable Blank Studio QA';
      name.dispatchEvent(new Event('change',{bubbles:true}));
      const selector=document.querySelector('#extraSourceFile');selector.value='project.json';
      selector.dispatchEvent(new Event('change',{bubbles:true}));
      const project=JSON.parse(document.querySelector('#extraSourceCode').value);
      project.pages[1].title='Month metadata survives reopen';
      project.pages[1].components[0].props.editorNote='saved from Studio';
      const source=document.querySelector('#extraSourceCode');
      source.value=JSON.stringify(project,null,2);
      source.dispatchEvent(new Event('input',{bubbles:true}));
    })()`);
    await waitFor(async () => evaluate("manifest.name==='Durable Blank Studio QA' && project.pages[1].title==='Month metadata survives reopen'"),
      "Studio did not apply the project name and page metadata edits");
    await evaluate("document.querySelector('#validate').click();true");
    await waitFor(async () => evaluate("document.querySelector('#toast').textContent.includes('文件结构、体积')"),
      "Studio project validation did not pass");

    const projectPath = path.join(downloadDir, "powercal-blank-skin.pcstudio-v3.json");
    await evaluate("document.querySelector('#exportProject').click();true");
    await waitFor(async () => fs.existsSync(projectPath) && fs.statSync(projectPath).size > 0,
      "Studio project file was not saved");
    const saved = JSON.parse(fs.readFileSync(projectPath, "utf8"));
    if (saved.format !== "powercal-v3-studio" || saved.manifest.name !== "Durable Blank Studio QA")
      throw Error("Saved Studio project metadata is incorrect");
    const savedProject = JSON.parse(Buffer.from(saved.files["project.json"], "base64").toString("utf8"));
    if (savedProject.pages[1].title !== "Month metadata survives reopen" ||
        savedProject.pages[1].components[0].props.editorNote !== "saved from Studio")
      throw Error("Saved project.json lost the edited page structure");

    // Unload the Studio document, reopen it, then use the visible Open Project input.
    await call("Page.navigate", {url: "about:blank"});
    await waitFor(async () => evaluate("location.href==='about:blank'"), "Studio page did not close");
    await call("Page.navigate", {url: origin});
    await waitFor(async () => evaluate("document.readyState==='complete' && typeof importProject==='function'"),
      "Studio did not reopen after navigation");
    try {
      await waitFor(async () => evaluate("!!manifest && manifest.id==='powercal-clear-custom'"),
        "Studio's initial project did not reload");
    } catch (error) {
      const state = await evaluate(`(()=>({url:location.href,ready:document.readyState,
        title:document.title,skinId:document.querySelector('#skinId')?.value,
        toast:document.querySelector('#toast')?.textContent,
        manifestId:manifest?.id,projectError:projectSourceError}))()`);
      throw Error(error.message + "; page state=" + JSON.stringify(state));
    }
    const documentInfo = await call("DOM.getDocument", {depth: 2});
    const input = await call("DOM.querySelector", {
      nodeId: documentInfo.root.nodeId, selector: "#importProject"
    });
    if (!input.nodeId) throw Error("Open Project file input was not found after restart");
    await call("DOM.setFileInputFiles", {files: [projectPath], nodeId: input.nodeId});
    await evaluate("document.querySelector('#importProject').dispatchEvent(new Event('change',{bubbles:true}));true");
    await waitFor(async () => evaluate(
      "manifest?.name==='Durable Blank Studio QA' && document.querySelector('#skinName').value==='Durable Blank Studio QA'"),
      "Saved project did not reopen through the Studio Open Project control");
    const reopened = await evaluate(`(()=>{
      const files=payload().files;
      const p=JSON.parse(new TextDecoder().decode(base64ToBytes(files['project.json'])));
      return {name:manifest.name,pages:p.pages,capabilities:manifest.capabilities,
        fileCount:Object.keys(files).length};
    })()`);
    if (reopened.pages.length !== 5 || reopened.pages[1].title !== "Month metadata survives reopen" ||
        reopened.pages[1].components[0].props.editorNote !== "saved from Studio" ||
        JSON.stringify(reopened.capabilities) !== '["host.read"]')
      throw Error("Reopened Studio project lost page structure or changed capabilities");

    await evaluate("document.querySelector('#exportPack').click();true");
    const packagePath = path.join(downloadDir, "powercal-blank-skin.pcskin");
    await waitFor(async () => fs.existsSync(packagePath) && fs.statSync(packagePath).size > 0,
      "Studio did not build the blank project's .pcskin");
    const validation = spawnSync("python", ["tools/skin-studio/skin_studio.py", "validate",
      packagePath, "--json"], {cwd: root, encoding: "utf8"});
    if (validation.status !== 0) throw Error("Exported .pcskin validation failed: " + validation.stderr);
    const receipt = JSON.parse(validation.stdout);
    if (receipt.skinId !== "powercal-blank-skin" || receipt.runtimeVersion !== 3 ||
        JSON.stringify(receipt.manifest.capabilities) !== '["host.read"]')
      throw Error("Exported .pcskin identity or minimum capabilities changed");
    console.log("BLANK V3 PROJECT SAVE/REOPEN/BUILD PASS", JSON.stringify({
      savedProject: projectPath, builtPackage: packagePath, pageRoutes: routes,
      stableIds: allIds, reopenedName: reopened.name, reopenedPage: reopened.pages[1],
      packageSha256: receipt.packageSha256, fileCount: receipt.fileCount,
      capabilities: receipt.manifest.capabilities
    }));
    await call("Page.navigate", {url: "about:blank"});
    socket.close();
  } finally {
    if (browser?.pid) spawnSync("taskkill", ["/PID", String(browser.pid), "/T", "/F"], {stdio: "ignore"});
    if (server?.pid) spawnSync("taskkill", ["/PID", String(server.pid), "/T", "/F"], {stdio: "ignore"});
  }
}

main().catch(error => {console.error(error); process.exitCode = 1;});
