# PowerCal Skin Studio v3 — 源码/预览/导出一致性验收

日期：2026-10-22。修改范围：`tools/skin-studio/` 与 Android
`WebSkinHost.kt` 素材 MIME 对齐。未修改或跟踪 `design/default-skin-01/`。

## 不变量

1. 编辑器内 canonical Web 源文件和二进制素材的字节数组是唯一真源；
   `project.json` 只保存编辑元数据和声明式材料槽。
2. 所见预览经 `/api/preview-v3` 校验与 Android 相同的 v3 ZIP 文件大小、
   路径、SHA-256、资源和项目限制；由本地临时 URL 读取源文件，不通过
   `srcdoc` 重新生成另一版 HTML/CSS/JS。
3. `/api/export-v3` 读取完全相同的文件集，重新计算清单哈希；
   `/api/import-v3` 必须逐文件保留源码/素材字节。
4. Studio 模拟真实 WebMessagePort v1，状态字段、时间轴发生实例、
   `optionGroups` 和动作都按照已发布宿主契约编码；高权限操作不模拟为成功。
5. 简单材料改变最终 CSS，图层几何改变最终 CSS/HTML；源代码编辑即时成为
   预览/导出的真源，没有「忘记应用源码」的第二状态。
6. `fullCanvas=false` 时 Android 原生页面覆盖 Web：Studio 拒绝将其
   伪称所见即所得运行包，必须显式启用完整画布。
7. 导入的额外 `web/` 与 `assets/` 文件在无相关修改时逐字节保留。
8. 编辑器预览为 sandbox iframe + CSP + Mock Host，Android 为独立 WebView。
   不宣称跨浏览器引擎像素完全一致，也不宣称已完成真机触摸、TalkBack、
   系统状态栏、中文字体替换和输入法验收。

## 已覆盖的自动化场景

- Python: v1/v2 兼容、v3 样板导出/导入、错误文件、资源越界、来源校验、
  预览 URL 拒绝穿越、逐文件预览内容与 ZIP 文件内容比对。
- Node: Studio 编辑器 JS 与示例皮肤 JS 的语法检查。
- Chromium+Puppeteer: 真实 v3 iframe 完整加载，左侧刻度、事件、同级选项
  动态显示；修改材质、生效的 CSS 图层拖拽、PNG 图像真实解码、
  动作按钮图标替换；导出 ZIP 后重新导入并确认每个文件字节与图层/素材
  元数据保留。此测试不需要应用真实日历权限。
- Android Gradle: 皮肤边界、全量 JVM 测试、APK 编译和 Lint。

## 尚待设备级证据

真实 Android WebView 的渲染截图与 Studio 相同布局规格对照；
深浅色/减少动画实际生效；重复发生与快速滚动；
中途崩溃/皮肤恢复；系统安全区与不同屏幕比例；
无障碍导航；第三方字体在特定 WebView 版本的差异。
这些未做设备级验证前，不得宣称完全像素级相同。

## 启动和验证

```powershell
python tools/skin-studio/skin_studio.py serve --port 8765
python -m unittest discover -s tools/skin-studio/tests -v
node --check tools/skin-studio/v3-studio.js
.\gradlew.bat :app:verifySkinSourceBoundary :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --console=plain
```

浏览器端到端：`tools/skin-studio/tests/e2e_v3_browser.cjs`；
需要 Puppeteer，设置 `PUPPETEER_PATH` 与 `STUDIO_URL` 后运行。
