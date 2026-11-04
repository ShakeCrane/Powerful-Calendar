(() => {
  "use strict";
  // Blank, unstyled presentation: all real state arrives from the Core Host.
  // No database, calendar, AI or reminder business logic is duplicated here.
  const tree = window.__POWERCAL_PAGE_TREE_V1__;
  let port = null;
  let state = {};
  let localRoute = null;
  const status = document.getElementById("status");
  const navigation = document.getElementById("page-navigation");
  const content = document.getElementById("content");

  const elementFor = type => ({
    "text": "p", "container": "section", "button": "button", "date-button": "button",
    "scroll": "div", "form-field": "label", "month-grid": "div", "timeline": "section",
    "event-card": "article", "memo-row": "article", "chat-item": "article",
    "settings-row": "div", "resource-layer": "div"
  }[type] || "div");

  function applyStyles(element, props) {
    const style = props.style && typeof props.style === "object" ? props.style : {};
    if (typeof style.color === "string" && /^#[0-9a-f]{3,8}$/i.test(style.color))
      element.style.color = style.color;
    if (typeof style.backgroundColor === "string" && /^#[0-9a-f]{3,8}$/i.test(style.backgroundColor))
      element.style.backgroundColor = style.backgroundColor;
    if (typeof style.fontSize === "string" && /^\d{1,3}(px|rem|em|%)$/.test(style.fontSize))
      element.style.fontSize = style.fontSize;
    if (["left", "center", "right"].includes(style.textAlign))
      element.style.textAlign = style.textAlign;
  }

  function stateValue(path) {
    if (typeof path !== "string" || !path) return "";
    let value = state;
    for (const key of path.split(".")) {
      if (!value || typeof value !== "object" || !Object.hasOwn(value, key)) return "";
      value = value[key];
    }
    return value == null || typeof value === "object" ? "" : String(value);
  }

  function renderNode(node) {
    if (!node || typeof node !== "object") return document.createTextNode("");
    const props = node.props && typeof node.props === "object" ? node.props : {};
    const element = document.createElement(elementFor(node.type));
    element.classList.add("pc-component", "pc-component-" + String(node.type).replace(/[^a-z0-9-]/g, ""));
    element.dataset.componentId = String(node.id || "");
    element.dataset.componentType = String(node.type || "");
    if (typeof props.binding === "string") element.dataset.binding = props.binding;
    if (typeof props.className === "string") {
      for (const token of props.className.split(/\s+/))
        if (/^[A-Za-z_-][A-Za-z0-9_-]{0,31}$/.test(token)) element.classList.add(token);
    }
    applyStyles(element, props);
    if (node.type === "form-field") {
      const label = document.createElement("span");
      label.textContent = String(props.label || "Field");
      const input = document.createElement("input");
      input.type = "text";
      input.placeholder = String(props.placeholder || "");
      input.readOnly = true;
      element.append(label, input);
    } else {
      if (node.type === "button" || node.type === "date-button") element.type = "button";
      const text = props.label ?? props.text ?? props.title;
      if (typeof text === "string" && text) element.textContent = text;
    }
    if (typeof props.hostStateBinding === "string") {
      element.dataset.hostStateBinding = props.hostStateBinding;
      const value = document.createElement("span");
      value.className = "pc-bound-value";
      value.textContent = stateValue(props.hostStateBinding);
      element.append(value);
    }
    const hostAction = props.hostAction;
    if ((node.type === "button" || node.type === "date-button") &&
        hostAction && typeof hostAction.name === "string" &&
        hostAction.parameters && typeof hostAction.parameters === "object") {
      element.dataset.hostAction = hostAction.name;
      element.addEventListener("click", () => {
        if (!port) {
          status.textContent = "Host bridge is not connected";
          return;
        }
        const payload = { name: hostAction.name, ...hostAction.parameters };
        port.postMessage(JSON.stringify({ version: 1, type: "action", payload }));
      });
    }
    for (const child of Array.isArray(node.children) ? node.children : [])
      element.append(renderNode(child));
    return element;
  }

  function render() {
    const pages = Array.isArray(tree?.pages) ? tree.pages : [];
    const requested = localRoute || String(state.page || "TIMELINE").toUpperCase();
    const page = pages.find(item => item.route === requested) || pages[0] || null;
    status.textContent = state.connected === false ? "等待 Core 接口" :
      (state.page ? "Core 状态已连接 · " + String(state.page) : "等待 Core 接口 · 页面结构预览");
    navigation.replaceChildren();
    for (const item of pages) {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = String(item.title || item.route);
      button.dataset.pageRoute = String(item.route);
      button.setAttribute("aria-current", item === page ? "page" : "false");
      button.onclick = () => { localRoute = item.route; render(); };
      navigation.append(button);
    }
    content.replaceChildren();
    if (!page) {
      const prompt = document.createElement("p");
      prompt.textContent = "在 Skin Studio 中创建页面与组件。";
      content.append(prompt);
      return;
    }
    const heading = document.createElement("h2");
    heading.textContent = String(page.title || page.route);
    heading.dataset.pageTitle = page.route;
    content.append(heading);
    for (const node of Array.isArray(page.components) ? page.components : [])
      content.append(renderNode(node));
  }

  render();
  window.addEventListener("message", event => {
    if (event.data !== "powercal-port" || !event.ports?.[0]) return;
    if (port) { try { port.close(); } catch {} }
    port = event.ports[0];
    port.onmessage = message => {
      let data;
      try {
        data = typeof message.data === "string"
          ? JSON.parse(message.data) : message.data;
      } catch { return; }
      if (data?.version === 1 && data.type === "state") {
        state = data.payload || {};
        if (typeof state.page === "string" && tree?.pages?.some(page => page.route === state.page.toUpperCase()))
          localRoute = null;
        render();
      }
    };
  });
})();
