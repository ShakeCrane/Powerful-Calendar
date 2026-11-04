(() => {
  "use strict";
  // Authored in a Blank v3 project. All calendar, recurrence, memo, AI and
  // reminder decisions stay behind the versioned Android Host bridge.
  const tree = window.__POWERCAL_PAGE_TREE_V1__;
  const $ = id => document.getElementById(id);
  const status = $("status"), nav = $("page-navigation"), content = $("content");
  const routes = ["TIMELINE", "MONTH", "MEMO", "AI", "SETTINGS"];
  const titles = {TIMELINE:"时间轴",MONTH:"月历",MEMO:"Memo / 待办",AI:"AI 对话",SETTINGS:"设置"};
  let port = null, state = {}, serial = 0, activeDialog = false;
  const pending = new Map(), queued = [];

  const el = (tag, text, cls) => {
    const value = document.createElement(tag);
    if (text !== undefined && text !== null) value.textContent = String(text);
    if (cls) value.className = cls;
    return value;
  };
  const safe = value => value === null || value === undefined ? "" : String(value);
  const sendFrame = frame => {
    if (!port) { if (queued.length < 48) queued.push(frame); return false; }
    try { port.postMessage(JSON.stringify(frame)); return true; } catch { return false; }
  };
  const action = (name, args = {}) => sendFrame({version:1,type:"action",payload:{name,...args}});
  const command = (name, args = {}) => new Promise(resolve => {
    const requestId = "blank_" + Date.now().toString(36) + "_" + (++serial).toString(36);
    pending.set(requestId, resolve);
    if (!sendFrame({version:1,type:"command",payload:{name,requestId,...args}})) {
      pending.delete(requestId);
      resolve({requestId,status:"unavailable"});
    }
  });
  const button = (id, text, onClick, cls = "") => {
    const value = el("button", text, cls);
    value.type = "button";
    if (id) value.id = id;
    value.addEventListener("click", onClick);
    return value;
  };
  const actionButton = (id, text, name, args = {}) => button(id, text, () => action(name, args));
  const createEventButton = (id, text, kind) => button(id, text, () => {
    void command("deck.create", {kind}).then(showResult);
  });
  const showResult = result => {
    if (!result) return;
    window.__fromBlankLastResult = result;
    status.textContent = safe(result.detail) || safe(result.status) || "Host 已返回";
  };

  function renderTree(page, root) {
    const map = {text:"p",container:"section",button:"button",scroll:"div", "form-field":"label",
      timeline:"section","event-card":"article","month-grid":"div","date-button":"button",
      "memo-row":"article","chat-item":"article","settings-row":"div","resource-layer":"div"};
    const visit = node => {
      const item = el(map[node.type] || "div", undefined, "component component-" + node.type);
      item.dataset.componentId = node.id;
      item.dataset.componentType = node.type;
      const props = node.props || {};
      if (node.type === "button" || node.type === "date-button") {
        item.type = "button";
        item.textContent = safe(props.label || props.text || node.type);
        const binding = props.hostAction;
        if (binding?.name) {
          item.dataset.catalogAction = binding.name;
          item.addEventListener("click", () => {
            if (binding.name === "createDraft" || binding.name === "createMemo") {
              const kind = binding.parameters?.kind || (binding.name === "createMemo" ? "MEMO" : "");
              if (kind) void command("deck.create", {kind}).then(showResult);
            } else action(binding.name, binding.parameters || {});
          });
        }
      } else if (node.type === "form-field") {
        const name = el("span", props.label || "表单字段");
        const input = el("input"); input.readOnly = true; input.placeholder = "由功能表单提供";
        item.append(name, input);
      } else {
        item.textContent = safe(props.label || props.text || props.title);
        for (const child of node.children || []) item.append(visit(child));
      }
      if (props.hostStateBinding) {
        item.dataset.hostStateBinding = props.hostStateBinding;
        const value = el("small", lookup(state, props.hostStateBinding), "bound-state");
        item.append(value);
      }
      return item;
    };
    const projectPage = tree?.pages?.find(item => item.route === page);
    const scaffold = el("details", undefined, "project-structure");
    scaffold.append(el("summary", "Studio 项目组件结构"));
    for (const node of projectPage?.components || []) scaffold.append(visit(node));
    root.append(scaffold);
  }
  function lookup(value, path) {
    let current = value;
    for (const key of String(path || "").split(".")) current = current && current[key];
    return current === undefined || current === null || typeof current === "object" ? "" : current;
  }
  function pageShell(route) {
    const root = el("section", undefined, "page-shell");
    root.dataset.route = route;
    root.append(el("h2", titles[route] || route));
    renderTree(route, root);
    return root;
  }
  function occurrenceAction(item) {
    const args = {eventSeq:Number(item.eventSeq),expectedRevision:Number(item.revision)};
    if (item.originalOccurrenceKey) args.occurrenceKey = item.originalOccurrenceKey;
    void command("editor.open", args).then(showResult);
  }

  function renderTimeline(root) {
    const timeline = state.timeline || {}, items = Array.isArray(timeline.items) ? timeline.items : [];
    const controls = el("div", undefined, "actions");
    controls.append(actionButton("fb-runtime-today", "回到今日", "today"));
    controls.append(actionButton("fb-runtime-pan", "向后 1 小时", "panTimeline", {deltaMinutes:60}));
    controls.append(actionButton("fb-runtime-reset", "恢复 24 小时", "resetTimelineZoom"));
    root.append(el("p", `本地时区 ${safe(timeline.zoneId)} · 时间窗口 ${safe(timeline.hours)} 小时`, "muted"), controls);
    const create = el("div", undefined, "actions");
    for (const [kind, label] of [["ACTIVITY","活动"],["ALARM","闹钟"],["DEADLINE","截止"],["MEMO","Memo"]])
      create.append(createEventButton("fb-create-" + kind.toLowerCase(), "新建" + label, kind));
    root.append(create);
    const list = el("div"); list.id = "fb-timeline-events";
    for (const item of items) {
      const row = el("article", undefined, "event");
      row.dataset.eventSeq = safe(item.eventSeq); row.dataset.revision = safe(item.revision);
      row.dataset.occurrenceKey = safe(item.occurrenceKey);
      row.append(el("span", `${safe(item.title)} · ${safe(item.kind)} · ${safe(item.occurrenceKey)}`));
      row.append(button("", "打开", () => occurrenceAction(item)));
      list.append(row);
    }
    root.append(list);
  }

  const isoDate = date => `${date.getUTCFullYear()}-${String(date.getUTCMonth()+1).padStart(2,"0")}-${String(date.getUTCDate()).padStart(2,"0")}`;
  function renderMonth(root) {
    const month = state.month || {}, controls = el("div", undefined, "actions");
    controls.append(actionButton("fb-month-previous", "上个月", "browseMonth", {deltaMonths:-1}));
    controls.append(el("strong", safe(month.month)));
    controls.append(actionButton("fb-month-next-runtime", "下个月", "browseMonth", {deltaMonths:1}));
    root.append(controls, el("p", `选中日期：${safe(month.selectedDate)}`, "muted"));
    const grid = el("div", undefined, "grid"); grid.id = "fb-month-grid-runtime";
    const first = new Date(`${safe(month.gridFrom)}T00:00:00Z`);
    for (let index=0; index<42 && Number.isFinite(first.getTime()); index++) {
      const value = new Date(first.getTime() + index*86_400_000), date = isoDate(value);
      const count = (month.items || []).filter(item => item.date === date).length;
      const day = button("fb-date-" + date, `${value.getUTCDate()}${count ? " · " + count : ""}`,
        () => action("selectMonthDate", {date}));
      day.dataset.date = date;
      if (date === month.selectedDate) day.setAttribute("aria-pressed", "true");
      grid.append(day);
    }
    root.append(grid);
    const selectedItems = (month.items || []).filter(item => item.date === month.selectedDate);
    const list = el("div"); list.id = "fb-month-events";
    for (const item of selectedItems) {
      const row = el("article", undefined, "event");
      row.dataset.eventSeq = safe(item.eventSeq);
      row.dataset.occurrenceKey = safe(item.occurrenceKey);
      row.append(el("span", `${safe(item.title)} · ${safe(item.kind)} · ${safe(item.occurrenceKey)}`));
      row.append(button("", "打开实例", () => occurrenceAction(item)));
      list.append(row);
    }
    root.append(list, createEventButton("fb-month-create", "在选中日期新建活动", "ACTIVITY"));
  }

  function renderMemo(root) {
    root.append(el("p", `未完成：${safe(state.memoCount)} · 更多 ${safe(state.memoRemainingCount || 0)}`, "muted"));
    const list = el("div"); list.id = "fb-memo-entries";
    for (const item of state.memoEntries || []) {
      const row = el("article", undefined, "row"); row.dataset.eventSeq = safe(item.eventSeq);
      row.dataset.occurrenceKey = safe(item.occurrenceKey);
      const title = button("", item.title, () => occurrenceAction(item));
      const toggle = button("fb-memo-toggle-" + item.eventSeq,
        item.completed ? "撤销完成" : "标记完成", () => action("toggleMemo", {
          eventSeq:Number(item.eventSeq), expectedRevision:Number(item.revision),
          occurrenceKey:item.occurrenceKey, completed:item.completed
        }));
      toggle.setAttribute("aria-pressed", item.completed ? "true" : "false");
      row.append(title, toggle); list.append(row);
    }
    root.append(list, el("div", undefined, "actions"));
    root.lastChild.append(createEventButton("fb-memo-new", "新建 Memo", "MEMO"));
    if (state.memoTruncated) root.lastChild.append(actionButton("fb-memo-more", "载入更多", "loadMoreMemo"));
  }

  function renderAi(root) {
    root.append(el("p", state.aiLocked ? "存在待审核的受保护操作" : "AI 由 Host 调用；提案必须人工审核", "muted"));
    const history = el("section"); history.id = "fb-ai-history";
    for (const item of state.chatMessages || []) {
      const row = el("article", `${safe(item.role)}：${safe(item.content)}`, "message body-copy");
      row.dataset.messageId = safe(item.messageId); history.append(row);
    }
    root.append(history);
    const input = el("textarea"); input.id = "fb-ai-input"; input.rows = 3;
    input.maxLength = 4000; input.setAttribute("aria-label", "AI 消息");
    const send = button("fb-ai-send-runtime", "发送", () => {
      const text = input.value.trim(); if (text) action("sendChat", {text});
    }); send.disabled = state.chatBusy === true;
    root.append(input, send);
    const review = state.aiProposalReview;
    if (review && Array.isArray(review.proposals)) {
      const section = el("section"); section.id = "fb-ai-review";
      section.append(el("h3", "提案审核"));
      for (const proposal of review.proposals) {
        const item = el("article", `${safe(proposal.operation)} · ${safe(proposal.kind)} · ${safe(proposal.title)}`, "message");
        item.dataset.proposalIndex = safe(proposal.index); section.append(item);
        for (const [decision,label] of [["stage","写入草稿"],["reject","拒绝"],...[...(proposal.publishable ? [["publish","审核并发布"]] : [])]]) {
          const indices = decision === "supplement" ? [] : [Number(proposal.index)];
          item.append(button("fb-ai-"+decision+"-"+proposal.index, label, () => action("resolveAiProposal", {
            reviewId:review.reviewId, decision, indices
          })));
        }
      }
      root.append(section);
    }
    if (state.aiBatch?.phase && state.aiBatch.phase !== "IDLE")
      root.append(el("p", `AI 草稿批次：${safe(state.aiBatch.phase)} · ${safe(state.aiBatch.scopeCount)}`, "muted"));
  }

  function renderSettings(root) {
    const notification = state.notificationPermission || {}, alarm = state.exactAlarmPermission || {};
    const reconcile = state.reminderReconcile || {};
    root.append(el("p", `通知权限：${notification.allowed === true ? "已允许" : notification.required ? "未允许" : "不需要"}`, "muted"));
    root.append(el("p", `精确闹钟：${alarm.allowed === true ? "已允许" : alarm.required ? "未允许" : "不需要"}`, "muted"));
    root.append(el("p", `提醒协调：${safe(reconcile.status)} ${safe(reconcile.message)}`, "muted"));
    const controls = el("div", undefined, "actions");
    controls.append(actionButton("fb-notification", "请求通知权限", "requestNotificationPermission"));
    controls.append(actionButton("fb-exact-alarm", "精确闹钟设置", "openExactAlarmSettings"));
    controls.append(actionButton("fb-reconcile", "重建提醒调度", "reconcileReminders"));
    root.append(controls);
    for (const group of state.optionGroups || []) {
      const section = el("section", undefined, "option"); section.append(el("h3", group.label));
      for (const item of group.items || []) {
        const control = button("fb-option-" + item.id.replace(/[^a-zA-Z0-9_-]/g,"-"),
          `${safe(item.label)}${item.detail ? " · " + safe(item.detail) : ""}`,
          () => action("invokeOption", {id:item.id}));
        control.disabled = item.enabled === false; control.dataset.optionId = item.id; section.append(control);
      }
      root.append(section);
    }
    const diagnostics = el("details"); diagnostics.id = "fb-settings-diagnostics";
    diagnostics.append(el("summary", "提醒与系统诊断"));
    diagnostics.append(el("pre", JSON.stringify({reminderDiagnostics:state.reminderDiagnostics,
      systemDiagnostics:state.systemDiagnostics}, null, 2), "body-copy")); root.append(diagnostics);
  }

  function editorField(form, key, label, type = "text", choices = null) {
    const wrapper = el("label", undefined, "editor-field"); wrapper.append(el("span", label));
    let input;
    if (choices) {
      input = el("select");
      for (const [value,text] of choices) { const option=el("option",text); option.value=value; input.append(option); }
    } else if (key === "notes") { input=el("textarea"); input.rows=3; }
    else { input=el("input"); input.type=type; }
    input.id = "fb-edit-" + key; input.dataset.editorField = key;
    input.value = safe(form[key]);
    input.addEventListener("change", async () => {
      const edit=state.editing || {}, editor=edit.editor || {}, deck=edit.deck || {};
      const args={field:key,value:String(input.value)};
      const reply = editor.open && editor.sessionId
        ? await command("editor.patch", {sessionId:editor.sessionId,...args})
        : deck.open && deck.transient && deck.currentId !== null
          ? await command("deck.patch", {draftId:Number(deck.currentId),draftVersion:Number(deck.version||0),
              stateRevision:Number(deck.stateRevision||0),...args})
          : {status:"missing"};
      showResult(reply);
    });
    wrapper.append(input); return wrapper;
  }
  function renderEditor(root) {
    const editing=state.editing || {}, editor=editing.editor || {}, deck=editing.deck || {};
    const isFormal=editor.open === true, isNew=!isFormal && deck.open === true && deck.transient === true;
    const form=isFormal ? editor.form : isNew ? deck.form : null;
    if (!form) return;
    activeDialog=true;
    const panel=el("section", undefined, "editor"); panel.id="fb-event-editor";
    panel.setAttribute("role","dialog"); panel.setAttribute("aria-label","事件编辑器");
    panel.append(el("h3", isFormal ? "编辑正式事件" : "新建 · 待 Host 审核"));
    panel.append(editorField(form,"title","标题"));
    if (isNew) panel.append(editorField(form,"kind","类型","text",[["ACTIVITY","活动"],["ALARM","闹钟"],["DEADLINE","截止"],["MEMO","Memo"]]));
    if (["ACTIVITY","ALARM","MEMO"].includes(form.kind)) panel.append(editorField(form,"startText","开始"));
    if (["ACTIVITY","DEADLINE","MEMO"].includes(form.kind)) panel.append(editorField(form,"endText","结束/截止"));
    panel.append(editorField(form,"location","地点"), editorField(form,"notes","备注"));
    if (form.kind !== "MEMO") panel.append(editorField(form,"trackingEnabled","跟踪完成","text",[["true","开启"],["false","关闭"]]));
    panel.append(editorField(form,"reminderMinutes","提醒分钟","number"));
    panel.append(editorField(form,"recurrenceFreq","重复规则","text",[["NONE","不重复"],["DAILY","每天"],["WEEKLY","每周"],["MONTHLY","每月"]]));
    if (form.recurrenceFreq && form.recurrenceFreq !== "NONE") {
      panel.append(editorField(form,"recurrenceInterval","重复间隔","number"));
      panel.append(editorField(form,"recurrenceFrom","重复开始日期"));
      panel.append(editorField(form,"recurrenceUntil","重复结束日期"));
    }
    const controls=el("div",undefined,"actions");
    if (isFormal && editor.requiresRecurrenceScope) {
      for (const [scope,label] of [["SINGLE_OCCURRENCE","仅本次"],["THIS_AND_FUTURE","本次及以后"],["WHOLE_SERIES","整个系列"]])
        controls.append(button("fb-scope-"+scope,label,()=>command("editor.scope",{sessionId:editor.sessionId,scope})));
    }
    if (isFormal) controls.append(button("fb-edit-cancel","取消本次修改",async()=>showResult(
      await command("editor.discardChanges",{sessionId:editor.sessionId}))));
    controls.append(button("fb-edit-save",isFormal?"提交修改":"请求正式发布",async()=>{
      const reply=isFormal ? await command("editor.save",{sessionId:editor.sessionId})
        : await command("deck.commit",{draftId:Number(deck.currentId),draftVersion:Number(deck.version||0)});
      showResult(reply);
    }));
    if (isFormal) controls.append(button("fb-edit-delete","请求删除",async()=>showResult(
      await command("editor.delete",{sessionId:editor.sessionId}))));
    controls.append(button("fb-edit-close","关闭",async()=>showResult(isFormal
      ? await command("editor.close",{sessionId:editor.sessionId}) : await command("deck.close"))));
    panel.append(controls);
    if (editor.error || deck.error) panel.append(el("p",editor.error || deck.error,"muted"));
    root.append(panel);
  }

  function render() {
    const current=routes.includes(String(state.page).toUpperCase()) ? String(state.page).toUpperCase() : "TIMELINE";
    status.textContent = state.hostNotice || (state.hostDataReady ? "Core 状态已连接" : "等待 Core 状态");
    nav.replaceChildren();
    for (const route of routes) {
      const tab=button("fb-nav-"+route.toLowerCase(),titles[route],()=>action(route.toLowerCase()));
      tab.dataset.route=route; tab.setAttribute("aria-current",route===current?"page":"false"); nav.append(tab);
    }
    content.replaceChildren();
    const root=pageShell(current); root.id="fb-page-"+current.toLowerCase(); content.append(root);
    if (current === "TIMELINE") renderTimeline(root);
    else if (current === "MONTH") renderMonth(root);
    else if (current === "MEMO") renderMemo(root);
    else if (current === "AI") renderAi(root);
    else renderSettings(root);
    activeDialog=false; renderEditor(root);
    window.__fromBlankRuntime = {page:current, bridgeVersion:state.bridgeVersion,
      treePageCount:tree?.pages?.length||0, editorOpen:activeDialog, state};
  }

  window.addEventListener("message", event => {
    if (event.data !== "powercal-port" || !event.ports?.[0]) return;
    try { port?.close(); } catch {}
    port=event.ports[0];
    port.onmessage = message => {
      let frame; try { frame=typeof message.data === "string" ? JSON.parse(message.data) : message.data; } catch { return; }
      if (frame?.version !== 1 || !frame.payload) return;
      if (frame.type === "state") { state=frame.payload; render(); }
      else if (frame.type === "result") {
        const result=frame.payload; showResult(result);
        const resolve=pending.get(result.requestId); if (resolve) { pending.delete(result.requestId); resolve(result); }
      }
    };
    while (queued.length) sendFrame(queued.shift());
  });
  window.PowerCalSkin = {
    handleHostBack() {
      const editing=state.editing||{}, editor=editing.editor||{}, deck=editing.deck||{};
      if (editor.open && editor.sessionId) { void command("editor.close",{sessionId:editor.sessionId}); return true; }
      if (deck.open && deck.transient) { void command("deck.close"); return true; }
      return false;
    },
    sendCommand(name, requestId, args = {}) {
      return sendFrame({version:1,type:"command",payload:{name,requestId,...args}});
    }
  };
  render();
})();
