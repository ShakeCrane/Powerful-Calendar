(() => {
  "use strict";
  // Blank, unstyled presentation: all real state arrives from the Core Host.
  // No database, calendar, AI or reminder business logic is duplicated here.
  let port = null;
  let state = null;
  const status = document.getElementById("status");
  const content = document.getElementById("content");
  function render() {
    if (!state) return;
    const page = String(state.page || "TIMELINE");
    status.textContent = "Core 状态已连接 · " + page;
    content.replaceChildren();
    const prompt = document.createElement("p");
    prompt.textContent = "在 Skin Studio 中创建页面与交互，通过正式 Host API 调用核心功能。";
    content.append(prompt);
  }
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
        render();
      }
    };
  });
})();
