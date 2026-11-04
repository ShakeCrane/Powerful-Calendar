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
    let port = "";
    for (let n = 0; n < 150 && !port; n++) {
      if (browser.exitCode !== null) throw Error("Browser exited " + browser.exitCode);
      try { if (fs.existsSync(activePort)) port = fs.readFileSync(activePort, "utf8").split(/\r?\n/)[0]; }
      catch (error) { if (error.code !== "EBUSY" && error.code !== "EPERM") throw error; }
      if (!port) await delay(100);
    }
    if (!port) throw Error("Browser startup timed out before its DevTools port became readable");
    const target = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURIComponent(origin)}`,
      {method: "PUT"})).json();
    socket = new WebSocket(target.webSocketDebuggerUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });
    let sequence = 0;
    const frameContexts = new Map();
    const pending = new Map();
    socket.onmessage = event => {
      const message = JSON.parse(event.data);
      if (message.method === "Runtime.executionContextCreated") {
        const context = message.params?.context;
        if (context?.auxData?.isDefault && context.auxData.frameId)
          frameContexts.set(context.auxData.frameId, context.id);
      } else if (message.method === "Runtime.executionContextDestroyed") {
        for (const [frameId, contextId] of frameContexts)
          if (contextId === message.params?.executionContextId) frameContexts.delete(frameId);
      }
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
    const mouseClick = async selector => {
      await evaluate(`document.querySelector(${JSON.stringify(selector)})?.scrollIntoView({block:'center'})`);
      const documentInfo = await call("DOM.getDocument", {depth: -1});
      const found = await call("DOM.querySelector", {
        nodeId: documentInfo.root.nodeId, selector
      });
      if (!found.nodeId) throw Error("Mouse target not found: " + selector);
      const box = await call("DOM.getBoxModel", {nodeId: found.nodeId});
      const quad = box.model.content;
      const x = (quad[0] + quad[2] + quad[4] + quad[6]) / 4;
      const y = (quad[1] + quad[3] + quad[5] + quad[7]) / 4;
      await call("Input.dispatchMouseEvent", {type: "mouseMoved", x, y});
      await call("Input.dispatchMouseEvent", {type: "mousePressed", x, y, button: "left", clickCount: 1});
      await call("Input.dispatchMouseEvent", {type: "mouseReleased", x, y, button: "left", clickCount: 1});
    };
    const evaluateFrame = async (frameId, expression) => {
      const contextId = frameContexts.get(frameId);
      if (!contextId) throw Error("Preview execution context not ready for frame " + frameId);
      const result = await call("Runtime.evaluate", {
        contextId, expression, awaitPromise: true, returnByValue: true
      });
      if (result.exceptionDetails)
        throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
      return result.result.value;
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

    const selectTreePage = async route => evaluate(`(()=>{
      const select=document.querySelector('#componentPage');
      const option=[...select.options].find(item=>item.textContent.endsWith('· ${route}'));
      if(!option)throw Error('Page route is missing from the page tree: ${route}');
      select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));return true;
    })()`);
    const selectComponentType = async type => evaluate(`(()=>{
      const select=document.querySelector('#componentType');select.value=${JSON.stringify(type)};
      return select.value;
    })()`);
    const setComponentField = async (id,value) => evaluate(`(()=>{
      const field=document.querySelector(${JSON.stringify(id)});field.value=${JSON.stringify(value)};
      field.dispatchEvent(new Event('change',{bubbles:true}));return true;
    })()`);
    const treeHasNode = async id => evaluate(
      `!!document.querySelector("#componentTree [data-component-id='${id}']")`);

    const supportedComponents = ["text", "container", "button", "scroll", "form-field", "timeline",
      "event-card", "month-grid", "date-button", "memo-row", "chat-item", "settings-row", "resource-layer"];

    // Author components on the five required blank routes through the Studio GUI.
    await selectTreePage("TIMELINE");
    await selectComponentType("container");
    await mouseClick("#componentAdd");
    await selectComponentType("text");
    await mouseClick("#componentAdd");
    await waitFor(async () => evaluate("project.pages[0].components[0].children.length===1"),
      "Timeline container did not accept a GUI-authored child");
    const timelineHeadingId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Timeline Header");
    await setComponentField("#componentBinding", "presentation.timeline.header");
    await setComponentField("#componentColor", "#345678");
    await selectComponentType("timeline");
    await mouseClick("#componentAdd");
    const timelineNodeId = await evaluate("selectedComponentId");
    await selectComponentType("event-card");
    await mouseClick("#componentAdd");
    await setComponentField("#componentLabel", "Timeline Event Card");
    await selectComponentType("button");
    await mouseClick("#componentAdd");
    const todayButtonId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Today");
    await setComponentField("#componentHostAction", "today");
    await mouseClick("#requestBindingCapabilities");
    await waitFor(async () => evaluate("manifest.capabilities.includes('host.action')"),
      "Studio did not record the explicit host.action request");

    await selectComponentType("button");
    await mouseClick("#componentAdd");
    const panButtonId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Pan +60 minutes");
    await setComponentField("#componentHostAction", "panTimeline");
    await setComponentField('[data-host-param="deltaMinutes"]', "60");
    await selectComponentType("text");
    await mouseClick("#componentAdd");
    const cameraStateTextId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Camera anchor");
    await setComponentField("#componentStateBinding", "timeline.anchorMillis");

    await selectTreePage("MONTH");
    await selectComponentType("text");
    await mouseClick("#componentAdd");
    const monthTextId = await evaluate("selectedComponentId");
    await waitFor(async () => treeHasNode(monthTextId),
      "The page-tree GUI did not add a text component");
    await setComponentField("#componentLabel", "November Header");
    await setComponentField("#componentBinding", "presentation.month.header");
    await setComponentField("#componentClassName", "qa-month-heading");
    await setComponentField("#componentColor", "#123456");
    await setComponentField("#componentFontSize", "18");

    await selectComponentType("month-grid");
    await mouseClick("#componentAdd");
    const monthGridId = await evaluate("selectedComponentId");
    await waitFor(async () => treeHasNode(monthGridId),
      "The page-tree GUI did not add a month-grid container");
    await selectComponentType("date-button");
    await mouseClick("#componentAdd");
    const firstDateId = await evaluate("selectedComponentId");
    await mouseClick("#componentAdd");
    const secondDateId = await evaluate("selectedComponentId");
    await waitFor(async () => treeHasNode(secondDateId),
      "The page-tree GUI did not add nested date buttons");
    await mouseClick(`#componentTree [data-component-id="${firstDateId}"]`);
    await setComponentField("#componentLabel", "2");
    await mouseClick(`#componentTree [data-component-id="${secondDateId}"]`);
    await setComponentField("#componentLabel", "3");
    await setComponentField("#componentBinding", "presentation.month.date");
    await mouseClick("#componentMoveUp");
    let treeState = await evaluate(`(()=>{
      const month=project.pages.find(page=>page.route==='MONTH');
      const root=month.components[0],grid=root.children.find(node=>node.type==='month-grid');
      return {rootChildren:root.children.map(node=>node.type),gridChildren:grid.children.map(node=>node.id)};
    })()`);
    if (JSON.stringify(treeState.rootChildren) !== JSON.stringify(["text", "month-grid"]) ||
        JSON.stringify(treeState.gridChildren) !== JSON.stringify([secondDateId, firstDateId]))
      throw Error("Component nesting/reordering did not update the canonical project tree: " + JSON.stringify(treeState));

    await mouseClick("#componentDelete");
    await waitFor(async () => !(await treeHasNode(secondDateId)),
      "Component delete did not remove the selected node");
    await mouseClick("#componentUndo");
    await waitFor(async () => treeHasNode(secondDateId),
      "Undo did not restore the deleted component");
    await mouseClick("#componentRedo");
    await waitFor(async () => !(await treeHasNode(secondDateId)),
      "Redo did not reapply component deletion");
    await mouseClick("#componentUndo");
    await waitFor(async () => treeHasNode(secondDateId),
      "Undo did not restore the saved Month structure");
    await mouseClick("#componentTree [data-component-id=\"month-root\"]");
    await selectComponentType("text");
    await mouseClick("#componentAdd");
    const reparentProbeId = await evaluate("selectedComponentId");
    await waitFor(async () => treeHasNode(reparentProbeId), "Could not create the reparenting probe");
    await setComponentField("#componentLabel", "Reparent Probe");
    await mouseClick("#componentNest");
    let parentState = await evaluate(`(()=>{
      const month=project.pages.find(page=>page.route==='MONTH'),root=month.components[0];
      const grid=root.children.find(node=>node.type==='month-grid');
      return {root:root.children.map(node=>node.id),grid:grid.children.map(node=>node.id)};
    })()`);
    if (parentState.root.includes(reparentProbeId) || !parentState.grid.includes(reparentProbeId))
      throw Error("Indent did not reparent the selected node under the preceding container: " +
        JSON.stringify(parentState));
    await mouseClick("#componentOutdent");
    parentState = await evaluate(`(()=>{
      const root=project.pages.find(page=>page.route==='MONTH').components[0];
      return {root:root.children.map(node=>node.id),grid:root.children.find(node=>node.type==='month-grid')
        .children.map(node=>node.id)};
    })()`);
    if (!parentState.root.includes(reparentProbeId) || parentState.grid.includes(reparentProbeId))
      throw Error("Outdent did not restore the component to its parent level");
    await mouseClick("#componentDelete");

    await selectTreePage("MEMO");
    await selectComponentType("scroll");
    await mouseClick("#componentAdd");
    const memoScrollId = await evaluate("selectedComponentId");
    await selectComponentType("memo-row");
    await mouseClick("#componentAdd");
    const memoRowId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Blank Memo Row");

    await selectTreePage("AI");
    await selectComponentType("scroll");
    await mouseClick("#componentAdd");
    const aiScrollId = await evaluate("selectedComponentId");
    await selectComponentType("chat-item");
    await mouseClick("#componentAdd");
    const chatItemId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Blank Chat Item");
    await selectComponentType("form-field");
    await mouseClick("#componentAdd");
    const formFieldId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Prompt Field");
    await selectComponentType("button");
    await mouseClick("#componentAdd");

    await selectTreePage("SETTINGS");
    await selectComponentType("settings-row");
    await mouseClick("#componentAdd");
    const settingsRowId = await evaluate("selectedComponentId");
    await setComponentField("#componentLabel", "Blank Settings Row");
    await selectComponentType("resource-layer");
    await mouseClick("#componentAdd");
    const resourceLayerId = await evaluate("selectedComponentId");
    await setComponentField("#componentClassName", "qa-resource-probe");
    await selectComponentType("text");
    await mouseClick("#componentAdd");
    await setComponentField("#componentLabel", "Resource Layer Label");

    const assetPath = path.join(root, "tools/skin-studio/tests/fixtures/blank-studio/probe.png");
    const studioDocument = await call("DOM.getDocument", {depth: 2});
    const assetInput = await call("DOM.querySelector", {
      nodeId: studioDocument.root.nodeId, selector: "#assetFile"
    });
    if (!assetInput.nodeId) throw Error("Blank Studio resource upload control was not found");
    await call("DOM.setFileInputFiles", {files: [assetPath], nodeId: assetInput.nodeId});
    await waitFor(async () => evaluate("sourceFiles.has('assets/probe.png')"),
      "Studio did not retain the uploaded PNG resource");
    await evaluate(`(()=>{
      const source=document.querySelector('#cssCode');
      source.value += '\\n.qa-resource-probe{width:2px;height:2px;background-image:url("../assets/probe.png")}\\n';
      source.dispatchEvent(new Event('input',{bubbles:true}));return true;
    })()`);
    await waitFor(async () => evaluate("sourceFiles.has('assets/probe.png') && new TextDecoder().decode(sourceFiles.get('web/style.css')).includes('probe.png')"),
      "Studio did not retain the CSS resource path");

    const authoredTypes = await evaluate(`(()=>{
      const visit=(nodes,out=[])=>{for(const node of nodes){out.push(node.type);visit(node.children||[],out)}return out};
      return project.pages.flatMap(page=>visit(page.components));
    })()`);
    if (supportedComponents.some(type => !authoredTypes.includes(type)))
      throw Error("The five-page component palette did not author every supported runtime type: " + authoredTypes);
    if (JSON.stringify(await evaluate("manifest.capabilities")) !== '["host.read","host.action"]')
      throw Error("The explicit Host action capability request was not retained by the project");

    treeState = await evaluate(`(()=>{
      const month=project.pages.find(page=>page.route==='MONTH');
      const root=month.components[0],heading=root.children[0],grid=root.children[1];
      return {title:month.title,heading:heading.props,grid:grid.type,dates:grid.children.map(node=>({id:node.id,props:node.props}))};
    })()`);
    if (treeState.title !== "Month metadata survives reopen" ||
        treeState.heading.label !== "November Header" ||
        treeState.heading.binding !== "presentation.month.header" ||
        treeState.heading.className !== "qa-month-heading" ||
        treeState.heading.style.color !== "#123456" || treeState.heading.style.fontSize !== "18px" ||
        treeState.dates[0]?.props.label !== "3" || treeState.dates[1]?.props.label !== "2")
      throw Error("The component inspector did not persist editable props/style/binding metadata: " + JSON.stringify(treeState));

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
    const savedMonthRoot=savedProject.pages[1].components[0];
    if (savedProject.pages.length !== 5 || savedProject.pages[1].title !== "Month metadata survives reopen" ||
        savedMonthRoot.props.editorNote !== "saved from Studio" ||
        savedMonthRoot.children[0].props.label !== "November Header" ||
        savedMonthRoot.children[1].type !== "month-grid" ||
        JSON.stringify(savedMonthRoot.children[1].children.map(node=>node.id)) !==
          JSON.stringify([secondDateId,firstDateId]))
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
        reopened.pages[1].components[0].children[0].props.label !== "November Header" ||
        JSON.stringify(reopened.pages[1].components[0].children[1].children.map(node=>node.id)) !==
          JSON.stringify([secondDateId,firstDateId]) ||
        JSON.stringify(reopened.capabilities) !== '["host.read","host.action"]')
      throw Error("Reopened Studio project lost page structure or changed capabilities");

    await waitFor(async () => evaluate("document.querySelector('#previewState').textContent.includes('连接成功')"),
      "Reopened component project did not load its WebView preview");
    const frameTreeResult = await call("Page.getFrameTree");
    const previewFrame = frameTreeResult.frameTree.childFrames?.find(frame => frame.frame.url.includes("/preview/"));
    if (!previewFrame) throw Error("Reopened component project has no preview frame");
    await waitFor(async () => frameContexts.has(previewFrame.frame.id),
      "The component preview frame did not create a browser execution context");
    const renderedPages = {};
    for (const route of routes) {
      await evaluateFrame(previewFrame.frame.id, `(()=>{
        const page=document.querySelector('[data-page-route="${route}"]');
        if(!page)throw Error('Required route is absent from runtime navigation: ${route} '+JSON.stringify({
          tree:window.__POWERCAL_PAGE_TREE_V1__?.pages?.map(item=>item.route),
          navigation:document.querySelector('#page-navigation')?.innerHTML,
          status:document.querySelector('#status')?.textContent,
          title:document.querySelector('#content [data-page-title]')?.dataset.pageTitle
        }));
        page.click();return true;
      })()`);
      await waitFor(async () => evaluateFrame(previewFrame.frame.id,
        `document.querySelector('#content [data-page-title]')?.dataset.pageTitle==='${route}'`),
      `The preview did not switch to ${route}`);
      renderedPages[route] = await evaluateFrame(previewFrame.frame.id, `(()=>({
        title:document.querySelector('#content [data-page-title]')?.textContent,
        nodes:[...document.querySelectorAll('#content [data-component-id]')].map(node=>({
          id:node.dataset.componentId,type:node.dataset.componentType,
          parent:node.parentElement.closest('[data-component-id]')?.dataset.componentId||null,
          text:node.dataset.componentType==="form-field"
            ?node.querySelector(':scope > span')?.textContent||""
            :[...node.childNodes].filter(child=>child.nodeType===Node.TEXT_NODE)
              .map(child=>child.textContent).join(""),
          stateValue:node.querySelector(':scope > .pc-bound-value')?.textContent||"",
          binding:node.dataset.binding||null,color:getComputedStyle(node).color,
          backgroundImage:getComputedStyle(node).backgroundImage
        }))
      }))()`);
    }
    const expectedPages = await evaluate(`(()=>{
      const flatten=(nodes,parent=null,out=[])=>{for(const node of nodes){
        out.push({id:node.id,type:node.type,parent,text:node.props?.label||node.props?.text||node.props?.title||""});
        flatten(node.children||[],node.id,out);
      }return out};
      return Object.fromEntries(project.pages.map(page=>[page.route,{
        title:page.title,nodes:flatten(page.components)
      }]));
    })()`);
    const renderedTypes = [...new Set(Object.values(renderedPages)
      .flatMap(page=>page.nodes.map(node=>node.type)))].sort();
    if (supportedComponents.some(type => !renderedTypes.includes(type)))
      throw Error("The five-page runtime preview omitted supported components: " + JSON.stringify(renderedTypes));
    for (const route of routes) {
      const expected = expectedPages[route], rendered = renderedPages[route];
      if (rendered.title !== expected.title || JSON.stringify(rendered.nodes.map(node=>({
        id:node.id,type:node.type,parent:node.parent,text:node.text
      }))) !== JSON.stringify(expected.nodes))
        throw Error(`The ${route} preview DOM differs from the saved component tree: ` +
          JSON.stringify({expected,rendered}));
    }
    const monthHeading = renderedPages.MONTH.nodes.find(node=>node.id===monthTextId);
    if (monthHeading?.text !== "November Header" || monthHeading.color !== "rgb(18, 52, 86)" ||
        !renderedPages.MONTH.nodes.some(node=>node.id===monthGridId && node.type==="month-grid") ||
        !renderedPages.MONTH.nodes.some(node=>node.id===secondDateId && node.parent===monthGridId))
      throw Error("Month attributes, hierarchy, or style did not survive in the preview DOM");
    if (!renderedPages.MEMO.nodes.some(node=>node.id===memoRowId && node.parent===memoScrollId) ||
        !renderedPages.AI.nodes.some(node=>node.id===chatItemId && node.parent===aiScrollId) ||
        !renderedPages.AI.nodes.some(node=>node.id===formFieldId) ||
        !renderedPages.SETTINGS.nodes.some(node=>node.id===settingsRowId) ||
        !renderedPages.SETTINGS.nodes.some(node=>node.id===resourceLayerId) ||
        !renderedPages.TIMELINE.nodes.some(node=>node.id===timelineNodeId) ||
        !renderedPages.TIMELINE.nodes.some(node=>node.id===timelineHeadingId))
      throw Error("A required page/component structure was not preserved through Studio preview");
    const resourceProbe = renderedPages.SETTINGS.nodes.find(node=>node.id===resourceLayerId);
    if (!resourceProbe?.backgroundImage.includes("probe.png"))
      throw Error("Studio preview did not resolve the uploaded PNG through the package-relative CSS path");
    if (!renderedPages.TIMELINE.nodes.some(node=>node.id===todayButtonId && node.type==="button") ||
        !renderedPages.TIMELINE.nodes.some(node=>node.id===panButtonId && node.type==="button") ||
        !renderedPages.TIMELINE.nodes.some(node=>node.id===cameraStateTextId &&
          node.type==="text" && node.parent===timelineNodeId))
      throw Error("The formal Host action/state bindings were not rendered on Timeline");
    await evaluateFrame(previewFrame.frame.id,
      "document.querySelector('[data-page-route=\"TIMELINE\"]')?.click();true");
    await evaluateFrame(previewFrame.frame.id,
      "document.querySelector('[data-host-action=\"panTimeline\"]')?.click();true");
    const baseCamera = new Date("2026-10-22T04:00:00Z").getTime();
    await waitFor(async () => evaluateFrame(previewFrame.frame.id,
      "document.querySelector('[data-host-state-binding=\"timeline.anchorMillis\"] .pc-bound-value')?.textContent===" +
        "'" + String(baseCamera+3600000) + "'"), "Mock Host did not publish the Pan camera update");
    await evaluateFrame(previewFrame.frame.id,
      "document.querySelector('[data-host-action=\"today\"]')?.click();true");
    await waitFor(async () => evaluateFrame(previewFrame.frame.id,
      "document.querySelector('[data-host-state-binding=\"timeline.anchorMillis\"] .pc-bound-value')?.textContent===" +
        "'" + String(baseCamera) + "'"), "Mock Host did not return the bound camera to local noon");
    if (await evaluate("mockHours") !== 24)
      throw Error("Today action did not restore the 24-hour camera");

    await evaluate("document.querySelector('#exportPack').click();true");
    const packagePath = path.join(downloadDir, "powercal-blank-skin.pcskin");
    await waitFor(async () => fs.existsSync(packagePath) && fs.statSync(packagePath).size > 0,
      "Studio did not build the blank project's .pcskin");
    const validation = spawnSync("python", ["tools/skin-studio/skin_studio.py", "validate",
      packagePath, "--json"], {cwd: root, encoding: "utf8"});
    if (validation.status !== 0) throw Error("Exported .pcskin validation failed: " + validation.stderr);
    const receipt = JSON.parse(validation.stdout);
    if (receipt.skinId !== "powercal-blank-skin" || receipt.runtimeVersion !== 3 ||
        JSON.stringify(receipt.manifest.capabilities) !== '["host.read","host.action"]')
      throw Error("Exported .pcskin identity or minimum capabilities changed");
    const importedResponse = await fetch(origin + "/api/import-v3", {
      method: "POST", headers: {"Content-Type": "application/octet-stream"},
      body: fs.readFileSync(packagePath)
    });
    if (!importedResponse.ok) throw Error("Exported .pcskin could not be reimported");
    const importedPackage = await importedResponse.json();
    const importedProject = JSON.parse(Buffer.from(importedPackage.files["project.json"], "base64").toString("utf8"));
    const importedRuntime = Buffer.from(importedPackage.files["web/project-tree-v1.js"], "base64").toString("utf8");
    const runtimeLiteral=importedRuntime.match(/^window\.__POWERCAL_PAGE_TREE_V1__=JSON\.parse\((.*)\);\n$/);
    const runtimeProject=runtimeLiteral?JSON.parse(JSON.parse(runtimeLiteral[1])):null;
    const findNode=(nodes,id)=>{for(const node of nodes||[]){
      if(node.id===id)return node;const found=findNode(node.children,id);if(found)return found;
    }return null};
    const importedToday=findNode(importedProject.pages[0].components,todayButtonId);
    const importedCameraBinding=findNode(importedProject.pages[0].components,cameraStateTextId);
    if (JSON.stringify(importedProject.pages) !== JSON.stringify(reopened.pages) ||
        JSON.stringify(runtimeProject?.pages) !== JSON.stringify(importedProject.pages) ||
        runtimeProject?.pages[1]?.components[0]?.children[0]?.props?.binding !==
          "presentation.month.header" ||
        importedToday?.props?.hostAction?.name !== "today" ||
        importedCameraBinding?.props?.hostStateBinding !== "timeline.anchorMillis")
      throw Error("Export/reimport did not preserve the component structure and runtime projection");
    const exportTarget = process.env.POWERCAL_HOST_BINDING_GATE_OUTPUT ||
      process.env.POWERCAL_BLANK_GATE_OUTPUT;
    if (exportTarget) {
      fs.mkdirSync(path.dirname(exportTarget), {recursive: true});
      fs.copyFileSync(packagePath, exportTarget);
    }
    console.log("BLANK V3 PROJECT SAVE/REOPEN/BUILD PASS", JSON.stringify({
      savedProject: projectPath, builtPackage: packagePath, pageRoutes: routes,
      stableIds: allIds, reopenedName: reopened.name, reopenedPage: reopened.pages[1],
      packageSha256: receipt.packageSha256, fileCount: receipt.fileCount,
      capabilities: receipt.manifest.capabilities, componentTree: treeState,
      previewDom: renderedPages, runtimeComponentTypes: renderedTypes,
      componentIds: {memoRowId, chatItemId, formFieldId, settingsRowId, resourceLayerId},
      hostBindings: {todayButtonId, panButtonId, cameraStateTextId},
      androidFixtureTarget: exportTarget||null,
      reimportedPageCount: importedProject.pages.length
    }));
    await call("Page.navigate", {url: "about:blank"});
    socket.close();
  } finally {
    if (browser?.pid) spawnSync("taskkill", ["/PID", String(browser.pid), "/T", "/F"], {stdio: "ignore"});
    if (server?.pid) spawnSync("taskkill", ["/PID", String(server.pid), "/T", "/F"], {stdio: "ignore"});
  }
}

main().catch(error => {console.error(error); process.exitCode = 1;});
