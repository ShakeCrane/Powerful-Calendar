# PowerCal visual asset inventory and Studio map

- Inventory date: 2026-11-05
- Baseline code: `b71cf0eedbd6dba9dafe0d9215ab33457258d045`
- Product baseline: the validated `powercal-clear-default.pcskin`; this inventory does not authorize a visual redesign.
- V-01 result: available Clear source and existing resources are mapped. A separate approved Claude artwork package was not found in this worktree.

## Asset provenance and disposition

| Source | Observed content | Disposition |
| --- | --- | --- |
| `design/` and `design/default-skin-01/assets/pc.css` | The directory is absent from this worktree and the named CSS source is not tracked here. `web/style.css` says its token source was copied from that path, but the original cannot be checked. | Do not claim source provenance is independently verified. The checked-in `web/clear-design.css` is the editable runtime source. |
| `docs/CLAUDE_SKIN_ASSET_API.md` | A v1 Compose/Android `SkinSlot` and `PowerCalSkinPack` integration guide. It explicitly says it did not generate or install Claude artwork and provides example names only. | Reference only. It uses Android drawable resources and is not the v3 Web `.pcskin` resource pipeline. Do not create a second Native skin or copy the example `ClaudeSkin` into production. |
| `docs/POWERCAL_GLASS_DESIGN.md` | Native Compose design and interaction document dated 2026-10-20. | Historical visual reference, not a Claude art package or the current Web page source. Reuse no gesture or layout that conflicts with the frozen current contract. |
| `docs/ui-evidence/*.png` | Six 360×800 screenshots for Timeline, Month and Draft Deck light/dark states. They show earlier Native Compose screens and test content. | Preserve as previous UI evidence. They are raster captures, not source artwork and not the current Web Clear target. |
| `app/src/main/res/drawable-nodpi/powercal_brand.png` | 1254×1254 RGBA pixel-art image. `app/src/main/AndroidManifest.xml` uses it as `icon` and `roundIcon`. | Launcher icon only. It is not in the default `.pcskin`, is not currently shown by Clear, and is not approved as an in-app brand mark by this inventory. |
| `tools/skin-studio/default-clear/source/web/fonts/InterVariable.woff2` and `LICENSE-Inter.txt` | The existing variable Inter font and SIL Open Font License 1.1 text; `clear-design.css` declares `PC Inter`. | Keep as the current Clear font resource; it is already bundled inside the default package. Chinese text falls through to Noto Sans SC/system fonts. |
| `tools/skin-studio/default-clear/source/web/clear-design.css` | Checked-in light/dark design tokens and component styles used by Clear. | Current editable Web design source and fallback baseline. No separate image or icon asset is required for its page presentation. |

`tools/skin-studio/default-clear/manifest.json` has empty `imagesLight` and `imagesDark`. The built package contains no image artwork; the only binary visual resource is the licensed Inter font. Existing automated image/font probes under `tools/skin-studio/tests/fixtures/` are test fixtures, not product assets.

## Screen-to-source map

The default Clear `source/project.json` has four editable color slots (`bg-canvas`, `accent`, `label`, `now`) and `layers: []`; it does not define `project.pages` or component-tree IDs. Therefore the IDs below are actual HTML/DOM anchors, not invented Studio component IDs. S-07 verified Studio source editing and package round-trip for this project. When changing current Clear, use its Studio-imported source files and preserve the existing imperative DOM and Host bindings.

The stylesheet identifies its baseline as “PowerCal Default Skin 01 — Clear” and “Apple-inspired Minimalism × Liquid Glass.” This is the checked-in Clear direction, not evidence of separate Claude design approval. `project.json` also retains an old `authoring.productStatus` string mentioning Native fallback; it is stale authoring metadata and does not describe the R-06/R-07-verified runtime.

| Screen / element | Existing HTML/DOM anchor (Studio tree ID: none) | Source and current resources | Adaptation and evidence to retain |
| --- | --- | --- | --- |
| Shared header and navigation | `#app`, `#dayTitle`, `#dateSubtitle`, `nav.clear-tabbar`; route IDs `timelinePage`, `monthPage`, `memoPage`, `aiPage`, `settingsPage` | `web/index.html`, `web/style.css`, `web/clear-design.css`; `PC Inter`, no image | System dark preference toggles the `dark` class; preserve all five routes and labels. Reduced-motion CSS disables animation/transition. Check 2× text and TalkBack order before any typography edit. |
| Timeline | `#timelinePage`, `#timelineSurface`, `#tlAxis`, `#timeLabels`, `#timeNow`, `#dateDeadlineBar`, `#timelineLeft`, `#timelineRight`, `#axisScroll` | `web/app.js` produces the data DOM; `web/style.css` owns layout; `web/clear-design.css` owns tokens. No background image. | Keep the actual time geometry and event labels above any decoration. Preserve the semantic event-kind colors; do not encode time ticks or business state in a bitmap. Reduced motion uses the shared media rule. Recheck dark, 2× font, true-time layout and measured contrast. |
| Month | `#monthPage`, `#monthTitle`, `#monthGrid`, `#monthAgenda`, `#viewMonthDate` | `web/index.html`, `web/app.js`, `web/style.css`; CSS cells and dots, no image | Keep repeat instances and selected-day state data-driven. Avoid painting dates/events into static imagery. Recheck dark colors, 2× text wrapping, 44px controls and contrast. |
| Memo / to-do | `#memoPage`, `.tabs`, `#memoList`, `#memoLimit` | `web/app.js`, `web/style.css`; no image | Keep Open/Done and completion state from Host data. Preserve reduced motion and visible focus; verify 2× text and state contrast. |
| AI | `#aiPage`, `#aiBatchControls`, `#aiBatchStatus`, `#chatMessages`, `#chatForm`, `#chatInput`, `#chatSend` | `web/app.js`, `web/style.css`; no image | Keep conversation and review status text as live DOM, preserve IME and scrolling. Do not bake provider/error state into assets. Recheck dark, reduced motion, 2× font and contrast. |
| Settings / permissions | `#settingsPage`, `#exactAlarmPermissionStatus`, `#notificationPermissionStatus`, `#reminderDiagnostics`, `#systemDiagnostics`, `#settingsOptionGroups`, `#openAiSettings` | `web/app.js`, `web/style.css`; no image | Permission and reminder states remain Host-projected text/actions. Use scalable rows and preserve focus/status announcements; never put secrets or state in an image. |
| Event editor | `#skinSheet`, `#sheetFields`, `#sheetActions`, `#sheetStatus` | `web/edit.js` creates form controls; `web/style.css` styles them; no image | Preserve dynamic four-kind fields, validation/errors and IME scroll. Recheck 2× text, dark surfaces, reduced motion and contrast before changing sheet styling. |
| Draft review / trusted approval | The Web trigger is `data-action="openDeck"`; Host dispatch is in `WebSkinHost.kt` and `PowerCalApp.kt`. There is no corresponding Clear DOM subtree or Studio component ID. | `SkinnableDraftDeck.kt` selects a deck renderer; the normal fallback is `NativeDraftDeckHost.kt` / `NativeDraftDeckDialog`. The old `DECK_BACKDROP` and `COMPLETION_EMBLEM` slots are Android Compose assets, not Web `.pcskin` resources. | Host-owned trusted approval surface; Studio cannot currently map Clear Web resources onto this Compose view. Keep the approval separate from decoration. This is an explicit visual-ownership limitation for any claim of a fully Studio-editable approval surface. |

## Shared visual baseline and verification limits

- Light tokens include white canvas `#FFFFFF`, grouped surface `#F3F3F6`, text `#16171B`, accent `#2A62E0`, and current-time `#D93A2E`. Event kinds have distinct alarm, activity, deadline and memo colors.
- Dark tokens are defined in `clear-design.css`; `app.js` follows `prefers-color-scheme`. `style.css` contains a `max-width:320px` layout rule and a global `prefers-reduced-motion:reduce` rule.
- The source comments specify at least 4.5:1 for white text on the current-time fill, but this is not a measured contrast report for every state. No blanket WCAG contrast PASS is inferred.
- F-09 previously exercised font scaling, reduced motion and narrow layouts on API 35; the exact test status and the outstanding TalkBack traversal gap remain in `CURRENT.md` and `SINGLE_SKIN_ACCEPTANCE.md`. This V-01 source inventory did not rerun those tests.
- No custom Claude image, icon set, typeface, motion asset or screen design source was available to import. V-02 may produce a Visual RC from the unchanged validated Clear fallback, but must label custom visual integration incomplete. Do not synthesize missing artwork or alter Core/Host to simulate it.

## Studio handoff

Editable project: `tools/skin-studio/default-clear/`. Import its manifest/source in Studio, edit `web/clear-design.css`, `web/style.css`, HTML or asset files through the Studio project editor, preserve `web/app.js`/`web/edit.js` Host calls, then build/export and re-import the `.pcskin`. Existing S-07 evidence covers a harmless Settings source edit, save/reopen, package export/import and restoration. The source tree is not a component-ID-based layout; do not claim it has a Studio page/component tree until one is actually present and verified.
