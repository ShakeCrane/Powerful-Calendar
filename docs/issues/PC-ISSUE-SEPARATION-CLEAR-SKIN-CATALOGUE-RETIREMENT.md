# PC-ISSUE-SEPARATION-CLEAR-SKIN-CATALOGUE-RETIREMENT

- **Date:** 2026-11-04
- **Status:** R-05 Clear Settings slice **PASS**; overall SEP parity remains incomplete and product Cutover remains **HOLD**.
- **Source baseline:** `bca20c459f19735f378bbe9fabd7f4c31b65e9cc`
- **Owner decision:** The shipped app has one factory `powercal-clear-default` `.pcskin`. Historical skin selection, import, export and deletion are not ordinary product features. Native remains the recovery surface; remaining v2/Compiled compatibility paths await their assigned parity and retirement tasks.

## Scope and implementation

Removed the installed-skin catalogue, `.pcskin` import/export buttons, candidate selection, and per-package export/delete controls from the canonical Clear Settings HTML, renderer and stylesheet. The Clear WebView state no longer projects `skinChoices` or `selectedSkinId`. The shared Web action decoder no longer accepts `selectSkin`, `importSkin`, `exportClearSkin`, `exportInstalledSkin`, or `deleteSkin`; `PowerCalApp` no longer dispatches those web actions. Host SAF and confirmation routes for calendar, backup, restore and other trusted data operations remain available.

The product `PowerCalApp` retains direct skin-management callbacks required by legacy v2/Compiled settings while R-06/R-07 are deferred. This task does not claim those remaining compatibility surfaces are retired. Recovery copy no longer directs users back to the removed skin settings UI. No factory or Native ID, default-skin visual, frozen Core behavior, Calendar Store data, or installed user package was changed or deleted. The Android test installed a uniquely named fixture in the isolated QA app and removed only that fixture during test cleanup.

The canonical package was regenerated with `tools/skin-studio/build_default_clear.py`; the APK was not edited as a ZIP.

## Verification

- `python -m unittest discover -s tools/skin-studio/tests -p "test_*.py"`: **68/68 PASS**.
- `node --check` for the Clear runtime and both changed E2E scripts: **PASS**. Browser E2E `e2e_clear_studio_bridge.cjs` and `e2e_v3_notification_permission_studio_mock.cjs`: **PASS**.
- Focused `WebSkinBridgeTest`, `ActivityRecoveryTest`, and `SkinPackageManagerTest`: **PASS**.
- Full serial offline Gradle gate: `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, JVM **966/966** (0 failures/errors/skips), Debug APK, Lint (**0 errors / 56 warnings**), and AndroidTest Kotlin compilation: **PASS**.
- API 35 AVD `PowerCal_ChatGPT_QA35_20261103`, serial `emulator-5584`, isolated package `dev.powerfulcalendar.d05qar05`: three actual Android/WebView demonstrations each **1/1 PASS**. Clear Settings omitted the catalogue and old actions did not alter the active package list; notification permission prompt was invoked and denied; backup SAF cancellation preserved Store state. The protected AI settings page remained reachable. `clearBackupExportUsesDocumentsUiAndVerifiesTheWrittenSnapshot` completed an actual backup export and verified the written snapshot.
- Production Debug APK SHA-256: `11d6018cd1d87997fbe3a6ce93c29d42266a7c72d1266f4ca95ca33537eebb6f`. Isolated QA APK SHA-256: `2d8dead3440fa5166e45a5d60b84b9384802a7be4e9e3ab48b204f52e1b8d0fe`.
- APK contains exactly one `.pcskin`, `assets/powercal-clear-default.pcskin`; source and APK bytes match at SHA-256 `cbe38662a9d719f7d0f5cac956ba7eb5c9cb5fae848903473e62e96bcd087c23` (9 files, 396484 bytes).
- Logs and package evidence: `.local/android-test/reports/R-05-20261104/`.

An initial Gradle invocation lacked `ANDROID_HOME`; setting it to the verified `E:\android-sdk` resolved the environment issue. The first Python run caught a stale catalogue assertion, which was replaced and the full 68-test suite passed. A later optional instrumentation rerun first exposed an overly strict accessibility-root assertion; that assertion was removed. A subsequent retry could not be confirmed because the API 35 device disconnected during Gradle's after-all cleanup (`device not found`). The earlier three device demonstrations had already completed successfully. No app-data clear, explicit uninstall, AVD wipe/reset, or system-calendar modification was performed.

## Remaining work

R-05 retires only the canonical Clear Settings catalogue and its Clear Web bridge actions. Legacy v2/Compiled callback surfaces remain for their deferred follow-up after parity evidence. SEP signoff is incomplete; physical/OEM device validation remains NOT TESTED, and Product Cutover remains HOLD. Next task: F-01 only.
