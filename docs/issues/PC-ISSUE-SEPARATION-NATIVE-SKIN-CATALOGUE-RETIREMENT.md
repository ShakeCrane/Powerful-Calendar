# PC-ISSUE-SEPARATION-NATIVE-SKIN-CATALOGUE-RETIREMENT

- **Date:** 2026-11-04
- **Status:** R-04 Native Tools slice **PASS**; overall SEP parity remains incomplete and product Cutover remains **HOLD**.
- **Source baseline:** `88c43ac22345bf729305f50aaba1dc4ff65228a2`
- **Owner decision:** The shipped app has one factory `.pcskin`; Native is retained for recovery and trusted host operations. Historical installed-skin selection/export is no longer a product goal.

## Scope and implementation

Removed the `SectionCard("皮肤", ...)` catalogue from `NativeToolsPage`, including candidate selection, package import, installed-package export/delete controls, candidate-Clear export and the obsolete third-party-skin instructions. The page no longer accepts skin catalogue data or catalogue callbacks; `PresentationSkinRuntime.ToolsPage` no longer passes those values to NativeToolsPage.

`PowerCalApp` Host actions and `SettingsVisualContext` fields remain because Clear v3 and legacy v2/Compiled settings still call them before their assigned retirement tasks. `NativeSkinCatalogReview` remains in use by those Host paths. This R-04 change does not claim the overall application catalogue is gone; Clear Settings is R-05 and the remaining v2/Compiled surface is deferred to R-06/R-07 after parity evidence.

No Native recovery, backup, restore, ICS, system-calendar migration, database migration, reminder permissions/scheduling, AI credential controls, trusted approval UI, default package, or Core behavior was removed. No installed package or user file was deleted.

## Verification

- Focused Compose/Host tests: `NativeToolsPageProtectedSlotsComposeTest` **4/4 PASS** and `SkinPackageExportFlowTest` **1/1 PASS**. They assert the Native page omits skin management, preserves other tools, leaves an installed test package byte-identical and inactive, and preserves Calendar Store state.
- Forced serial offline JVM rerun: **966/966 PASS**, 0 failures, 0 errors, 0 skipped. Evidence log: `.local/android-test/reports/R-04-20261104/jvm-full-rerun.log`.
- Full build gate: `verifySkinSourceBoundary`, `verifyNativeSkinBoundary`, `assembleDebug`, `lintDebug`, and `compileDebugAndroidTestKotlin` **PASS**. Lint: 0 errors, 56 warnings. Evidence log: `.local/android-test/reports/R-04-20261104/full-gate-final2.log`. AndroidTest sources were subsequently recompiled and run by the connected device task below.
- API 35 AVD `PowerCal_ChatGPT_QA35_20261103`, serial `emulator-5584`, Android 15. `WebSkinRendererRecoveryInstrumentedTest.clearRendererExitPersistsNativeRecoveryAndPreservesStore` **1/1 PASS** after a real WebView renderer termination. Native recovery has no skin import or candidate export; ICS, backup, restore, notification permission, and exact-alarm controls remain actionable. Formal records, pending count, commit count, identity count and next sequence match their pre-fault snapshots.
- Device evidence: `.local/android-test/reports/R-04-20261104/android-recovery-demo-final2.log`, `device-identity.txt`, and `app/build/outputs/androidTest-results/connected/debug/TEST-PowerCal_ChatGPT_QA35_20261103(AVD) - 15-_app-.xml`.
- Production Debug APK (`dev.powerfulcalendar`) SHA-256: `33E831A4A194A72FA35A0B626C58183597DA7FB8120DDB1E2E432ACCF5B66431`. Isolated QA Debug APK (`dev.powerfulcalendar.d05qar04`) SHA-256: `E2954B09FD676229DC3E7D2D217CE6052C7503C7354C46845FFC8D57D994C225`.

The Android test used a newly installed unique QA package. It did not clear app data, uninstall an existing app, wipe or reset the AVD, or modify the system calendar. The test's uniquely named temporary database was removed by its fixture cleanup. Initial failures were test expectation/import issues, not product regressions; their summaries and logs are retained in the report directory, and corrected reruns passed.

## Remaining work

R-04 retires only the Native Tools catalogue. Clear Settings still contains the old catalogue and remains R-05. Host actions used by Clear/v2 and `NativeSkinCatalogReview` are retained until their assigned follow-up tasks. Native recovery remains available. No SSA status was changed and no product Cutover authorization was implied.
