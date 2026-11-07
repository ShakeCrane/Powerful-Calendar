# Native 0.9.5 Draft Deck persisted-card surface parity

Status: **NARROW PASS** for the persisted-card stack/search/browse surface. The complete Draft Deck and overall Native-to-Web migration remain **PARTIAL / IN_PROGRESS**. Product Cutover remains **HOLD**.

## Baseline

- Frozen source SHA: `b1f7629f0c52812e1887443872f43aa6c49908c4`.
- Frozen APK SHA-256: `DCD35A1CB0A5DED6D51ACB0B9275A2EE3BF1D8D107631AA640A58D73FC626F76`.
- Web QA: `PowerCal_Wave2_QA35_20261104` / `emulator-5556`, API 35, 1080×2400, 420 dpi, WebView 124.0.6367.219.
- Native reference AVD: `PowerCal_Native095_MenuReference_20261107` / `emulator-5560`, API 35, same display geometry. The frozen APK identity was verified from the raw artifact; the reference AVD was not reset or wiped in this slice.
- Product implementation commit: `26cea3e974c5f695b940d8b26c9fa19a8641cc9c` (`fix(皮肤): 对齐冻结原生草稿卡组`).

## Implementation

- Replaced the ordinary full-height editor presentation for a persisted Draft Deck with the measured Native card stack: dark backdrop, title and search pills, stacked card surface, collapsed summary, expanded edit form, sequence/type/operation badges, paging, discard/undo and publish controls.
- Hid the extra manual “暂存” button. Existing Core/Host checkpoint and close-flush behavior remains responsible for preserving edits; no Calendar Store behavior or business rule was changed.
- Added the existing Core draft operation to the Host presentation projection so the card may label CREATE/REPLACE without Skin-side inference.
- Extended the existing visual-only modal presentation signal with a bounded scrim alpha. The Web skin reports the actual CSS backdrop alpha; the Host paints only transparent status/navigation-bar insets. Old v1 boolean payloads still decode with the old 156 alpha default. No Host action or write authority was added.

## Evidence and checks

- Actual Android instrumentation `DraftDeckSearchWebViewInstrumentedTest#actualClearWebViewSearchSelectNavigateAndProposeReplaceWithoutPublishing`: `1/1 PASS` in gesture mode and `1/1 PASS` in three-button mode. WebView touch performs card selection, search, swipe/step navigation, expansion/collapse and replacement proposal. The isolated Store counts remain unchanged for browsing; the replacement remains a proposal and is not published.
- Native system-bar sample pixels: `(56,57,58)`. Web screenshot pixels: `(58,58,58)` in gesture and three-button modes; maximum channel difference is 2. Header/search/card screen geometry is within 2.4 physical pixels of measured Native geometry, with adjusted card-center delta below 1 pixel.
- Month-picker compatibility test after the presentation-message change: `NativeClearMonthOccurrenceParityInstrumentedTest#monthYearMonthDateAndTodayTransitionsMatchWithoutMovingTimelineOrWritingStore`, `1/1 PASS`.
- Full serial Gradle gate: `BUILD SUCCESSFUL`; 142 JUnit XML files, 988 tests, 0 failures/errors/skips. Source boundaries, APK build, lint and AndroidTest Kotlin compilation passed.
- Skin Studio Python suite: `90/90 PASS`; changed JS syntax checks passed; Preview Mock `e2e_v3_deck_lifecycle.cjs` passed for automatic close checkpoint, reopen, discard/undo and review boundaries.
- After restoring unrelated Timeline source hunks to baseline, the official `build_default_clear.py` rebuilt and verified the final Draft Deck package. Only `assets/powercal-clear-default.pcskin` is packaged. ID remains `powercal-clear-default`; 10 files / 420396 bytes; source/APK package SHA-256 is `dd49636784dfab7ee356a114d3d4db0963087f37a4e1f0bc3ce505b766f9d14e`. Debug APK SHA-256 is `3E95E2992AD89EAE7BC8C0B59084F39504A27432A3533532BB78B1D6AD58EA56` (67,984,444 bytes).
- The exact rebuilt package passed the focused Deck Android instrumentation 1/1 on the named QA AVD. The connected Gradle task then queued `emulator-5554` despite the supplied serial property; it was interrupted before a second result. The reference AVD remained on its `0.9.5-rc.1` / 905 app package and was not wiped or replaced.
- The integrated Browser E2E currently fails at an independent Timeline initial-left-wing folding expectation (items 201/202 are clipped); this is tracked as a separate follow-up, not counted as Draft Deck evidence. Deck-specific lifecycle and search/replace Preview Mock E2Es pass.
- Detailed command record and screenshots are in `.local/android-test/reports/native095-draftdeck-20261107/manifest.md`.

The Android test uses a per-run UUID-isolated app context/database and deletes only that namespace in `finally`; it does not clear application data, wipe an AVD, touch system calendar records or change user data.

## Remaining scope

This does **not** pass the full Draft Deck. Empty and transient states, AI-locked read-only behavior, AI batch progress, restore/conflict review, delete confirmation, completion/undo snapshots, IME/accessibility and all-state screenshot parity remain open. The gesture-navigation run where the system edge inset covered the 24 CSS px side gutter reports outside swipe `NOT TESTED`; the three-button run exercised it. Native recovery and single-package identity remain unchanged.

Next slice: compare frozen Native empty/transient and AI-locked Deck states against the same default Web v3 runtime, preserving the existing Host/Core actions.
